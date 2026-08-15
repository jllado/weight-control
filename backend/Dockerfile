FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app/backend

COPY backend/gradlew backend/gradlew.bat backend/settings.gradle backend/build.gradle backend/gradle.properties ./
COPY backend/gradle gradle
COPY backend/src src
COPY src/assets/logo.png /app/src/assets/logo.png

RUN chmod +x gradlew && ./gradlew bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/backend/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
