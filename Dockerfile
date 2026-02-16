FROM gcr.io/distroless/java21-debian12:nonroot

COPY target/stirimm-webapp-0.0.1-SNAPSHOT.jar /app/stirimm-webapp.jar

ENTRYPOINT ["java", "-jar", "/app/stirimm-webapp.jar"]
