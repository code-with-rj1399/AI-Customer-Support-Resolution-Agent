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

ARG SPLUNK_OTEL_JAVA_AGENT_VERSION=2.25.0
ADD https://github.com/signalfx/splunk-otel-java/releases/download/v${SPLUNK_OTEL_JAVA_AGENT_VERSION}/splunk-otel-javaagent.jar /app/splunk-otel-javaagent.jar

RUN useradd --system --uid 10001 appuser && chown appuser:appuser /app/app.jar /app/splunk-otel-javaagent.jar
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-javaagent:/app/splunk-otel-javaagent.jar", "-Dsplunk.profiler.enabled=true", "-Dsplunk.profiler.memory.enabled=true", "-jar", "/app/app.jar"]
