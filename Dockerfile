# syntax=docker/dockerfile:1

# ---- Build stage: compile the jar with Maven (not needed at runtime) ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Dependencies are cached in their own layer so `docker build` only re-downloads
# them when pom.xml changes, not on every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests clean package

# ---- Runtime stage: slim JRE only, non-root user ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app
USER appuser

COPY --from=build --chown=appuser:appuser /build/target/myworktea.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
