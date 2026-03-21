# ============================================================
#  OCT Invoice System — Backend Dockerfile
#  Multi-stage build: compile → runtime (smaller final image)
#  Place this file at the project root (same level as pom.xml)
# ============================================================

# ── Stage 1: Build ───────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build (skip tests — tests run in CI, not here)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S octapp && adduser -S octapp -G octapp

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=builder /app/target/invoice-system-*.jar app.jar

# Create log directory
RUN mkdir -p /var/log/oct-invoice && chown octapp:octapp /var/log/oct-invoice

USER octapp

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]