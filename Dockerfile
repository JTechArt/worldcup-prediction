# =============================================================================
# Multi-stage build: Stage 1 — Build
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# SQLite data directory (persisted via Docker volume)
RUN mkdir /data && chown appuser:appgroup /data
VOLUME /data

USER appuser

# Copy JAR from builder
COPY --from=builder /app/target/world-cup-prediction-0.0.1-SNAPSHOT.jar app.jar

# Default: SQLite mode (zero external dependencies)
ENV APP_PROFILE=sqlite
ENV SQLITE_PATH=/data/worldcup.db

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
