#!/usr/bin/env bash
# 다시보기 채팅 — 사건별 유실 측정. (#201 / B8 · B9r)
#
#   RUN=b9r-memory MODE=memory ./scripts/run-chat-archive-loss.sh
#   RUN=b9r-stream MODE=stream ./scripts/run-chat-archive-loss.sh
#   RUN=... MODE=stream CASES="db-cut" ./scripts/run-chat-archive-loss.sh
#
#  세 경우를 돈다 (2회차는 순서를 뒤집어 조건을 갈아 탄다)
#   db-cut   발행 중 DB 30초 정지          배치가 못 나간다
#   kill9    발행 중 kill -9               메모리 큐가 사라진다
#   sigterm  정상 종료(SIGTERM)            @PreDestroy 가 비운다
#
# ★ 이 스크립트가 B9 와 달라진 곳 (검토 반영)
#
#   1) db-cut 이 **진짜 장애**다.
#      예전에는 Toxiproxy 프록시를 껐다(`enabled:false`). 그건 **새 연결만** 막는다 -
#      HikariCP 는 맺어 둔 연결을 재사용하므로 풀에 남은 연결로 쿼리가 계속 나갔고,
#      30초 동안 3,800건이 정상 발행됐다. 장애가 아니었다.
#      이제 `timeout` 독(timeout=0)을 건다 - 연결을 닫지 않고 흐름을 멈추므로
#      **풀에 있는 연결까지** 멈춘다.
#
#   2) 세는 시점이 "발행이 끝나고 대기열이 빈 뒤"다.
#      수락 − 저장 은 대기열이 차 있으면 미저장과 유실이 섞인다.
#      사건 뒤 발행을 멈추고 lag(미독)·pending(ACK 전)이 0 이 될 때까지(상한 60초) 기다린 뒤 센다.
#      memory 는 chat_archive_queued 가 0 이 될 때까지. 죽은 프로세스의 큐는 기다릴 것이 없다.
#
#   3) 스키마를 유지한다.
#      회차마다 지우면 앞 회차가 남긴 스트림 항목을 다음 회차가 저장할 수 없어
#      stream 의 "미저장" 을 회수로 바꿔 볼 수 없다. 준비 단계에서 한 번만 만든다.
#      그래서 stream + kill -9/sigterm 은 **앱을 다시 띄워 회수한 뒤** 센다.
#
# ★ 워밍업은 측정과 분리한다(JIT). 워밍업은 버리고 측정 창의 델타만 적는다.
# ★ "수락" 은 부하 도구가 구독으로 확인한 수다 = 앱이 처리해 대기열에 넣은 수.
# ★ MODE=memory|stream 으로 저장 대기열을 고른다.
#
#   memory : 프로세스가 죽으면 큐가 사라진다
#   stream : Redis Stream. ACK 못 한 항목을 다른 소비자가 클레임한다

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/chat-archive-perf.sh
. scripts/lib/chat-archive-perf.sh

RUN="${RUN:-$(date +%Y%m%d-%H%M%S)}"
MODE="${MODE:-memory}"
RATE="${RATE:-100}"
PUBLISHERS="${PUBLISHERS:-1}"
WARMUP_MS="${WARMUP_MS:-10000}"
DB_CUT_MS="${DB_CUT_MS:-30000}"
DRAIN_CAP_S="${DRAIN_CAP_S:-60}"
ROUNDS="${ROUNDS:-2}"
CASES="${CASES:-}"

OUT_DIR="perf/out/chat-archive-loss/$RUN"
TABLE_FILE="$OUT_DIR/table.md"
SAMPLER_PID=""
K6_PID=""

cleanup() {
  [ -n "$K6_PID" ] && kill "$K6_PID" 2>/dev/null
  [ -n "$SAMPLER_PID" ] && kill "$SAMPLER_PID" 2>/dev/null
  stall_off
  app_stop TERM
}
trap cleanup EXIT INT TERM

for tool in docker k6 java curl python3; do need "$tool"; done

write_json() {  # write_json <path> key=value ...
  local path="$1"; shift
  python3 - "$path" "$@" <<'PY'
import json, sys
out = {}
for pair in sys.argv[2:]:
    key, _, value = pair.partition('=')
    if value in ('', '-', 'None'):
        out[key] = None
    else:
        try:
            out[key] = int(value)
        except ValueError:
            try:
                out[key] = float(value)
            except ValueError:
                out[key] = value
open(sys.argv[1], 'w', encoding='utf-8').write(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
PY
}

last_sample() { awk -v f="$2" 'length($f) > 0 { v = $f } END { print (length(v) > 0 ? v : "-") }' "$1"; }
reverse() { printf '%s\n' "$@" | awk '{a[NR]=$0} END { for (i=NR; i>=1; i--) print a[i] }'; }

case_publish_ms() {
  case "$1" in
    db-cut) echo "${DB_CUT_CASE_MS:-60000}" ;;
    *) echo "${EVENT_CASE_MS:-25000}" ;;
  esac
}
case_event_at() {
  case "$1" in
    db-cut) echo "${DB_CUT_AT_S:-10}" ;;
    *) echo "${EVENT_AT_S:-15}" ;;
  esac
}

# ── 준비 ──────────────────────────────────────────────────────────
banner "준비 — 로컬 perf"
compose_up
mkdir -p "$OUT_DIR"
log "   RUN=$RUN · MODE=$MODE · RATE=$RATE/s · 산출물 $OUT_DIR"
build_jar
toxiproxy_ready

log "== 스키마와 회의를 한 번만 만든다 (이후 기동은 ddl-auto=none) =="
app_start "$OUT_DIR/schema.log"
if ! app_wait_ready; then log "기동 실패"; tail -30 "$OUT_DIR/schema.log"; exit 1; fi
SETUP=$(perf_setup BROADCAST)
MEETING_ID=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["meetingId"])')
TOKEN=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
APP_EXTRA=(--spring.jpa.hibernate.ddl-auto=none)
log "   meetingId=$MEETING_ID"
app_stop TERM

# ── 한 회차 ───────────────────────────────────────────────────────
run_case() {  # run_case <case> <round>
  local case_name="$1" round="$2"
  local publish_ms event_at
  publish_ms=$(case_publish_ms "$case_name")
  event_at=$(case_event_at "$case_name")

  banner "$case_name — ${round}회차 (MODE=$MODE · 발행 ${publish_ms}ms · 사건 +${event_at}s)"
  local app_log="$OUT_DIR/$case_name-r$round-app.log"
  local samples="$OUT_DIR/$case_name-r$round.samples"

  local mode_args=("--edumeet.chat.archive.mode=$MODE")
  [ "$MODE" = "stream" ] && mode_args+=("--edumeet.chat.archive.claim-min-idle-ms=${CLAIM_MIN_IDLE_MS:-5000}")

  app_start "$app_log" "${APP_EXTRA[@]}" "${mode_args[@]}"
  if ! app_wait_ready; then log "기동 실패"; tail -20 "$app_log"; return 1; fi

  log "   워밍업 ${WARMUP_MS}ms (버린다)"
  run_k6 "$MEETING_ID" "$TOKEN" "$WARMUP_MS" "" "$RATE" "$PUBLISHERS" >/dev/null 2>&1
  sleep 3

  local rows_before
  rows_before=$(mysql_count "$MEETING_ID")

  : > "$samples"
  ( while :; do printf '%s %s\n' "$(date +%s)" "$(scrape_metrics)" >> "$samples"; sleep 1; done ) &
  SAMPLER_PID=$!

  local k6json="$OUT_DIR/$case_name-r$round-k6.json"
  run_k6 "$MEETING_ID" "$TOKEN" "$publish_ms" "$k6json" "$RATE" "$PUBLISHERS" \
    > "$OUT_DIR/$case_name-r$round-k6.txt" 2>&1 &
  K6_PID=$!

  sleep "$event_at"
  local shutdown_ms="-" t0
  case "$case_name" in
    db-cut)
      log "   사건: DB 정지 ${DB_CUT_MS}ms (timeout 독 - 풀에 있는 연결까지 멈춘다)"
      stall_on
      sleep $((DB_CUT_MS / 1000))
      stall_off
      log "   사건 종료: DB 복구"
      ;;
    kill9)
      log "   사건: kill -9"
      app_stop KILL 3000
      ;;
    sigterm)
      t0=$(now_s)
      log "   사건: SIGTERM"
      app_stop TERM 15000
      shutdown_ms=$(elapsed_ms "$t0")
      log "   종료까지 ${shutdown_ms}ms"
      ;;
  esac

  wait "$K6_PID" 2>/dev/null
  K6_PID=""
  kill "$SAMPLER_PID" 2>/dev/null
  SAMPLER_PID=""

  # ── 배출: 대기열이 빌 때까지. 여기서 미저장과 유실이 갈린다. ──
  local drain_started drained="?" drain_result="-" drain_took="0"
  drain_started=$(now_s)
  if [ "$MODE" = "stream" ]; then
    if ! app_alive; then
      log "   배출: 앱이 죽어 있다 → 다시 띄워 회수한다"
      app_start "$app_log.restart" "${APP_EXTRA[@]}" "${mode_args[@]}"
      if app_wait_ready; then
        if drain_result=$(wait_drained stream "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
      else
        log "   재기동 실패 - 배출을 못 본다"
        drained="restart-failed"
      fi
    else
      if drain_result=$(wait_drained stream "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
    fi
    app_stop TERM
  else
    if app_alive; then
      if drain_result=$(wait_drained memory "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
      app_stop TERM
    else
      # 프로세스가 죽었으면 큐도 같이 죽었다. 기다릴 것이 없다.
      drain_result="프로세스와 함께 사라짐"
      drained="died"
    fi
  fi
  drain_took=$(elapsed_s "$drain_started")

  local rows_after accepted attempted publish_rate accept_rate
  local dropped persist_failed duplicates fallback p50 p99 uid_dups
  rows_after=$(mysql_count "$MEETING_ID")
  uid_dups=$(mysql_uid_duplicates "$MEETING_ID")
  accepted=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["accepted"])' "$k6json" 2>/dev/null || echo "-")
  attempted=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["published"])' "$k6json" 2>/dev/null || echo "-")
  publish_rate=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["publishRate"])' "$k6json" 2>/dev/null || echo "-")
  accept_rate=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["acceptRate"])' "$k6json" 2>/dev/null || echo "-")
  persist_failed=$(last_sample "$samples" 3)
  dropped=$(last_sample "$samples" 4)
  duplicates=$(last_sample "$samples" 5)
  fallback=$(last_sample "$samples" 6)
  p50=$(last_sample "$samples" 9)
  p99=$(last_sample "$samples" 10)

  local saved=$((rows_after - rows_before))
  local lost="-"
  [ "$accepted" != "-" ] && lost=$((accepted - saved))

  write_json "$OUT_DIR/$case_name-r$round.json" \
    "mode=$MODE" "case=$case_name" "round=$round" "meetingId=$MEETING_ID" "rate=$RATE" \
    "publishers=$PUBLISHERS" "publishMs=$publish_ms" "eventAtS=$event_at" "warmupMs=$WARMUP_MS" \
    "attempted=$attempted" "accepted=$accepted" "publishedPerSec=$publish_rate" "acceptedPerSec=$accept_rate" \
    "saved=$saved" "lost=$lost" "rowsBefore=$rows_before" "rowsAfter=$rows_after" "uidDuplicates=$uid_dups" \
    "dropped=$dropped" "persistFailed=$persist_failed" "duplicates=$duplicates" "fallback=$fallback" \
    "publishP50Ms=$p50" "publishP99Ms=$p99" \
    "drained=$drained" "drainResult=$drain_result" "drainSeconds=$drain_took" "shutdownMs=$shutdown_ms"

  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$MODE" "$case_name" "$round" "$accepted" "$saved" "$lost" "$duplicates" "$fallback" "$p50" "$p99" \
    "$([ "$drained" = "yes" ] && echo "비었음 ${drain_took}s" || echo "$drained ${drain_took}s")" \
    "$([ "$shutdown_ms" = "-" ] && echo "persist.failed $persist_failed · dropped $dropped · $drain_result" \
        || echo "종료 ${shutdown_ms}ms · $drain_result")" \
    >> "$TABLE_FILE"

  log "   수락 $accepted ($accept_rate/s) · 저장 $saved · 유실 ${lost} · 중복 $duplicates · uid중복 $uid_dups · 배출 $drained(${drain_took}s)"
  hikari_symptoms "$app_log" "$case_name"
}

printf '| 모드 | 경우 | 회차 | 발행 수락 | 저장 | 유실 | 중복 | fallback | 발행 p50(ms) | 발행 p99(ms) | 배출 | 비고 |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n' > "$TABLE_FILE"

if [ -n "$CASES" ]; then
  IFS=' ' read -r -a SELECTED <<<"$CASES"
else
  SELECTED=(db-cut kill9 sigterm)
fi
for r in $(seq 1 "$ROUNDS"); do
  ORDER=()
  if [ "$r" -eq 1 ]; then
    ORDER=("${SELECTED[@]}")
  else
    # macOS 의 bash 3.2 에는 mapfile 이 없다. while read 로 받는다.
    while IFS= read -r line; do ORDER+=("$line"); done < <(reverse "${SELECTED[@]}")
  fi
  for c in "${ORDER[@]}"; do run_case "$c" "$r"; done
done

banner "표"
cat "$TABLE_FILE"
log "   산출물: $OUT_DIR"
