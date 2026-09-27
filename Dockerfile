FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system smartpai \
    && useradd --system --gid smartpai --create-home --home-dir /app smartpai

WORKDIR /app

COPY --from=build /workspace/target/SmartPAI-0.0.1-SNAPSHOT.jar ./app.jar
COPY docs/paismart.pdf ./docs/paismart.pdf

RUN chown -R smartpai:smartpai /app

USER smartpai

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
