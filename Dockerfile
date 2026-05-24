# ---- Stage 1: build the fat jar inside a Maven container (no local Maven needed) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies first for faster rebuilds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the application (tests run in CI / `mvn test`; skipped here for fast image builds)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: minimal runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl is used by the container HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 appuser

COPY --from=build /build/target/securebank-api-*.jar app.jar
USER appuser

# Container-aware heap sizing; JAVA_TOOL_OPTIONS is picked up automatically by the JVM,
# keeping `java` as PID 1 so it receives SIGTERM for graceful shutdown.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
