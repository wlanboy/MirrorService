![Java CI with Maven](https://github.com/wlanboy/MirrorService/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master) ![Docker build and publish image](https://github.com/wlanboy/MirrorService/workflows/Docker%20build%20and%20publish%20image/badge.svg)

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
docker build -t mirrorservice:latest . 
docker build -f Dockerfile25Jlink -t mirrorservice:jlink .

docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep "mirrorservice"
mirrorservice   jre       479MB
mirrorservice   jlink     236MB
```

## Docker run

```bash
docker run --rm --name mirrorservice -m 256M -p 8003:8080 -v /tmp:/tmp mirrorservice:latest
Completed initialization in 2 ms
Started MirrorserviceApplication in 2.154 seconds (process running for 2.891)

docker run --rm --name mirrorservice -m 256M -p 8003:8080 -v /tmp:/tmp mirrorservice:jlink
Completed initialization in 1 ms
Started MirrorserviceApplication in 1.184 seconds (process running for 1.401)
```

## Docker Hub

* https://hub.docker.com/r/wlanboy/mirrorservice

## Docker Registry repro

* https://github.com/wlanboy/MirrorService/packages/278492

## Native build

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.5-tem
sdk install java 21.0.2-graalce
export GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce
sudo apt-get install build-essential zlib1g-dev
mvn -Pnative spring-boot:build-image
take a coffee && do a walk && docker rm mirrorservice
docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp docker.io/library/mirrorservice:0.3.1-SNAPSHOT
```

## Native publish

```bash
docker tag docker.io/library/mirrorservice:0.3.1-SNAPSHOT wlanboy/mirrorservice:nativelatest
docker push wlanboy/mirrorservice:nativelatest
```

## Native executable

```bash
mvn -Pnative native:compile
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
