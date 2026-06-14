# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
# A floating tag is acceptable for the build stage (its output is reproducible
# and never shipped). Temurin 25 JDK matches the project toolchain (Java 25).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper and build descriptors first so dependency resolution
# can be cached independently of source changes.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Then the sources.
COPY src ./src

# Build the layered Spring Boot jar. --no-daemon keeps the build hermetic in CI.
RUN ./gradlew bootJar --no-daemon

# Extract the layered jar using the Boot 4 "tools" jarmode (-Djarmode=tools,
# replaces the Boot 2/early-3 layertools). --layers emits one directory per layer
# in volatility order; --launcher produces the exploded runnable layout
# (BOOT-INF/ + the spring-boot-loader classes) launched via JarLauncher.
WORKDIR /workspace/extracted
RUN java -Djarmode=tools -jar /workspace/build/libs/ledgerforge.jar \
        extract --layers --launcher --destination .

# The runtime image is a JRE (no curl/wget, no javac), so compile a tiny
# dependency-free health probe here in the JDK stage and ship the .class.
WORKDIR /workspace/health
COPY docker/HealthCheck.java ./HealthCheck.java
RUN javac HealthCheck.java

# ---- Runtime stage -----------------------------------------------------------
# Runtime base PINNED BY DIGEST (no floating tag, no :latest). Resolved from
# `docker pull eclipse-temurin:25-jre` -> RepoDigests. JRE only: no compiler in
# the shipped image.
FROM eclipse-temurin:25-jre@sha256:5cf92df78f6dba978777d5cffa3c856e583f86814fde82a6c3534ccdfd794f2f
WORKDIR /app

# Non-root runtime user (numeric-stable). Running as root is an anti-pattern.
RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --no-create-home --shell /usr/sbin/nologin spring

# Container-aware JVM: cap heap at 75% of the cgroup memory limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Copy the extracted layers in volatility order (least -> most volatile) so a
# code-only change invalidates only the final, smallest layer. Every layer merges
# into the same exploded /app tree (BOOT-INF/lib, BOOT-INF/classes, loader classes)
# that JarLauncher resolves from the WORKDIR.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

# Self-contained health probe (no curl/wget in a JRE image).
COPY --from=build --chown=spring:spring /workspace/health/HealthCheck.class /app/HealthCheck.class

USER spring

EXPOSE 8080

# Container HEALTHCHECK via the JDK HttpClient probe (exit 0 only when the
# actuator reports "status":"UP"). start-period covers Flyway migration + boot.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD ["java", "HealthCheck", "http://localhost:8080/actuator/health"]

# Exploded layered launch (no fat jar): JarLauncher resolves BOOT-INF/ from /app.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
