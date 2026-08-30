#!/usr/bin/env bash
# .env 템플릿이 한 줄에 설정 하나씩 내는지 확인한다. (#186)
#
# ★ 왜 필요한가
#
#   Ansible 은 Jinja 를 trim_blocks=True 로 돌린다. 블록 태그 **바로 뒤의
#   줄바꿈을 지우는** 옵션이라, 조건문을 한 줄로 쓰면 인접한 설정이 붙는다.
#
#       {% if a %}A={{ a }}{% endif %}
#       {% if b %}B={{ b }}{% endif %}      ->  A=...B=...
#
#   앞의 변수는 값이 오염되고 뒤의 변수는 아예 존재하지 않게 된다.
#
#   운영에서 FRONT_URL 이 FRONT_URL2 를 삼켰고, 그 결과 브라우저가
#   WebSocket 도 API 도 못 붙었다. 부하 도구는 Origin 을 안 보내서 다 통과했다.
#
# ★ 왜 값이 비면 안 터지나
#
#   비어 있는 묶음은 한 줄만 렌더링돼 붙을 상대가 없다. 그래서 이 함정은
#   **인접한 선택 설정이 둘 다 채워질 때만** 나타난다 -
#   AWS 키를 넣는 순간 그쪽도 같은 모양이 된다. 잠복해 있다가 나중에 터진다.
#
# 사용법:  scripts/verify-env-template.sh
set -euo pipefail
cd "$(dirname "$0")/.."

TPL=ansible/templates/env.j2
[ -f "$TPL" ] || { echo "템플릿이 없다: $TPL"; exit 2; }

command -v ansible >/dev/null || { echo "ansible 이 없어 건너뛴다"; exit 0; }

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

# ★ 실제 값이 아니라 더미로 채운다. 모든 조건을 참으로 만들어야
#   "둘 다 채워졌을 때" 의 모양을 본다 - 비어 있으면 이 함정이 안 보인다.
python3 - "$TPL" "$out/vars.json" <<'PY'
import json, re, sys
t = open(sys.argv[1]).read()
names = set(re.findall(r'\{\{\s*([a-z_][a-z0-9_]*)\s*\}\}', t))
names |= set(re.findall(r'\{%\s*if\s+([a-z_][a-z0-9_]*)\s*%\}', t))
json.dump({n: "dummy-" + n for n in sorted(names)}, open(sys.argv[2], "w"))
PY

ansible localhost -m template \
    -a "src=$PWD/$TPL dest=$out/rendered.env" -e "@$out/vars.json" >/dev/null 2>&1 \
    || { echo "템플릿 렌더링 실패"; exit 1; }

# 대문자 키만 본다. DB_URL 의 serverTimezone= 같은 것은 소문자라 안 걸린다.
merged=$(grep -nE '^[A-Z][A-Z0-9_]*=.*[A-Z][A-Z0-9_]{2,}=' "$out/rendered.env" \
         | sed -E 's/=[^=]*([A-Z][A-Z0-9_]{2,}=)/=(값)\1/g; s/=[^=]*$/=(값)/' || true)

if [ -n "$merged" ]; then
    echo "한 줄에 설정이 둘 이상 들어간다:"
    echo "$merged" | sed 's/^/  /'
    echo
    echo "조건문의 태그를 각자 줄에 둬야 한다:"
    echo "  {% if x %}"
    echo "  KEY={{ x }}"
    echo "  {% endif %}"
    exit 1
fi

n=$(grep -cE '^[A-Z][A-Z0-9_]*=' "$out/rendered.env")
echo "설정 ${n}개가 전부 한 줄에 하나씩이다"
