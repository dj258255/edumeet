#!/usr/bin/env bash
#
# #200 게이트 — 400명 채팅 fan-out 부하에서 조각 업로드가 실제로 밀리는가
#
#   BASE_URL=http://localhost:8081 TOKEN=... MEETING_ID=2 ./scripts/run-chunk-under-fanout.sh
#
# ★ 이 묶음의 완료 조건은 **판정 한 줄**이다. 안 밀리면 #200 은 기각으로 끝난다.
#
# ★ "안 밀린다" 를 내려면 **측정이 성립했다는 증거가 다 있어야 한다** (#200 검토).
#   없으면 판정하지 않고 "조건 불성립"(exit 1)으로 끝낸다. 실패한 측정이 기각 근거가 되면
#   그게 제일 나쁘다 - 아무 일도 안 하고 "괜찮다" 고 말하는 것과 같다.
#
#   성립 조건
#     1. k6 가 A·B 둘 다 종료 코드 0 이고 요약 파일이 있다
#     2. 방송 산출물(broadcast.json)이 있다 - 원격이면 회차가 끝난 뒤 가져온다
#     3. 로컬 방송이 창 중에 죽지 않았다
#     4. 조각 표본이 k6 부하 창 안에서 50건 이상이고, 기대 조각 수의 90% 이상이다
#     5. 프로브 요약이 있다
#
#   조각 집계는 **k6 부하 창과 같은 구간만** 본다. 방송은 창보다 오래 살아 있으므로
#   (창 + 60초) 자르지 않으면 부하 밖 조각이 p99 에 섞인다.
#
# 배경: #163 에서 400명 방송형 채팅이 붕괴점이었다(400명 e2e p95 4.2초).
#   #151·#157 로 자막 역압을 톰캣 요청 스레드에서 떼어 낸 뒤로는 조각 업로드도 안 밀릴 수 있다 -
#   그걸 확인하는 것이 이 게이트다. 합성 방송은 원래 조각별 업로드 지연을 안 남겼다(#200).
#
# 측정 앱은 이 스크립트가 띄우지 않는다. /actuator/health 로 확인만 한다.
#   띄우는 법: docker-compose.perf.yml · docs/performance/09
#
# 워밍업 회차를 먼저 돌리고 버린다 (#163 규칙 - JIT 가 덥혀지면 회차마다 빨라진다).
#
# 인자 목록은 하나다 (#233 · #199 교훈). k6·방송 인자는 scripts/lib/*-args.sh 가 조립하고
# 로컬·원격 두 경로가 같은 목록을 쓴다. scripts/selftest-*-args.sh 가 그걸 지킨다.
#
# 환경변수
#   BASE_URL http://localhost:8081 · TOKEN(필수) · MEETING_ID(필수)
#   WS_URL (기본: BASE_URL 을 ws 로) · WINDOW_S 120 · WARMUP_ROUNDS 1
#   SUBSCRIBERS 400 · PUBLISHERS 4 · RATE 20 (#163 과 같은 4 x 20 = 80 msg/s) · PROBE_RATE 10
#   CHUNK_MS 2000 (조각 간격 = "밀렸다" 의 기준) · MIN_SAMPLES 50 · MIN_COVERAGE 90(%)
#   K6_HOST / BROADCAST_HOST (기본 로컬) · K6_BIN / K6_REMOTE_DIR / BROADCAST_IMAGE
#   OUT perf/browser/out · DRY_RUN=1 (실행할 명령만 찍는다)
#
# 종료 코드: 0 = 안 밀린다(#200 기각 근거) · 2 = 밀린다 · 1 = 조건 불성립
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

# shellcheck source=scripts/lib/broadcast-args.sh
. scripts/lib/broadcast-args.sh
# shellcheck source=scripts/lib/k6-args.sh
. scripts/lib/k6-args.sh

: "${TOKEN:?TOKEN 이 필요하다}"
: "${MEETING_ID:?MEETING_ID 가 필요하다}"
BASE_URL="${BASE_URL:-http://localhost:8081}"
WS_URL="${WS_URL:-$(printf '%s' "$BASE_URL" | sed 's|^http|ws|')}"
WINDOW_S="${WINDOW_S:-120}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-1}"
SUBSCRIBERS="${SUBSCRIBERS:-400}"
PUBLISHERS="${PUBLISHERS:-4}"
RATE="${RATE:-20}"
PROBE_RATE="${PROBE_RATE:-10}"
CHUNK_MS="${CHUNK_MS:-2000}"
MIN_SAMPLES="${MIN_SAMPLES:-50}"
MIN_COVERAGE="${MIN_COVERAGE:-90}"
K6_HOST="${K6_HOST:-}"
BROADCAST_HOST="${BROADCAST_HOST:-}"
DRY_RUN="${DRY_RUN:-0}"
BASE_OUT="${OUT:-perf/browser/out}"
BROWSER_DIR="perf/browser"
BROADCAST_IMAGE="${BROADCAST_IMAGE:-edumeet-perf-bcast}"
SEGMENT_TYPE="${SEGMENT_TYPE:-fmp4}"
HLS_TIME="${HLS_TIME:-2}"
BITRATE_K="${BITRATE_K:-}"
BROADCAST_DURATION_S=$((WINDOW_S + 60))
RUN_PREFIX="chunk-under-fanout-$(date +%Y%m%d-%H%M%S)"
TABLE="$BASE_OUT/$RUN_PREFIX.md"
EXPECTED_CHUNKS=$(( WINDOW_S * 1000 / CHUNK_MS ))

log() { printf '%s\n' "$*"; }
remote() { [ -n "$1" ]; }
now_iso() { date -u +%Y-%m-%dT%H:%M:%S.000Z; }

# 조건 불성립 사유를 모은다. 비어 있지 않으면 판정하지 않는다.
declare -a PROBLEMS=()
problem() { PROBLEMS+=("$1"); log "   ✗ $1"; }

health_check() {
  if [ "$DRY_RUN" = 1 ]; then
    log "   (dry-run) curl -fsS $BASE_URL/actuator/health"
    return 0
  fi
  if ! curl -fsS --max-time 5 "$BASE_URL/actuator/health" >/dev/null; then
    log "측정 앱이 $BASE_URL 에 없다. 이 스크립트는 띄우지 않는다 - 먼저 띄워라."
    log "  (docker-compose.perf.yml · docs/performance/09)"
    exit 1
  fi
}

# ── k6 실행 ─────────────────────────────────────────────────────────────
# run_k6 <회차 폴더> <태그> <스크립트> <초> <요약 경로> [추가 -e ...]
# 종료 코드를 그대로 돌려준다 - 부르는 쪽이 본다.
run_k6() {
  local dir="$1" tag="$2" script="$3" seconds="$4" summary="$5"; shift 5
  K6_SCRIPT="$script"
  # 두 스크립트가 함께 쓰는 것만 기본으로 넣고, 나머지는 부르는 쪽이 붙인다.
  K6_ENVS=("BASE_URL=$WS_URL" "HTTP_BASE=$BASE_URL" "TOKEN=$TOKEN" "MEETING_ID=$MEETING_ID"
           "DURATION=${seconds}s" "SUMMARY_PATH=$summary")
  K6_ENVS+=("$@")
  build_k6_args
  if [ "$DRY_RUN" = 1 ]; then
    log "   (dry-run) k6 @ ${K6_HOST:-로컬} :: $(k6_local_command)"
    return 0
  fi
  if remote "$K6_HOST"; then
    # shellcheck disable=SC2029 # 이 문자열은 부하 호스트에서 실행돼야 한다.
    ssh "$K6_HOST" "$(k6_remote_command)" >>"$dir/$tag.k6.log" 2>&1
  else
    # 원격과 **같은 배열**을 쓴다 - 손으로 다시 나열하지 않는다.
    "$K6_BIN" "${K6_ARGS[@]}" >>"$dir/$tag.k6.log" 2>&1
  fi
}

# ── 합성 방송 ───────────────────────────────────────────────────────────
start_broadcast() {  # start_broadcast <회차 폴더> <태그>
  local dir="$1" tag="$2"
  RUN="$RUN_PREFIX-$tag"          # 방송 산출물(outDir)이 회차 폴더와 같아진다
  build_broadcast_args
  BROADCAST_PID=""
  if [ "$DRY_RUN" = 1 ]; then
    log "   (dry-run) 방송 @ ${BROADCAST_HOST:-로컬} :: $(broadcast_local_command)"
    return 0
  fi
  if remote "$BROADCAST_HOST"; then
    # shellcheck disable=SC2029 # 이 문자열은 방송 호스트에서 실행돼야 한다.
    ssh "$BROADCAST_HOST" "$(broadcast_remote_command)"
  else
    node "$BROWSER_DIR/broadcast-synthetic.mjs" "${BROADCAST_ARGS[@]}" \
      >>"$dir/broadcast.log" 2>&1 &
    BROADCAST_PID=$!
  fi
}

stop_broadcast() {
  if [ "$DRY_RUN" = 1 ]; then
    return 0
  fi
  if remote "$BROADCAST_HOST"; then
    # shellcheck disable=SC2029 # 원격에서 실행돼야 한다.
    ssh "$BROADCAST_HOST" "cd \$HOME/edumeet-perf && node broadcast-synthetic.mjs --stop-only" >/dev/null 2>&1 || true
  else
    node "$BROWSER_DIR/broadcast-synthetic.mjs" --stop-only >/dev/null 2>&1 || true
    if [ -n "${BROADCAST_PID:-}" ] && kill -0 "$BROADCAST_PID" 2>/dev/null; then
      kill "$BROADCAST_PID" 2>/dev/null || true
    fi
  fi
}
trap 'stop_broadcast' EXIT

# 방송 프로세스가 창 중에 죽었는지 본다 - 죽었으면 산출물이 불완전하다 (#200 검토 3).
check_broadcast_alive() {
  [ "$DRY_RUN" = 1 ] && return 0
  remote "$BROADCAST_HOST" && return 0     # 원격은 산출물로만 본다
  if [ -z "${BROADCAST_PID:-}" ]; then
    problem "방송 프로세스 PID 가 없다 - 시작이 실패했다"
    return 1
  fi
  if ! kill -0 "$BROADCAST_PID" 2>/dev/null; then
    local code=0
    wait "$BROADCAST_PID" 2>/dev/null || code=$?
    problem "방송이 창 중에 죽었다 (종료 코드 ${code}) - 산출물이 불완전하다"
    return 1
  fi
  return 0
}

# ── 한 회차 ─────────────────────────────────────────────────────────────
# run_round <태그> <채팅부하 on|off>
run_round() {
  local tag="$1" chat="$2"
  local dir="$BASE_OUT/$RUN_PREFIX-$tag"
  mkdir -p "$dir"
  log "== 조건 $tag — 채팅 부하 ${chat} · 창 ${WINDOW_S}초 · 산출물 $dir =="
  start_broadcast "$dir" "$tag"

  # ★ 방송이 **조각을 받아들이기 시작할 때까지** 기다린다 (#200e 에서 확인).
  #   시작 직후 몇 초는 앱의 HLS 파이프라인이 아직 안 서서 429 가 난다 - 그 실패가 부하 창
  #   안에 들어오면 게이트가 "밀린다" 로 잘못 판정한다. 5초 고정 대기로는 부족했다.
  if [ "$DRY_RUN" != 1 ]; then
    for _ in $(seq 1 30); do
      sent=$(jget "$dir/broadcast.json" "d.get('chunksSent',0) or 0")
      [ -n "$sent" ] && [ "$sent" -gt 0 ] 2>/dev/null && break
      sleep 1
    done
  fi

  # ★ 조각 집계를 자를 구간. k6 가 실제로 도는 창과 같게 잡는다 (#200 검토 4).
  WINDOW_FROM="$(now_iso)"
  local -a pids=() names=()
  if [ "$chat" = on ]; then
    run_k6 "$dir" "$tag-chat" k6/chat-fanout.js "$WINDOW_S" "$dir/$tag-chat-summary.json" \
      "SUBSCRIBERS=$SUBSCRIBERS" "PUBLISHERS=$PUBLISHERS" "RATE=$RATE" &
    pids+=("$!"); names+=("$tag-chat")
  fi
  run_k6 "$dir" "$tag-probe" k6/rest-probe.js "$WINDOW_S" "$dir/$tag-probe-summary.json" \
    "PROBE_RATE=$PROBE_RATE" &
  pids+=("$!"); names+=("$tag-probe")

  # ★ 대상 PID 만 기다린다 (#200 검토 4). 인자 없는 wait 는 방송 백그라운드까지 기다려
  #   부하 밖 60초가 회차에 섞인다.
  local i summary code
  for i in "${!pids[@]}"; do
    code=0
    wait "${pids[$i]}" || code=$?
    case "${names[$i]}" in
      *-chat) summary="$dir/$tag-chat-summary.json" ;;
      *) summary="$dir/$tag-probe-summary.json" ;;
    esac
    if [ "$code" -ne 0 ]; then
      problem "${names[$i]} k6 가 실패했다 (종료 코드 ${code}) - $(grep -a -m1 -i 'error\|panic' "$dir/${names[$i]}.k6.log" 2>/dev/null | head -c 120)"
    fi
    if [ ! -s "$summary" ]; then
      problem "${names[$i]} 요약 파일이 없다 ($summary) - 그 조건은 측정되지 않았다"
    fi
  done
  # ★ 창의 끝은 **k6 가 끝난 지금**이다. 처음엔 WINDOW_FROM + WINDOW_S 로 계산했는데,
  #   그 시점의 now 가 이미 창이 끝난 뒤라 창이 두 배가 됐고, 종료 DELETE 뒤에 도착한 조각의
  #   409 까지 창 안에 들어왔다(#200e 에서 환경을 띄우고 스모크를 돌려 보고 찾았다).
  WINDOW_TO="$(now_iso)"
  printf '%s %s\n' "$WINDOW_FROM" "$WINDOW_TO" > "$dir/window.txt"

  check_broadcast_alive
  stop_broadcast

  # ★ 방송 세션이 **실제로 사라진 것**을 보고 다음 회차로 간다 (#200e 에서 확인).
  #   종료 DELETE 가 늦게 도착하면 다음 회차 세션을 지워 그 조각이 409 로 실패한다 -
  #   그 실패가 부하 창에 들어오면 게이트가 "밀린다" 로 잘못 판정한다.
  if [ "$DRY_RUN" != 1 ]; then
    for _ in $(seq 1 20); do
      live=$(curl -fsS --max-time 3 -H "Authorization: Bearer $TOKEN" \
        "$BASE_URL/api/v1/meeting/$MEETING_ID" 2>/dev/null \
        | python3 -c "import json,sys; print(json.load(sys.stdin).get('broadcasting'))" 2>/dev/null)
      [ "$live" != "True" ] && break
      sleep 1
    done
    sleep 3     # 마지막 조각 응답이 파일에 반영될 시간
  fi

  if remote "$BROADCAST_HOST"; then
    # ★ 회차가 끝난 **뒤에** 가져온다 (#200 검토 3). 시작 직후에 가져오면 아직 없다.
    if ! scp -q "$BROADCAST_HOST:edumeet-perf-bcast/out/$RUN/broadcast.json" "$dir/broadcast.json" 2>/dev/null; then
      problem "원격 방송 산출물을 가져오지 못했다 ($RUN)"
    fi
  fi
  log "   끝: $(for f in "$dir"/*; do [ -f "$f" ] && basename "$f"; done | tr '\n' ' ')"
}

# ── 산출물 읽기 ─────────────────────────────────────────────────────────
jget() {  # jget <파일> <파이썬 식>
  python3 -c "import json,sys
try:
    d=json.load(open(sys.argv[1]))
    print($2)
except Exception:
    print('')" "$1" 2>/dev/null
}

fmt() { if [ -n "${1:-}" ]; then printf '%.0f' "$1"; else printf '-'; fi; }

# 부하 창 안의 조각만 집계한다 (#200 검토 4). CSV 를 자르므로 실패도 같은 구간에서 센다.
# windowed <회차 폴더> <태그> → "count p50 p95 p99 max over failed" 한 줄
windowed() {
  python3 - "$1/$2.broadcast.json" "$1/chunks.csv" "$1/window.txt" <<'PY'
import csv, json, math, sys
bc, csv_path, win = sys.argv[1:4]
def q(v, p):
    v = sorted(v)
    if not v:
        return None
    k = (len(v) - 1) * p
    f, c = math.floor(k), math.ceil(k)
    return v[f] if f == c else v[f] * (c - k) + v[c] * (k - f)
try:
    frm, to = open(win).read().split()
except Exception:
    frm, to = '0', 'z'
rows = []
try:
    for r in csv.DictReader(open(csv_path)):
        if frm <= r['startedAt'] <= to:
            rows.append(r)
except Exception:
    pass
ms = [int(r['ms']) for r in rows if r['ms']]
interval = 2000
try:
    interval = json.load(open(bc))['chunkLatency']['intervalMs'] or 2000
except Exception:
    pass
over = sum(1 for v in ms if v > interval)
# 2xx 가 아니면 실패다 - 409(배포 복구 신호)·429(큐 참)·오류(null) 포함 (#200 검토 1·6)
failed = sum(1 for r in rows if not r['status'].startswith('2'))
print(len(ms), q(ms, .5), q(ms, .95), q(ms, .99), (max(ms) if ms else None), over, failed)
PY
}

# 표 한 줄 + 판정용 값. 성립 검사도 여기서 한다.
LAST_P99=""; LAST_FAILED=0
row() {  # row <태그> <채팅부하>
  local tag="$1" chat="$2"
  local dir="$BASE_OUT/$RUN_PREFIX-$tag"
  local bc="$dir/broadcast.json" pc="$dir/$tag-probe-summary.json" cc="$dir/$tag-chat-summary.json"
  local count="" p50="" p95="" p99="" max="" over="" failed=""
  local probe_p99 chat_p95
  if [ ! -s "$bc" ]; then
    problem "조건 $tag: 방송 산출물이 없다 ($bc)"
  else
    read -r count p50 p95 p99 max over failed <<<"$(windowed "$dir" "$tag")"
  fi
  probe_p99=$(jget "$pc" "d.get('probe',{}).get('p99') or ''")
  chat_p95=$(jget "$cc" "d['metrics']['chat_e2e_latency_ms']['values'].get('p(95)') if d.get('metrics',{}).get('chat_e2e_latency_ms') else ''")

  if [ -n "${count:-}" ]; then
    if [ "${count}" -lt "$MIN_SAMPLES" ]; then
      problem "조건 $tag: 부하 창 안 조각 표본이 ${count}건뿐이다 (최소 ${MIN_SAMPLES})"
    fi
    if [ "${count}" -lt $(( EXPECTED_CHUNKS * MIN_COVERAGE / 100 )) ]; then
      problem "조건 $tag: 표본 ${count}건이 기대 ${EXPECTED_CHUNKS}건의 ${MIN_COVERAGE}% 에 못 미친다"
    fi
  fi

  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$tag" "$chat" "${count:--}" "$(fmt "$p50")" "$(fmt "$p95")" "$(fmt "$p99")" "$(fmt "$max")" \
    "${over:-0}" "${failed:-0}" "$(fmt "$probe_p99")" "$(fmt "$chat_p95")"
  LAST_P99="${p99:-}"
  LAST_FAILED="${failed:-0}"
}

# ── 흐름 ────────────────────────────────────────────────────────────────
mkdir -p "$BASE_OUT"
log "#200 게이트 — 조각 업로드가 400명 fan-out 에서 밀리는가"
log "   BASE_URL=$BASE_URL · WS_URL=$WS_URL · 창 ${WINDOW_S}초 · 조각 ${CHUNK_MS}ms 기준"
log "   부하: 구독자 $SUBSCRIBERS · 발행 $PUBLISHERS x $RATE/s (#163 과 같게) · 프로브 $PROBE_RATE/s"
log "   기대 조각 ${EXPECTED_CHUNKS}건 · 최소 표본 ${MIN_SAMPLES}건 · 최소 채움 ${MIN_COVERAGE}%"
health_check

if [ "$WARMUP_ROUNDS" -gt 0 ]; then
  log
  log "== 워밍업 ${WARMUP_ROUNDS}회 — 이 결과는 버린다 (#163) =="
  for i in $(seq 1 "$WARMUP_ROUNDS"); do
    run_round "warmup$i" on
  done
  # 워밍업에서 나온 문제는 버린다 - 그 회차는 측정이 아니다.
  if [ "${#PROBLEMS[@]}" -gt 0 ]; then
    log "   (워밍업에서 나온 문제 ${#PROBLEMS[@]}건은 버린다)"
    PROBLEMS=()
  fi
else
  log "== ★ 워밍업을 껐다 (WARMUP_ROUNDS=0). 이 회차의 절대값은 못 믿는다 =="
fi

log
run_round A off
run_round B on

if [ "$DRY_RUN" = 1 ]; then
  log
  log "== dry-run 끝 - 위 명령 문자열이 실제로 실행할 것이다 =="
  exit 0
fi

log
log "== 결과 (k6 부하 창 안의 조각만) =="
# ★ 파이프로 묶지 않는다 - 파이프는 서브셸이라 row 가 남긴 판정값이 사라진다.
{
  printf '| 조건 | 채팅 부하 | 표본 | 조각 p50 | p95 | p99 | 최대 | %sms 초과 | 실패 | REST p99 | 채팅 전달 p95 |\n' "$CHUNK_MS"
  printf '|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n'
} > "$TABLE"
row A off >> "$TABLE"; A_P99="$LAST_P99"; A_FAILED="$LAST_FAILED"
row B on  >> "$TABLE"; B_P99="$LAST_P99"; B_FAILED="$LAST_FAILED"
cat "$TABLE"

log
log "== 판정 =="
log "   조건 A: 조각 p99 ${A_P99:-?}ms · 실패 ${A_FAILED}건 (채팅 부하 없음)"
log "   조건 B: 조각 p99 ${B_P99:-?}ms · 실패 ${B_FAILED}건 (구독자 $SUBSCRIBERS)"
log "   산출물: $TABLE"

if [ "${#PROBLEMS[@]}" -gt 0 ]; then
  log "   ★ 조건 불성립 - 아래 때문에 판정하지 않는다:"
  for p in "${PROBLEMS[@]}"; do log "     - $p"; done
  exit 1
fi
if [ -z "${B_P99:-}" ]; then
  log "   ★ 조건 불성립 - 조건 B 의 조각 표본이 없다."
  exit 1
fi
if awk -v p99="$B_P99" -v limit="$CHUNK_MS" 'BEGIN { exit !(p99 < limit) }' && [ "$B_FAILED" -eq 0 ]; then
  log "   ★ 400명에서 조각 업로드가 밀리지 않는다 → #200 기각 근거"
  exit 0
fi
log "   ★ 밀린다 → #200 은 살아 있다. 조건 A 대비로 얼마나 밀렸는지 본다."
exit 2
