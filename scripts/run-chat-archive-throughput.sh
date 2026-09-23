#!/usr/bin/env bash
# 저장이 발행을 못 따라가기 시작하는 지점을 찾는다. (#201 B9r)
#
#   RUN=b9r-ladder MODES="memory stream" RATES="100 200 400 800 1600" ./scripts/run-chat-archive-throughput.sh
#
# ★ 사다리를 오르는 이유.
#   "초당 몇 건까지 저장되나" 는 한 점에서는 안 보인다. 비율을 올리면서
#   저장이 발행을 따라가는 구간과 못 따라가는 구간이 갈리는 곳을 찾는다.
#
# ★ 무엇을 보나
#   발행 p50/p99     서버 안 Timer(히스토그램 버킷) - 발행 경로가 언제 무너지나
#   실제 발행/수락 비율  목표가 아니라 **낸** 비율. 발행 자체가 못 나가면 그것부터 갈린다
#   XLEN 증가율      스트림에 쌓이는 속도. 저장이 따라가면 0 에 가깝다
#    저장 지연        발행(스트림 항목 id 의 ms) → DB 행이 처음 보인 표본 시각 (stream)
#   배출 시간        발행을 멈춘 뒤 대기열이 빌 때까지. 적체의 총량이다
#   dropped          memory 모드에서 상한을 넘겨 버린 수
#
# ★ 워밍업은 버린다(JIT). 측정 창은 발행이 정상 궤도에 오른 뒤의 구간만 센다.
# ★ 각 단계 2회차.
# ★ 표본 간격 500ms - 저장 지연의 해상도가 그 값이다(그보다 촘촘한 값은 주장하지 않는다).

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/chat-archive-perf.sh
. scripts/lib/chat-archive-perf.sh

RUN="${RUN:-$(date +%Y%m%d-%H%M%S)}"
MODES="${MODES:-memory stream}"
RATES="${RATES:-100 200 400 800 1600}"
ROUNDS="${ROUNDS:-2}"
WARMUP_MS="${WARMUP_MS:-10000}"
MEASURE_MS="${MEASURE_MS:-20000}"
SAMPLE_MS="${SAMPLE_MS:-500}"
DRAIN_CAP_S="${DRAIN_CAP_S:-90}"

OUT_DIR="perf/out/chat-archive-throughput/$RUN"
TABLE_FILE="$OUT_DIR/table.md"
SAMPLER_PID=""
K6_PID=""
cleanup() { [ -n "$K6_PID" ] && kill "$K6_PID" 2>/dev/null; [ -n "$SAMPLER_PID" ] && kill "$SAMPLER_PID" 2>/dev/null; app_stop TERM; }
trap cleanup EXIT INT TERM
for tool in docker k6 java curl python3; do need "$tool"; done

# 목표 비율을 낼 발행자 수.
# ★ 상한을 실측해서 정했다 (perf/out/chat-archive-throughput/calibration).
#   발행자 1명   목표 100 → 실제 96건/s · 목표 200 → 191건/s
#   발행자 4명   목표 400 → 서버 처리 385건/s
#   발행자 16명  목표 1600 → 서버 처리 1560건/s
#   즉 연결 하나가 초당 150~190건을 낸다. 목표÷150 으로 잡으면 발행자가 병목이 되지 않는다.
publishers_for() { python3 -c "import math;print(max(1, math.ceil($1/150)))"; }

# 마지막 스트림 항목 id. 측정 창 안의 항목만 골라내는 기준점이다.
last_stream_id() { redis_cli XREVRANGE "$STREAM_KEY" + - COUNT 1 | head -1 | tr -d '[:space:]'; }

run_case() {  # run_case <mode> <rate> <round>
  local mode="$1" rate="$2" round="$3"
  local pubs; pubs=$(publishers_for "$rate")
  local name="${mode}-${rate}-r${round}"
  banner "$name (목표 ${rate}/s · 발행자 ${pubs} · 측정 ${MEASURE_MS}ms)"

  local mode_args=("--edumeet.chat.archive.mode=$mode")
  [ "$mode" = "stream" ] && mode_args+=("--edumeet.chat.archive.claim-min-idle-ms=5000")
  local schema_args=()
  [ -f "$OUT_DIR/schema.done" ] && schema_args=(--spring.jpa.hibernate.ddl-auto=none)

  # ★ 회차마다 스트림을 비운다. 앱은 회차마다 스키마를 새로 만드는데(ddl-auto=create)
  #   앞 회차가 남긴 항목은 그 스키마에 없는 회의 것이라 **버려진다** -
  #   소비자가 그걸 처리하느라 측정 창을 다 쓰면 "저장 0" 이 나온다(실제로 그렇게 나왔다).
  #   한 회차가 자기 발행만 재도록 격리한다.
  redis_cli DEL "$STREAM_KEY" >/dev/null
  redis_cli DEL "chat:archive:dead" >/dev/null

  app_start "$OUT_DIR/$name-app.log" ${schema_args[@]+"${schema_args[@]}"} "${mode_args[@]}"
  if ! app_wait_ready; then log "기동 실패"; tail -20 "$OUT_DIR/$name-app.log"; return 1; fi
  touch "$OUT_DIR/schema.done"

  local setup meeting_id token
  setup=$(perf_setup BROADCAST)
  meeting_id=$(printf '%s' "$setup" | python3 -c 'import json,sys;print(json.load(sys.stdin)["meetingId"])')
  token=$(printf '%s' "$setup" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

  log "   워밍업 ${WARMUP_MS}ms (버린다)"
  run_k6 "$meeting_id" "$token" "$WARMUP_MS" "" "$rate" "$pubs" >/dev/null 2>&1
  sleep 2

  local from_id; from_id=$(last_stream_id)
  local samples="$OUT_DIR/$name.samples"
  : > "$samples"
  (
    while :; do
      now=$(now_ms)
      rows=$(mysql_count "$meeting_id")
      # ★ xlen 이 아니라 lag 을 적체로 본다. XLEN 은 ACK 해도 줄지 않는다(잘라야 준다) -
      #   그래서 XLEN 증가율은 그냥 발행률이다. 미처리분은 lag(미독) + pending 이다.
      printf '%s %s %s %s %s\n' "$now" "${rows:-?}" "$(scrape_metrics)" "$(xlen)" "$(stream_lag)" >> "$samples"
      sleep 0.5
    done
  ) &
  SAMPLER_PID=$!

  local k6json="$OUT_DIR/$name-k6.json"
  local server_before; server_before=$(server_published)
  run_k6 "$meeting_id" "$token" "$MEASURE_MS" "$k6json" "$rate" "$pubs" \
    > "$OUT_DIR/$name-k6.txt" 2>&1 &
  K6_PID=$!
  wait "$K6_PID" 2>/dev/null
  K6_PID=""
  local server_after; server_after=$(server_published)
  kill "$SAMPLER_PID" 2>/dev/null
  SAMPLER_PID=""

  local drain_started drained="?" drain_s="0"
  drain_started=$(now_s)
  local drain=""
  if [ "$mode" = "stream" ]; then
    if drain=$(wait_drained stream "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
  else
    if drain=$(wait_drained memory "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
  fi
  drain_s=$(elapsed_s "$drain_started")
  log "   배출 $drained (${drain_s}s, 남은값: $drain)"

  local rows_after; rows_after=$(mysql_count "$meeting_id")
  # ★ --csv 로 받으면 안 된다 - redis-cli 가 여러 항목을 한 줄에 몰아 넣어
  #   id 와 필드가 뒤섞인다(그렇게 6.9초짜리 가짜 지연이 나왔다). --raw 로 줄 단위 파싱.
  redis_cli --raw XRANGE "$STREAM_KEY" "$from_id" + > "$OUT_DIR/$name-stream.txt" 2>/dev/null

  # ── 분석 ────────────────────────────────────────────────────────
  python3 - "$OUT_DIR/$name.json" "$mode" "$rate" "$round" "$meeting_id" "$rows_after" "$drain_s" "$drained" "$k6json" "$samples" "$OUT_DIR/$name-stream.txt" "$server_before" "$server_after" <<'PY'
import json, re, sys

(path, mode, rate, round_no, meeting, rows_after, drain_s, drained,
 k6json, samples_path, stream_path, server_before, server_after) = sys.argv[1:14]
rate = int(rate); rows_after = int(rows_after)
out = {"mode": mode, "targetRate": rate, "round": int(round_no), "meetingId": int(meeting),
       "drainSeconds": float(drain_s), "drained": drained}

# k6 요약: 목표가 아니라 실제로 낸 비율
try:
    k6 = json.load(open(k6json))
    out.update(accepted=k6["accepted"], acceptedPerSec=k6["acceptRate"],
               publishedPerSec=k6["publishRate"], publishers=k6["publishers"],
               e2eP50Ms=k6["e2eP50Ms"], e2eP99Ms=k6["e2eP99Ms"])
except Exception:
    out.update(accepted=None, acceptedPerSec=None)

# 표본: 시각(ms) 행수 queued persist_failed dropped duplicates fallback length pending p50 p99 ... xlen
rows_series, xlen_series, queued_series, lag_series, dropped_last = [], [], [], [], 0
for line in open(samples_path, encoding="utf-8"):
    p = line.split()
    if len(p) < 13:
        continue
    t = int(p[0])
    try:
        rows = int(p[1])
    except ValueError:
        continue
    rows_series.append((t, rows))
    queued_series.append(p[2])
    dropped_last = p[4] or dropped_last
    xlen_series.append((t, int(p[11])))
    try:
        lag_series.append(int(p[12]))
    except ValueError:
        pass

def nums(values):
    return [v for v in values if v not in ("", "-")]

if rows_series:
    window = (rows_series[-1][0] - rows_series[0][0]) / 1000.0
    deltas = sorted(rows_series[i + 1][0] - rows_series[i][0] for i in range(len(rows_series) - 1))
    out["sampleIntervalMs"] = deltas[len(deltas) // 2] if deltas else None
    out["windowSeconds"] = round(window, 2)
    out["savedPerSec"] = round((rows_series[-1][1] - rows_series[0][1]) / window, 1) if window > 0 else None

if xlen_series and xlen_series[0][1] is not None:
    win = (xlen_series[-1][0] - xlen_series[0][0]) / 1000.0
    out["xlenStart"] = xlen_series[0][1]
    out["xlenEnd"] = xlen_series[-1][1]
    out["xlenPerSec"] = round((xlen_series[-1][1] - xlen_series[0][1]) / win, 1) if win > 0 else None

# ★ 수락은 서버 쪽 수로도 본다. 구독자 VU 하나가 초당 1,500건 근처에서 포화해
#   서버가 처리한 것보다 적게 센다(B9r 보정). 저장 대기열에 들어간 수는 서버가 안다.
if server_before not in ("", "-") and server_after not in ("", "-"):
    out["serverAccepted"] = int(float(server_after)) - int(float(server_before))
    out["serverPerSec"] = round(out["serverAccepted"] / (out["windowSeconds"] or 1), 1)

out["queuedMax"] = max([float(q) for q in nums(queued_series)] or [0])
out["lagMax"] = max(lag_series) if lag_series else None
out["dropped"] = float(dropped_last) if dropped_last else 0
out["drainedResult"] = drained

# ★ 저장 지연: 발행(스트림 항목 id 의 ms) → DB 행이 처음 보인 표본 시각.
#   행의 삽입 시각은 저장하지 않으므로 표본 해상도(SAMPLE_MS)로만 주장한다.
#   uid 로 스트림 항목과 DB 행을 맞춘다 - 시간대 변환이 끼지 않는다.
if mode == "stream" and rows_series:
    #   --raw 출력은 "항목 id" 줄 뒤에 필드/값 줄이 번갈아 온다(항목마다 반복).
    published_ms = {}
    rid, field = None, None
    for line in open(stream_path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line:
            continue
        if re.fullmatch(r"\d+-\d+", line):
            rid, field = line, None
            continue
        if rid is None:
            continue
        if field is None:
            field = line
        else:
            if field == "uid":
                published_ms[line] = int(rid.split("-")[0])
            field = None
    lookups = sorted(published_ms.values())
    base = rows_series[0][1]
    latencies = []
    for i, pub in enumerate(lookups):
        target = base + i + 1
        for t, rows in rows_series:
            if rows >= target:
                latencies.append(t - pub)
                break
    if latencies:
        latencies.sort()
        out["saveLatencyP50Ms"] = latencies[len(latencies) // 2]
        out["saveLatencyP99Ms"] = latencies[int(len(latencies) * 0.99) - 1 if len(latencies) > 1 else 0]
        out["saveLatencySamples"] = len(latencies)
    else:
        out["saveLatencyP50Ms"] = None

out["rowsSaved"] = rows_after
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("   %s" % json.dumps({k: out[k] for k in
      ("accepted", "acceptedPerSec", "savedPerSec", "xlenPerSec", "queuedMax", "dropped",
       "saveLatencyP50Ms", "saveLatencyP99Ms", "drainSeconds") if k in out}, ensure_ascii=False))
PY

  local p50 p99 xlen_rate saved_rate accepted_rate server_rate
  p50=$(scrape_field 8); p99=$(scrape_field 9)
  read -r accepted_rate saved_rate xlen_rate <<<"$(python3 -c "
import json
d=json.load(open('$OUT_DIR/$name.json'))
print(d.get('acceptedPerSec'), d.get('savedPerSec'), d.get('xlenPerSec'))
")"
  server_rate=$(python3 -c "import json;d=json.load(open('$OUT_DIR/$name.json'));print(d.get('serverPerSec','-'))")
  queue_col=$(python3 -c "
import json
d=json.load(open('$OUT_DIR/$name.json'))
q=d.get('queuedMax') or 0; dr=d.get('dropped') or 0; lag=d.get('lagMax')
print('큐 %.0f/drop %.0f/lag %s' % (q, dr, lag))
")
  latency_col=$(python3 -c "
import json
d=json.load(open('$OUT_DIR/$name.json'))
print('%s/%s (n=%s, 표본 %sms)' % (d.get('saveLatencyP50Ms','-'), d.get('saveLatencyP99Ms','-'),
                                   d.get('saveLatencySamples','-'), d.get('sampleIntervalMs','-')))
")
  log "   구독자수락 ${accepted_rate}/s · 서버수락 ${server_rate}/s · 저장 ${saved_rate}/s · XLEN +${xlen_rate}/s"

  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$mode" "$rate" "$round" "${server_rate}" \
    "${saved_rate:-?}" "${xlen_rate:-?}" \
    "${p50:-?}" "${p99:-?}" \
    "${latency_col}" \
    "${queue_col} · ${drain_s}s($drained)" \
    >> "$TABLE_FILE"

  app_stop TERM
  sleep 2
}

banner "준비 — 처리량 사다리"
compose_up
mkdir -p "$OUT_DIR"
log "   RUN=$RUN · MODES='$MODES' · RATES='$RATES' · 회차 $ROUNDS"
build_jar
toxiproxy_ready

printf '| 모드 | 목표(건/s) | 회차 | 서버수락(건/s) | 저장(건/s) | XLEN증가(건/s) | 발행 p50(ms) | 발행 p99(ms) | 저장지연 p50/p99(ms) | 큐/drop/lag · 배출 |\n|---|---|---|---|---|---|---|---|---|---|\n' > "$TABLE_FILE"

for round in $(seq 1 "$ROUNDS"); do
  for mode in $MODES; do
    for rate in $RATES; do
      run_case "$mode" "$rate" "$round"
    done
  done
done

banner "표"
cat "$TABLE_FILE"
log "   산출물: $OUT_DIR"
