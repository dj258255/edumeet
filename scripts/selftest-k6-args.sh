#!/usr/bin/env bash
# k6 인자가 **로컬·원격 실행 두 경로 모두**에 들어가는지 확인한다. (#200)
#
#   ./scripts/selftest-k6-args.sh
#
# ★ 왜 필요한가. 실행 경로가 인자를 손으로 나열하면 새 옵션이 한쪽에서 조용히 빠진다 -
#   실제로 두 번 났다(#233 `--catchup-rate`, #199 `--bitrate-k`). 실행하지 않고 조립만 본다.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/broadcast-args.sh
. scripts/lib/broadcast-args.sh
# shellcheck source=scripts/lib/k6-args.sh
. scripts/lib/k6-args.sh

K6_BIN=k6
K6_SCRIPT=k6/chat-fanout.js
K6_ENVS=(
  "BASE_URL=ws://localhost:8081"
  "TOKEN=secret-token"
  "MEETING_ID=2"
  "SUBSCRIBERS=400"
  "PUBLISHERS=4"
  "RATE=20"
  "DURATION=120s"
  "SUMMARY_PATH=/tmp/chat-summary.json"
)

failures=0
expect_contains() {  # expect_contains <설명> <본문> <있어야 하는 조각>
  case "$2" in
    *"$3"*) printf '  ✓ %s\n' "$1" ;;
    *) printf '  ✗ %s\n' "$1"; failures=$((failures + 1)) ;;
  esac
}

expect_not_contains() {  # expect_not_contains <설명> <본문> <없어야 하는 조각>
  case "$2" in
    *"$3"*) printf '  ✗ %s\n' "$1"; failures=$((failures + 1)) ;;
    *) printf '  ✓ %s\n' "$1" ;;
  esac
}

printf '\n[채팅 부하 - #163 과 같은 발행률]\n'
build_k6_args
LOCAL=$(k6_local_command)
REMOTE=$(k6_remote_command)
printf '  로컬 실행: %s\n' "$LOCAL"
printf '  원격 실행: %s\n' "$REMOTE"

expect_contains '로컬 명령에 스크립트가 있다' "$LOCAL" "k6/chat-fanout.js"
expect_contains '원격 명령에 스크립트가 있다' "$REMOTE" "k6/chat-fanout.js"
expect_contains '로컬 명령에 SUBSCRIBERS=400 이 있다' "$LOCAL" "-e' 'SUBSCRIBERS=400"
expect_contains '원격 명령에 SUBSCRIBERS=400 이 있다' "$REMOTE" "-e' 'SUBSCRIBERS=400"
expect_contains '로컬 명령에 RATE=20 이 있다' "$LOCAL" "-e' 'RATE=20"
expect_contains '원격 명령에 RATE=20 이 있다' "$REMOTE" "-e' 'RATE=20"
expect_contains '원격 명령에 DURATION=120s 가 있다' "$REMOTE" "-e' 'DURATION=120s"
expect_contains '원격 명령이 저장소 디렉터리로 먼저 들어간다' "$REMOTE" "cd "
# shellcheck disable=SC2016 # 원격에서 확장돼야 하는 리터럴이다.
expect_contains '원격 명령의 $HOME 은 확장되지 않고 남는다' "$REMOTE" '$HOME/edumeet-perf'

printf '\n[프로브 - 같은 목록을 쓴다]\n'
K6_SCRIPT=k6/rest-probe.js
K6_ENVS=("HTTP_BASE=http://localhost:8081" "PROBE_RATE=10" "DURATION=120s" "SUMMARY_PATH=/tmp/rest-probe-summary.json")
build_k6_args
LOCAL=$(k6_local_command)
REMOTE=$(k6_remote_command)
expect_contains '로컬 명령에 PROBE_RATE=10 이 있다' "$LOCAL" "-e' 'PROBE_RATE=10"
expect_contains '원격 명령에 PROBE_RATE=10 이 있다' "$REMOTE" "-e' 'PROBE_RATE=10"
expect_not_contains '프로브 명령에 채팅 인자가 남아 있지 않다' "$LOCAL" "SUBSCRIBERS"

printf '\n'
if [ "$failures" -gt 0 ]; then
  printf '실패 %d건\n' "$failures"
  exit 1
fi
printf '모두 통과\n'
