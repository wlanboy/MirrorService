# MirrorService – Docker Build Varianten

Zwei Varianten stehen zur Verfügung. Beide nutzen Java 25, einen Multi-Stage-Build, Spring AOT,
den JDK-25-AOT-Cache und Virtual Threads. Sie unterscheiden sich im Runtime-Image.

`Dockerfile` ist aktuell identisch mit `Dockerfile.25` und wird von CI, Tekton und
`multiarch-build.sh` verwendet.

---

## Übersicht der Varianten

| Merkmal               | `Dockerfile.25` (= `Dockerfile`) | `Dockerfile.25Jlink`                      |
|-----------------------|----------------------------------|-------------------------------------------|
| Base-Image (Build)    | ubi10/openjdk-25                 | ubi10/openjdk-25                          |
| Base-Image (Runtime)  | ubi10/openjdk-25-runtime         | gcr.io/distroless/base-nossl-debian13     |
| JRE                   | System-JRE (vollständig)         | Custom JRE via jlink                      |
| Shell im Image        | ja                               | nein                                      |
| JAR-Format            | Thin-JAR + `lib/` (Layer)        | Thin-JAR + `lib/` (Layer)                 |
| Spring AOT            | ja                               | ja                                        |
| JDK-AOT-Cache         | ja (`app.aot`, ~80 MB)           | ja (`app.aot`, ~80 MB)                    |
| Garbage Collector     | SerialGC                         | SerialGC                                  |
| Heap                  | `-Xms64m -Xmx192m`               | `-Xms64m -Xmx192m`                        |
| Compact Object Headers| ja                               | ja                                        |
| Virtual Threads       | ja                               | ja                                        |
| User                  | 185                              | nonroot (65532)                           |
| Image-Größe           | 677 MB                           | 297 MB                                    |
| Start (`-m 256m`)     | 0,53 s                           | 0,52 s                                    |

---

## Bauen

```bash
# UBI-Variante – Red-Hat-Runtime, AOT-Cache, Virtual Threads
docker build -f Dockerfile.25 -t mirrorservice:ubi .

# Jlink-Variante – Custom JRE, AOT-Cache, Virtual Threads, Distroless
docker build -f Dockerfile.25Jlink -t mirrorservice:jlink .
```

Beide Builds brauchen BuildKit (`RUN --mount=type=cache`). Falls nicht standardmäßig aktiv:

```bash
DOCKER_BUILDKIT=1 docker build ...
```

---

## Starten

Der Service lauscht im Container auf Port `8003`:

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
docker history mirrorservice:ubi   --format "table {{.Size}}\t{{.CreatedBy}}"
docker history mirrorservice:jlink --format "table {{.Size}}\t{{.CreatedBy}}"
```

Ergebnis:

```
TAG       SIZE
ubi       677MB
jlink     297MB
```

Der AOT-Cache (`app.aot`) belegt in beiden Images rund 80 MB. Das ist der Preis für den
schnelleren Start.

---

## Vergleich: Startgeschwindigkeit

Spring Boot schreibt die Startzeit ins Log (`Started ... in X.XXX seconds`):

```bash
docker run -d --rm --name ms-ubi   --memory 256m -p 8003:8003 mirrorservice:ubi
docker logs -f ms-ubi 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 0.533 seconds

docker run -d --rm --name ms-jlink --memory 256m -p 8004:8003 mirrorservice:jlink
docker logs -f ms-jlink 2>&1 | grep -m1 "Started MirrorserviceApplication"
# Started MirrorserviceApplication in 0.525 seconds
```

Zum Vergleich die alten Varianten (Fat-JAR ohne AOT bzw. jlink mit wirkungslosem AppCDS):
2,1 s bzw. 1,3 s.

Prüfen, ob der AOT-Cache wirklich geladen wird:

```bash
docker run --rm --entrypoint java mirrorservice:ubi \
  -Xlog:aot=info -XX:AOTCache=/app/app.aot -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -version \
  | grep "Opened AOT cache"
```

Passen JVM-Build, GC oder `UseCompactObjectHeaders` nicht zum Trainingslauf, startet die JVM
mit einer Warnung ohne Cache.

---

## Vergleich: RAM-Bedarf

Der Heap ist fest eingestellt (`-Xms64m -Xmx192m`) und hängt nicht vom Container-Limit ab.
Off-Heap kommen AOT-Cache (~80 MB), Metaspace (max. 64 MB), Code-Cache (max. 48 MB) und
Direct Buffers (max. 64 MB) dazu. Messung mit `--memory 256m`:

```bash
docker run -d --name ms-ubi   --memory 256m -p 8003:8003 mirrorservice:ubi
docker run -d --name ms-jlink --memory 256m -p 8004:8003 mirrorservice:jlink

# Last erzeugen
for i in $(seq 1 300); do
  curl -s -o /dev/null "http://localhost:8003/mirror?statusCode=200&waitMs=0&responseBody=x"
  curl -s -o /dev/null "http://localhost:8004/mirror?statusCode=200&waitMs=0&responseBody=x"
done

docker stats --no-stream ms-ubi ms-jlink \
  --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"

docker rm -f ms-ubi ms-jlink
```

Gemessen nach 600 Requests plus 20 s `hey -c 50` (~3100 req/s): `ubi` ca. 157 MiB, `jlink` ca. 156 MiB
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
  gelinkt vor, dazu kommen JIT-Profile aus dem Trainingslauf. Training in der Build-Stage mit
  `-XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh`.
- Der Cache prüft Pfad, Größe und mtime jedes JARs. Deshalb wird in der Build-Stage unter `/app`
  dasselbe Layout wie zur Laufzeit aufgebaut (`cp -a` erhält die mtime).
- GC und `-XX:+UseCompactObjectHeaders` müssen in Training, `ENTRYPOINT` und
  `JAVA_TOOL_OPTIONS` identisch sein, sonst verwirft die JVM den Cache still. SerialGC ist
  explizit gesetzt, damit die Ergonomie nicht vom Build-Container abhängt.
- JVM-Flags: fester Heap `-Xms64m -Xmx192m`, `-Xss512k`, `MaxMetaspaceSize=64m`,
  `ReservedCodeCacheSize=48m`, `TieredStopAtLevel=1` (nur C1, I/O-gebundene Last),
  `ActiveProcessorCount=2`. Ca. 98 % der Klassen kommen aus dem AOT-Cache (10386/10624).
- Build-Stage: `-Dmaven.test.skip=true` (Tests werden nicht kompiliert), `/build` und `/app`
  gehören User 185, `extract --application-filename app.jar` statt Umkopieren.
- `containerconfig/application.yml` liegt unter `/app/config/` und wird über die
  Spring-Default-Location `file:./config/` geladen. Es überschreibt einzelne Keys der eingebauten
  `application.yml`. `spring.config.location` wird bewusst nicht gesetzt, weil es die eingebaute
  Konfiguration komplett ersetzen würde (dann fehlen Port 8003, Actuator-Endpunkte und
  Graceful Shutdown).
- Maven-Cache: Das UBI-Build-Image läuft als User 185 mit `HOME=/home/default`. Der Cache-Mount
  auf `/root/.m2` wirkt nur mit `uid=185` und `-Dmaven.repo.local=/root/.m2/repository`.

### Gemeinsam: Virtual Threads und Minimal-Jetty

- `spring.threads.virtual.enabled=true` (in `src/main/resources/application.yml`): Jetty nutzt
  einen `VirtualThreadPool`. Blockierende Aufrufe (DNS-Lookup, `isReachable`) belegen keinen
  OS-Thread.
- In der `pom.xml` ist der WebSocket-Stack aus `spring-boot-starter-jetty` ausgeschlossen
  (Jakarta- und Jetty-WebSocket, jetty-annotations, jetty-plus, CDI-API, ASM). Übrig bleiben
  jetty-server, -servlet, -webapp, -session und gzip-Kompression.

### `Dockerfile.25` – UBI-Runtime

- Runtime-Image `ubi10/openjdk-25-runtime` mit Shell (`docker exec`, `jcmd` verfügbar).
- Build- und Runtime-Image müssen denselben JVM-Build haben, sonst wird der AOT-Cache verworfen.
  Beide `:latest`-Tags kommen aus derselben UBI-Release-Welle. Nach einem Base-Image-Update beide
  neu ziehen.

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
- Build-Zeit-Pakete `java-25-openjdk-jmods` und `binutils` (für `--strip-debug`) landen nicht im
  Runtime-Image.
