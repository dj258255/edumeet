"""공유 계약을 읽어 URL 을 만든다. (#133)

`contracts/internal-api.json` 은 이미 Java 테스트와 파이썬 테스트가 함께 읽는다.
MCP 서버가 **세 번째 소비자**다.

왜 경로를 여기 하드코딩하지 않는가 -
  #27 에서 내부 API 헤더 이름을 바꾸고 파이썬을 안 고쳐서 오래 403 이었다.
  그때 얻은 규칙이 "경계는 문서가 아니라 기계가 읽는 파일" 이다.
  손으로 베끼면 한쪽만 고쳐도 시험이 통과해 버린다.
"""
from __future__ import annotations

import json
import os
from functools import lru_cache

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CONTRACT_PATH = os.path.normpath(
    os.path.join(HERE, "..", "contracts", "internal-api.json")
)


@lru_cache(maxsize=4)
def load_contract(path: str | None = None) -> dict:
    """계약 파일을 읽는다. 없으면 바로 죽는다 - 조용히 기본값으로 돌지 않는다."""
    target = path or os.environ.get("EDUMEET_CONTRACT_PATH") or DEFAULT_CONTRACT_PATH
    if not os.path.isfile(target):
        raise FileNotFoundError(
            f"공유 계약 파일이 없다: {target}\n"
            "MCP 서버는 저장소 체크아웃에서 돈다. 저장소 밖에서 실행했다면 "
            "EDUMEET_CONTRACT_PATH 로 경로를 준다."
        )
    with open(target, encoding="utf-8") as f:
        return json.load(f)


def auth_header_name(contract: dict | None = None) -> str:
    return (contract or load_contract())["authHeader"]


def endpoint(name: str, contract: dict | None = None) -> dict:
    endpoints = (contract or load_contract())["endpoints"]
    if name not in endpoints:
        raise KeyError(
            f"계약에 없는 엔드포인트: {name}. 있는 것: {sorted(endpoints)}"
        )
    return endpoints[name]


def build_url(base_url: str, name: str, contract: dict | None = None, **path_vars) -> str:
    """계약의 path 에 경로 변수를 채워 절대 URL 을 만든다.

    치환되지 않은 `{...}` 가 남으면 여기서 죽인다. 그대로 보내면 Java 가 400 을
    내는데, 그 400 은 "잘못된 회의" 처럼 보여서 원인을 엉뚱한 데서 찾게 된다.
    실제로 #113 에서 `{meetingId}` 를 문자 그대로 보낸 적이 있다.
    """
    spec = endpoint(name, contract)
    path = spec["path"]
    for key, value in path_vars.items():
        path = path.replace("{" + key + "}", str(value))
    if "{" in path:
        missing = [v for v in spec.get("pathVariables", []) if "{" + v + "}" in path]
        raise ValueError(f"경로 변수가 안 채워졌다: {missing} (path={path})")
    return base_url.rstrip("/") + path
