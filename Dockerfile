# ============================
# 1. Build Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi9/openjdk-25:latest AS build
# Eclipse Temurin bietet aktuelle Java-Versionen inkl. Java 25

WORKDIR /app

RUN mkdir -p /app/config /app/data && \
    touch /app/config/.keep /app/data/.keep && \
    chmod -R g+w /app/config /app/data
# → Verzeichnisse in der Build-Stage anlegen (kein root in der Runtime-Stage nötig)

COPY pom.xml .
# → Nur die pom.xml wird kopiert, damit Maven bereits alle Dependencies auflösen kann,
#   ohne dass sich der Sourcecode ändert. Das verbessert das Layer-Caching.

RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
# → Lädt alle Maven-Dependencies vorab herunter.
# → --mount=type=cache sorgt dafür, dass das lokale Maven-Repository zwischen Builds gecached wird.

COPY src ./src
# → Jetzt erst der Sourcecode, damit Änderungen am Code nicht das Dependency-Layer invalidieren.

RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests compile spring-boot:process-aot package
# → Baut das eigentliche JAR mit AOT (Ahead-of-Time) Processing.
# → compile: Kompiliert die Klassen (notwendig für process-aot).
# → spring-boot:process-aot: Generiert AOT-Metadaten basierend auf den kompilierten Klassen.
# → package: Baut das finale JAR inkl. AOT-Klassen.
# → Wieder mit Maven-Cache, um Build-Zeit zu sparen.

RUN JAR=$(ls target/*.jar | grep -v original) && \
    cp "$JAR" app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted
# → Spring Boot 4.x Layertools: --launcher ist erforderlich um den Loader zu extrahieren
# → Extrahierte Layer gemäß src/main/resources/layers.xml:
#     - dependencies            (Spring, Jetty, Thymeleaf, Reactor …)
#     - observability-dependencies (Micrometer, Prometheus, SpringDoc)
#     - spring-boot-loader      (org/springframework/boot/loader/*)
#     - snapshot-dependencies
#     - application             (BOOT-INF/classes + AOT-Metadaten)
# → Vorteil: Docker kann diese Layer getrennt cachen → schnellere Deployments.

RUN java -XX:ArchiveClassesAtExit=app.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.aot.enabled=true \
         -cp "extracted/dependencies/*:extracted/observability-dependencies/*:extracted/snapshot-dependencies/*:extracted/application/" \
         org.springframework.boot.loader.launch.JarLauncher || [ -f app.jsa ]

# ============================
# 2. Runtime Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:latest

# OCI-konforme Labels
LABEL org.opencontainers.image.title="MirrorService" \
      org.opencontainers.image.description="Spring Framework based service mirroring requests" \
      org.opencontainers.image.version="0.0.1-SNAPSHOT" \
      org.opencontainers.image.vendor="wlanboy" \
      org.opencontainers.image.source="https://github.com/wlanboy/MirrorService" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.base.name="openjdk-25:latest"

WORKDIR /app

USER 185

COPY --from=build --chown=185:0 /app/config /app/config
COPY --from=build --chown=185:0 /app/data /app/data
# → Verzeichnisse mit korrekter Ownership aus Build-Stage übernehmen (kein root erforderlich)

COPY --from=build --chown=185:185 /app/extracted/dependencies/ ./
# → Stabile Release-Bibliotheken (Spring, Jetty, Thymeleaf, Reactor …). Ändern sich selten.

COPY --from=build --chown=185:185 /app/extracted/observability-dependencies/ ./
# → Micrometer, Prometheus-Client, SpringDoc/OpenAPI. Eigener Release-Zyklus.

COPY --from=build --chown=185:185 /app/extracted/spring-boot-loader/ ./
# → Spring Boot Launcher. Ändert sich nur bei Spring-Boot-Version-Upgrade.

COPY --from=build --chown=185:185 /app/extracted/snapshot-dependencies/ ./
# → Snapshot-Dependencies (z. B. lokale libs), ändern sich häufiger.

COPY --from=build --chown=185:185 /app/extracted/application-resources/ ./
# → Konfigurationsdateien (yml, properties) und Thymeleaf-Templates.
# → Getrennt vom Kompilat: Konfigurationsänderungen invalidieren nicht den Class-Layer.

COPY --from=build --chown=185:185 /app/extracted/application/ ./
# → Kompilierter App-Code + AOT-Metadaten. Ändert sich am häufigsten.

COPY --from=build --chown=185:185 /app/app.jsa /app/app.jsa

COPY --chown=185:185 containerconfig/application.properties /app/config/application.properties
# → Externe Konfiguration ins Config-Verzeichnis für die Referenz für ENV Vars

EXPOSE 8003
# → Dokumentiert den Port, den die App verwendet (Spring Boot Default).

# Wir nutzen exec, damit Java die PID 1 übernimmt.
# Dies ist wichtig für das Signal-Handling (z.B. in Kubernetes).
# exec ersetzt den aktuellen Shell-Prozess durch den Java-Prozess.

# JVM-Optionen:
# -Djava.security.egd: Beschleunigt kryptografische Initialisierung
# -XX:MaxRAMPercentage=50: Java nutzt max 50% des Container-RAMs
# -XX:InitialRAMPercentage=30: Startet mit 30% RAM (schnellerer Startup)
# -XX:+UseG1GC: G1 Garbage Collector für niedrige Latenz
# -XX:MaxGCPauseMillis=200: Zielwert für GC-Pause
# -XX:+ExplicitGCInvokesConcurrent: System.gc() läuft parallel
# -XX:+ExitOnOutOfMemoryError: JVM beendet bei OOM (Kubernetes kann neustarten)
ENTRYPOINT ["java", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-Dspring.aot.enabled=true", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:MaxRAMPercentage=50", \
  "-XX:InitialRAMPercentage=30", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+ExplicitGCInvokesConcurrent", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Dspring.config.location=file:/app/config/application.properties", \
  "org.springframework.boot.loader.launch.JarLauncher"]
