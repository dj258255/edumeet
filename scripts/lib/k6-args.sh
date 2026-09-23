#!/usr/bin/env bash
# k6 실행 인자와 명령 조립. (#200)
#
# ★ 왜 파일로 뺐나. 실행 두 곳(로컬·원격)이 각자 인자를 나열하면 새 옵션이 한쪽에서 조용히 빠진다 -
#   #233 에서 `--catchup-rate` 가 원격에서 빠진 채 11회차가 돌았고, #199 에서 `--bitrate-k` 가
#   양쪽 모두에서 빠졌다. 여기서도 k6 의 `-e` 를 두 번 적으면 같은 일이 난다.
#
# 실행 두 곳이 이 파일의 함수만 쓴다.
#   build_k6_args     전역 K6_ARGS 를 채운다 (run -q + -e 들 + 스크립트)
#   k6_local_command  로컬 실행 명령 문자열
#   k6_remote_command 원격(부하 호스트)에서 실행할 문자열
# 검증은 이 파일을 source 해 명령을 찍어 본다 - 실행 경로와 같은 코드다.
# (scripts/selftest-k6-args.sh)
#
# 필요한 전역: K6_SCRIPT(저장소 기준 경로) · K6_ENVS(배열, "KEY=VALUE") · K6_BIN(기본 k6) ·
#              K6_HOST(기본 로컬) · K6_REMOTE_DIR(원격에서 저장소가 있는 곳)
# shell_quote 는 broadcast-args.sh 에 있다 - 함께 source 한다.

K6_BIN="${K6_BIN:-k6}"
# 원격 부하 호스트에서 저장소가 놓이는 자리. run-qoe-crosscheck.sh 가 쓰는 마운트와 같다.
# shellcheck disable=SC2016 # 원격에서 확장돼야 한다 - 여기서 확장하면 로컬 경로가 박힌다.
K6_REMOTE_DIR="${K6_REMOTE_DIR:-\$HOME/edumeet-perf}"

# k6 인자 목록. **여기가 유일한 목록이다.**
build_k6_args() {
  K6_ARGS=(run -q)
  local kv
  for kv in "${K6_ENVS[@]}"; do
    K6_ARGS+=(-e "$kv")
  done
  K6_ARGS+=("$K6_SCRIPT")
}

_quoted_k6_args() {
  local out="" arg
  for arg in "${K6_ARGS[@]}"; do
    out+=" $(shell_quote "$arg")"
  done
  printf '%s' "$out"
}

# 로컬에서 그대로 실행할 명령(사람이 읽는 용도 - 실행은 배열로 한다).
k6_local_command() {
  printf '%s%s' "$K6_BIN" "$(_quoted_k6_args)"
}

# 원격 부하 호스트에서 실행할 문자열.
#
# ★ 저장소 디렉터리로 먼저 들어간다 - k6 스크립트 경로가 저장소 기준 상대경로이기 때문이다.
#   원격에 k6 가 없으면 K6_BIN 을 감싼 문자열을 주면 된다(예: "docker run --rm -i grafana/k6").
k6_remote_command() {
  printf 'cd %s && %s%s' "$K6_REMOTE_DIR" "$K6_BIN" "$(_quoted_k6_args)"
}
