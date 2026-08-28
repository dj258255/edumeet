#!/usr/bin/env bash
#
# 세션 정원 제어를 MySQL InnoDB 위에서 검증한다.
#
#   ./scripts/run-session-benchmark.sh
#
# 이미 JUnit 동시성 테스트가 있지만 H2 위에서 돈다. H2 의 잠금과 InnoDB 의
# SELECT ... FOR UPDATE 는 동작이 다르다. 운영 DB 에서 다시 확인한다.
#
# 잠금 있는 경로와 없는 경로를 같은 조건으로 돌려 잠금이 실제로 무언가를
# 막고 있음을 보인다.

set -euo pipefail
cd "$(dirname "$0")/.."

PERF_PORT="${PERF_PORT:-8081}"
export PERF_PORT
CAPACITY="${CAPACITY:-30}"
ATTEMPTS="${ATTEMPTS:-150}"
LOG_DIR="build/benchmark-logs"
mkdir -p "$LOG_DIR" docs/performance/data

command -v k6 >/dev/null || { echo "k6 가 없다: brew install k6"; exit 1; }

docker compose -f docker-compose.perf.yml up -d >/dev/null
for i in $(seq 1 40); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' edumeet-perf-mysql 2>/dev/null)" = "healthy" ] && break
  sleep 2
done

echo "== 애플리케이션 빌드 =="
(cd backend && ./gradlew perfBootJar -q)
# 벤치마크 엔드포인트는 perf 소스셋에만 있다. 운영 jar 에는 없다. (#57)
JAR=$(ls backend/build/libs/*-perf.jar | head -1)

APP_PID=""
cleanup() { [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true; }
trap cleanup EXIT

PERF_BATCH_SIZE=100 java -jar "$JAR" --spring.profiles.active=perf \
    > "$LOG_DIR/app-session.log" 2>&1 &
APP_PID=$!

echo -n "   기동 대기"
for i in $(seq 1 120); do
  MID=$(curl -sf "http://localhost:$PERF_PORT/api/perf/sessions/seeded" 2>/dev/null \
        | sed -n 's/.*"meetingId":\([0-9]*\).*/\1/p' || true)
  if [ -n "$MID" ]; then echo " ok (meetingId=$MID)"; break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo " 실패"; tail -30 "$LOG_DIR/app-session.log"; exit 1
  fi
  echo -n "."; sleep 2
done
[ -n "${MID:-}" ] || { echo "세션을 찾지 못했다"; exit 1; }

run_case() {
  local lock="$1" label="$2"
  echo
  echo "=============================================================="
  echo " $label  (lock=$lock, 정원 $CAPACITY, 동시 시도 $ATTEMPTS)"
  echo "=============================================================="

  k6 run -q -e LOCK="$lock" -e LABEL="$label" -e CAPACITY="$CAPACITY" \
      -e ATTEMPTS="$ATTEMPTS" k6/session-capacity.js

  # k6 가 끝난 뒤 실제 DB 상태를 읽어 결과에 합친다.
  local state
  state=$(curl -sf "http://localhost:$PERF_PORT/api/perf/sessions/state?meetingId=$MID" || echo '{}')
  python3 - "$label" "$state" <<'PY'
import json, sys, pathlib
label, state = sys.argv[1], json.loads(sys.argv[2] or '{}')
path = pathlib.Path(f"docs/performance/data/k6-session-{label}.json")
data = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
data.update({
    "final_active": state.get("active"),
    "final_limit": state.get("limit"),
    "overflow": state.get("overflow"),
    "verdict": "정원 초과" if (state.get("overflow") or 0) > 0 else "정원 준수",
})
path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
print(f"   최종: 정원 {data.get('final_limit')} / 입장 {data.get('final_active')} "
      f"/ 초과 {data.get('overflow')} → {data['verdict']}")
PY
}

# 잠금 없는 경우를 먼저 돌린다. 잠금 있는 경우가 먼저면 캐시가 덥혀져서
# 두 번째 회차가 유리해진다.
run_case false without-lock
run_case true  with-lock

echo
echo "== 완료: docs/performance/data/k6-session-*.json =="
