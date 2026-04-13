# syntax=docker/dockerfile:1.7

# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY settings.xml /tmp/settings.xml
COPY pom.xml .

RUN cat /tmp/settings.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /tmp/settings.xml -B -U dependency:resolve-plugins dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /tmp/settings.xml -B -U -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
