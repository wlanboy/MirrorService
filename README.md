# MirrorService
Spring Framework based Service which is mirroring requests

## Dependencies
At least: Java 8 and Maven 3.5

## Build Service Logging
mvn package -DskipTests=true

## Run Service
### Environment variables
-none-

### Windows
java -jar target\mirrorservice-0.1.1-SNAPSHOT.jar

### Linux (service enabled)
./target/mirrorservice-0.1.1-SNAPSHOT.jar start

## Docker build
docker build -t mirrorservice:latest . --build-arg JAR_FILE=./target/mirrorservice-0.1.1-SNAPSHOT.jar

## Docker run
export DOCKERHOST=192.168.0.100
docker run --name mirrorservice -m 256M -d -p 8080:8080 -v /tmp:/tmp -e DOCKERHOST=$DOCKERHOST -e EUREKA_ZONE=http://$DOCKERHOST:8761/eureka/ mirrorservice:latest

## Get your requests back
http://localhost:8080/mirror
