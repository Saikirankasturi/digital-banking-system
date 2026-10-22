# Multi-stage Dockerfile for Render Deployment
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-common banking-common
COPY payment-service payment-service

RUN mvn clean package -pl payment-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/payment-service/target/payment-service-*.jar app.jar

ENV JAVA_OPTS="-Xms200m -Xmx420m -XX:+UseG1GC"
EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
