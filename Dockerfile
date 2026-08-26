FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/customer-support-resolution-0.0.1-SNAPSHOT.jar app.jar

RUN useradd --system --uid 10001 appuser
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
