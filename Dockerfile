FROM openjdk:8-jdk

RUN mkdir /app

ADD target/stirimm-webapp-0.0.1-SNAPSHOT.jar /app/stirimm-webapp.jar

CMD [ "java", "-jar", "/app/stirimm-webapp.jar" ]