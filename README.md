[![Java CI with Maven](https://github.com/wlanboy/MirrorService/actions/workflows/maven.yml/badge.svg)](https://github.com/wlanboy/MirrorService/actions/workflows/maven.yml) ![Docker build and publish image](https://github.com/wlanboy/MirrorService/workflows/Docker%20build%20and%20publish%20image/badge.svg)

# MirrorService
Spring Framework based Service which is mirroring requests with the option to delay requests and alter http response codes

## Dependencies
At least: Java 21 and Maven 3.5

## Build Service Logging

```bash
mvn package -DskipTests=true
```

## Run with devtools
```bash
mvn spring-boot:run
```

## Run Service

### Windows

```bash
java -jar target\mirrorservice-0.3.1-SNAPSHOT.jar
```

### Linux (service enabled)

```bash
./target/mirrorservice-0.3.1-SNAPSHOT.jar start
```

## Docker build

```bash
docker build -f Dockerfile.25 -t mirrorservice:latest .
docker build -f Dockerfile.25Jlink -t mirrorservice:jlink .
docker build -f Dockerfile.native -t mirrorservice:native .

docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep "mirrorservice"
mirrorservice   latest    677MB
mirrorservice   jlink     297MB
```

Both variants use Spring AOT, the JDK 25 AOT cache and virtual threads. Details: [docker.md](docker.md)

## Docker run

```bash
docker run --rm --name mirrorservice -m 256M -p 8003:8003 -v /tmp:/tmp mirrorservice:latest
Started MirrorserviceApplication in 0.552 seconds (process running for 0.692)

docker run --rm --name mirrorservice -m 256M -p 8003:8003 -v /tmp:/tmp mirrorservice:jlink
Started MirrorserviceApplication in 0.505 seconds (process running for 0.636)
```

## Docker Hub

* https://hub.docker.com/r/wlanboy/mirrorservice

## Docker Registry repro

* https://github.com/wlanboy/MirrorService/packages/278492

## Native build

Multi-stage build with GraalVM CE 25 (`native-image`) on `distroless/base-nossl`. No local GraalVM needed, takes about 2.5 minutes and ~4.5 GB RAM.

```bash
docker build -f Dockerfile.native -t mirrorservice:native .
docker run --rm --name mirrorservice -m 256M -p 8003:8003 mirrorservice:native
Started MirrorserviceApplication in 0.076 seconds (process running for 0.08)
```

Image 181 MB, RSS 65 MiB after start, 70 MiB after 600 requests.

## Native publish

```bash
docker tag mirrorservice:native wlanboy/mirrorservice:nativelatest
docker push wlanboy/mirrorservice:nativelatest
```

## Native executable

Requires a local GraalVM 25 (`GRAALVM_HOME`) plus `build-essential` and `zlib1g-dev`:

```bash
./mvnw -Pnative native:compile
./target/mirrorservice
```

## Call microservice

* curl http://localhost:8003/actuator/health

Result

```json
{"status":"UP","groups":["liveness","readiness"]}
```

## Get your requests back with http status code 201 and a wait time of 1000 ms

* http://localhost:8003/mirror?statuscode=201&wait=1000 (1000ms)

returns http status code 201 and waits for 10 ms / mirrors body and headers

## Response headers and security defaults

`/mirror` returns any body and any headers the caller asks for. Anyone can build a link that
serves HTML with a script, sets a cookie or redirects to another site via `Location`. The
service adds two headers to every mirror response unless the caller sets them in
`responseHeaders`:

* `Content-Security-Policy: sandbox` blocks scripts and forms and gives the page an opaque
  origin, so it cannot read cookies or storage of the service's domain.
* `X-Content-Type-Options: nosniff` stops browsers from rendering a `text/plain` body as HTML.

A test that needs a different policy sets the header itself, for example
`responseHeaders[Content-Security-Policy]=default-src 'self'`.

The service drops `Set-Cookie` by default and returns `X-Mirror-Blocked-Header: Set-Cookie`
instead. To pass cookies through, set `mirror.allow-set-cookie=true`. As an environment
variable this is `MIRROR_ALLOW_SET_COOKIE=true`; in the Helm chart add it to `extraEnv`.

`Location` stays unrestricted, so the service is an open redirect. Run it under a domain of
its own, not as a subdomain next to other applications. The browser treats `mirror.gmk.lan`
and `app.gmk.lan` as the same site because `.lan` is not on the Public Suffix List. A cookie
set with `Domain=.gmk.lan` would reach every application under `gmk.lan`, and a redirect from
the mirror to another application counts as a same-site request, so the browser sends
`SameSite` cookies along.

## Call microservice for dns resolve
* curl http://localhost:8003/resolve/gmk.lan

Result

```txt
["192.168.100.101"]
```

## Call microservice for reachability check
* curl "http://localhost:8003/ping/gmk.lan?timeoutMs=2000"

Result

```json
{"hostname":"gmk.lan","resolvedIp":"192.168.100.101","reachable":true,"responseTimeMs":3}
```

`timeoutMs` must be between 1 and 10000 (default 1000); other values return 400.
An unreachable host returns 408, a hostname that cannot be resolved returns 404.

The check uses `InetAddress.isReachable`. Java sends an ICMP echo only if the process has
`CAP_NET_RAW`. The container images run as a non-root user, so the JVM has no such
capability and opens a TCP connection to port 7 (echo) instead. In that case
`reachable: true` only means the target host accepted the connection or rejected it with
a TCP RST. It does not mean that any service on that host is up. If a firewall drops
packets to port 7, the endpoint reports `reachable: false` even for a host that is running.

## Call microservice for request echo
* curl -H "Authorization: Bearer abc" "http://localhost:8003/echo/some/path?x=1"

Result

```json
{"method":"GET","url":"http://localhost:8003/echo/some/path","path":"/echo/some/path","query":"x=1",
 "remoteAddress":"127.0.0.1","peerAddress":"127.0.0.1",
 "headers":{"Host":["localhost:8003"],"User-Agent":["curl/8.18.0"],"Accept":["*/*"],"Authorization":["Bearer ***"]},
 "body":"","bodyBytes":0,"bodyTruncated":false}
```

`/echo` and every path below it accept GET, HEAD, POST, PUT, PATCH, DELETE and OPTIONS. The
response shows the request as the service received it behind an ingress, Istio sidecar or proxy.

* `headers` lists the headers exactly as received, including `Forwarded` and `X-Forwarded-*`.
  The service masks the values of `Authorization` and `Proxy-Authorization` but keeps the scheme
  (`Bearer ***`). It also masks cookie values and keeps the cookie names (`session=***`).
* `url` and `remoteAddress` are the values after Spring evaluated the forwarded headers
  (`server.forward-headers-strategy: framework`). If `url` still starts with `http://` behind a TLS
  ingress, the ingress does not send `X-Forwarded-Proto`.
* `peerAddress` is the address of the direct TCP peer, usually the ingress pod or the sidecar.
* `body` contains at most 64 KiB of the request body. `bodyTruncated` is `true` if the body
  was longer.

## Kubernets deployment

### Prepare

```bash
cd ~
git clone https://github.com/wlanboy/MirrorService.git
```

### check you local kubectl

```bash
kubectl cluster-info
kubectl get pods --all-namespaces
```

### deploy service on new namespace

```bash
cd ~/MirrorService
kubectl create namespace mirror
kubectl apply -f mirrorservice-deployment.yaml
kubectl apply -f mirrorservice-service.yaml
kubectl get pods -n mirror -o wide
```

### check deployment and service

```bash
kubectl describe deployments -n mirror mirrorservice 
kubectl describe services -n mirror mirrorservice-service
```

### expose service and get node port
```bash
docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp mirrorservice:latest
kubectl expose deployment -n mirror mirrorservice --type=NodePort --name=mirrorservice-serviceexternal --port 8003
kubectl describe services -n mirror mirrorservice-serviceexternal 
```

Result:
```bash
Name:                     mirrorservice-serviceexternal
Namespace:                mirror
Labels:                   app=mirrorservice
Annotations:              <none>
Selector:                 app=mirrorservice
Type:                     NodePort
IP Family Policy:         SingleStack
IP Families:              IPv4
IP:                       10.108.40.139
IPs:                      10.108.40.139
Port:                     <unset>  8003/TCP
TargetPort:               8002/TCP
NodePort:                 <unset>  30412/TCP  <--- THIS IS THE PORT WE NEED
Endpoints:                10.10.0.7:8003
Session Affinity:         None
External Traffic Policy:  Cluster
Events:                   <none>
```

## sdk command

```bash
sdk install java 21.0.5-tem
sdk install maven 3.9.9
```
