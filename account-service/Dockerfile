# Multi-stage Dockerfile for Render Deployment
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy root POM and dependent modules
COPY pom.xml .
COPY banking-common banking-common
COPY account-service account-service

# Build account-service and its common dependencies
RUN mvn clean package -pl account-service -am -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/account-service/target/account-service-*.jar app.jar

ENV JAVA_OPTS="-Xms200m -Xmx420m -XX:+UseG1GC"
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
