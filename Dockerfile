# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests \
    && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

# ---- Run ----
FROM eclipse-temurin:21-jre
# fontconfig: Java2D (share images) needs it even though the app bundles its own font.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# Layers ordered from least to most frequently changed, so a code-only change re-ships only the last one.
COPY --from=build /src/target/extracted/dependencies/ ./
COPY --from=build /src/target/extracted/spring-boot-loader/ ./
COPY --from=build /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build /src/target/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
# JVM flags live here rather than in JAVA_TOOL_OPTIONS, which makes the JVM print a plain-text
# "Picked up ..." line and break the all-JSON log stream.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true", \
            "org.springframework.boot.loader.launch.JarLauncher"]
