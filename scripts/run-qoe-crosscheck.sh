#!/usr/bin/env bash
#
# 시청 품질 대조 하네스 진입점. (#197)
#
#   준비물 확인 → 운영 서버 CPU 기록 → 합성 방송 → 첫 세그먼트 대기
#   → 시청자 N대 → 방송 종료 → drain → 서버 수집 → 대조
#
# ★ DURATION_S 는 스로틀 일정 길이보다 길어야 한다.
#   측정 도중 방송이 끝나면 매니페스트가 멈춘 채 벽시계만 흘러 정답이 무한히 커진다.
#   (docs/performance/25 "재다가 만난 것 셋")
#
# ★ 어떤 경로로 끝나도 방송을 내린다.
#   정상 경로는 합성 방송에 SIGINT 를 보내고, 그 스크립트가 DELETE 를 부른다. (#168)
#   자식이 이미 죽어 있으면(--stop-only) 셸이 직접 DELETE 를 보장한다.
#   토큰은 노드가 설정 파일에서 읽는다 - 셸이 curl 로 넘기면 ps 명령줄에 보인다.
#
# ★ 신호를 받으면 정리한 뒤 셸을 끝낸다.
#   INT 는 130, TERM 은 143 으로 끝나 서버 수집·대조 단계로 되돌아가지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

RUN="${RUN:-$(date +%Y%m%d-%H%M%S)}"
VIEWERS="${VIEWERS:-5}"
DURATION_S="${DURATION_S:-330}"
BROWSER_DIR="perf/browser"
OUT="$BROWSER_DIR/out/$RUN"

for tool in node ffmpeg ssh curl; do
  command -v "$tool" >/dev/null || { echo "없다: $tool"; exit 1; }
done
[ -f "$HOME/.edumeet-perf.env" ] || {
  echo "없다: ~/.edumeet-perf.env  ($BROWSER_DIR/README.md 참고)"; exit 1; }
[ -d "$BROWSER_DIR/node_modules" ] || {
  echo "먼저 설치: cd $BROWSER_DIR && npm install && npx playwright install chromium"
  exit 1; }

# 설정은 env.mjs 한 곳에서 읽는다. 토큰은 여기로 나오지 않는다.
CONF=$(node --input-type=module -e '
  import { loadEnv } from "./perf/browser/lib/env.mjs";
  const e = loadEnv();
  process.stdout.write([e.SITE, e.SSH_HOST, e.DOCKER_NET].join("\t"));
')
IFS=$'\t' read -r SITE SSH_HOST DOCKER_NET <<< "$CONF"

ssh -o BatchMode=yes -o ConnectTimeout=10 "$SSH_HOST" true \
  || { echo "ssh 로 $SSH_HOST 에 닿지 못한다"; exit 1; }

echo "== 준비 완료 =="
echo "   RUN=$RUN  VIEWERS=$VIEWERS  DURATION_S=${DURATION_S}s"
echo "   사이트=$SITE  서버=$SSH_HOST  네트워크=$DOCKER_NET"

mkdir -p "$OUT"

# 측정 직전 다른 컨테이너의 CPU. 측정 중 잡음이 있었는지 나중에 볼 근거다. (a02-* 포함)
ssh "$SSH_HOST" "docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}'" > "$OUT/prod-cpu.txt"

BROADCAST_PID=""
VIEWER_PID=""
CLEANED=""

cleanup() {
  if [ -n "$CLEANED" ]; then
    return 0
  fi
  CLEANED=1

  # 시청자 하네스가 남아 있으면 끝낸다. 안 그러면 브라우저·보고가 계속 돈다.
  if [ -n "$VIEWER_PID" ]; then
    kill "$VIEWER_PID" 2>/dev/null || true
    wait "$VIEWER_PID" 2>/dev/null || true
  fi

  if [ -n "$BROADCAST_PID" ]; then
    if kill -0 "$BROADCAST_PID" 2>/dev/null; then
      # 살아 있으면 SIGINT - 그 스크립트가 DELETE 를 부르고 끝난다.
      kill -INT "$BROADCAST_PID" 2>/dev/null || true
      wait "$BROADCAST_PID" 2>/dev/null || true
    else
      # 이미 죽었다(ffmpeg 사망·크래시). DELETE 를 셸 차원에서 보장한다.
      echo "   방송 자식이 이미 죽었다. --stop-only 로 DELETE 를 보장한다"
      node "$BROWSER_DIR/broadcast-synthetic.mjs" --stop-only >/dev/null 2>&1 || true
    fi
  fi
  return 0
}

on_signal() {
  cleanup
  exit "$1"
}

trap cleanup EXIT
trap 'on_signal 130' INT
trap 'on_signal 143' TERM

echo "== 합성 방송 시작 =="
node "$BROWSER_DIR/broadcast-synthetic.mjs" --run "$RUN" --duration-s "$DURATION_S" \
  > "$OUT/broadcast.log" 2>&1 &
BROADCAST_PID=$!

PLAYLIST_URL=""
for _ in $(seq 1 60); do
  if [ -f "$OUT/broadcast.json" ]; then
    PLAYLIST_URL=$(node -e '
      const fs = require("fs");
      const j = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
      process.stdout.write(j.playlistUrl || "");
    ' "$OUT/broadcast.json")
  fi
  [ -n "$PLAYLIST_URL" ] && break
  kill -0 "$BROADCAST_PID" 2>/dev/null || {
    echo "합성 방송이 시작하지 못했다"; cat "$OUT/broadcast.log"; exit 1; }
  sleep 1
done
[ -n "$PLAYLIST_URL" ] || { echo "재생목록 주소를 못 받았다"; exit 1; }

# /hls/ 는 API 호스트가 아니라 사이트 호스트에서 나간다. (docs/performance/25)
echo "== 첫 세그먼트 대기: ${SITE}${PLAYLIST_URL} =="
for _ in $(seq 1 60); do
  if curl -fsS "${SITE}${PLAYLIST_URL}" 2>/dev/null | grep -q '\.ts'; then
    echo "   매니페스트 준비됨"
    break
  fi
  kill -0 "$BROADCAST_PID" 2>/dev/null || {
    echo "합성 방송이 죽었다"; cat "$OUT/broadcast.log"; exit 1; }
  sleep 1
done

echo "== 시청자 $VIEWERS 대 =="
# SCHEDULE 을 주면 그대로 넘긴다. 넘긴 일정은 산출물 폴더의 schedule.json 에 남는다.
VIEWER_ARGS=(--run "$RUN" --viewers "$VIEWERS")
if [ -n "${SCHEDULE:-}" ]; then
  VIEWER_ARGS+=(--schedule "$SCHEDULE")
  echo "   SCHEDULE 을 넘긴다 (schedule.json 에 기록됨)"
fi
if [ -n "${FORCE_PATH:-}" ]; then
  VIEWER_ARGS+=(--force-path "$FORCE_PATH")
  echo "   FORCE_PATH=$FORCE_PATH 를 넘긴다 (진단용 경로 강제)"
fi
node "$BROWSER_DIR/qoe-crosscheck.mjs" "${VIEWER_ARGS[@]}" &
VIEWER_PID=$!

VIEWER_STATUS=0
wait "$VIEWER_PID" || VIEWER_STATUS=$?
VIEWER_PID=""
if [ "$VIEWER_STATUS" -ne 0 ]; then
  echo "시청자 하네스가 실패했다 (exit $VIEWER_STATUS)"
  exit 1
fi

echo "== 합성 방송 종료 =="
kill -INT "$BROADCAST_PID" 2>/dev/null || true
wait "$BROADCAST_PID" 2>/dev/null || true
BROADCAST_PID=""

echo "== drain 30초 (마지막 보고·로그 수집 지연) =="
sleep 30

echo "== 서버 쪽 수집 =="
node "$BROWSER_DIR/server-side.mjs" --run "$RUN"

echo "== 대조 =="
# compare 는 측정 조건이 성립하지 않으면(영상이 한 번도 안 재생됨) 2 로 끝난다.
# 그 코드를 그대로 돌려준다 - 0 을 성공으로 넘기면 다음 단계가 빈 값을 근거로 삼는다.
COMPARE_STATUS=0
node "$BROWSER_DIR/compare.mjs" --run "$RUN" || COMPARE_STATUS=$?
if [ "$COMPARE_STATUS" -ne 0 ]; then
  echo "대조가 실패했다 (exit $COMPARE_STATUS). 산출물: $OUT"
  exit "$COMPARE_STATUS"
fi

echo "== 완료: $OUT =="
