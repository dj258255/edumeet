#!/usr/bin/env bash
#
# 시청 품질 대조 하네스 진입점. (#197)
#
#   준비물 확인 → 원격 준비 → 운영 서버 CPU 기록 → 합성 방송 → 첫 세그먼트 대기
#   → 시청자 N대 → drain → 방송 종료 → 서버 수집 → 대조
#
# ★ 방송은 BROADCAST_DURATION_S 안전 상한으로 시작하고, 시청자 종료·drain 뒤 셸이 내린다.
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
#
# ★ VIEWER_HOST 를 주면 시청자 브라우저만 원격 Playwright Docker에서 돌린다.
#   방송·서버 수집은 이 노트북에서 그대로 하고, 토큰 파일은 stdin 으로만 보낸다.
set -euo pipefail
cd "$(dirname "$0")/.."

RUN="${RUN:-$(date +%Y%m%d-%H%M%S)}"
VIEWERS="${VIEWERS:-5}"
# 방송은 시청자 일정에 맞춰 외부에서 내린다. 이 값은 안전 상한일 뿐 측정 종료 시각이 아니다.
BROADCAST_DURATION_S="${BROADCAST_DURATION_S:-86400}"
SEGMENT_TYPE="${SEGMENT_TYPE:-mpegts}"
HLS_TIME="${HLS_TIME:-2}"
CHUNK_MS="${CHUNK_MS:-2000}"
LIVE_SYNC="${LIVE_SYNC:-}"
VIEWER_HOST="${VIEWER_HOST:-}"
BROWSER_DIR="perf/browser"
OUT="$BROWSER_DIR/out/$RUN"
PERF_ENV_FILE="${EDUMEET_PERF_ENV:-$HOME/.edumeet-perf.env}"
REMOTE_VIEWERS=0
REMOTE_CPU_AFTER=0
[ -n "$VIEWER_HOST" ] && REMOTE_VIEWERS=1

for tool in node ffmpeg ssh curl; do
  command -v "$tool" >/dev/null || { echo "없다: $tool"; exit 1; }
done
if [ "$REMOTE_VIEWERS" -eq 1 ]; then
  for tool in rsync scp; do
    command -v "$tool" >/dev/null || { echo "없다: $tool"; exit 1; }
  done
else
  [ -d "$BROWSER_DIR/node_modules" ] || {
    echo "먼저 설치: cd $BROWSER_DIR && npm install && npx playwright install chromium"
    exit 1
  }
fi
[ -f "$PERF_ENV_FILE" ] || {
  echo "없다: $PERF_ENV_FILE  ($BROWSER_DIR/README.md 참고)"; exit 1; }

# 원격 명령 한 덩어리에 값을 넣을 때도 셸 재해석이 일어나지 않게 한다.
# 토큰은 이 함수를 거치지 않는다 - 아래 env 파일 stdin 전달만 쓴다.
shell_quote() {
  local value=${1//\'/\'\\\'\'}
  printf "'%s'" "$value"
}

# 원격 Playwright 이미지의 버전은 package.json 의 playwright와 반드시 맞춘다.
PLAYWRIGHT_VERSION=$(node --input-type=module -e '
  import { readFileSync } from "node:fs";
  const p = JSON.parse(readFileSync("./perf/browser/package.json", "utf8"));
  const version = p.dependencies?.playwright?.match(/[0-9]+\.[0-9]+\.[0-9]+/)?.[0];
  if (!version) throw new Error("package.json에서 playwright 버전을 찾지 못했다");
  process.stdout.write(version);
')
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-noble"

# 설정은 env.mjs 한 곳에서 읽는다. 토큰은 여기로 나오지 않는다.
CONF=$(node --input-type=module -e '
  import { loadEnv } from "./perf/browser/lib/env.mjs";
  const e = loadEnv();
  process.stdout.write([e.SITE, e.SSH_HOST, e.DOCKER_NET].join("\t"));
')
IFS=$'\t' read -r SITE SSH_HOST DOCKER_NET <<< "$CONF"

ssh -o BatchMode=yes -o ConnectTimeout=10 "$SSH_HOST" true \
  || { echo "ssh 로 $SSH_HOST 에 닿지 못한다"; exit 1; }
if [ "$REMOTE_VIEWERS" -eq 1 ]; then
  ssh -o BatchMode=yes -o ConnectTimeout=10 "$VIEWER_HOST" true \
    || { echo "시청자 호스트 ssh 로 $VIEWER_HOST 에 닿지 못한다"; exit 1; }
fi

echo "== 준비 완료 =="
echo "   RUN=$RUN  VIEWERS=$VIEWERS  방송 안전 상한=${BROADCAST_DURATION_S}s"
echo "   SEGMENT_TYPE=$SEGMENT_TYPE  HLS_TIME=$HLS_TIME  CHUNK_MS=$CHUNK_MS  LIVE_SYNC=${LIVE_SYNC:-기본}"
echo "   사이트=$SITE  서버=$SSH_HOST  네트워크=$DOCKER_NET"

mkdir -p "$OUT"

record_remote_cpu() {
  local label="$1"
  {
    echo "[$label] $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    ssh "$VIEWER_HOST" docker stats --no-stream
  } >> "$OUT/viewer-host-cpu.txt" || {
    echo "[$label] docker stats 실패" >> "$OUT/viewer-host-cpu.txt"
  }
}

BROADCAST_PID=""
VIEWER_PID=""
CLEANED=""

stop_broadcast() {
  if [ -z "$BROADCAST_PID" ]; then
    return 0
  fi
  if kill -0 "$BROADCAST_PID" 2>/dev/null; then
    # 살아 있으면 SIGINT - 그 스크립트가 DELETE 를 부르고 끝난다.
    kill -INT "$BROADCAST_PID" 2>/dev/null || true
    wait "$BROADCAST_PID" 2>/dev/null || true
  else
    # 이미 죽었다(ffmpeg 사망·크래시). DELETE 를 셸 차원에서 보장한다.
    echo "   방송 자식이 이미 죽었다. --stop-only 로 DELETE 를 보장한다"
    node "$BROWSER_DIR/broadcast-synthetic.mjs" --stop-only >/dev/null 2>&1 || true
  fi
  BROADCAST_PID=""
}

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
    stop_broadcast
  fi

  if [ "$REMOTE_VIEWERS" -eq 1 ]; then
    if [ "$REMOTE_CPU_AFTER" -eq 0 ] && [ -d "$OUT" ]; then
      record_remote_cpu after-viewers
      REMOTE_CPU_AFTER=1
    fi
    # 이 두 대상만 정리한다. ~/edumeet-perf 디렉터리와 byeolchi-* 서비스는 남긴다.
    ssh "$VIEWER_HOST" \
      'rm -f ~/edumeet-perf/.perf.env; docker rm -f edumeet-perf-viewers >/dev/null 2>&1 || true' \
      >/dev/null 2>&1 || true
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

if [ "$REMOTE_VIEWERS" -eq 1 ]; then
  # 남의 서비스(byeolchi-*)에 준 영향을 측정 직전·직후에 남긴다.
  : > "$OUT/viewer-host-cpu.txt"
  record_remote_cpu before-viewers
  rsync -a --delete --exclude node_modules --exclude out perf/browser/ \
    "$VIEWER_HOST:edumeet-perf/"
  # 토큰은 프로세스 목록에 나오지 않게 stdin 으로만 보낸다.
  ssh "$VIEWER_HOST" 'umask 077; cat > ~/edumeet-perf/.perf.env' < "$PERF_ENV_FILE"
  # 방송보다 늦게 시청자가 붙지 않도록 이미지 pull·의존성 설치를 미리 끝낸다.
  # shellcheck disable=SC2029 # pull은 viewer 호스트에서 실행돼야 한다.
  ssh "$VIEWER_HOST" "docker pull $PLAYWRIGHT_IMAGE"
  # shellcheck disable=SC2016 # 변수는 준비 컨테이너 안에서 확장돼야 한다.
  REMOTE_PREP_SCRIPT='mkdir -p "$HOME"; npm install --no-audit --no-fund >/dev/null'
  REMOTE_PREP_CMD="docker run --rm --ipc=host -v \"\$HOME/edumeet-perf:/work\" -w /work"
  REMOTE_PREP_CMD+=" -e HOME=/tmp/h -e EDUMEET_PERF_ENV=/work/.perf.env"
  REMOTE_PREP_CMD+=" $PLAYWRIGHT_IMAGE sh -c $(shell_quote "$REMOTE_PREP_SCRIPT")"
  # shellcheck disable=SC2029 # 준비 컨테이너는 viewer 호스트에서 실행돼야 한다.
  ssh "$VIEWER_HOST" "$REMOTE_PREP_CMD"
else
  # 측정 직전 다른 컨테이너의 CPU. 측정 중 잡음이 있었는지 나중에 볼 근거다. (a02-* 포함)
  ssh "$SSH_HOST" "docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}'" > "$OUT/prod-cpu.txt"
fi

echo "== 합성 방송 시작 =="
node "$BROWSER_DIR/broadcast-synthetic.mjs" --run "$RUN" --duration-s "$BROADCAST_DURATION_S" \
  --segment-type "$SEGMENT_TYPE" --hls-time "$HLS_TIME" --chunk-ms "$CHUNK_MS" \
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
if [ -n "$LIVE_SYNC" ]; then
  VIEWER_ARGS+=(--live-sync "$LIVE_SYNC")
fi
if [ -n "${SCHEDULE:-}" ]; then
  VIEWER_ARGS+=(--schedule "$SCHEDULE")
  echo "   SCHEDULE 을 넘긴다 (schedule.json 에 기록됨)"
fi
if [ -n "${FORCE_PATH:-}" ]; then
  VIEWER_ARGS+=(--force-path "$FORCE_PATH")
  echo "   FORCE_PATH=$FORCE_PATH 를 넘긴다 (진단용 경로 강제)"
fi
if [ "$REMOTE_VIEWERS" -eq 1 ]; then
  # 배열 값은 환경 변수로 컨테이너에 넘기고, 컨테이너 안에서 배열을 다시 만든다.
  # SCHEDULE JSON도 셸 코드가 아니라 하나의 인자로만 전달된다.
  REMOTE_DOCKER_CMD="docker run --rm --name edumeet-perf-viewers --ipc=host"
  REMOTE_DOCKER_CMD+=" -v \"\$HOME/edumeet-perf:/work\" -w /work"
  REMOTE_DOCKER_CMD+=" -e HOME=/tmp/h -e EDUMEET_PERF_ENV=/work/.perf.env"
  REMOTE_DOCKER_CMD+=" -e RUN=$(shell_quote "$RUN") -e VIEWERS=$(shell_quote "$VIEWERS")"
  if [ -n "${SCHEDULE:-}" ]; then
    REMOTE_DOCKER_CMD+=" -e SCHEDULE=$(shell_quote "$SCHEDULE")"
  fi
  if [ -n "${FORCE_PATH:-}" ]; then
    REMOTE_DOCKER_CMD+=" -e FORCE_PATH=$(shell_quote "$FORCE_PATH")"
  fi
  if [ -n "$LIVE_SYNC" ]; then
    REMOTE_DOCKER_CMD+=" -e LIVE_SYNC=$(shell_quote "$LIVE_SYNC")"
  fi
  # shellcheck disable=SC2016 # 변수는 컨테이너 안에서 확장돼야 한다.
  REMOTE_SCRIPT='set -- --run "$RUN" --viewers "$VIEWERS"; if [ -n "${SCHEDULE:-}" ]; then set -- "$@" --schedule "$SCHEDULE"; fi; if [ -n "${FORCE_PATH:-}" ]; then set -- "$@" --force-path "$FORCE_PATH"; fi; if [ -n "${LIVE_SYNC:-}" ]; then set -- "$@" --live-sync "$LIVE_SYNC"; fi; node qoe-crosscheck.mjs "$@"'
  REMOTE_DOCKER_CMD+=" $PLAYWRIGHT_IMAGE sh -c $(shell_quote "$REMOTE_SCRIPT")"
  # shellcheck disable=SC2029 # 이 문자열은 viewer 호스트에서 실행돼야 한다.
  ssh "$VIEWER_HOST" "$REMOTE_DOCKER_CMD" &
else
  node "$BROWSER_DIR/qoe-crosscheck.mjs" "${VIEWER_ARGS[@]}" &
fi
VIEWER_PID=$!

VIEWER_STATUS=0
wait "$VIEWER_PID" || VIEWER_STATUS=$?
VIEWER_PID=""
if [ "$VIEWER_STATUS" -ne 0 ]; then
  echo "시청자 하네스가 실패했다 (exit $VIEWER_STATUS)"
  exit 1
fi

if [ "$REMOTE_VIEWERS" -eq 1 ]; then
  record_remote_cpu after-viewers
  REMOTE_CPU_AFTER=1
  scp -r "$VIEWER_HOST:edumeet-perf/out/$RUN" "$BROWSER_DIR/out/"
fi

echo "== drain 30초 (마지막 보고·로그 수집 지연) =="
sleep 30

echo "== 합성 방송 종료 =="
stop_broadcast

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
