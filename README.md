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

## Call microservice for dns resolve
* curl http://localhost:8003/resolve/gmk.lan

Result

```txt
["192.168.100.101"]
```

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
