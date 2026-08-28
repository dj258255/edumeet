#!/usr/bin/env bash
#
# LiveKit 장애를 주입해 타임아웃과 폴백이 실제로 동작하는지 확인한다.
#
#   ./scripts/run-fault-injection.sh
#
# 순서가 중요하다 — 막을 장치를 먼저 만들고(#19), 그 장치가 동작함을
# 증명하는 데 장애 주입을 쓴다. 장치 없이 넣으면 앱이 죽는 것을 증명할 뿐이다.
#
#   앱 --> toxiproxy:7881 --[toxic]--> livekit:7880
#
# 대조군(legacy=true)은 수정 전 코드(new RestTemplate, 타임아웃 무한)를 탄다.

set -euo pipefail
cd "$(dirname "$0")/.."

PERF_PORT="${PERF_PORT:-8081}"; export PERF_PORT
TOXI="http://localhost:8474"
APP="http://localhost:$PERF_PORT/api/perf/livekit/room"
LOG_DIR="build/benchmark-logs"
OUT="docs/performance/data/fault-injection.json"
mkdir -p "$LOG_DIR" docs/performance/data

echo "== 컨테이너 기동 =="
docker compose -f docker-compose.perf.yml up -d >/dev/null
for i in $(seq 1 40); do
  curl -sf "$TOXI/version" >/dev/null 2>&1 && break
  sleep 2
done

# 프록시를 다시 만든다. 이전 실행의 toxic 이 남아 있으면 결과가 오염된다.
curl -sf -X DELETE "$TOXI/proxies/livekit" >/dev/null 2>&1 || true
curl -sf -X POST "$TOXI/proxies" -H 'Content-Type: application/json' \
  -d '{"name":"livekit","listen":"0.0.0.0:7881","upstream":"livekit:7880","enabled":true}' >/dev/null

echo "== 애플리케이션 빌드 =="
(cd backend && ./gradlew perfBootJar -q)
# 벤치마크 엔드포인트는 perf 소스셋에만 있다. 운영 jar 에는 없다. (#57)
JAR=$(ls backend/build/libs/*-perf.jar | head -1)

APP_PID=""
cleanup() {
  curl -sf -X DELETE "$TOXI/proxies/livekit/toxics/latency" >/dev/null 2>&1 || true
  curl -sf -X DELETE "$TOXI/proxies/livekit/toxics/blackhole" >/dev/null 2>&1 || true
  curl -sf -X POST "$TOXI/proxies/livekit" -H 'Content-Type: application/json' \
       -d '{"enabled":true}' >/dev/null 2>&1 || true
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

PERF_BATCH_SIZE=100 java -jar "$JAR" --spring.profiles.active=perf \
    > "$LOG_DIR/app-fault.log" 2>&1 &
APP_PID=$!

echo -n "   기동 대기"
for i in $(seq 1 120); do
  # 룸이 없으면 404 가 정상이다. -sf 는 404 를 실패로 보므로 상태코드로 판정한다.
  # curl 은 실패해도 -w 로 000 을 찍는다. || echo 를 덧붙이면 000000 이 되어
  # 판정이 즉시 통과해버리므로 || true 로만 종료코드를 삼킨다.
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$APP?name=probe" 2>/dev/null || true)
  if [ -n "$CODE" ] && [ "$CODE" != "000" ]; then echo " ok (HTTP $CODE)"; break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo " 실패"; tail -30 "$LOG_DIR/app-fault.log"; exit 1
  fi
  echo -n "."; sleep 2
done

RESULTS="[]"

# $1 라벨  $2 legacy(true/false)  $3 curl 최대 대기(초)
probe() {
  local label="$1" legacy="$2" maxtime="$3"
  local body http elapsed
  body=$(curl -s --max-time "$maxtime" -w '\n%{http_code}\n%{time_total}' \
         "$APP?name=probe&legacy=$legacy" 2>/dev/null || echo -e '\n000\ntimeout')
  http=$(echo "$body" | tail -2 | head -1)
  elapsed=$(echo "$body" | tail -1)
  local outcome
  outcome=$(echo "$body" | head -1 | sed -n 's/.*"outcome":"\([A-Z_]*\)".*/\1/p')
  [ "$http" = "000" ] && outcome="클라이언트가 먼저 포기함"
  printf "   %-22s HTTP %-4s %8ss  %s\n" "$label" "${http:-000}" "$elapsed" "${outcome:-?}"
  RESULTS=$(python3 -c "
import json,sys
r=json.loads(sys.argv[1]); r.append({'case':sys.argv[2],'legacy':sys.argv[3]=='true',
 'http':sys.argv[4],'seconds':sys.argv[5],'outcome':sys.argv[6]})
print(json.dumps(r,ensure_ascii=False))" "$RESULTS" "$label" "$legacy" "${http:-000}" "$elapsed" "${outcome:-?}")
}

add_latency() {
  curl -sf -X POST "$TOXI/proxies/livekit/toxics" -H 'Content-Type: application/json' \
    -d "{\"name\":\"latency\",\"type\":\"latency\",\"stream\":\"downstream\",\"attributes\":{\"latency\":$1,\"jitter\":0}}" >/dev/null
}
del_latency() { curl -sf -X DELETE "$TOXI/proxies/livekit/toxics/latency" >/dev/null 2>&1 || true; }
# 연결은 열어두고 데이터를 한 바이트도 흘리지 않는다. 상대가 "응답하지 않는" 상태다.
# 지연 주입은 결국 응답이 오지만 이건 오지 않는다. 상한이 없다는 것을 이걸로 보인다.
add_blackhole() {
  curl -sf -X POST "$TOXI/proxies/livekit/toxics" -H 'Content-Type: application/json' \
    -d '{"name":"blackhole","type":"timeout","stream":"upstream","attributes":{"timeout":0}}' >/dev/null
}
del_blackhole() { curl -sf -X DELETE "$TOXI/proxies/livekit/toxics/blackhole" >/dev/null 2>&1 || true; }
set_proxy()   { curl -sf -X POST "$TOXI/proxies/livekit" -H 'Content-Type: application/json' -d "{\"enabled\":$1}" >/dev/null; }

echo
echo "=============================================================="
echo " 1. 정상 — LiveKit 이 정상 응답"
echo "=============================================================="
probe "수정 후" false 10
probe "수정 전(대조군)" true 10

echo
echo "=============================================================="
echo " 2. 지연 주입 — 응답이 10초 늦게 온다 (read timeout 3초)"
echo "=============================================================="
add_latency 10000
probe "수정 후" false 20
echo "   (대조군은 무한 대기라 30초에서 클라이언트가 끊는다)"
probe "수정 전(대조군)" true 30
del_latency

echo
echo "=============================================================="
echo " 3. 연결 불가 — LiveKit 에 아예 닿지 못한다 (connect timeout 2초)"
echo "=============================================================="
set_proxy false
probe "수정 후" false 20
probe "수정 전(대조군)" true 20
set_proxy true

echo
echo "=============================================================="
echo " 4. 무응답 — 연결은 되지만 한 바이트도 오지 않는다"
echo "=============================================================="
add_blackhole
probe "수정 후" false 20
echo "   (대조군은 상한이 없다. 30초에서 클라이언트가 먼저 끊는다)"
probe "수정 전(대조군)" true 30
del_blackhole

echo
echo "=============================================================="
echo " 5. 복구 — 장애가 걷힌 뒤 정상으로 돌아오는가"
echo "=============================================================="
probe "수정 후" false 10

echo "$RESULTS" > "$OUT"
echo
echo "== 완료: $OUT =="
