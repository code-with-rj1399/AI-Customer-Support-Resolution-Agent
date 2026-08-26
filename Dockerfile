# Build the application from the current source tree so the image cannot accidentally
# run a stale locally-built JAR.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /workspace/target/customer-support-resolution-0.0.1-SNAPSHOT.jar app.jar

RUN useradd --system --uid 10001 appuser
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
