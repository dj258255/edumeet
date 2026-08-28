#!/usr/bin/env bash
#
# 무한 큐 OOM 을 재현하고, 큐 제한을 건 뒤 다시 잰다. (#43)
#
#   ./scripts/run-chat-oom.sh              # 전/후 둘 다
#   MODE=before ./scripts/run-chat-oom.sh  # 개선 전만
#
# 왜 힙을 512m 으로 조이나
#   기본 힙이면 OOM 까지 수십 분이 걸린다. 조이면 수 분 안에 같은 현상이 나온다.
#   조인 것은 "언제 죽는가" 이지 "죽는가" 가 아니다.
#
# 왜 구독자를 늘리나
#   fan-out 이 증폭기다. 발행 1건이 구독자 N명에게 가므로
#   아웃바운드 큐는 발행 속도의 N배로 쌓인다.
#   발행 속도를 올리는 것보다 구독자를 늘리는 편이 빠르다.

set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${MODE:-both}"
SUBSCRIBERS="${SUBSCRIBERS:-150}"
PUBLISHERS="${PUBLISHERS:-4}"
RATE="${RATE:-60}"
DURATION="${DURATION:-4m}"
HEAP="${HEAP:-512m}"
PERF_PORT="${PERF_PORT:-8081}"
MGMT_PORT="${MGMT_PORT:-9092}"

LOG_DIR="build/chat-oom"
DATA_DIR="docs/performance/data"
mkdir -p "$LOG_DIR" "$DATA_DIR"

command -v k6 >/dev/null || { echo "k6 가 없다: brew install k6"; exit 1; }

echo "== 인프라 =="
docker compose -f docker-compose.perf.yml up -d mysql redis >/dev/null
for i in $(seq 1 40); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' edumeet-perf-mysql 2>/dev/null)" = "healthy" ] && break
  sleep 2
done
echo "   mysql ready"

echo "== 빌드 =="
(cd backend && ./gradlew perfBootJar -q)
# 벤치마크 엔드포인트는 perf 소스셋에만 있다. 운영 jar 에는 없다. (#57)
JAR=$(ls backend/build/libs/*-perf.jar | head -1)

APP_PID=""; POLL_PID=""
cleanup() {
  [ -n "$POLL_PID" ] && kill "$POLL_PID" 2>/dev/null || true
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

# 관측 지표를 1초마다 찍는다. 붕괴는 형태로 봐야 한다 - 마지막 값만 보면 안 보인다.
poll_metrics() {
  local out="$1" tmp t=0
  tmp=$(mktemp)
  rm -f "$out"
  while true; do
    if curl -fsS "http://localhost:$MGMT_PORT/actuator/prometheus" -o "$tmp" 2>/dev/null; then
      python3 scripts/scrape_to_csv.py "$out" "$t" "$tmp" 2>/dev/null || true
    fi
    t=$((t+1)); sleep 1
  done
}

run_round() {
  local tag="$1"; shift
  local extra_opts="$*"

  echo
  echo "=============================================================="
  echo " $tag   힙 ${HEAP} · 구독자 ${SUBSCRIBERS} · 발행 ${PUBLISHERS}x${RATE}/s"
  echo "=============================================================="

  # shellcheck disable=SC2086
  java -Xmx"$HEAP" -Xms"$HEAP" \
       -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="$LOG_DIR/$tag.hprof" \
       -XX:+ExitOnOutOfMemoryError \
       $extra_opts \
       -jar "$JAR" --spring.profiles.active=perf \
       --server.port="$PERF_PORT" --management.server.port="$MGMT_PORT" \
       > "$LOG_DIR/app-$tag.log" 2>&1 &
  APP_PID=$!

  echo -n "   기동 대기"
  for i in $(seq 1 90); do
    if curl -fsS "http://localhost:$MGMT_PORT/actuator/health" >/dev/null 2>&1; then
      echo " ok"; break
    fi
    kill -0 "$APP_PID" 2>/dev/null || { echo " 실패"; tail -30 "$LOG_DIR/app-$tag.log"; exit 1; }
    echo -n "."; sleep 2
  done

  # 채팅 방과 토큰을 받는다. BROADCAST 는 DB 쓰기가 없어 큐 소진만 본다.
  local setup
  setup=$(curl -fsS -X POST "http://localhost:$PERF_PORT/api/perf/chat/setup?type=BROADCAST")
  local meeting_id token
  meeting_id=$(echo "$setup" | python3 -c 'import sys,json;print(json.load(sys.stdin)["meetingId"])')
  token=$(echo "$setup" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
  echo "   meetingId=$meeting_id"

  poll_metrics "$LOG_DIR/metrics-$tag.csv" &
  POLL_PID=$!

  local started; started=$(date +%s)
  set +e
  k6 run -q \
    -e BASE_URL="ws://localhost:$PERF_PORT" -e TOKEN="$token" -e MEETING_ID="$meeting_id" \
    -e SUBSCRIBERS="$SUBSCRIBERS" -e PUBLISHERS="$PUBLISHERS" -e RATE="$RATE" -e DURATION="$DURATION" \
    -e SUMMARY_PATH="$DATA_DIR/chat-oom-$tag.json" \
    k6/chat-fanout.js 2>&1 | tee "$LOG_DIR/k6-$tag.log"
  set -e
  local ended; ended=$(date +%s)

  kill "$POLL_PID" 2>/dev/null || true; POLL_PID=""

  # 앱이 살아 있나. OOM 이면 ExitOnOutOfMemoryError 로 이미 죽었다.
  local survived="yes"
  kill -0 "$APP_PID" 2>/dev/null || survived="no"

  echo
  echo "   경과: $((ended-started))초"
  echo "   생존: $survived"
  if [ -f "$LOG_DIR/$tag.hprof" ]; then
    echo "   힙 덤프: $LOG_DIR/$tag.hprof ($(du -h "$LOG_DIR/$tag.hprof" | cut -f1))"
  fi
  grep -c "OutOfMemoryError" "$LOG_DIR/app-$tag.log" 2>/dev/null | xargs echo "   OOM 로그:" || true
  tail -3 "$LOG_DIR/metrics-$tag.csv" 2>/dev/null | sed 's/^/   /' || true

  kill "$APP_PID" 2>/dev/null || true
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=""
  sleep 3
}

case "$MODE" in
  before) run_round before ;;
  after)  run_round after -Dspring.task.execution.pool.queue-capacity=1000 ;;
  both)
    run_round before
    run_round after -Dspring.task.execution.pool.queue-capacity=1000
    ;;
esac

echo
echo "== 결과 =="
ls -1 "$LOG_DIR"/metrics-*.csv "$DATA_DIR"/chat-oom-*.json 2>/dev/null | sed 's/^/  /'
