# MirrorService – Docker Build Varianten

Zwei Varianten stehen zur Verfügung. Beide nutzen Java 25, einen Multi-Stage-Build, Spring AOT,
den JDK-25-AOT-Cache und Virtual Threads. Sie unterscheiden sich im Runtime-Image.

`Dockerfile` ist aktuell identisch mit `Dockerfile.25` und wird von CI, Tekton und
`multiarch-build.sh` verwendet.

---

## Übersicht der Varianten

| Merkmal               | `Dockerfile.25` (= `Dockerfile`) | `Dockerfile.25Jlink`                      |
|-----------------------|----------------------------------|-------------------------------------------|
| Base-Image (Build)    | eclipse-temurin:25-jdk           | eclipse-temurin:25-jdk                    |
| Base-Image (Runtime)  | eclipse-temurin:25-jre           | gcr.io/distroless/base-nossl-debian13     |
| JRE                   | System-JRE (vollständig)         | Custom JRE via jlink                      |
| Shell im Image        | ja                               | nein                                      |
| JAR-Format            | Thin-JAR + `lib/` (Layer)        | Thin-JAR + `lib/` (Layer)                 |
| Spring AOT            | ja                               | ja                                        |
| JDK-AOT-Cache         | ja (`app.aot`, ~80 MB)           | ja (`app.aot`, ~80 MB)                    |
| Garbage Collector     | SerialGC                         | SerialGC                                  |
| Heap (Default)        | `-Xms64m -Xmx192m`               | `-Xms64m -Xmx192m`                        |
| Compact Object Headers| ja                               | ja                                        |
| Virtual Threads       | ja                               | ja                                        |
| User                  | 185                              | nonroot (65532)                           |
| Image-Größe           | 636 MB                           | 297 MB                                    |
| Start (`-m 256m`)     | 0,53 s                           | 0,52 s                                    |

---

## Bauen

```bash
# Temurin-Variante – Temurin-JRE, AOT-Cache, Virtual Threads
docker build -f Dockerfile.25 -t mirrorservice:temurin .

# Jlink-Variante – Custom JRE, AOT-Cache, Virtual Threads, Distroless
docker build -f Dockerfile.25Jlink -t mirrorservice:jlink .
```

Beide Builds brauchen BuildKit (`RUN --mount=type=cache`). Falls nicht standardmäßig aktiv:

```bash
DOCKER_BUILDKIT=1 docker build ...
```

---

## Starten

Der Service lauscht im Container auf Port `8003` (API) und `8081` (Actuator). Den Actuator-Port nur bei Bedarf veröffentlichen, z. B. lokal mit `-p 127.0.0.1:8081:8081`:

```bash
docker run --rm --name mirrorservice -m 256m -p 8003:8003 mirrorservice:jlink
```

---

## Vergleich: Image-Größe

```bash
docker images mirrorservice --format "table {{.Tag}}\t{{.Size}}"
```

Mit Layer-Größen:

```bash
docker history mirrorservice:temurin --format "table {{.Size}}\t{{.CreatedBy}}"
docker history mirrorservice:jlink --format "table {{.Size}}\t{{.CreatedBy}}"
```

Ergebnis:

```
TAG       SIZE
temurin   636MB
jlink     297MB
```

Der AOT-Cache (`app.aot`) belegt in beiden Images rund 80 MB. Das ist der Preis für den
schnelleren Start.

---

## Vergleich: Startgeschwindigkeit

Spring Boot schreibt die Startzeit ins Log (`Started ... in X.XXX seconds`):

```bash
docker run -d --rm --name ms-temurin --memory 256m -p 8003:8003 mirrorservice:temurin
docker logs -f ms-temurin 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 0.533 seconds

docker run -d --rm --name ms-jlink --memory 256m -p 8004:8003 mirrorservice:jlink
docker logs -f ms-jlink 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 0.525 seconds
```

Zum Vergleich die alten Varianten (Fat-JAR ohne AOT bzw. jlink mit wirkungslosem AppCDS):
2,1 s bzw. 1,3 s.

Prüfen, ob der AOT-Cache wirklich geladen wird:

```bash
docker run --rm --entrypoint java mirrorservice:temurin \
  -Xlog:aot=info -XX:AOTCache=/app/app.aot -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -version \
  | grep "Opened AOT cache"
```

Passen JVM-Build, GC oder `UseCompactObjectHeaders` nicht zum Trainingslauf, startet die JVM
mit einer Warnung ohne Cache.

---

## Vergleich: RAM-Bedarf

Der Heap ist fest eingestellt (Default `-Xms64m -Xmx192m` aus `JAVA_TOOL_OPTIONS`) und hängt nicht
vom Container-Limit ab.
Off-Heap kommen AOT-Cache (~80 MB), Metaspace (max. 64 MB), Code-Cache (max. 48 MB) und
Direct Buffers (max. 64 MB) dazu. Messung mit `--memory 256m`:

```bash
docker run -d --name ms-temurin --memory 256m -p 8003:8003 mirrorservice:temurin
docker run -d --name ms-jlink --memory 256m -p 8004:8003 mirrorservice:jlink

# Last erzeugen
for i in $(seq 1 300); do
  curl -s -o /dev/null "http://localhost:8003/mirror?statusCode=200&waitMs=0&responseBody=x"
  curl -s -o /dev/null "http://localhost:8004/mirror?statusCode=200&waitMs=0&responseBody=x"
done

docker stats --no-stream ms-temurin ms-jlink \
  --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"

docker rm -f ms-temurin ms-jlink
```

Gemessen nach 600 Requests plus 20 s `hey -c 50` (~3100 req/s, damals noch mit UBI-Runtime): `ubi` ca. 157 MiB, `jlink` ca. 156 MiB
(Limit 256 MiB, `memory.peak` 189 MiB). Vorher mit G1 und `MaxRAMPercentage=70`: 211 bzw. 205 MiB.

---

## Technische Details

### Gemeinsam: Spring AOT, Thin-JAR, AOT-Cache

- Maven baut mit `compile spring-boot:process-aot package`: Bean-Definitionen, Proxies und
  Reflection-Hints werden vorab generiert.
- `java -Djarmode=tools -jar app.jar extract --layers` (ohne `--launcher`) erzeugt ein Thin-JAR
  (`app.jar`) mit `Class-Path`-Manifest auf `lib/*.jar`. Die Layer aus `layers.xml` bleiben
  erhalten. `spring-boot-loader`, `snapshot-dependencies` und `application-resources` sind in
  diesem Layout leer, die `COPY`-Schritte schaden aber nicht.
- Warum ohne `--launcher`: Beim `JarLauncher`-Layout lädt ein eigener Classloader die Klassen
  aus Verzeichnissen. Solche Klassen kann die JVM nicht archivieren. Mit dem Thin-JAR kommen
  alle Klassen über den System-Classloader aus JAR-Dateien und landen im Cache
  (ca. 98 % der geladenen Klassen).
- Der AOT-Cache (JEP 483/514/515) ist der Nachfolger von AppCDS: Klassen liegen geladen und
  gelinkt vor, dazu kommen JIT-Profile aus dem Trainingslauf. Training mit
  `-XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh`, und zwar mit genau der JVM,
  die auch im Runtime-Image läuft (Temurin: eigene Stage `train` auf `-jre`, jlink: `/opt/jre`).
- Der Cache prüft Pfad, Größe und mtime jedes JARs. Deshalb wird in der Build-Stage unter `/app`
  dasselbe Layout wie zur Laufzeit aufgebaut (`cp -a` erhält die mtime).
- GC und `-XX:+UseCompactObjectHeaders` stehen wortgleich im Training und im `ENTRYPOINT`,
  nie in `JAVA_TOOL_OPTIONS`. Weicht eins ab, verwirft die JVM den Cache still. SerialGC ist
  explizit gesetzt, damit die Ergonomie nicht vom Build-Container abhängt.
- Der `ENTRYPOINT` enthält nur AOT-Cache, Spring AOT, GC und Header. Alles Dimensionierbare steht
  als Default in `ENV JAVA_TOOL_OPTIONS`: fester Heap `-Xms64m -Xmx192m`, `-Xss512k`,
  `MaxMetaspaceSize=64m`, `ReservedCodeCacheSize=48m`, `TieredStopAtLevel=1` (nur C1,
  I/O-gebundene Last), `ActiveProcessorCount=2`, `MaxDirectMemorySize=64m`. Flags auf der
  Kommandozeile würden `JAVA_TOOL_OPTIONS` überstimmen, deshalb stehen sie nicht im
  `ENTRYPOINT`. Abweichende Werte setzt man über die Variable, immer mit dem vollständigen Satz:
  `docker run -e JAVA_TOOL_OPTIONS="-Xms64m -Xmx128m …"`. Das Helm-Chart macht das über `javaOpts`.
- Ca. 98 % der Klassen kommen aus dem AOT-Cache (10386/10624).
- Build-Stage: `-Dmaven.test.skip=true` (Tests werden nicht kompiliert),
  `extract --application-filename app.jar` statt Umkopieren.
- Das Image enthält ein leeres `/app/config/`. Eine Datei, die dort per Volume eingehängt wird
  (z. B. `application.properties` aus der ConfigMap im Helm-Chart), lädt Spring über die
  Default-Location `file:./config/`. Sie überschreibt einzelne Keys der eingebauten
  `application.yml`. `spring.config.location` wird bewusst nicht gesetzt, weil es die eingebaute
  Konfiguration komplett ersetzen würde (dann fehlen Port 8003, Actuator-Endpunkte und
  Graceful Shutdown).
- Maven: Das Temurin-JDK-Image enthält kein Maven, gebaut wird mit dem Maven-Wrapper (`./mvnw`).
  Der Build läuft als root, Wrapper-Distribution und Repository liegen im Cache-Mount `/root/.m2`.

### Gemeinsam: Virtual Threads und Minimal-Jetty

- `spring.threads.virtual.enabled=true` (in `src/main/resources/application.yml`): Jetty nutzt
  einen `VirtualThreadPool`. Blockierende Aufrufe (DNS-Lookup, `isReachable`) belegen keinen
  OS-Thread.
- In der `pom.xml` ist der WebSocket-Stack aus `spring-boot-starter-jetty` ausgeschlossen
  (Jakarta- und Jetty-WebSocket, jetty-annotations, jetty-plus, CDI-API, ASM). Übrig bleiben
  jetty-server, -servlet, -webapp, -session und gzip-Kompression.

### `Dockerfile.25` – Temurin-Runtime

- Runtime-Image `eclipse-temurin:25-jre` (Ubuntu) mit Shell (`docker exec` verfügbar). `jcmd`
  fehlt im JRE, dafür einen Debug-Container mit `eclipse-temurin:25-jdk` nutzen.
- Der Prozess läuft als UID 185 mit GID 0. Die UID hat keinen Eintrag in `/etc/passwd`, das
  stört die JVM nicht. Das Helm-Chart (`runAsUser: 185`) passt damit weiterhin.
- Das Temurin-JRE ist ein eigenes jlink-Image, seine `lib/modules` unterscheidet sich vom JDK.
  Einen im JDK trainierten Cache verwirft die JVM im JRE („size has changed“). Deshalb trainiert die
  Stage `train` auf `eclipse-temurin:25-jre`, die Runtime-Stage übernimmt nur `app.aot`.
- Alle Stages nutzen dasselbe `ARG TEMURIN_TAG` (Default `25`). Zum Pinnen einen Release-Tag wie
  `25.0.5_8` setzen: `docker build --build-arg TEMURIN_TAG=25.0.5_8 ...`.

### `Dockerfile.25Jlink` – Custom JRE auf Distroless

- `jdeps` analysiert App und alle Libraries in `lib/` und bestimmt die benötigten Module. Dazu
  kommen Module, die nur per Reflection oder ServiceLoader genutzt werden (`java.management`,
  `jdk.management`, `jdk.zipfs`, `jdk.charsets`, `jdk.unsupported`, `java.security.sasl`,
  `jdk.naming.dns`).
- `jlink` baut daraus `/opt/jre` (`--strip-debug`, `--compress zip-9`). Training und Laufzeit
  nutzen dasselbe `/opt/jre/bin/java`, der AOT-Cache passt also immer.
- Runtime-Image `gcr.io/distroless/base-nossl-debian13:nonroot`: Die JVM braucht nur glibc
  (libstdc++ statisch gelinkt, zlib gebündelt, TLS macht Java selbst). Kein Shell, kein
  Paketmanager, kein OpenSSL.
- Kein ZGC: In JDK 25 kann der AOT-Cache mit ZGC keine Heap-Objekte archivieren (kommt mit
  JEP 516 in JDK 26). Lambdas, Proxies und der Modul-Graph werden dann nicht gecached. Gemessen
  bei `-m 256m`: ZGC 0,79 s Start und 256 MiB nach 600 Requests (am Limit).
- Temurin 25 liefert keine JMODs. `jlink` baut ohne `--module-path` aus dem linkbaren
  Runtime-Image des JDK (JEP 493). `objcopy` für `--strip-debug` ist im JDK-Image schon enthalten.
