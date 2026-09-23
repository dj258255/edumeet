#!/usr/bin/env bash
# Redis 를 멈춘 채 발행 지연을 잰다. (#201 · 검토 8)
#
#   TAG=before BREAKER_MS=0 ./scripts/run-chat-archive-redis-down.sh   # 차단 창 끔 = 넣기 전
#   TAG=after              ./scripts/run-chat-archive-redis-down.sh   # 차단 창 켬 (기본 5초)
#
# ★ 왜 재나.
#   기본 모드가 stream 이라 **모든 방송 채팅이 XADD 를 먼저 시도한다.** Redis 가 죽어 있으면
#   그 실패를 기다리는 시간이 발행 요청에 그대로 붙는다. 그 값이 얼마인지 모르면
#   "차단 창을 둘까" 를 감으로 정하게 된다 - 이 저장소는 감으로 정한 것을 되돌린 적이 있다
#   (CLAUDE.md §6 의 LiveKit 서킷 브레이커가 그 예다: 근거가 없어서 기각됐지, 틀려서가 아니다).
#
# ★ Lettuce 타임아웃 (이 측정의 상한을 정하는 값)
#   spring.data.redis.timeout          2s    명령 타임아웃 (Lettuce 기본은 60s - 부트가 덮는다)
#   spring.data.redis.connect-timeout  1s    연결 수립 타임아웃
#   lettuce.pool                       없음  풀을 안 쓴다 = 연결 하나를 공유한다
#   shutdown-timeout                   없음  100ms(기본)
#
#   로컬에서 컨테이너를 stop 하면 포트가 닫혀 **연결 거부가 즉시** 온다. 그래서 이 조건의
#   지연은 "응답 없는 Redis" 의 최악이 아니라 최선이다. 멈춘 채로 두는 이유는
#   실제로 일어나는 모양(레디스가 죽어 있음)이 그것이고, 그때 발행이 어떻게 되는지가
#   알고 싶은 것이기 때문이다.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/chat-archive-perf.sh
. scripts/lib/chat-archive-perf.sh

TAG="${TAG:-before}"
RATE="${RATE:-100}"
PUBLISHERS="${PUBLISHERS:-1}"
MEASURE_MS="${MEASURE_MS:-20000}"
ROUNDS="${ROUNDS:-1}"
WARMUP_MS="${WARMUP_MS:-10000}"
OUT_DIR="perf/out/chat-archive-redis-down/$TAG"

cleanup() {
  docker start "$REDIS_CONTAINER" >/dev/null 2>&1
  app_stop TERM
}
trap cleanup EXIT INT TERM
for tool in docker k6 java curl python3; do need "$tool"; done

banner "Redis 정지 상태의 발행 지연 — $TAG"
compose_up
mkdir -p "$OUT_DIR"
log "   RATE=$RATE/s · 측정 ${MEASURE_MS}ms · 산출물 $OUT_DIR"
build_jar
toxiproxy_ready

log "== 스키마와 회의를 만든다 (Redis 가 살아 있을 때) =="
app_start "$OUT_DIR/schema.log"
app_wait_ready || { log "기동 실패"; tail -20 "$OUT_DIR/schema.log"; exit 1; }
SETUP=$(perf_setup BROADCAST)
MEETING_ID=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["meetingId"])')
TOKEN=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
app_stop TERM
log "   meetingId=$MEETING_ID"

log "== 앱을 stream 모드로 띄운다 =="
# BREAKER_MS=0 이면 차단 창을 끈다 - "넣기 전" 을 같은 코드로 잰다(다른 커밋과 비교하지 않는다).
BREAKER_ARGS=()
[ -n "${BREAKER_MS:-}" ] && BREAKER_ARGS=("--edumeet.chat.archive.stream.breaker-ms=$BREAKER_MS")
app_start "$OUT_DIR/app.log" --spring.jpa.hibernate.ddl-auto=none \
  --edumeet.chat.archive.mode=stream --edumeet.chat.archive.claim-min-idle-ms=30000 \
  ${BREAKER_ARGS[@]+"${BREAKER_ARGS[@]}"}
app_wait_ready || { log "기동 실패"; tail -20 "$OUT_DIR/app.log"; exit 1; }


# 발행 지연 히스토그램에서 "느린 발행"을 직접 센다.
#   p99 만 보면 "1%가 느렸다" 까지만 알 수 있다. 몇 건이 얼마나 느렸는지가 알고 싶은 것이다.
latency_slow_counts() {
  # 1초를 넘긴 발행이 몇 건인가. p99 만 보면 "1%가 느렸다" 까지만 알 수 있다.
  # ★ 버킷 이름은 지수(exponential) 경계라 정확히 "1.0" 이 없다 - 1.0 이하 중 가장 큰 버킷을 쓴다.
  curl -sf --max-time 3 "http://localhost:${MGMT_PORT}/actuator/prometheus" 2>/dev/null \
    | python3 -c '
import sys
total = 0
under = None
best = -1.0
for line in sys.stdin:
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    head, _, rest = line.rpartition(" ")
    name = head.split("{")[0]
    if name == "chat_publish_latency_seconds_count":
        total = float(rest)
    elif name == "chat_publish_latency_seconds_bucket" and "le=\"" in head:
        try:
            le = float(head.split("le=\"")[1].split("\"")[0])
        except (IndexError, ValueError):
            continue
        if le <= 1.0 and le > best:
            best = le
            under = float(rest)
print(f"{int(total)} {int(under) if under is not None else 0}")
'
}

measure_round() {  # measure_round <회차>
  local round="$1"
  local round_dir="$OUT_DIR/round-$round"
  mkdir -p "$round_dir"

  # 회차마다 앱이 새로 떠 있다(JIT 전). 워밍업을 버리고 측정 창만 센다.
  log "   워밍업 ${WARMUP_MS}ms (Redis 살아 있음 · 버린다)"
  run_k6 "$MEETING_ID" "$TOKEN" "$WARMUP_MS" "" "$RATE" "$PUBLISHERS" >/dev/null 2>&1
  sleep 2

  : > "$round_dir/samples"
  ( while :; do printf '%s %s %s\n' "$(now_ms)" "$(scrape_metrics)" "$(latency_slow_counts)" >> "$round_dir/samples"; sleep 1; done ) &
  SAMPLER_PID=$!

  log "== Redis 컨테이너를 멈춘다 =="
  docker stop "$REDIS_CONTAINER" >/dev/null
  sleep 1

  log "== ${round}회차 발행 ${MEASURE_MS}ms (목표 ${RATE}/s) =="
  local k6json="$round_dir/k6.json"
  run_k6 "$MEETING_ID" "$TOKEN" "$MEASURE_MS" "$k6json" "$RATE" "$PUBLISHERS" \
    > "$round_dir/k6.txt" 2>&1

  sleep 2
  kill "$SAMPLER_PID" 2>/dev/null
  SAMPLER_PID=""

  log "== Redis 를 다시 띄운다 =="
  docker start "$REDIS_CONTAINER" >/dev/null
  for _ in $(seq 1 30); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' "$REDIS_CONTAINER" 2>/dev/null)" = "healthy" ] && break
    sleep 1
  done

  python3 - "$round_dir/result.json" "$TAG" "$round" "$RATE" "$MEASURE_MS" "$k6json" "$round_dir/samples" <<'PY'
import json, sys

(path, tag, round_no, rate, measure_ms, k6json, samples) = sys.argv[1:8]
out = {"tag": tag, "round": int(round_no), "targetRate": int(rate), "measureMs": int(measure_ms)}

k6 = json.load(open(k6json))
out.update(published=k6["published"], accepted=k6["accepted"], acceptRate=k6["acceptRate"],
           connectErrors=k6["connectErrors"], e2eP50Ms=k6["e2eP50Ms"], e2eP99Ms=k6["e2eP99Ms"])

def last(field):
    value = ""
    for line in open(samples, encoding="utf-8"):
        parts = line.split()
        if len(parts) > field and parts[field]:
            value = parts[field]
    return value

# scrape: 1 queued 2 persist_failed 3 dropped 4 duplicates 5 fallback 6 length 7 pending 8 p50 9 p99
out["queued"] = last(1)
out["fallback"] = last(5)
out["publishP50Ms"] = last(8)
out["publishP99Ms"] = last(9)
# 10=전체 발행 수, 11=1초 이하 건수 -> 1초 초과 = 10-11
try:
    total = int(last(10))
    under = int(last(11))
    out["publishSamples"] = total
    out["publishOver1s"] = total - under
    out["publishOver1sPct"] = round((total - under) / total * 100, 2) if total else None
except (TypeError, ValueError):
    pass
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("   " + json.dumps(out, ensure_ascii=False))
PY
}

for round in $(seq 1 "$ROUNDS"); do
  measure_round "$round"
  app_stop TERM
  # 앱은 회차마다 새로 띄운다 (앞 회차의 히스토그램이 섞이면 p50/p99 가 오염된다)
  sleep 2
  app_start "$OUT_DIR/app-$round.log" --spring.jpa.hibernate.ddl-auto=none \
    --edumeet.chat.archive.mode=stream --edumeet.chat.archive.claim-min-idle-ms=30000 \
    ${BREAKER_ARGS[@]+"${BREAKER_ARGS[@]}"}
  app_wait_ready || { log "재기동 실패"; exit 1; }
done
app_stop TERM

python3 - "$OUT_DIR" "$TAG" <<'PY'
import glob, json, sys

out_dir, tag = sys.argv[1:3]
rounds = sorted(glob.glob(f"{out_dir}/round-*/result.json"))
rows = [json.load(open(p, encoding="utf-8")) for p in rounds]

def num(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None

print("")
print("| 회차 | 수락(/s) | 발행 p50(ms) | 발행 p99(ms) | 표본 | 1초 초과 | 1초 초과 % | fallback |")
print("|---|---|---|---|---|---|---|---|")
for r in rows:
    print(f"| {r['round']} | {r['acceptRate']} | {num(r['publishP50Ms']):.3f} | {num(r['publishP99Ms']):.1f} | "
          f"{r.get('publishSamples')} | {r.get('publishOver1s')} | {r.get('publishOver1sPct')} | {r['fallback']} |")

def mean(key, digits):
    values = [num(r.get(key)) for r in rows]
    values = [v for v in values if v is not None]
    return round(sum(values) / len(values), digits) if values else "-"

print("")
print(f"평균 ({tag}, {len(rows)}회차): 수락 {mean('acceptRate', 1)}/s · "
      f"p50 {mean('publishP50Ms', 3)}ms · p99 {mean('publishP99Ms', 1)}ms · "
      f"1초 초과 {mean('publishOver1sPct', 2)}% · fallback {mean('fallback', 0)}")
json.dump({"tag": tag, "rounds": rows,
           "meanP50Ms": mean('publishP50Ms', 3), "meanP99Ms": mean('publishP99Ms', 1),
           "meanAcceptRate": mean('acceptRate', 1), "meanOver1sPct": mean('publishOver1sPct', 2)},
          open(f"{out_dir}/summary.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
app_stop TERM
log "   산출물: $OUT_DIR"
