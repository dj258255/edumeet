#!/usr/bin/env bash
# 부하 창 동안 운영 앱이 2코어를 얼마나 가져가는지 잰다.
# 운영 앱을 못 내리므로, 대신 그 몫을 숫자로 만들어 한계를 좁힌다.
set -uo pipefail
OUT=$HOME/perf160/out; mkdir -p "$OUT"
CSV="$OUT/prodshare.csv"; : > "$CSV"

( while true; do
    T=$(date +%s)
    for C in edumeet-app perf-app; do
      L=$(docker stats --no-stream --format '{{.CPUPerc}}' "$C" 2>/dev/null) || continue
      [ -n "$L" ] && echo "$T,$C,$L" >> "$CSV"
    done
  done ) &
SAMPLER=$!

TOXIC=1 "$HOME/perf160/run.sh" after caption-backpressure.js prodshare \
  -e SUBSCRIBERS=150 -e CAPTION_RATE=45 -e DURATION=90s

kill "$SAMPLER" 2>/dev/null
echo "SAMPLES $(wc -l < "$CSV")"
