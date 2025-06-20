![Java CI with Maven](https://github.com/wlanboy/MirrorService/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master) ![Docker build and publish image](https://github.com/wlanboy/MirrorService/workflows/Docker%20build%20and%20publish%20image/badge.svg)

# MirrorService
Spring Framework based Service which is mirroring requests with the option to delay requests and alter http response codes

## Dependencies
At least: Java 11 and Maven 3.5

## Build Service Logging
mvn package -DskipTests=true

## Run with devtools
mvn spring-boot:run

## Run Service
### Windows
java -jar target\mirrorservice-0.3.1-SNAPSHOT.jar

### Linux (service enabled)
./target/mirrorservice-0.3.1-SNAPSHOT.jar start

## Docker build
docker build -t mirrorservice:latest . --build-arg JAR_FILE=./target/mirrorservice-0.3.1-SNAPSHOT.jar

## Docker publish to github registry
- docker tag mirrorservice:latest docker.pkg.github.com/wlanboy/mirrorservice/mirrorservice:latest
- docker push docker.pkg.github.com/wlanboy/mirrorservice/mirrorservice:latest

## Docker Hub
- https://hub.docker.com/r/wlanboy/mirrorservice

## Docker Registry repro
- https://github.com/wlanboy/MirrorService/packages/278492

## Docker run
- docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp mirrorservice:latest

## Native build
- curl -s "https://get.sdkman.io" | bash
- sdk install java 21.0.5-tem
- sdk install java 21.0.2-graalce
- export GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce
- sudo apt-get install build-essential zlib1g-dev
- mvn -Pnative spring-boot:build-image
- take a coffee && do a walk && docker rm mirrorservice
- docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp docker.io/library/mirrorservice:0.3.1-SNAPSHOT

## Native publish
- docker tag docker.io/library/mirrorservice:0.3.1-SNAPSHOT wlanboy/mirrorservice:latest
- docker push wlanboy/mirrorservice:latest

## Native executable
- mvn -Pnative native:compile
- ./target/mirrorservice
- docker build -f Dockerfile.native -t mirrorservice:latest .
- docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp mirrorservice:latest

##  call microservice
* curl http://localhost:8003/actuator/health
* Result
```
{"status":"UP","groups":["liveness","readiness"]}
```

## Get your requests back with http status code 201 and a wait time of 1000 ms
http://localhost:8003/mirror?statuscode=201&wait=1000 (1000ms)

returns http status code 201 and waits for 10 ms / mirrors body and headers

##  call microservice for dns resolve
* curl http://localhost:8003/resolve/gmk.lan
* Result
```
["192.168.100.101"]
```

## Kubernets deployment
### Prepare
```
cd ~
git clone https://github.com/wlanboy/MirrorService.git
```

### check you local kubectl
```
kubectl cluster-info
kubectl get pods --all-namespaces
```

### deploy service on new namespace
```
cd ~/MirrorService
kubectl create namespace mirror
kubectl apply -f mirrorservice-deployment.yaml
kubectl apply -f mirrorservice-service.yaml
kubectl get pods -n mirror -o wide
```

### check deployment and service
```
kubectl describe deployments -n mirror mirrorservice 
kubectl describe services -n mirror mirrorservice-service
```

### expose service and get node port
```
kubectl expose deployment -n mirror mirrorservice --type=NodePort --name=mirrorservice-serviceexternal --port 8003
kubectl describe services -n mirror mirrorservice-serviceexternal 
```
Result:
```
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
```
sdk install java 21.0.5-tem
sdk install maven 3.9.9
```