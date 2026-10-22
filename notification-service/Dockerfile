# Multi-stage Dockerfile for Render Deployment
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-common banking-common
COPY notification-service notification-service

RUN mvn clean package -pl notification-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/notification-service/target/notification-service-*.jar app.jar

ENV JAVA_OPTS="-Xms150m -Xmx350m -XX:+UseG1GC"
EXPOSE 8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
