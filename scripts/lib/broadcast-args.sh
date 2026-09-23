#!/usr/bin/env bash
# 합성 방송 실행 인자와 명령 조립. (#199)
#
# ★ 왜 파일로 뺐나.
#   로컬·원격 실행이 각자 인자를 나열하면 **새 옵션이 한쪽에서 조용히 빠진다.**
#   실제로 두 번 났다 - #233 에서 시청자 쪽 `--catchup-rate` 가 원격에서 빠졌고,
#   #199 에서 방송 쪽 `--bitrate-k` 가 양쪽 모두에서 빠졌다(배열만 만들고 실행은 손으로 나열했다).
#
#   그래서 **실행 두 곳이 이 파일의 함수만** 쓴다.
#     build_broadcast_args   전역 BROADCAST_ARGS 를 채운다
#     broadcast_local_command   로컬 실행 명령 문자열
#     broadcast_remote_command  원격(방송 호스트)에서 실행할 문자열
#   검증은 이 파일을 source 해 명령을 찍어 본다 - 실행 경로와 같은 코드다.
#   (셸을 실제로 돌리지 않고 조립만 하려고 함수로 뺐다)
#
# 필요한 전역: RUN · BROADCAST_DURATION_S · SEGMENT_TYPE · HLS_TIME · CHUNK_MS · BITRATE_K ·
#              BROADCAST_IMAGE · BROWSER_DIR

shell_quote() {
  local value=${1//\'/\'\\\'\'}
  printf "'%s'" "$value"
}

# 방송 인자 목록. **여기가 유일한 목록이다** - 실행 두 곳이 이 배열만 쓴다.
# BITRATE_K 가 비면 `--bitrate-k` 를 아예 넣지 않는다(합성 방송 기본 2500 을 쓴다).
build_broadcast_args() {
  BROADCAST_ARGS=(--run "$RUN" --duration-s "$BROADCAST_DURATION_S" \
    --segment-type "$SEGMENT_TYPE" --hls-time "$HLS_TIME" --chunk-ms "$CHUNK_MS")
  if [ -n "${BITRATE_K:-}" ]; then
    BROADCAST_ARGS+=(--bitrate-k "$BITRATE_K")
  fi
}

# BROADCAST_ARGS 를 원격 명령에 넣을 수 있게 인용해 잇는다.
_quoted_broadcast_args() {
  local out="" arg
  for arg in "${BROADCAST_ARGS[@]}"; do
    out+=" $(shell_quote "$arg")"
  done
  printf '%s' "$out"
}

# 로컬에서 그대로 실행할 명령(사람이 읽는 용도 - 실행은 배열로 한다).
broadcast_local_command() {
  printf 'node %s%s' "$BROWSER_DIR/broadcast-synthetic.mjs" "$(_quoted_broadcast_args)"
}

# 원격 방송 호스트에서 실행할 문자열.
#
# ★ `$HOME` 은 **원격에서** 확장돼야 한다 - 여기서 확장하면 로컬 경로가 원격 명령에 박힌다.
#   그래서 리터럴로 두고 그대로 넘긴다.
broadcast_remote_command() {
  # shellcheck disable=SC2016 # 원격에서 확장돼야 한다 - 여기서 확장하면 로컬 경로가 박힌다.
  local home='$HOME'
  local dir="${home}/edumeet-perf-bcast"
  printf 'mkdir -p "%s/out/%s"; docker rm -f %s >/dev/null 2>&1 || true; docker run -d --name %s --ipc=host -v "%s:/work" -w /work -e HOME=/tmp/h -e EDUMEET_PERF_ENV=/work/.perf.env %s node broadcast-synthetic.mjs%s' \
    "$dir" "$RUN" "$BROADCAST_IMAGE" "$BROADCAST_IMAGE" "$dir" "$BROADCAST_IMAGE" "$(_quoted_broadcast_args)"
}
