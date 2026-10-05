FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY app/build.gradle ./app/build.gradle
COPY mock-kakao/build.gradle ./mock-kakao/build.gradle
COPY app/src/main ./app/src/main

RUN chmod +x gradlew && ./gradlew :app:bootJar --no-daemon \
    && find app/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system solomon && useradd --system --gid solomon solomon
COPY --from=build --chown=solomon:solomon /workspace/app.jar ./app.jar
USER solomon
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
