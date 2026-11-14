# syntax=docker/dockerfile:1
# ======================================================================
# MirrorService – Java 25 mit JDK-AOT-Cache (Project Leyden, JEP 483/514/515)
#
#   Spring AOT  +  Thin-JAR-Layer  +  JDK-25-AOT-Cache  +  SerialGC  +  Virtual Threads
#
# Der AOT-Cache ist der Nachfolger von AppCDS: Klassen liegen geladen und gelinkt
# vor (JEP 483), dazu kommen Methoden-Profile aus dem Trainingslauf (JEP 515).
# ======================================================================

# ============================
# 1. Build Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi10/openjdk-25:latest AS build

# Das UBI-Image läuft als User 185 mit HOME=/home/default. Ohne explizites
# maven.repo.local würde Maven ~/.m2 nutzen und der Cache-Mount auf /root/.m2
# bliebe leer. uid=185 macht den Mount für den Build-User beschreibbar.
# -Dmaven.test.skip=true statt -DskipTests: Tests werden gar nicht erst kompiliert.
ARG MVN_OPTS="-q -Dmaven.test.skip=true -Dmaven.repo.local=/root/.m2/repository"

# WORKDIR würde die Verzeichnisse root-owned anlegen → vorher als root für 185 anlegen.
USER root
RUN mkdir /build /app && chown 185:0 /build /app
USER 185

WORKDIR /build

COPY pom.xml .
# → Nur die pom.xml: Dependency-Layer bleibt gecached, solange sich die pom.xml nicht ändert.
RUN --mount=type=cache,target=/root/.m2,uid=185 mvn ${MVN_OPTS} dependency:go-offline

COPY src ./src
# → Sourcecode zuletzt, damit Code-Änderungen das Dependency-Layer nicht invalidieren.
# → compile ist Voraussetzung für spring-boot:process-aot (Bean-Definitionen,
#   Proxies, Reflection-Hints vorab generieren), package baut das JAR inkl. AOT-Klassen.
RUN --mount=type=cache,target=/root/.m2,uid=185 \
    mvn ${MVN_OPTS} compile spring-boot:process-aot package

# Extraktion OHNE --launcher: Thin-JAR (application/app.jar) mit Class-Path-Manifest
# auf lib/*.jar. Nur so kommen alle Klassen über den System-Classloader aus JARs und
# landen im AOT-Cache (beim JarLauncher-Layout nicht).
RUN JAR=$(ls target/*.jar | grep -v original) && \
    java -Djarmode=tools -jar "$JAR" extract --layers \
         --application-filename app.jar --destination extracted

# Laufzeit-Layout unter /app nachbauen. Der AOT-Cache prüft Pfad, Größe und mtime
# jedes JARs – deshalb identische Pfade wie in der Runtime-Stage und cp -a (mtime bleibt).
WORKDIR /app
RUN mkdir -p config data && \
    touch config/.keep data/.keep && \
    chmod -R g+w config data && \
    for layer in dependencies observability-dependencies spring-boot-loader \
                 snapshot-dependencies application-resources application; do \
        [ -d /build/extracted/$layer ] && cp -a /build/extracted/$layer/. /app/ ; \
    done

# AOT-Cache trainieren (JEP 514: Training + Assembly in einem Schritt).
# - spring.context.exit=onRefresh: Context komplett aufbauen, danach sauber beenden.
# - GC und UseCompactObjectHeaders MÜSSEN wortgleich im ENTRYPOINT stehen (nie in
#   JAVA_TOOL_OPTIONS), sonst verwirft die JVM den Cache still.
# - Build- und Runtime-JVM brauchen denselben Build (beide :latest gemeinsam ziehen).
RUN java -XX:AOTCacheOutput=/app/app.aot \
         -XX:+UseSerialGC \
         -XX:+UseCompactObjectHeaders \
         -Dspring.aot.enabled=true \
         -Dspring.context.exit=onRefresh \
         -jar /app/app.jar

# ============================
# 2. Runtime Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi10/openjdk-25-runtime:latest

LABEL org.opencontainers.image.title="MirrorService" \
      org.opencontainers.image.description="Spring Framework based service mirroring requests (Java 25, AOT cache, virtual threads)" \
      org.opencontainers.image.version="0.3.1-SNAPSHOT" \
      org.opencontainers.image.vendor="wlanboy" \
      org.opencontainers.image.source="https://github.com/wlanboy/MirrorService" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.base.name="openjdk-25:latest"

WORKDIR /app

USER 185

COPY --from=build --chown=185:0 /app/config /app/config
COPY --from=build --chown=185:0 /app/data /app/data

# Layer-Reihenfolge gemäß layers.xml (seltenste Änderung zuerst)
COPY --from=build --chown=185:185 /build/extracted/dependencies/ ./
COPY --from=build --chown=185:185 /build/extracted/observability-dependencies/ ./
COPY --from=build --chown=185:185 /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=185:185 /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=185:185 /build/extracted/application-resources/ ./
COPY --from=build --chown=185:185 /build/extracted/application/ ./
COPY --from=build --chown=185:185 /app/app.aot /app/app.aot

COPY --chown=185:0 containerconfig/application.yml /app/config/application.yml
# → Wird über die Spring-Default-Location file:./config/ (WORKDIR /app) automatisch geladen
#   und überschreibt einzelne Keys der eingebauten application.yml. Bewusst KEIN
#   spring.config.location: das würde die eingebaute Konfiguration komplett ersetzen.

EXPOSE 8003

# Aufteilung der JVM-Flags:
# - ENTRYPOINT: nur, was zum AOT-Cache gehört und nie überschrieben werden darf
#   (Cache, Spring AOT, GC, Header). GC und Header wortgleich zum Trainingslauf.
# - ENV JAVA_TOOL_OPTIONS: alles Dimensionierbare als Default. Kommandozeilen-Flags
#   schlagen JAVA_TOOL_OPTIONS, deshalb steht davon nichts im ENTRYPOINT. Helm (javaOpts),
#   Manifeste und Compose ersetzen die Variable komplett, also immer den vollen Satz setzen.
#   GC- und Header-Flags gehören nie in JAVA_TOOL_OPTIONS.
#
# JAVA_TOOL_OPTIONS (Default):
# -Xms64m / -Xmx192m           fester Heap statt RAM-Prozent (unabhängig vom Container-Limit)
# -Xss512k                     kleinere Plattform-Thread-Stacks
# -XX:MaxMetaspaceSize=64m     reicht mit Class-Cache
# -XX:ReservedCodeCacheSize=48m passt zu C1-only
# -XX:TieredStopAtLevel=1      nur C1: I/O-gebundene Last, spart CPU und Code-Cache
# -XX:ActiveProcessorCount=2   = CPU-Limit, sonst dimensioniert die JVM nach Host-Kernen
# -XX:MaxDirectMemorySize=64m  deckelt NIO-Direct-Buffers (Jetty)
# -XX:+ExitOnOutOfMemoryError  bei OOM sofort beenden → Orchestrator startet neu
# -XX:-UsePerfData             kein /tmp/hsperfdata_* (readOnlyRootFilesystem-tauglich)
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -Xss512k -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -XX:ActiveProcessorCount=2 -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData"

# ENTRYPOINT:
# -XX:AOTCache                 AOT-Cache aus der Build-Stage (vorgeladene + gelinkte Klassen, Profile)
# -Dspring.aot.enabled         vorberechnete Bean-Definitionen statt Classpath-Scanning
# -XX:+UseSerialGC             kleiner Heap; muss zum Trainingslauf passen
# -XX:+UseCompactObjectHeaders (JEP 519) 8- statt 12-Byte-Objekt-Header; muss zum Trainingslauf passen
# -jar /app/app.jar            Thin-JAR, Classpath kommt aus dem Manifest (lib/*.jar)
# Virtual Threads werden über spring.threads.virtual.enabled in application.yml aktiviert.
ENTRYPOINT ["java", \
  "-XX:AOTCache=/app/app.aot", \
  "-Dspring.aot.enabled=true", \
  "-XX:+UseSerialGC", \
  "-XX:+UseCompactObjectHeaders", \
  "-jar", "/app/app.jar"]
