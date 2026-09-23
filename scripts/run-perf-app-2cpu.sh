#!/usr/bin/env bash
#
# #200 게이트 환경 — 측정 앱을 로컬 Docker 에서 **2코어**로 묶어 띄운다.
#
#   ./scripts/run-perf-app-2cpu.sh          # jar 빌드 → 이미지 빌드 → compose → 앱 → health 대기
#   ./scripts/run-perf-app-2cpu.sh stop     # 앱 컨테이너만 내린다 (MySQL·Redis 등은 남긴다)
#   ./scripts/run-perf-app-2cpu.sh down     # 앱 + compose 스택까지 내린다
#
# ★ 왜 2코어인가.
#   #200 의 질문은 "2코어에서 채팅 역압이 조각 업로드를 밀어내는가" 다.
#   OCI 측정 인스턴스가 2코어(aarch64)라(docs/performance/08) 맥의 12코어로 재면
#   게이트의 답이 조건과 무관해진다. 그래서 컨테이너로 묶는다.
#
# ★ 메모리 상한을 두는 이유 (PERF_MEMORY, 기본 4g).
#   OCI 인스턴스는 11Gi(여유 8.6Gi)이고 그 안에서 앱·ffmpeg·LiveKit 이 나눠 쓴다.
#   상한이 없으면 맥의 큰 메모리에서 압박이 아예 안 생겨 2코어 조건이 실제보다 좋아 보인다.
#   더 필요하면 PERF_MEMORY=8g 처럼 올린다.
#
# ★ 시드 데이터는 기본으로 끈다 (PERF_SEED_CLASSES=0).
#   PerfDataSeeder 는 N+1 측정용 과제·제출을 만든다(수천 행). 이 게이트는 안 쓴다 -
#   ddl-auto=create 라 시작할 때마다 다시 만들므로 시작만 느려진다. 필요하면 값을 올린다.
#
# 환경변수
#   PERF_CPUS 2 · PERF_MEMORY 4g · PERF_PORT 8081 · APP_NAME edumeet-perf-app
#   IMAGE edumeet-perf-app · NETWORK edumeet_default · HEALTH_TIMEOUT_S 180
#   DB_URL(기본: 컨테이너 이름으로 MySQL 직접) · DB_USERNAME edumeet · DB_PASSWORD perfpass
#   REDIS_HOST edumeet-perf-redis · LIVEKIT_URL http://edumeet-perf-toxiproxy:7881
#   BROADCAST_OUTPUT_DIR /tmp/edumeet-hls (컨테이너 안 - 호스트로 안 꺼낸다)
#
# ★ 관리 포트를 8081 로 모은다 (MANAGEMENT_PORT=8081).
#   기본은 9090 이고 운영은 그 포트를 publish 하지 않는다. 여기서는 게이트가 한 포트만
#   보면 되도록 앱 포트와 같이 둔다 - /actuator/health 도 8081 에서 답한다.
#
# 측정에 쓰는 법: 띄운 뒤 TOKEN·MEETING_ID 를 만든다.
#   node perf/app/provision.mjs        # export TOKEN=... MEETING_ID=... 를 stdout 에 낸다
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

PERF_CPUS="${PERF_CPUS:-2}"
PERF_MEMORY="${PERF_MEMORY:-4g}"
PERF_PORT="${PERF_PORT:-8081}"
APP_NAME="${APP_NAME:-edumeet-perf-app}"
IMAGE="${IMAGE:-edumeet-perf-app}"
NETWORK="${NETWORK:-edumeet_default}"
HEALTH_TIMEOUT_S="${HEALTH_TIMEOUT_S:-180}"
PERF_SEED_CLASSES="${PERF_SEED_CLASSES:-0}"

# 컨테이너끼리 같은 도커 네트워크로 붙는다 - 주소는 컨테이너 이름이다.
#
# ★ perf 프로파일은 datasource 를 **덮어쓴다** (application.yml 의 on-profile: perf):
#     jdbc:mysql://localhost:${PERF_DB_PORT:3307}/edumeet_perf
#   컨테이너 안의 localhost 는 자기 자신이라 그대로 두면 붙지 못한다.
#   그래서 기본 설정의 DB_URL 이 아니라 **SPRING_DATASOURCE_URL** 로 넘긴다 -
#   프로퍼티 이름을 직접 주면 프로파일 yml 보다 우선한다.
# (호스트에서 돌던 chat-archive-perf.sh 는 127.0.0.1:13307 로 Toxiproxy 를 거쳤다.
#  여기서는 앱이 컨테이너 안이므로 MySQL 로 직접 간다 - 장애 주입은 이 게이트의 변수가 아니다.)
DB_URL="${DB_URL:-jdbc:mysql://edumeet-perf-mysql:3306/edumeet_perf?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true}"
DB_USERNAME="${DB_USERNAME:-edumeet}"
DB_PASSWORD="${DB_PASSWORD:-perfpass}"
REDIS_HOST="${REDIS_HOST:-edumeet-perf-redis}"
# perf 프로파일이 redis 포트를 6380 으로 덮어쓴다(호스트 포트다). 컨테이너 안에서는 6379 이므로
# DB 와 같은 이유로 프로퍼티 이름을 직접 준다 - SPRING_DATA_REDIS_*.
REDIS_PORT="${REDIS_PORT:-6379}"
LIVEKIT_URL="${LIVEKIT_URL:-http://edumeet-perf-toxiproxy:7881}"
BROADCAST_OUTPUT_DIR="${BROADCAST_OUTPUT_DIR:-/tmp/edumeet-hls}"

log() { printf '%s\n' "$*"; }

stop_app() {
  docker rm -f "$APP_NAME" >/dev/null 2>&1 || true
}

case "${1:-up}" in
  stop)
    stop_app
    log "앱 컨테이너를 내렸다 ($APP_NAME). MySQL·Redis·LiveKit·Toxiproxy 는 그대로다."
    exit 0
    ;;
  down)
    stop_app
    docker compose -f docker-compose.perf.yml down
    log "앱과 compose 스택을 내렸다."
    exit 0
    ;;
  up) ;;
  *)
    log "쓰는 법: $0 [up|stop|down]"
    exit 1
    ;;
esac

log "== 1/5 perf jar 빌드 =="
(cd backend && ./gradlew perfBootJar -q) || { log "perfBootJar 실패"; exit 1; }
JAR=$(find backend/build/libs -maxdepth 1 -name '*-perf.jar' 2>/dev/null | head -1)
[ -n "$JAR" ] || { log "perf jar 를 찾지 못했다"; exit 1; }
log "   $JAR"

log "== 2/5 측정 스택 (MySQL·Redis·LiveKit·Toxiproxy) =="
docker compose -f docker-compose.perf.yml up -d >/dev/null || { log "compose 실패"; exit 1; }
for name in edumeet-perf-mysql edumeet-perf-redis; do
  for _ in $(seq 1 40); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null)" = "healthy" ] && break
    sleep 1
  done
  log "   $name: $(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null)"
done

log "== 3/5 이미지 빌드 =="
docker build -f perf/app/Dockerfile -t "$IMAGE" . >/dev/null || { log "이미지 빌드 실패"; exit 1; }
log "   $IMAGE"

log "== 4/5 앱 컨테이너 (cpus=$PERF_CPUS · memory=$PERF_MEMORY · port=$PERF_PORT) =="
stop_app
docker run -d --name "$APP_NAME" \
  --cpus="$PERF_CPUS" --memory="$PERF_MEMORY" \
  --network "$NETWORK" \
  -p "${PERF_PORT}:8081" \
  -e "SPRING_DATASOURCE_URL=$DB_URL" \
  -e "SPRING_DATASOURCE_USERNAME=$DB_USERNAME" -e "SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD" \
  -e "SPRING_DATA_REDIS_HOST=$REDIS_HOST" -e "SPRING_DATA_REDIS_PORT=$REDIS_PORT" \
  -e "LIVEKIT_URL=$LIVEKIT_URL" \
  -e "BROADCAST_OUTPUT_DIR=$BROADCAST_OUTPUT_DIR" \
  -e "EDUMEET_PERF_SEED_CLASSES=$PERF_SEED_CLASSES" \
  -e "MANAGEMENT_PORT=8081" \
  "$IMAGE" >/dev/null || { log "앱 컨테이너 시작 실패"; exit 1; }

log "== 5/5 /actuator/health 가 UP 이 될 때까지 (최대 ${HEALTH_TIMEOUT_S}초) =="
for _ in $(seq 1 "$HEALTH_TIMEOUT_S"); do
  status=$(curl -fsS --max-time 3 "http://localhost:${PERF_PORT}/actuator/health" 2>/dev/null \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
  if [ "$status" = "UP" ]; then
    log "   UP · http://localhost:${PERF_PORT}"
    log
    log "다음: node perf/app/provision.mjs   (TOKEN·MEETING_ID 를 만든다)"
    log "그다음: TOKEN=... MEETING_ID=... ./scripts/run-chunk-under-fanout.sh"
    exit 0
  fi
  if ! docker inspect --format '{{.State.Running}}' "$APP_NAME" 2>/dev/null | grep -q true; then
    log "   컨테이너가 죽었다. 로그 마지막 40줄:"
    docker logs --tail 40 "$APP_NAME" 2>&1 | sed 's/^/   /'
    exit 1
  fi
  sleep 1
done
log "   시간 안에 UP 이 안 됐다. 로그 마지막 40줄:"
docker logs --tail 40 "$APP_NAME" 2>&1 | sed 's/^/   /'
exit 1
