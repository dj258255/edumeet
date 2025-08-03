FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

RUN gradle dependencies --no-daemon

COPY src src
RUN gradle bootJar --no-daemon

FROM openjdk:17-jdk-slim
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system spring --ingroup spring

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]