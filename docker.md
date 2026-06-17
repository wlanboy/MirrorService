# MirrorService – Docker Build Varianten

Drei Dockerfiles stehen zur Verfügung, die sich in Komplexität, Image-Größe und Startverhalten
unterscheiden. Alle nutzen Java 25 und ein Multi-Stage-Build.

---

## Übersicht der Varianten

| Merkmal                  | `Dockerfile25`       | `Dockerfile`               | `Dockerfile25Jlink`             |
|--------------------------|----------------------|----------------------------|---------------------------------|
| Base-Image (Build)       | ubi10/openjdk-25        | ubi10/openjdk-25         | ubi10/openjdk-25                |
| Base-Image (Runtime)     | ubi10/openjdk-25-runtime | ubi10/openjdk-25-runtime | gcr.io/distroless/cc           |
| JRE                      | System-JRE (vollständig) | System-JRE (vollständig) | Custom JRE via jlink (minimal) |
| JAR-Format               | Fat-JAR              | Explodierte Schichten      | Explodierte Schichten           |
| AOT                      | nein                 | ja                         | ja                              |
| AppCDS                   | nein                 | ja (G1GC)                  | ja (ZGC)                        |
| Garbage Collector        | G1GC (Standard)      | G1GC                       | ZGC                             |
| Startup-Erwartung        | langsam              | schnell                    | am schnellsten                  |
| Image-Größe-Erwartung    | groß                 | mittel                     | klein                           |
| Komplexität              | minimal              | mittel                     | hoch                            |

---

## Bauen

```bash
# Einfache Variante – Fat-JAR, kein AOT/CDS
docker build -f Dockerfile25 -t mirrorservice:simple .

# Standard-Variante – Layers, AOT, AppCDS, G1GC
docker build -f Dockerfile -t mirrorservice:full .

# Jlink-Variante – Custom JRE, Layers, AOT, AppCDS, ZGC, Distroless
docker build -f Dockerfile25Jlink -t mirrorservice:jlink .
```

Alle Builds nutzen BuildKit. Falls nicht standardmäßig aktiv:

```bash
DOCKER_BUILDKIT=1 docker build ...
```

---

## Vergleich: Image-Größe

```bash
docker images mirrorservice --format "table {{.Tag}}\t{{.Size}}"
```

Oder detaillierter mit den Layer-Größen:

```bash
docker history mirrorservice:simple  --no-trunc --format "table {{.Size}}\t{{.CreatedBy}}"
docker history mirrorservice:full    --no-trunc --format "table {{.Size}}\t{{.CreatedBy}}"
docker history mirrorservice:jlink   --no-trunc --format "table {{.Size}}\t{{.CreatedBy}}"
```

Ergebnis

```
TAG       SIZE
jlink     236MB
full      583MB
simple    580MB
```

---

## Vergleich: Startgeschwindigkeit

Spring Boot schreibt die Startzeit in das Log (`Started ... in X.XXX seconds`).
Der folgende Befehl startet den Container, wartet auf diese Zeile und gibt sie aus:

```bash
docker run -d --rm --name ms-simple --memory 512m -p 8003:8003 mirrorservice:simple
docker logs -f ms-simple 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 2.092 seconds (process running for 2.399)

docker run -d --rm --name ms-full --memory 512m -p 8004:8003 mirrorservice:full
docker logs -f ms-full 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 1.472 seconds (process running for 1.66)

docker run -d --rm --name ms-jlink --memory 512m -p 8005:8003 mirrorservice:jlink
docker logs -f ms-jlink 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 1.426 seconds (process running for 3.256)
```

---

## Vergleich: RAM-Bedarf

Die JVM-Flags `MaxRAMPercentage` und `InitialRAMPercentage` rechnen gegen das Container-Speicherlimit.
Ohne `--memory` gilt der gesamte Host-RAM als Limit (z. B. 30 GiB), wodurch `InitialRAMPercentage=40`
sofort ~12 GiB Heap reserviert. Deshalb immer mit explizitem `--memory` starten:

```bash
# Alle drei mit gleichem Limit starten (Ports unterschiedlich, damit kein Konflikt)
docker run -d --name ms-simple --memory 512m -p 8003:8003 mirrorservice:simple
docker run -d --name ms-full   --memory 512m -p 8004:8003 mirrorservice:full
docker run -d --name ms-jlink  --memory 512m -p 8005:8003 mirrorservice:jlink

# Snapshot der Metriken (einmalig, kein Live-Update)
docker stats --no-stream ms-simple ms-full ms-jlink \
  --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}"
```

Unter Last (optional, mit [hey](https://github.com/rakyll/hey) oder curl-Schleife):

```bash
# Kurze Last erzeugen
for i in $(seq 1 200); do curl -s http://localhost:8004/actuator/health > /dev/null; done

# Dann Snapshot
docker stats --no-stream ms-simple ms-full ms-jlink \
  --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

Aufräumen:

```bash
docker rm -f ms-simple ms-full ms-jlink
```

---

## Technische Unterschiede im Detail

### `Dockerfile25` – Einfach
- Maven baut ein einzelnes Fat-JAR (`mvn package`)
- Das JAR wird direkt in die Runtime-Stage kopiert und mit `-jar` gestartet
- Kein AOT: Spring analysiert Bean-Graph und Proxies erst zur Laufzeit
- Kein CDS: JVM lädt und verifiziert alle Klassen beim ersten Start neu
- Geeignet für lokale Entwicklung und schnelles Debugging

### `Dockerfile` – Standard (AOT + Layers + AppCDS)
- Maven baut mit `spring-boot:process-aot`: Bean-Definitionen, Proxies und Reflection-Metadaten
  werden vorab berechnet und in das JAR eingebacken
- Spring Boot Layertools zerlegt das JAR in sechs Layer (`layers.xml`): Dependencies ändern sich
  selten und bleiben im Docker-Cache; nur der `application`-Layer muss bei Code-Änderungen
  neu übertragen werden
- AppCDS (G1GC): Die JVM speichert analysierte und gemappte Klassendaten in `app.jsa`.
  Folgestarts laden diese Datei direkt in den Shared Memory, ohne Klassen neu zu parsen
- Runtime-Image ist `ubi10/openjdk-25-runtime` (vollständiges Red-Hat-JRE)

### `Dockerfile25Jlink` – Maximal optimiert (Custom JRE + ZGC)
- Zusätzlich zu AOT und AppCDS wird mit `jdeps` der tatsächliche Modul-Bedarf der Anwendung
  analysiert und mit `jlink` ein Custom JRE gebaut, das nur die benötigten Module enthält
- Das Runtime-Image ist `gcr.io/distroless/cc`: kein Shell, kein Paketmanager, minimale
  Angriffsfläche; das einzige "Java" im Image ist das per jlink erzeugte `/opt/jre`
- ZGC statt G1GC: sehr kurze GC-Pausen (<1 ms), das AppCDS-Archiv ist GC-gebunden und muss
  mit demselben GC erstellt werden, mit dem die App läuft
- Erfordert `java-25-openjdk-jmods` und `binutils` (für `--strip-debug`) als Build-Zeit-Pakete,
  die nicht ins Runtime-Image gelangen
