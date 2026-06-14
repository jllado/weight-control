FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
