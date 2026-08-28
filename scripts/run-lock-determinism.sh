#!/usr/bin/env bash
#
# 정원 잠금 테스트를 결정론적으로 만든다.
#
#   ./scripts/run-lock-determinism.sh
#
# 문제 — 잠금 없는 구현의 버그(정원 초과)는 스레드가 "우연히" 겹쳐야 잡힌다.
# 실행에 따라 초과가 0 이 나올 수 있고, 그러면 테스트가 버그를 놓친다.
#
# 해법 — DB 응답에 인위적 지연을 넣어 "센다 -> 비교한다 -> 기록한다" 사이의
# 창을 넓힌다. 경합이 우연이 아니라 필연이 된다.
#
#   앱 --> toxiproxy:13307 --[latency]--> mysql:3306
#
# 이건 성능 측정이 아니다. 지연을 넣었으니 당연히 느리다.
# 재는 것은 "정원을 넘긴 인원이 몇 명인가" 뿐이다.

set -euo pipefail
cd "$(dirname "$0")/.."

PERF_PORT="${PERF_PORT:-8081}"; export PERF_PORT
TOXI="http://localhost:8474"
CAPACITY="${CAPACITY:-30}"
ATTEMPTS="${ATTEMPTS:-150}"
DB_LATENCY_MS="${DB_LATENCY_MS:-20}"
ROUNDS="${ROUNDS:-3}"
LOG_DIR="build/benchmark-logs"
OUT="docs/performance/data/lock-determinism.json"
mkdir -p "$LOG_DIR" docs/performance/data

command -v k6 >/dev/null || { echo "k6 가 없다: brew install k6"; exit 1; }

echo "== 컨테이너 기동 =="
docker compose -f docker-compose.perf.yml up -d >/dev/null
for i in $(seq 1 40); do
  curl -sf "$TOXI/version" >/dev/null 2>&1 && break
  sleep 2
done

echo "== MySQL 프록시 등록 =="
curl -sf -X DELETE "$TOXI/proxies/mysql" >/dev/null 2>&1 || true
curl -sf -X POST "$TOXI/proxies" -H 'Content-Type: application/json' \
  -d '{"name":"mysql","listen":"0.0.0.0:13307","upstream":"mysql:3306","enabled":true}' >/dev/null
echo "   앱 --> toxiproxy:13307 --> mysql:3306"

echo "== 애플리케이션 빌드 =="
(cd backend && ./gradlew perfBootJar -q)
# 벤치마크 엔드포인트는 perf 소스셋에만 있다. 운영 jar 에는 없다. (#57)
JAR=$(ls backend/build/libs/*-perf.jar | head -1)

APP_PID=""
cleanup() {
  curl -sf -X DELETE "$TOXI/proxies/mysql/toxics/dblatency" >/dev/null 2>&1 || true
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

# 앱은 프록시를 통해 DB 에 붙는다.
PERF_DB_PORT=13307 PERF_BATCH_SIZE=100 java -jar "$JAR" --spring.profiles.active=perf \
    > "$LOG_DIR/app-lock.log" 2>&1 &
APP_PID=$!

echo -n "   기동 대기"
for i in $(seq 1 120); do
  MID=$(curl -sf "http://localhost:$PERF_PORT/api/perf/sessions/seeded" 2>/dev/null \
        | sed -n 's/.*"meetingId":\([0-9]*\).*/\1/p' || true)
  [ -n "$MID" ] && { echo " ok (meetingId=$MID)"; break; }
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo " 실패"; tail -30 "$LOG_DIR/app-lock.log"; exit 1
  fi
  echo -n "."; sleep 2
done
[ -n "${MID:-}" ] || { echo "세션을 찾지 못했다"; exit 1; }

add_db_latency() {
  curl -sf -X POST "$TOXI/proxies/mysql/toxics" -H 'Content-Type: application/json' \
    -d "{\"name\":\"dblatency\",\"type\":\"latency\",\"stream\":\"downstream\",\"attributes\":{\"latency\":$1,\"jitter\":0}}" >/dev/null
}
del_db_latency() { curl -sf -X DELETE "$TOXI/proxies/mysql/toxics/dblatency" >/dev/null 2>&1 || true; }

RESULTS="[]"

# $1 라벨  $2 lock(true/false)
round() {
  local label="$1" lock="$2" overflow active
  k6 run -q --no-summary -e LOCK="$lock" -e LABEL="tmp-$label" -e CAPACITY="$CAPACITY" \
      -e ATTEMPTS="$ATTEMPTS" k6/session-capacity.js >/dev/null 2>&1 || true
  local state
  state=$(curl -sf "http://localhost:$PERF_PORT/api/perf/sessions/state?meetingId=$MID" || echo '{}')
  active=$(echo "$state" | sed -n 's/.*"active":\([0-9]*\).*/\1/p')
  overflow=$(echo "$state" | sed -n 's/.*"overflow":\([0-9]*\).*/\1/p')
  printf "     입장 %-4s 정원 %-4s 초과 %s\n" "${active:-?}" "$CAPACITY" "${overflow:-?}"
  RESULTS=$(python3 -c "
import json,sys
r=json.loads(sys.argv[1]); r.append({'case':sys.argv[2],'lock':sys.argv[3]=='true',
 'active':int(sys.argv[4] or 0),'capacity':int(sys.argv[5]),'overflow':int(sys.argv[6] or 0)})
print(json.dumps(r,ensure_ascii=False))" "$RESULTS" "$label" "$lock" "${active:-0}" "$CAPACITY" "${overflow:-0}")
}

echo
echo "=============================================================="
echo " A. DB 지연 없음 — 경합이 우연에 달렸다"
echo "=============================================================="
del_db_latency
for r in $(seq 1 "$ROUNDS"); do
  echo "   [${r}회차] 잠금 없음"
  round "지연없음-잠금없음" false
done

echo
echo "=============================================================="
echo " B. DB 지연 ${DB_LATENCY_MS}ms — 레이스 윈도우를 넓힌다"
echo "=============================================================="
add_db_latency "$DB_LATENCY_MS"
for r in $(seq 1 "$ROUNDS"); do
  echo "   [${r}회차] 잠금 없음"
  round "지연${DB_LATENCY_MS}ms-잠금없음" false
done

echo
echo "=============================================================="
echo " C. DB 지연 ${DB_LATENCY_MS}ms + 비관적 잠금 — 지연이 있어도 지켜지는가"
echo "=============================================================="
for r in $(seq 1 "$ROUNDS"); do
  echo "   [${r}회차] 비관적 잠금"
  round "지연${DB_LATENCY_MS}ms-잠금있음" true
done
del_db_latency

rm -f docs/performance/data/k6-session-tmp-*.json
echo "$RESULTS" > "$OUT"
echo
python3 - "$OUT" <<'PY'
import json, sys, collections
rows = json.load(open(sys.argv[1]))
g = collections.OrderedDict()
for r in rows:
    g.setdefault(r['case'], []).append(r['overflow'])
print("== 요약: 회차별 정원 초과 인원 ==")
for case, overflows in g.items():
    detected = sum(1 for o in overflows if o > 0)
    print(f"  {case:<26} {overflows}   버그 검출 {detected}/{len(overflows)}회")
PY
echo
echo "== 완료: $OUT =="
