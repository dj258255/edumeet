#!/usr/bin/env bash
#
# 과제 목록 조회 N+1 개선 전/후를 MySQL 에서 측정한다.
#
#   ./scripts/run-benchmark.sh
#
# 왜 2x2 인가 — 개선은 두 가지였다.
#
#   (1) 앱 레벨: 과제마다 제출물을 조회하던 것을 IN 절 한 번으로 묶음
#   (2) Hibernate: default_batch_fetch_size 로 지연 로딩 컬렉션을 묶음
#
# 둘을 동시에 켜고 끄면 어느 쪽이 얼마나 기여했는지 말할 수 없다.
# (2)는 요청 단위로 못 바꾸는 전역 설정이라 앱을 두 번 띄운다.
#
#   nobatch-naive    아무것도 안 함        = 개선 전
#   nobatch-batch    (1)만
#   batch100-naive   (2)만
#   batch100-batch   둘 다                 = 개선 후

set -euo pipefail
cd "$(dirname "$0")/.."

VUS="${VUS:-50}"
PERF_PORT="${PERF_PORT:-8081}"
export PERF_PORT
DURATION="${DURATION:-60s}"
LOG_DIR="build/benchmark-logs"
mkdir -p "$LOG_DIR" docs/performance/data

command -v k6 >/dev/null || { echo "k6 가 없다: brew install k6"; exit 1; }

echo "== MySQL/Redis 확인 =="
docker compose -f docker-compose.perf.yml up -d >/dev/null
for i in $(seq 1 40); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' edumeet-perf-mysql 2>/dev/null)" = "healthy" ] && break
  sleep 2
done

echo "== 애플리케이션 빌드 =="
(cd backend && ./gradlew perfBootJar -q)
# 벤치마크 엔드포인트는 perf 소스셋에만 있다. 운영 jar 에는 없다. (#57)
JAR=$(ls backend/build/libs/*-perf.jar | head -1)
echo "   $JAR"

APP_PID=""
cleanup() { [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true; }
trap cleanup EXIT

run_measurements() {
  local bs="$1" tag="$2"

  echo
  echo "=============================================================="
  echo " PERF_BATCH_SIZE=$bs  ($tag)"
  echo "=============================================================="

  PERF_BATCH_SIZE="$bs" java -jar "$JAR" --spring.profiles.active=perf \
      > "$LOG_DIR/app-$tag.log" 2>&1 &
  APP_PID=$!

  echo -n "   기동 및 시드 대기"
  for i in $(seq 1 120); do
    # 웹 서버는 ApplicationRunner(시드)보다 먼저 뜬다. 200 만 보고 넘어가면
    # 빈 테이블에 부하를 걸게 되므로 결과 건수까지 확인한다.
    # set -e + pipefail 이라 curl 실패가 스크립트를 죽인다. 기동 대기 중에는
    # 실패가 정상이므로 || true 로 막는다.
    SIZE=$(curl -sI "http://localhost:$PERF_PORT/api/perf/assignments?classId=1&strategy=batch" \
           2>/dev/null | tr -d '\r' | awk -F': ' '/^X-Result-Size/{print $2}' || true)
    if [ -n "$SIZE" ] && [ "$SIZE" -gt 0 ] 2>/dev/null; then
      echo " ok (과제 $SIZE 건)"; break
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      echo " 실패"; tail -30 "$LOG_DIR/app-$tag.log"; exit 1
    fi
    echo -n "."; sleep 2
  done

  # 시드 규모와 요청당 SQL 수를 먼저 찍어둔다. 나중에 수치를 해석할 때 필요하다.
  for s in naive batch; do
    local qc
    qc=$(curl -sI "http://localhost:$PERF_PORT/api/perf/assignments?classId=1&strategy=$s" \
         | tr -d '\r' | awk -F': ' '/^X-Query-Count/{print $2}' || true)
    echo "   strategy=$s 요청당 SQL: $qc"
  done

  for s in naive batch; do
    echo
    echo "   --- $tag-$s 워밍업 ---"
    PERF_BATCH_SIZE="$bs" k6 run -q --no-summary \
      -e STRATEGY="$s" -e WARMUP=1 k6/assignment-list.js >/dev/null 2>&1 || true

    echo "   --- $tag-$s 측정 ---"
    PERF_BATCH_SIZE="$bs" k6 run -q \
      -e STRATEGY="$s" -e LABEL="$tag-$s" -e VUS="$VUS" -e DURATION="$DURATION" \
      k6/assignment-list.js
  done

  kill "$APP_PID" 2>/dev/null || true
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=""
  sleep 3
}

run_measurements "-1"  "nobatch"
run_measurements "100" "batch100"

echo
echo "== 완료. 결과: docs/performance/data/k6-*.json =="
ls -1 docs/performance/data/k6-*.json
