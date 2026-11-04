# ==============================================================================
# Stage 1: Build executable JAR using Maven & JDK 21
# ==============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# 1. Copy pom.xml first and download dependencies to leverage Docker layer cache
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# 2. Copy source code and build production executable JAR (tests run in CI stage)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Lightweight Production Runtime with JRE 21 Alpine
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# 1. Security by Default: Create a non-root system user to run the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 2. Copy compiled Spring Boot JAR from the build stage
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

# 3. Switch to non-root user
USER appuser

EXPOSE 8080

# 4. Run with container-aware JVM memory limits (optimized for 1GB RAM AWS EC2)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]