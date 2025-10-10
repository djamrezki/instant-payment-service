# =========================
# 1) Build stage
# =========================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Install utilities needed for healthcheck in final image cache layer (optional)
RUN apk add --no-cache bash

# Copy Gradle wrapper & build files first (to leverage Docker layer caching)
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle

# Warm up dependency cache (will cache if build files haven't changed)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now copy the source
COPY src ./src

# Build the fat jar (skip tests in container build; run them in CI instead)
RUN ./gradlew --no-daemon clean bootJar -x test

# =========================
# 2) Runtime stage
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S app && adduser -S app -G app

# Copy the artifact from the builder stage
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# Helpful defaults; override at runtime as needed
ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=container \
    SERVER_PORT=8080

# (Optional) Install curl for Docker HEALTHCHECK; remove if you prefer a sidecar checker
RUN apk add --no-cache curl

# Healthcheck pings your controller health endpoint
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD \
  curl -fsS http://localhost:${SERVER_PORT}/api/payments/health || exit 1

EXPOSE 8080
USER app

# Use exec form; JAVA_OPTS is expanded via sh -c
ENTRYPOINT ["/bin/sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
