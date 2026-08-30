#!/usr/bin/env bash
# 배포가 켜지 않는 서비스가 배포 전체를 막지 않는지 확인한다. (#187)
#
# ★ 왜 필요한가
#
#   Compose 는 프로필이 꺼진 서비스라도 **변수 보간을 먼저 한다.**
#   프로필 필터링은 그 다음 단계다. 그래서 안 켜는 서비스 안에서 ${X:?} 를 쓰면,
#   그 서비스와 아무 상관 없는 평범한 배포까지
#   "required variable is missing" 으로 죽는다.
#
#   이 저장소는 같은 함정을 두 번 만났다 -
#     #110  ai 서비스의 AI_IMAGE        거기만 고쳤다
#     #187  livekit 의 LIVEKIT_API_KEY   값이 비는 순간 배포 전체가 멈췄다
#
#   한 번 만난 함정은 같은 모양을 전부 찾아야 한다.
#
# ★ 그런데 "프로필이 있으면 무조건 안 된다" 는 아니다
#
#   배포가 실제로 켜는 프로필(observability, blue/green)의 서비스는
#   ${X:?} 가 오히려 맞다 - 값이 없으면 안 뜨는 게 낫다(#83).
#   grafana 의 비밀번호가 그렇다. 자리표시자로 뜨면 알려진 비밀번호로
#   관측 화면이 열린다 - 안 뜨는 것보다 나쁘다.
#
#   그래서 기준은 "프로필이 있는가" 가 아니라 **"배포가 켜는가"** 다.
set -euo pipefail
cd "$(dirname "$0")/.."

FILE=${1:-docker-compose.prod.yml}

# 배포가 실제로 켜는 프로필.
#   observability  .github/workflows/deploy.yml 의 COMPOSE 정의
#   blue / green   scripts/deploy-app.sh 가 슬롯마다 켠다
ENABLED=${COMPOSE_ENABLED_PROFILES:-"observability blue green"}

python3 - "$FILE" "$ENABLED" <<'PY'
import re, sys, yaml

path, enabled = sys.argv[1], set(sys.argv[2].split())
doc = yaml.safe_load(open(path))
raw = open(path).read().splitlines()

bad, checked = [], 0
for name, svc in (doc.get("services") or {}).items():
    profiles = set(svc.get("profiles") or [])
    if not profiles or (profiles & enabled):
        continue                      # 기본으로 뜨거나, 배포가 켜는 서비스
    checked += 1
    start = next((i for i, l in enumerate(raw)
                  if re.match(rf"^  {re.escape(name)}:\s*$", l)), None)
    if start is None:
        continue
    end = next((i for i in range(start + 1, len(raw))
                if re.match(r"^  \S", raw[i])), len(raw))
    for i in range(start, end):
        for var in re.findall(r"\$\{([A-Z_][A-Z0-9_]*):\?", raw[i]):
            bad.append((name, i + 1, var))

if bad:
    print("배포가 켜지 않는 서비스가 변수를 필수로 요구한다:")
    for name, line, var in bad:
        print(f"  {path}:{line}  서비스 {name}  ->  ${{{var}:?...}}")
    print()
    print("Compose 는 프로필이 꺼진 서비스도 변수 보간을 먼저 한다.")
    print("그 서비스를 안 켜는 배포까지 같이 죽는다. :- 로 자리표시자를 준다.")
    sys.exit(1)

print(f"배포가 안 켜는 서비스 {checked}개가 배포를 막지 않는다 "
      f"(켜는 프로필: {', '.join(sorted(enabled))})")
PY
