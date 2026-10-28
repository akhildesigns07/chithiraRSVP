# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Java2D (share images) needs fontconfig even though the app bundles its own font.
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
