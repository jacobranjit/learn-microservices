# syntax=docker/dockerfile:1

# ---- Build stage: compiles the jar with the project's Maven wrapper (no local Java needed) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Download dependencies first, so they are cached until pom.xml changes.
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -q -B package -DskipTests && cp target/*.jar app.jar

# ---- Runtime stage: JRE only, runs as a non-root user ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8083
# The JVM sizes its heap from the container's memory limit (not the host's RAM).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
