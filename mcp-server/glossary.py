"""저장된 자막과 같은 용어 사전을 질의에도 적용한다. (#133)

★ 왜 이게 필요한가 - **정규화가 저장 쪽에만 걸려 있으면 검색이 조용히 실패한다.**

자막은 hot path 에서 `ai/caption_normalizer.py` 를 지나며 정규화된 뒤 저장된다.

    STT "python 을 배웠습니다"  ->  저장 "파이썬 을 배웠습니다"

그래서 사용자가 `python` 으로 찾으면 **0건**이 나온다. 오류가 아니라 빈 결과라
"그 회의에서 파이썬 얘기를 안 했나 보다" 로 잘못 읽힌다.

★ 왜 사전을 복사하지 않는가

`ai/` 는 컨테이너로 배포되고 빌드 컨텍스트가 `./ai` 다. 저장소 루트를 컨텍스트로
주면 프론트·ai 의 수백 MB 가 도커 데몬으로 간다 - 그래서 이미 거부된 선택이다.
즉 `ai/` 는 `contracts/` 를 런타임에 읽을 수 없다.

**그런데 MCP stdio 서버는 컨테이너가 아니다.** MCP 클라이언트 옆에서, 저장소
체크아웃 위에서 돈다. 그러니 사전을 새로 만들거나 베낄 이유가 없다 -
`ai/caption_normalizer.py` 를 그대로 import 하면 원본이 하나로 유지된다.
"""
from __future__ import annotations

import os
import sys

_AI_DIR = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ai")
)


def _load_normalizer():
    if _AI_DIR not in sys.path:
        sys.path.insert(0, _AI_DIR)
    from caption_normalizer import normalize_caption_text  # noqa: E402
    return normalize_caption_text


try:
    normalize_query = _load_normalizer()
    GLOSSARY_AVAILABLE = True
except ImportError:  # 저장소 밖에서 실행한 경우
    GLOSSARY_AVAILABLE = False

    def normalize_query(text: str) -> str:
        """사전을 못 찾았다. 원문 그대로 찾는다 - 조용히 틀리는 것보다 낫다."""
        return text
