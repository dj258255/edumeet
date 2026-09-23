#!/usr/bin/env bash
# 발행 중 kill -9 를 반복해 **회수**와 **중복**을 직접 만든다. (#201 B9r)
#
#   RUN=b9r-recovery ./scripts/run-chat-archive-recovery.sh
#   ITERS=10 RATE=200 ./scripts/run-chat-archive-recovery.sh
#
# ★ 왜 이 스크립트가 따로 있나.
#   기본 하네스는 회차마다 스키마를 새로 만든다(ddl-auto=create). 그러면 앞 회차가
#   스트림에 남긴 항목을 다음 회차가 **저장할 수 없다**(회의 행이 없다) -
#   그래서 "미저장" 과 "유실" 이 구분되지 않는다.
#   여기서는 스키마를 만들고 그 뒤로는 ddl-auto=none 으로 띄운다.
#   같은 스키마·같은 회의 위에서 회수가 일어나는지 본다.
#
# ★ kill 시점을 배치 주기 안에서 흩는다.
#   고정 시점으로 죽이면 "읽기 전 / 저장 중 / ACK 직전" 중 한 모드만 만난다.
#   저장 주기는 1초(폴링)이고 배치는 200건이다. 1.5~6.0초 사이에서 난수로 흩어
#   ACK 직전에 죽는 경우가 섞이게 한다. 난수 씨앗은 남긴다(재현용).
#
# ★ 중복을 실제로 만든다.
#   ACK 직전에 죽는 창은 밀리초라 난수로 맞히기를 기대할 수 없다.
#   그래서 **같은 uid 로 두 번 XADD** 한다 - Redis Stream 이 "최소 한 번 전달" 이라
#   실제로 만들어 내는 바로 그 모양이다. 소비자는 둘 다 읽고 하나만 저장해야 한다.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/chat-archive-perf.sh
. scripts/lib/chat-archive-perf.sh

RUN="${RUN:-$(date +%Y%m%d-%H%M%S)}"
ITERS="${ITERS:-10}"
RATE="${RATE:-200}"
PUBLISHERS="${PUBLISHERS:-1}"
PUBLISH_MS="${PUBLISH_MS:-8000}"
# ★ k6 는 구독자가 붙을 때까지 SETTLE_MS(4초) 기다렸다가 발행을 시작한다.
#   그 전에 죽이면 발행이 0 이라 회수도 중복도 볼 수 없다 - 실제로 그렇게 8회차를 날렸다.
KILL_MIN_S="${KILL_MIN_S:-4.5}"
KILL_MAX_S="${KILL_MAX_S:-8.0}"
SEED="${SEED:-20260923}"
DRAIN_CAP_S="${DRAIN_CAP_S:-60}"
OUT_DIR="perf/out/chat-archive-recovery/$RUN"
CSV="$OUT_DIR/iterations.csv"
LOG_LEVEL="${LOG_LEVEL:-WARN}"

cleanup() { app_stop TERM; }
trap cleanup EXIT INT TERM
for tool in docker k6 java curl python3; do need "$tool"; done

banner "준비 — 회수·중복 시험 (MODE=stream, 스키마 유지)"
compose_up
mkdir -p "$OUT_DIR"
echo "RUN=$RUN · ITERS=$ITERS · RATE=$RATE/s · SEED=$SEED" > "$OUT_DIR/README.txt"
build_jar
toxiproxy_ready

log "== 스키마와 회의를 한 번만 만든다 (이후 ddl-auto=none) =="
app_start "$OUT_DIR/schema.log"
app_wait_ready || { log "기동 실패"; tail -30 "$OUT_DIR/schema.log"; exit 1; }
SETUP=$(perf_setup BROADCAST)
MEETING_ID=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["meetingId"])')
TOKEN=$(printf '%s' "$SETUP" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
APP_EXTRA=(--spring.jpa.hibernate.ddl-auto=none)
MODE_ARGS=(--edumeet.chat.archive.mode=stream --edumeet.chat.archive.claim-min-idle-ms=5000)
log "   meetingId=$MEETING_ID"
app_stop TERM

printf 'iter,killAtS,accepted,savedBeforeKill,recovered,rowsAfter,savedDelta,duplicatesMetric,drainS\n' > "$CSV"

metric_counter() {  # metric_counter <지표이름> → 앱이 살아 있을 때만 값이 나온다
  curl -sf --max-time 3 "http://localhost:${MGMT_PORT}/actuator/prometheus" 2>/dev/null \
    | awk -v n="$1" '{ name = $1; sub(/\{.*/, "", name); if (name == n) { print $2; exit } }'
}

total_accepted=0
total_saved=0
for i in $(seq 1 "$ITERS"); do
  banner "회차 $i / $ITERS"
  local_rows_before=$(mysql_count "$MEETING_ID")

  app_start "$OUT_DIR/iter$i-app.log" "${APP_EXTRA[@]}" "${MODE_ARGS[@]}"
  app_wait_ready || { log "기동 실패 - 건너뛴다"; continue; }

  local_dups_before=$(metric_counter chat_archive_stream_duplicates_total)
  : "${local_dups_before:=0}"

  k6json="$OUT_DIR/iter$i-k6.json"
  run_k6 "$MEETING_ID" "$TOKEN" "$PUBLISH_MS" "$k6json" "$RATE" "$PUBLISHERS" \
    > "$OUT_DIR/iter$i-k6.txt" 2>&1 &
  K6_PID=$!

  kill_at=$(python3 -c "import random;print(round(random.Random($SEED+$i).uniform($KILL_MIN_S,$KILL_MAX_S),2))")
  log "   ${kill_at}초에 kill -9 (배치 주기 안에서 흩는다)"
  sleep "$kill_at"
  app_stop KILL 3000

  wait "$K6_PID" 2>/dev/null
  K6_PID=""

  rows_at_kill=$(mysql_count "$MEETING_ID")
  accepted=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["accepted"])' "$k6json" 2>/dev/null || echo 0)

  # 다시 띄운다. 여기서 스트림에 남은 것이 저장되면 회수다.
  log "   재기동 → 배출 대기"
  app_start "$OUT_DIR/iter$i-restart.log" "${APP_EXTRA[@]}" "${MODE_ARGS[@]}"
  if app_wait_ready; then
    t0=$(now_s)
    if drain=$(wait_drained stream "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
    drain_s=$(elapsed_s "$t0")
  else
    drained="restart-failed"; drain_s="-"; drain="?"
  fi

  rows_after=$(mysql_count "$MEETING_ID")
  dups_after=$(metric_counter chat_archive_stream_duplicates_total)
  : "${dups_after:=0}"
  dups_delta=$(python3 -c "print($dups_after-$local_dups_before)")
  saved=$((rows_after - local_rows_before))
  recovered=$((rows_after - rows_at_kill))
  total_accepted=$((total_accepted + accepted))
  total_saved=$((total_saved + saved))

  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$i" "$kill_at" "$accepted" "$((rows_at_kill - local_rows_before))" "$recovered" \
    "$rows_after" "$saved" "$dups_delta" "$drain_s" >> "$CSV"
  log "   수락 $accepted · 킬 직후 저장 $((rows_at_kill - local_rows_before)) · 회수 $recovered · 배출 $drained(${drain_s}s)"

  app_stop TERM
done

# ── 중복 배달을 실제로 만든다 ──────────────────────────────────────
banner "중복 배달 (같은 uid 로 두 번 XADD)"
app_start "$OUT_DIR/dup-app.log" "${APP_EXTRA[@]}" "${MODE_ARGS[@]}"
app_wait_ready || { log "기동 실패"; exit 1; }

DUP_UID=$(python3 -c 'import uuid;print(uuid.uuid4())')
NOW_MS=$(python3 -c 'import time;print(int(time.time()*1000))')
rows_before_dup=$(mysql_count "$MEETING_ID")
dups_before=$(metric_counter chat_archive_stream_duplicates_total)
: "${dups_before:=0}"

for n in 1 2; do
  redis_cli XADD "$STREAM_KEY" '*' \
    uid "$DUP_UID" meetingId "$MEETING_ID" sender "dup@test" \
    content "dup-probe-$n" offsetMillis 1000 sentAt "$NOW_MS" >/dev/null
  log "   XADD #$n (uid=$DUP_UID)"
done

if drain=$(wait_drained stream "$DRAIN_CAP_S"); then drained="yes"; else drained="cap"; fi
rows_after_dup=$(mysql_count "$MEETING_ID")
dups_after=$(metric_counter chat_archive_stream_duplicates_total)
: "${dups_after:=0}"

dup_rows=$((rows_after_dup - rows_before_dup))
log "   두 번 넣었는데 저장된 행: $dup_rows (1 이어야 한다)"
log "   chat.archive.stream.duplicates: $dups_before → $dups_after"
log "   배출: $drained ($drain)"

uid_dups=$(mysql_query "SELECT COUNT(*)-COUNT(DISTINCT message_uid) FROM $DB_NAME.chat_message WHERE meeting_id=$MEETING_ID" | tr -d '[:space:]')
uid_nulls=$(mysql_query "SELECT COUNT(*) FROM $DB_NAME.chat_message WHERE meeting_id=$MEETING_ID AND message_uid IS NULL" | tr -d '[:space:]')
total_saved=$((total_saved + dup_rows))

cat > "$OUT_DIR/summary.txt" <<EOF
kill -9 반복        $ITERS 회 (kill 시점 ${KILL_MIN_S}~${KILL_MAX_S}초 난수, SEED=$SEED)
수락 합계           $total_accepted
저장 합계           $total_saved  (kill 회차 저장 + 중복 시험 저장 $dup_rows)
UID 중복 행 수      $uid_dups   (0 이어야 한다)
UID NULL 행 수      $uid_nulls
중복 배달 저장 행    $dup_rows (같은 uid 두 번 XADD → 1행)
EOF

banner "결과"
cat "$CSV"
cat "$OUT_DIR/summary.txt"
app_stop TERM
