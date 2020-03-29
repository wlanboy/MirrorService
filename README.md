![Java CI with Maven](https://github.com/wlanboy/MirrorService/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master)

# MirrorService
Spring Framework based Service which is mirroring requests

## Dependencies
At least: Java 8 and Maven 3.5

## Build Service Logging
mvn package -DskipTests=true

## Run Service
### Environment variables
export DOCKERHOST=192.168.0.100

### Windows
java -jar target\mirrorservice-0.1.1-SNAPSHOT.jar

### Linux (service enabled)
./target/mirrorservice-0.1.1-SNAPSHOT.jar start

## Docker build
docker build -t mirrorservice:latest . --build-arg JAR_FILE=./target/mirrorservice-0.1.1-SNAPSHOT.jar

## Docker run
docker run --name mirrorservice -m 256M -d -p 8003:8003 -v /tmp:/tmp -e DOCKERHOST=$DOCKERHOST mirrorservice:latest

## Get your requests back
http://localhost:8003/mirror
