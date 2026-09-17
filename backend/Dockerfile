# Multi-stage build: compile with the JDK, run on the smaller JRE image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy the wrapper and build files first so dependency resolution is cached separately
# from source changes.
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
