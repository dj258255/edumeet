#!/usr/bin/env bash
# 시청자 실행 인자와 명령 조립. (#233)
#
# ★ 왜 파일로 뺐나. 실행 두 곳(로컬·원격)이 각자 인자를 나열하면 새 옵션이 한쪽에서 조용히 빠진다.
#   실제로 났다 - #233 그리드 11회차가 `--catchup-rate` 가 원격에서 빠진 채 전부 "따라잡기 끔" 으로
#   돌았고, 준비 로그에는 CATCHUP_RATE 가 찍혀 있어 아무도 눈치채지 못했다.
#   (방송 쪽도 같은 이유로 scripts/lib/broadcast-args.sh 로 뺐다 - #199)
#
# 실행 두 곳이 이 파일의 함수만 쓴다.
#   build_viewer_args       전역 VIEWER_ARGS 를 채운다
#   viewer_local_command    로컬 실행 명령 문자열
#   viewer_remote_command   원격(시청자 호스트)에서 실행할 문자열
# 검증은 이 파일을 source 해 명령을 찍어 본다 - 실행 경로와 같은 코드다.
#
# 필요한 전역: RUN · VIEWERS · LIVE_SYNC · CATCHUP_RATE · CATCHUP_MODE · SCHEDULE · FORCE_PATH ·
#              PLAYWRIGHT_IMAGE · BROWSER_DIR
# (shell_quote 는 broadcast-args.sh 에 있다 - 함께 source 한다)

# 시청자 인자 목록. **여기가 유일한 목록이다.**
build_viewer_args() {
  VIEWER_ARGS=(--run "$RUN" --viewers "$VIEWERS")
  if [ -n "${LIVE_SYNC:-}" ]; then
    VIEWER_ARGS+=(--live-sync "$LIVE_SYNC")
  fi
  if [ -n "${CATCHUP_RATE:-}" ]; then
    VIEWER_ARGS+=(--catchup-rate "$CATCHUP_RATE")
  fi
  if [ -n "${CATCHUP_MODE:-}" ]; then
    VIEWER_ARGS+=(--catchup-mode "$CATCHUP_MODE")
  fi
  if [ -n "${SCHEDULE:-}" ]; then
    VIEWER_ARGS+=(--schedule "$SCHEDULE")
  fi
  if [ -n "${FORCE_PATH:-}" ]; then
    VIEWER_ARGS+=(--force-path "$FORCE_PATH")
  fi
}

# VIEWER_ARGS 를 원격 명령에 넣을 수 있게 인용해 잇는다.
_quoted_viewer_args() {
  local out="" arg
  for arg in "${VIEWER_ARGS[@]}"; do
    out+=" $(shell_quote "$arg")"
  done
  printf '%s' "$out"
}

# 로컬에서 그대로 실행할 명령(사람이 읽는 용도 - 실행은 배열로 한다).
viewer_local_command() {
  printf 'node %s%s' "$BROWSER_DIR/qoe-crosscheck.mjs" "$(_quoted_viewer_args)"
}

# 원격 시청자 호스트에서 실행할 문자열.
#
# ★ `$HOME` 은 **원격에서** 확장돼야 한다 - 여기서 확장하면 로컬 경로가 원격 명령에 박힌다.
viewer_remote_command() {
  # shellcheck disable=SC2016 # 원격에서 확장돼야 한다 - 여기서 확장하면 로컬 경로가 박힌다.
  local home='$HOME'
  local dir="${home}/edumeet-perf"
  printf 'docker run --rm --name edumeet-perf-viewers --ipc=host -v "%s:/work" -w /work -e HOME=/tmp/h -e EDUMEET_PERF_ENV=/work/.perf.env %s node qoe-crosscheck.mjs%s' \
    "$dir" "$PLAYWRIGHT_IMAGE" "$(_quoted_viewer_args)"
}
