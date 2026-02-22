# ============================
# 1. Build Stage (Java 25)
# ============================
FROM registry.access.redhat.com/ubi9/openjdk-25:latest AS build
# Eclipse Temurin bietet aktuelle Java-Versionen inkl. Java 25

WORKDIR /app

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

RUN cp target/mirrorservice-0.3.1-SNAPSHOT.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted
# → Spring Boot 4.x Layertools: --launcher ist erforderlich um den Loader zu extrahieren
# → Extrahierte Layer gemäß src/main/resources/layers.xml:
#     - dependencies            (Spring, Jetty, Thymeleaf, Reactor …)
#     - observability-dependencies (Micrometer, Prometheus, SpringDoc)
#     - spring-boot-loader      (org/springframework/boot/loader/*)
#     - snapshot-dependencies
#     - application             (BOOT-INF/classes + AOT-Metadaten)
# → Vorteil: Docker kann diese Layer getrennt cachen → schnellere Deployments.

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

USER root
# → Temporär root, um Verzeichnisse anzulegen und Berechtigungen zu setzen.

RUN mkdir -p /app/config /app/data && \
    chown -R 185:0 /app && \
    chmod -R g+w /app
# → /app/config: für externe Konfigurationen
# → /app/data: für persistente Daten
# → Non-root User für sicheren Betrieb

USER 185
# → Zurück zum nicht-privilegierten User.

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

COPY --chown=185:185 containerconfig/application.properties /app/config/application.properties
# → Externe Konfiguration ins Config-Verzeichnis für die Referenz für ENV Vars

COPY --chown=185:185 entrypoint.sh /app/entrypoint.sh
# → Custom Entrypoint für Java OPTS.

EXPOSE 8003
# → Dokumentiert den Port, den die App verwendet (Spring Boot Default).

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8003/actuator/health || exit 1
# → Nutzt den Spring Boot Actuator Health Endpoint.
# → Alternativ: wget oder ein einfacher TCP-Check

ENTRYPOINT ["/app/entrypoint.sh"]
# → Startet die App über das Entry-Skript.
# → Vorteil: Skript kann Umgebungsvariablen verarbeiten, ENTRYPOINT nicht.
