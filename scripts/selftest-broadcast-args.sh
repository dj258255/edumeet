#!/usr/bin/env bash
# 합성 방송 인자가 **로컬·원격 실행 두 경로 모두**에 들어가는지 확인한다. (#199)
#
#   ./scripts/selftest-broadcast-args.sh
#
# ★ 왜 필요한가. 실행 경로가 인자를 손으로 나열하면 새 옵션이 한쪽에서 조용히 빠진다.
#   실제로 두 번 났다 - #233 에서 시청자 쪽 `--catchup-rate` 가, #199 에서 방송 쪽
#   `--bitrate-k` 가 빠졌다(배열을 만들어 놓고 실행은 손으로 나열했다).
#
# ★ 실행하지 않고 조립만 본다. `run-qoe-crosscheck.sh` 가 실제로 쓰는 그 함수를 source 해서
#   두 명령을 찍는다 - 검증하는 코드 경로와 실행하는 코드 경로가 같다.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=scripts/lib/broadcast-args.sh
. scripts/lib/broadcast-args.sh

RUN=selftest
BROADCAST_DURATION_S=1800
SEGMENT_TYPE=fmp4
HLS_TIME=2
CHUNK_MS=2000
BROADCAST_IMAGE=edumeet-perf-bcast
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

printf '\n[BITRATE_K=1200]\n'
BITRATE_K=1200
build_broadcast_args
LOCAL=$(broadcast_local_command)
REMOTE=$(broadcast_remote_command)
printf '  로컬 실행: %s\n' "$LOCAL"
printf '  원격 실행: %s\n' "$REMOTE"

# 인용된 쌍 그대로를 본다 - 플래그와 값이 둘 다 있어야 한다.
expect_contains '로컬 명령에 --bitrate-k 1200 이 있다' "$LOCAL" "--bitrate-k' '1200"
expect_contains '원격 명령에 --bitrate-k 1200 이 있다' "$REMOTE" "--bitrate-k' '1200"
expect_contains '로컬 명령의 나머지 인자도 그대로다(--chunk-ms)' "$LOCAL" "--chunk-ms' '2000"
expect_contains '원격 명령의 나머지 인자도 그대로다(--chunk-ms)' "$REMOTE" "--chunk-ms' '2000"
# shellcheck disable=SC2016 # 원격에서 확장돼야 하는 리터럴이다 - 확장되면 안 된다.
expect_contains '원격 명령의 $HOME 은 확장되지 않고 남는다' "$REMOTE" '$HOME/edumeet-perf-bcast'

printf '\n[BITRATE_K 비움 - 기본 2500 을 쓰게 인자를 안 붙인다]\n'
BITRATE_K=""
build_broadcast_args
LOCAL=$(broadcast_local_command)
REMOTE=$(broadcast_remote_command)
expect_not_contains '로컬 명령에 --bitrate-k 가 없다' "$LOCAL" "--bitrate-k"
expect_not_contains '원격 명령에 --bitrate-k 가 없다' "$REMOTE" "--bitrate-k"

printf '\n'
if [ "$failures" -gt 0 ]; then
  printf '실패 %d건\n' "$failures"
  exit 1
fi
printf '모두 통과\n'
