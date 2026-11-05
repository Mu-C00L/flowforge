FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B verify

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S flowforge && adduser -S flowforge -G flowforge
WORKDIR /app
COPY --from=build /workspace/target/flowforge-*.jar app.jar
USER flowforge
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
