FROM openjdk:8-jdk

RUN mkdir /app

ADD target/stirimm-webapp-0.0.1-SNAPSHOT.jar /app/stirimm-webapp.jar

HEALTHCHECK CMD curl --fail http://localhost:8080/ || exit 1

CMD [ "java", "-jar", "/app/stirimm-webapp.jar" ]