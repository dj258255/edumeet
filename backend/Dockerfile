# syntax=docker/dockerfile:1

# ── 빌드 ────────────────────────────────────────────────────────────────
# gradle 이미지는 멀티아키를 제공하므로 ARM64 러너에서도 그대로 동작한다.
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# 의존성만 먼저 받아 레이어를 캐시한다. src 가 바뀌어도 이 레이어는 재사용된다.
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon || true

COPY src src
RUN gradle bootJar --no-daemon -x test

# ── 실행 ────────────────────────────────────────────────────────────────
# openjdk 이미지는 2022년 deprecated 됐다. Eclipse Temurin 이 후속이다.
# 런타임에는 JDK 가 필요 없으므로 JRE 를 쓴다.
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ffmpeg time \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 spring \
 && useradd --uid 10001 --gid spring --home-dir /app --shell /usr/sbin/nologin spring

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown -R spring:spring /app

USER spring:spring
EXPOSE 8080

# 컨테이너에 걸린 메모리 제한을 JVM 이 인식하게 한다.
# 이 옵션이 없으면 JVM 이 호스트 전체 메모리를 보고 힙을 잡아 OOMKilled 가 난다.
#   MaxRAMPercentage  컨테이너 메모리의 몇 %를 힙 상한으로 쓸지
#   ExitOnOutOfMemoryError  OOM 이면 죽는다. 좀비 상태로 버티는 것보다 재시작이 낫다
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod
# 액추에이터는 관리 포트로 분리한다. 지표를 인터넷 쪽 포트에 두지 않기 위해서다. (#28)
# 이 포트가 바뀌면 아래 HEALTHCHECK 도 같이 바뀐다.
#
# HEALTHCHECK 는 /actuator/health 가 아니라 readiness 를 본다. (#53)
# 전체 health 는 메일·S3 까지 포함해서, 메일 하나 때문에 컨테이너가 죽는다.
ENV MANAGEMENT_PORT=9090

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:${MANAGEMENT_PORT:-9090}/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
