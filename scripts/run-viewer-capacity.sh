#!/usr/bin/env bash
# 원격 시청자 한도 찾기 — 하네스 자신이 먼저 무너지는 지점. (#235 · #160)
#
#   ./scripts/run-viewer-capacity.sh                 # 10 · 20 · 30
#   COUNTS="10 20" DURATION_S=90 ./scripts/run-viewer-capacity.sh
#
# ★ 왜 필요한가. #160 에서 "부하 생성기가 병목이면 정답이 틀어진다" 를 확인했다.
#   30명·50명 측정을 믿으려면 **몇 명부터 하네스 자신이 끊기는지**를 먼저 알아야 한다.
#   스로틀을 걸지 않았는데(= 제한 네트워크가 아닌데) 정답 끊김이 나오면
#   그건 네트워크가 아니라 원격 VM(시청자 컨테이너가 도는 2코어)이나 노트북의 한계다 -
#   그 위 수치는 측정 조건이 성립하지 않는다.
#
# ★ 원격 VM 에 들어간다(시청자 컨테이너가 거기서 돈다). 다른 측정이 같은 VM 을 쓰고 있으면
#   돌리지 마라 - 두 부하가 섞이면 어느 쪽 한계인지 알 수 없다.
#   운영 서버에는 붙지 않는다(START_MODE=broadcast-first, 스로틀 없음, 짧게).
#
# ★ 원격 VM CPU 는 run-qoe-crosscheck.sh 가 이미 남긴다(out/<run>/viewer-host-cpu.txt).
#   여기서는 그것과 정답 끊김만 모아 표로 만든다.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

COUNTS="${COUNTS:-10 20 30}"
RUN_PREFIX="${RUN_PREFIX:-capacity}"
DURATION_S="${DURATION_S:-120}"
OUT_DIR="perf/out/viewer-capacity"
TABLE="$OUT_DIR/table.md"
LOG_DIR="$OUT_DIR/logs"

# 스로틀 없음. 워밍업 60초는 대조가 빼므로 그 뒤 DURATION_S 초만 창으로 잡는다.
SCHEDULE="[{\"from\":0,\"to\":$((DURATION_S + 60)),\"limit\":null}]"

log() { printf '%s\n' "$*"; }
command -v node >/dev/null || { log "node 가 없다"; exit 1; }
mkdir -p "$LOG_DIR"

# ★ 부하 **중**의 원격 VM CPU 를 1초 간격으로 표본한다. (#235 · #160)
#   docker stats --no-stream 은 호출마다 1~2초가 걸려 1초 표본이 안 된다 - /proc/stat 을 1초마다 읽는다.
#   표본은 백그라운드로 돌리고, 회차가 끝나면 멈춘다.
start_cpu_sampler() {  # start_cpu_sampler <출력파일>
  ssh "$VIEWER_HOST" \
    'for i in $(seq 1 100000); do printf "%s " "$(date +%s)"; head -1 /proc/stat; sleep 1; done' \
    > "$1" 2>&1 &
  SAMPLER_PID=$!
}

stop_cpu_sampler() {
  [ -n "${SAMPLER_PID:-}" ] || return 0
  kill "$SAMPLER_PID" 2>/dev/null
  wait "$SAMPLER_PID" 2>/dev/null
  SAMPLER_PID=""
}

# ★ 머리 줄도 표에 넣는다. 첫 printf 에 `> "$TABLE"` 이 없어서 머리만 stdout 으로 나가고
#   파일에는 구분선부터 들어갔다. (#199)
{
  printf '| 시청자 | 정답 끊김 합(초) | 정답 끊김 중앙(초) | 끊긴 시청자 | 첫 재생 중앙(ms) | 원격 VM CPU 최대/평균 |\n'
  printf '|---|---:|---:|---:|---:|---:|\n'
} > "$TABLE"

for n in $COUNTS; do
  run="$RUN_PREFIX-n$n"
  log "== 시청자 $n 대 (스로틀 없음 · 창 ${DURATION_S}초) =="

  # ★ 회차마다 새 디렉터리로 시작한다. 지난 회차의 viewer-*.json 이 남아 있으면
  #   시청자 수를 줄여 재실행할 때 표본 수와 끊김 수가 부풀려진다. (#235)
  case "$run" in
    "$RUN_PREFIX"-*) rm -rf "perf/browser/out/$run" ;;
    *) log "이름이 $RUN_PREFIX 로 시작하지 않아 지우지 않는다: $run"; exit 1 ;;
  esac

  cpu_file="$LOG_DIR/$run-cpu.txt"
  start_cpu_sampler "$cpu_file"

  if ! RUN="$run" VIEWERS="$n" SCHEDULE="$SCHEDULE" \
      SEGMENT_TYPE="${SEGMENT_TYPE:-fmp4}" HLS_TIME="${HLS_TIME:-2}" \
      ./scripts/run-qoe-crosscheck.sh > "$LOG_DIR/$run.log" 2>&1; then
    stop_cpu_sampler
    log "   실패 - $LOG_DIR/$run.log 를 본다"
    printf '| %s | - | - | - | - | 실행 실패 |\n' "$n" >> "$TABLE"
    continue
  fi

  stop_cpu_sampler

  node - "$run" "$n" "$TABLE" "$cpu_file" <<'NODE'
const fs = require('fs')
const path = require('path')

const [run, viewers, table, cpuFile] = process.argv.slice(2)
const dir = path.join('perf/browser/out', run)
const files = fs.readdirSync(dir).filter((f) => /^viewer-\d+\.json$/.test(f))
const rows = files.map((f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')))

const stalls = rows.map((r) =>
  (r.truth?.stallsEvent ?? []).reduce((acc, s) => acc + Math.max(0, (s.end - s.start) / 1000), 0))
const total = stalls.reduce((a, b) => a + b, 0)
const median = stalls.length ? [...stalls].sort((a, b) => a - b)[Math.floor(stalls.length / 2)] : 0
const stalled = stalls.filter((s) => s > 0).length
const startups = rows.map((r) => r.truth?.startupMs).filter((v) => Number.isFinite(v))
const startupMedian = startups.length
  ? [...startups].sort((a, b) => a - b)[Math.floor(startups.length / 2)]
  : null

// ★ 부하 중 원격 VM CPU (#235). /proc/stat 의 cpu 줄에서 busy 비율을 이웃 표본끼리 계산한다.
//   cpu 줄은 코어 전체를 합친 값이라 이 비율이 곧 "VM 전체(2코어) 사용률" 이다 - 100% 면 두 코어가 다 찼다.
let cpu = '-'
try {
  const lines = fs.readFileSync(cpuFile, 'utf8').split('\n')
  const samples = []
  for (const line of lines) {
    const m = /^(\d+) cpu\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)/.exec(line.trim())
    if (!m) continue
    const [, at, user, nice, system, idle, iowait, irq, softirq, steal] = m.map(Number)
    const busy = user + nice + system + irq + softirq + steal
    const total = busy + idle + iowait
    samples.push({ at, busy, total })
  }
  const utils = []
  for (let i = 1; i < samples.length; i += 1) {
    const dTotal = samples[i].total - samples[i - 1].total
    const dBusy = samples[i].busy - samples[i - 1].busy
    if (dTotal > 0) utils.push(dBusy / dTotal)
  }
  if (utils.length > 0) {
    const max = Math.max(...utils)
    const avg = utils.reduce((a, b) => a + b, 0) / utils.length
    cpu = `${(max * 100).toFixed(0)}% / ${(avg * 100).toFixed(0)}% (최대/평균, 표본 ${utils.length})`
  }
} catch {
  cpu = '(표본 없음)'
}

fs.appendFileSync(table,
  `| ${viewers} | ${total.toFixed(1)} | ${median.toFixed(1)} | ${stalled}/${rows.length} | ` +
  `${startupMedian ?? '-'} | ${cpu} |\n`)
console.log(`   정답 끊김 합 ${total.toFixed(1)}초 · 중앙 ${median.toFixed(1)}초 · 끊긴 시청자 ${stalled}/${rows.length}`)
NODE
done

log ""
log "표: $TABLE"
log ""
log "읽는 법 - 스로틀이 없는데 정답 끊김이 0 이 아니면 **하네스나 원격 VM 이 한계**다."
log "그 위 시청자 수로 잰 지연·끊김은 조건 불성립이다(#160)."
