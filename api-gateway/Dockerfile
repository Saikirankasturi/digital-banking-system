# Multi-stage Dockerfile for Render Deployment
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY banking-common banking-common
COPY api-gateway api-gateway

RUN mvn clean package -pl api-gateway -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/api-gateway/target/api-gateway-*.jar app.jar

ENV JAVA_OPTS="-Xms200m -Xmx420m -XX:+UseG1GC"
EXPOSE 8000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
