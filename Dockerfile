# Multi-stage Dockerfile for 100% Free Single-Service Render Deployment
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-common banking-common
COPY account-service account-service
COPY fraud-detection-service fraud-detection-service
COPY transaction-service transaction-service
COPY notification-service notification-service
COPY payment-service payment-service
COPY api-gateway api-gateway
COPY banking-server banking-server

RUN mvn clean package -pl banking-server -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/banking-server/target/banking-server-*-exec.jar app.jar

ENV JAVA_OPTS="-Xms200m -Xmx420m -XX:+UseG1GC"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

