# syntax=docker/dockerfile:1
# ======================================================================
# MirrorService – Java 25 mit JDK-AOT-Cache (Project Leyden, JEP 483/514/515)
#
#   Spring AOT  +  Thin-JAR-Layer  +  JDK-25-AOT-Cache  +  SerialGC  +  Virtual Threads
#
# Der AOT-Cache ist der Nachfolger von AppCDS: Klassen liegen geladen und gelinkt
# vor (JEP 483), dazu kommen Methoden-Profile aus dem Trainingslauf (JEP 515).
# ======================================================================

# Temurin: JDK- und JRE-Image mit demselben Tag enthalten denselben JVM-Build
# (Pflicht für den AOT-Cache). Alle Stages nutzen deshalb dasselbe TEMURIN_TAG.
# Pinnen mit einem Release-Tag wie 25.0.5_8, nie zwei unabhängige Digests.
ARG TEMURIN_TAG=25

# ============================
# 1. Build Stage (Java 25)
# ============================
FROM eclipse-temurin:${TEMURIN_TAG}-jdk AS build

# Das Temurin-JDK-Image bringt kein Maven mit → Maven-Wrapper (.mvn/wrapper, Maven 3.9.x).
# Der Build läuft als root: Wrapper-Distribution und Repository liegen beide unter
# /root/.m2 und damit im Cache-Mount.
# -Dmaven.test.skip=true statt -DskipTests: Tests werden gar nicht erst kompiliert.
ARG MVN_OPTS="-q -Dmaven.test.skip=true"

WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn ./.mvn
# → Nur pom.xml und Wrapper: Dependency-Layer bleibt gecached, solange sich die pom.xml nicht ändert.
RUN --mount=type=cache,target=/root/.m2 ./mvnw ${MVN_OPTS} dependency:go-offline

COPY src ./src
# → Sourcecode zuletzt, damit Code-Änderungen das Dependency-Layer nicht invalidieren.
# → compile ist Voraussetzung für spring-boot:process-aot (Bean-Definitionen,
#   Proxies, Reflection-Hints vorab generieren), package baut das JAR inkl. AOT-Klassen.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw ${MVN_OPTS} compile spring-boot:process-aot package

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

# ============================
# 2. AOT-Training (Java 25 JRE)
# ============================
# Das Temurin-JRE ist ein eigenes jlink-Image: Seine lib/modules unterscheidet sich vom
# JDK. Einen im JDK trainierten Cache verwirft die JRE-JVM ("size has changed").
# Deshalb mit genau dem Runtime-Image trainieren.
FROM eclipse-temurin:${TEMURIN_TAG}-jre AS train

# COPY erhält die mtimes aus cp -a → gleiche Pfade, Größen und mtimes wie zur Laufzeit.
COPY --from=build /app /app

# AOT-Cache trainieren (JEP 514: Training + Assembly in einem Schritt).
# - spring.context.exit=onRefresh: Context komplett aufbauen, danach sauber beenden.
# - GC und UseCompactObjectHeaders MÜSSEN wortgleich im ENTRYPOINT stehen (nie in
#   JAVA_TOOL_OPTIONS), sonst verwirft die JVM den Cache still.
RUN java -XX:AOTCacheOutput=/app/app.aot \
         -XX:+UseSerialGC \
         -XX:+UseCompactObjectHeaders \
         -Dspring.aot.enabled=true \
         -Dspring.context.exit=onRefresh \
         -jar /app/app.jar

# ============================
# 3. Runtime Stage (Java 25)
# ============================
FROM eclipse-temurin:${TEMURIN_TAG}-jre

LABEL org.opencontainers.image.title="MirrorService" \
      org.opencontainers.image.description="Spring Framework based service mirroring requests (Java 25, AOT cache, virtual threads)" \
      org.opencontainers.image.version="0.3.1-SNAPSHOT" \
      org.opencontainers.image.vendor="wlanboy" \
      org.opencontainers.image.source="https://github.com/wlanboy/MirrorService" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre"

WORKDIR /app

# UID 185 ohne Eintrag in /etc/passwd → GID 0 (wie zuvor im UBI-Image). Helm-Chart
# (runAsUser: 185, fsGroup: 185) bleibt unverändert gültig.
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
COPY --from=train --chown=185:185 /app/app.aot /app/app.aot

# 8003 = server.port (API), 8081 = management.server.port (Actuator: Probes, Prometheus)
EXPOSE 8003 8081

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
