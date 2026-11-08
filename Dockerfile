# syntax=docker/dockerfile:1
# ======================================================================
# MirrorService – Java 25 mit JDK-AOT-Cache (Project Leyden, JEP 483/514/515)
#
#   Spring AOT  +  Thin-JAR-Layer  +  JDK-25-AOT-Cache  +  Virtual Threads
#
# Unterschied zu ./Dockerfile (klassisches AppCDS):
#   Der AOT-Cache ist der Nachfolger von AppCDS. Er speichert Klassen nicht nur
#   geparst, sondern bereits geladen + gelinkt (JEP 483) und enthält zusätzlich
#   Methoden-Profile aus dem Trainingslauf (JEP 515), damit der JIT früher
#   optimierten Code erzeugt.
# ======================================================================

# ============================
# 1. Build Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi10/openjdk-25:latest AS build

# Das UBI-Image läuft als User 185 mit HOME=/home/default. Ohne explizites
# maven.repo.local würde Maven ~/.m2 nutzen und der Cache-Mount auf /root/.m2
# bliebe leer. uid=185 macht den Mount für den Build-User beschreibbar.
ARG MVN_OPTS="-q -DskipTests -Dmaven.repo.local=/root/.m2/repository"

WORKDIR /build

COPY pom.xml .
# → Nur die pom.xml: Dependency-Layer bleibt gecached, solange sich die pom.xml nicht ändert.

RUN --mount=type=cache,target=/root/.m2,uid=185 mvn ${MVN_OPTS} dependency:go-offline

COPY src ./src
# → Sourcecode zuletzt, damit Code-Änderungen das Dependency-Layer nicht invalidieren.

RUN --mount=type=cache,target=/root/.m2,uid=185 mvn ${MVN_OPTS} compile spring-boot:process-aot package
# → compile: Klassen kompilieren (Voraussetzung für process-aot)
# → spring-boot:process-aot: Bean-Definitionen, Proxies, Reflection-Hints vorab generieren
# → package: finales JAR inkl. AOT-Klassen

RUN JAR=$(ls target/*.jar | grep -v original) && \
    cp "$JAR" app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --destination extracted
# → Extraktion OHNE --launcher: Ergebnis ist ein Thin-JAR (application/app.jar) mit
#   Class-Path-Manifest auf lib/*.jar. Das ist das von Spring dokumentierte Layout für
#   CDS/AOT-Cache: alle Klassen kommen aus JARs über den System-Classloader und landen
#   damit im Cache. (Beim JarLauncher-Layout lädt ein eigener Classloader aus
#   Verzeichnissen – diese Klassen kann die JVM nicht archivieren.)
# → Layer gemäß src/main/resources/layers.xml:
#     dependencies/lib, observability-dependencies/lib, spring-boot-loader (leer),
#     snapshot-dependencies (leer), application-resources (leer), application/app.jar

# Laufzeit-Layout unter /app nachbauen. Der AOT-Cache prüft Pfad, Größe und mtime
# jedes JARs – deshalb identische Pfade wie in der Runtime-Stage und cp -a (mtime bleibt).
WORKDIR /app
RUN mkdir -p config data && \
    touch config/.keep data/.keep && \
    chmod -R g+w config data && \
    for layer in dependencies observability-dependencies spring-boot-loader \
                 snapshot-dependencies application-resources application; do \
        cp -a /build/extracted/$layer/. /app/ ; \
    done

# AOT-Cache trainieren (JEP 514: ein Schritt – Training + Assembly).
# - spring.context.exit=onRefresh: Context komplett aufbauen (alle Beans, Jetty,
#   Virtual-Thread-Executor), danach sauber beenden.
# - GC und UseCompactObjectHeaders MÜSSEN mit dem ENTRYPOINT übereinstimmen,
#   sonst verwirft die JVM den Cache beim Start.
# - Die JVM in build und runtime muss dieselbe Build-Nummer haben (beide :latest
#   aus derselben UBI-Release-Welle; bei Abweichung startet die App ohne Cache).
RUN java -XX:AOTCacheOutput=/app/app.aot \
         -XX:+UseG1GC \
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

COPY --chown=185:185 containerconfig/application.yml /app/config/application.yml
# → Wird über die Spring-Default-Location file:./config/ (WORKDIR /app) automatisch geladen
#   und überschreibt einzelne Keys der eingebauten application.yml. Bewusst KEIN
#   spring.config.location: das würde die eingebaute Konfiguration komplett ersetzen.

EXPOSE 8003

# JVM-Optionen:
# -XX:AOTCache                 AOT-Cache aus der Build-Stage (vorgeladene + gelinkte Klassen, Profile)
# -Dspring.aot.enabled         vorberechnete Bean-Definitionen statt Classpath-Scanning
# -XX:+UseG1GC                 explizit, da sonst bei <2 CPUs / <1792 MB SerialGC gewählt würde
#                              und der GC zum Trainingslauf passen muss
# -XX:+UseCompactObjectHeaders (JEP 519) 8- statt 12-Byte-Objekt-Header → ca. 10–20 % weniger Heap
# -XX:MaxRAMPercentage=70      Heap max. 70 % des Container-Limits (Rest: Metaspace, Code-Cache, Stacks)
# -XX:InitialRAMPercentage=30  moderater Start-Heap
# -XX:MaxMetaspaceSize=128m    deckelt Klassen-Metadaten (natives OOM außerhalb des Heaps vermeiden)
# -XX:MaxDirectMemorySize=64m  deckelt NIO-Direct-Buffers (Jetty)
# -XX:+ExitOnOutOfMemoryError  bei OOM sofort beenden → Orchestrator startet neu
# -XX:-UsePerfData             kein /tmp/hsperfdata_* (readOnlyRootFilesystem-tauglich)
# -jar /app/app.jar            Thin-JAR, Classpath kommt aus dem Manifest (lib/*.jar)
# Virtual Threads werden über spring.threads.virtual.enabled in application.yml aktiviert.
ENTRYPOINT ["java", \
  "-XX:AOTCache=/app/app.aot", \
  "-Dspring.aot.enabled=true", \
  "-XX:+UseG1GC", \
  "-XX:+UseCompactObjectHeaders", \
  "-XX:MaxRAMPercentage=70", \
  "-XX:InitialRAMPercentage=30", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:MaxDirectMemorySize=64m", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:-UsePerfData", \
  "-jar", "/app/app.jar"]
