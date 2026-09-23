#!/usr/bin/env bash
# 시청자 인자가 **로컬·원격 실행 두 경로 모두**에 들어가는지 확인한다. (#233)
#
#   ./scripts/selftest-viewer-args.sh
#
# ★ 왜 필요한가. #233 그리드 11회차가 `--catchup-rate` 가 원격에서 빠진 채 전부 "따라잡기 끔" 으로
#   돌았고, 준비 로그에는 CATCHUP_RATE 가 찍혀 있어 아무도 눈치채지 못했다.
#   실행하지 않고 조립만 본다 - run-qoe-crosscheck.sh 가 실제로 쓰는 그 함수를 source 한다.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/broadcast-args.sh
. scripts/lib/broadcast-args.sh
# shellcheck source=scripts/lib/viewer-args.sh
. scripts/lib/viewer-args.sh

RUN=selftest
VIEWERS=3
LIVE_SYNC=2
CATCHUP_RATE=1.25
CATCHUP_MODE=adaptive
SCHEDULE=""
FORCE_PATH=""
PLAYWRIGHT_IMAGE=edumeet-perf-viewers
BROWSER_DIR=perf/browser

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

printf '\n[CATCHUP_MODE=adaptive · CATCHUP_RATE=1.25]\n'
build_viewer_args
LOCAL=$(viewer_local_command)
REMOTE=$(viewer_remote_command)
printf '  로컬 실행: %s\n' "$LOCAL"
printf '  원격 실행: %s\n' "$REMOTE"

expect_contains '로컬 명령에 --catchup-mode adaptive 가 있다' "$LOCAL" "--catchup-mode' 'adaptive"
expect_contains '원격 명령에 --catchup-mode adaptive 가 있다' "$REMOTE" "--catchup-mode' 'adaptive"
expect_contains '로컬 명령에 --catchup-rate 1.25 도 있다' "$LOCAL" "--catchup-rate' '1.25"
expect_contains '원격 명령에 --catchup-rate 1.25 도 있다' "$REMOTE" "--catchup-rate' '1.25"
expect_contains '원격 명령에 --viewers 3 이 있다' "$REMOTE" "--viewers' '3"
# shellcheck disable=SC2016 # 원격에서 확장돼야 하는 리터럴이다 - 확장되면 안 된다.
expect_contains '원격 명령의 $HOME 은 확장되지 않고 남는다' "$REMOTE" '$HOME/edumeet-perf'

printf '\n[CATCHUP_MODE 비움 - 앱 기본(끔)을 쓴다]\n'
CATCHUP_MODE=""
build_viewer_args
LOCAL=$(viewer_local_command)
REMOTE=$(viewer_remote_command)
expect_not_contains '로컬 명령에 --catchup-mode 가 없다' "$LOCAL" "--catchup-mode"
expect_not_contains '원격 명령에 --catchup-mode 가 없다' "$REMOTE" "--catchup-mode"
expect_contains '그래도 --catchup-rate 는 남는다' "$REMOTE" "--catchup-rate' '1.25"

printf '\n'
if [ "$failures" -gt 0 ]; then
  printf '실패 %d건\n' "$failures"
  exit 1
fi
printf '모두 통과\n'
