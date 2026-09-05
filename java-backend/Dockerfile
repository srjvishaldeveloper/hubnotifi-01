# Multi-stage build: compile with the project's own Gradle wrapper, ship only the runtime JRE + jar.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependency resolution separately from source changes.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle* ./
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown app:app app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
