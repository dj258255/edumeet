#!/usr/bin/env bash
# 문서가 "재현" 으로 가리키는 파일이 실제로 저장소에 있는지 본다. (#162)
#
# 왜 필요한가
#   두 번 빠졌다.
#     #156 squash 에서 k6/caption-backpressure.js 가 통째로 빠졌다
#     .gitignore 의 *.sh 가 scripts/ 의 재현 스크립트 6개를 먹고 있었다
#   둘 다 문서는 그대로 그 파일을 가리키고 있었고, 로컬에는 파일이 있어서
#   아무도 눈치채지 못했다. 재현 경로가 저장소에 없으면 그 측정은 다시 못 돌린다.
#
# 왜 재현 경로만 보나
#   문서에는 "이 선택지는 기각했다" 로 적힌 없는 경로도 있고(contracts/caption-glossary.json),
#   k6/websockets 처럼 저장소 경로가 아닌 모듈 이름도 있다.
#   넓게 잡아 오탐이 나면 이 검사부터 무시당한다. 그러면 없느니만 못하다.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0
while IFS=: read -r doc line ref; do
  [ -z "${ref:-}" ] && continue
  if ! git ls-files --error-unmatch "$ref" >/dev/null 2>&1; then
    echo "없음  $ref   ($doc:$line)"; FAIL=1
  fi
done < <(grep -rnoE '`(scripts/[A-Za-z0-9._-]+\.(sh|py)|k6/[A-Za-z0-9._-]+\.js)`' \
           docs/ CLAUDE.md 2>/dev/null | sed 's/`//g')
if [ "$FAIL" = 0 ]; then
  echo "문서가 가리키는 재현 파일이 전부 저장소에 있다"
fi
exit "$FAIL"
