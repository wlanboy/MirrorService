#!/bin/sh
# Standard-Shebang für POSIX-kompatible Shells.
# /bin/sh ist bewusst gewählt, weil es in minimalen Container-Images garantiert vorhanden ist.

set -e
# Beendet das Skript sofort, wenn ein Befehl einen Fehler zurückgibt.

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

exec java \
  -Djava.security.egd=file:/dev/./urandom \
  -XX:MaxRAMPercentage=50 \
  -XX:InitialRAMPercentage=30 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ExplicitGCInvokesConcurrent \
  -XX:+ExitOnOutOfMemoryError \
  org.springframework.boot.loader.launch.JarLauncher \
  --spring.config.location=file:/app/config/application.properties
  