# 측정 앱 이미지 (#200).
#
# ★ 왜 컨테이너인가.
#   #200 의 질문은 "**2코어에서** 채팅 역압이 조각 업로드를 밀어내는가" 다.
#   OCI 측정 인스턴스는 2코어(aarch64)인데, 맥에서 그냥 jar 를 돌리면 12코어를 다 쓴다 -
#   코어 수를 맞추지 않으면 게이트의 답이 조건과 무관해진다.
#   호스트에 java·ffmpeg 를 요구하지 않는 것도 같이 얻는다.
#
# ★ eclipse-temurin:17-jre-jammy 를 쓰는 이유.
#   arm64 매니페스트가 있어 맥(arm)과 OCI(aarch64)에서 같은 태그가 그대로 돈다.
#   ffmpeg 는 합성 방송과 HLS 리먹싱에 필요하다(ubuntu jammy 의 ffmpeg 는 arm64 패키지가 있다).
#
# 빌드 (저장소 루트에서):
#   backend/build/libs/*-perf.jar 를 먼저 만든다:  (cd backend && ./gradlew perfBootJar)
#   docker build -f perf/app/Dockerfile -t edumeet-perf-app .
#
#   보통은 scripts/run-perf-app-2cpu.sh 가 이 순서를 다 한다.
FROM eclipse-temurin:17-jre-jammy

# ffmpeg: 발표자 브라우저 대신 서버에서 HLS 조각을 만드는 경로가 쓴다 (#123 · #197).
#   --no-install-recommends 로 필요한 것만 넣는다 (이미지 크기).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# perf 소스셋 jar 만 들어간다 - perfBootJar 산출물 이름이 *-perf.jar 다.
COPY backend/build/libs/*-perf.jar /app/app.jar

# 8081 은 perf 프로파일의 포트다 (application.yml on-profile: perf).
EXPOSE 8081

# 프로파일은 이미지에 박아 둔다. 나머지(DB·Redis·LiveKit 주소)는 실행할 때 넘긴다 -
# 컨테이너 이름은 띄우는 방식에 따라 달라질 수 있어서다.
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=perf"]
