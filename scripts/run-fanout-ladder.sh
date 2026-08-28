#!/usr/bin/env bash
#
# fan-out 사다리를 올려 "몇 명까지 채팅답나" 를 찾는다. (#164)
#
#   BASE_URL=ws://localhost:48081 TOKEN=... MEETING_ID=2 ./scripts/run-fanout-ladder.sh
#   STEPS="200 300 400 500" ./scripts/run-fanout-ladder.sh
#
# ★ 워밍업을 이 스크립트가 강제한다.
#
#   #163 에서 갓 띄운 JVM 부터 사다리를 올려 이렇게 나왔다.
#
#       200명 18,466ms · 300명 12,663ms · 400명 8,186ms
#
#   인원이 늘수록 빨라지는 순서는 나올 수 없다. JIT 가 덥혀지면서 회차마다
#   빨라진 것이었다. 측정 원칙에 "워밍업을 측정과 분리한다" 가 이미 적혀 있었는데
#   사람이 그걸 잊었다. 그래서 사람한테 안 맡긴다.
#
#   버리는 회차를 먼저 돌리고, 그 결과는 집계에 안 넣는다.
#   WARMUP_ROUNDS=0 으로 끌 수 있지만 껐다는 사실이 출력에 남는다.
#
# ★ 단조롭지 않으면 실패로 끝낸다.
#
#   인원이 늘었는데 지연이 줄면 그 회차는 못 믿는다. 워밍업이든 다른 오염이든,
#   원인을 모른 채로 표를 그리면 안 된다. 종료 코드 2 로 끝내고 표를 남긴다.

set -uo pipefail
cd "$(dirname "$0")/.."

STEPS="${STEPS:-200 300 400 500}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-2}"
WARMUP_SUBS="${WARMUP_SUBS:-200}"
: "${TOKEN:?TOKEN 이 필요하다}"
: "${MEETING_ID:?MEETING_ID 가 필요하다}"
BASE_URL="${BASE_URL:-ws://localhost:8081}"

run_one() {  # run_one <구독자수> ; e2e p95 를 stdout 으로
  k6 run -q \
    -e BASE_URL="$BASE_URL" -e TOKEN="$TOKEN" -e MEETING_ID="$MEETING_ID" \
    -e SUBSCRIBERS="$1" -e PUBLISHERS="${PUBLISHERS:-4}" -e RATE="${RATE:-20}" \
    k6/chat-delivery.js 2>&1
}

if [ "$WARMUP_ROUNDS" -gt 0 ]; then
  echo "== 워밍업 ${WARMUP_ROUNDS}회 (구독자 ${WARMUP_SUBS}) — 이 결과는 버린다 =="
  for i in $(seq 1 "$WARMUP_ROUNDS"); do
    p=$(run_one "$WARMUP_SUBS" | grep -a 'e2e p95' | head -1 | tr -dc 0-9)
    echo "   ${i}회차 e2e p95 ${p:-?} ms"
  done
else
  echo "== ★ 워밍업을 껐다 (WARMUP_ROUNDS=0). 이 회차의 절대값은 못 믿는다 =="
fi

echo
echo "== 사다리 =="
PREV_N=0; PREV_P=0; MONO=1
declare -a TABLE
for N in $STEPS; do
  OUT=$(run_one "$N")
  P=$(echo "$OUT"  | grep -a 'e2e p95'   | head -1 | tr -dc 0-9)
  OK=$(echo "$OUT" | grep -a '제때 도착' | head -1 | awk '{print $3}')
  LOST=$(echo "$OUT" | grep -a '유실'    | head -1 | awk '{print $2}')
  printf '   구독자 %4s   제때 %-8s 유실 %-6s e2e p95 %6s ms\n' "$N" "${OK:-?}" "${LOST:-?}" "${P:-?}"
  TABLE+=("$N|${OK:-?}|${LOST:-?}|${P:-?}")
  if [ "$PREV_N" -gt 0 ] && [ -n "$P" ] && [ "$P" -lt "$PREV_P" ]; then
    echo "   ★ 인원이 ${PREV_N} -> ${N} 으로 늘었는데 지연이 ${PREV_P} -> ${P} ms 로 줄었다"
    MONO=0
  fi
  PREV_N=$N; PREV_P=${P:-0}
done

if [ "$MONO" = 0 ]; then
  echo
  echo "실패: 단조롭지 않다. 인원이 늘었는데 지연이 준 구간이 있다."
  echo "      워밍업이 모자랐거나 다른 것이 섞였다. 이 표로 상한을 말하면 안 된다."
  exit 2
fi
echo
echo "단조롭다. 상한은 지연이 무너지는 두 지점 사이다."
