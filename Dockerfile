FROM registry.access.redhat.com/ubi8/openjdk-21-runtime
VOLUME /tmp

WORKDIR /app
COPY target/mirrorservice-0.3.1-SNAPSHOT.jar /app/mirrorservice.jar

EXPOSE 8003

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/mirrorservice.jar", "--spring.config.location=file:/app/application.properties"]
