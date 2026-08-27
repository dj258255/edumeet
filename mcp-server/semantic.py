"""선택적 의미 검색. (#140)

★ 이 파일은 **없어도 서버가 돈다.**

  30개 질의로 재 본 결과가 이 설계를 정했다.

      방식                  전체 recall@5   질문형   질의당
      A 문자열 전체(전)            50%        0%     0.03ms
      B' 문자 2-gram             77%       54%     0.06ms   <- 기본
      C 임베딩                    80%       60%    15.4ms
      D 하이브리드                 85%       70%    16.9ms

  **가장 큰 개선은 모델이 아니었다.** 0% -> 54% 는 문자열 전체 매칭을
  2-gram 으로 바꾼 것뿐이고, 값이 공짜다.

  임베딩은 거기서 +6%p 를 얻는데 질의 지연이 250배가 되고
  venv 가 857MB 로 늘어난다(torch). 그 대가를 모든 사용자에게 강제할 이유가 없다.

  그래서 **선택적 의존성**으로 둔다.

      pip install -r requirements.txt             렉시컬만. 이것으로 충분한 경우가 많다
      pip install -r requirements-semantic.txt    + torch. 질문형이 중요할 때

  모델이 없으면 이 모듈의 함수는 빈 결과를 돌려주고, 서버는 렉시컬로 계속 돈다.
  **없다고 죽지 않고, 있다고 자동으로 켜지지도 않는다** - 환경변수로 명시해야 켜진다.
"""
from __future__ import annotations

import os

#: 명시적으로 켜야 한다. torch 가 설치돼 있다는 이유만으로 15ms 를 물리지 않는다.
ENABLED = os.environ.get("EDUMEET_SEMANTIC_SEARCH", "").lower() in ("1", "true", "on")
MODEL_NAME = os.environ.get("EDUMEET_EMBED_MODEL", "intfloat/multilingual-e5-small")

_model = None
_cache: dict[int, tuple[list[str], object]] = {}


def available() -> tuple[bool, str]:
    """쓸 수 있는가, 아니면 왜 못 쓰는가.

    이유를 문자열로 돌려주는 이유 - 도구 응답에 적어야 한다.
    "의미 검색이 꺼져 있다" 를 안 알려 주면 사용자는 그냥 못 찾았다고 읽는다.
    """
    if not ENABLED:
        return False, "EDUMEET_SEMANTIC_SEARCH 가 꺼져 있다"
    try:
        import sentence_transformers  # noqa: F401
    except ImportError:
        return False, "sentence-transformers 가 없다 (requirements-semantic.txt)"
    return True, MODEL_NAME


def _load():
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer
        _model = SentenceTransformer(MODEL_NAME, device="cpu")
    return _model


def _prefix(texts: list[str], kind: str) -> list[str]:
    """e5 계열은 접두어를 요구한다. 안 붙이면 품질이 눈에 띄게 떨어진다."""
    return [f"{kind}: {t}" for t in texts] if "e5" in MODEL_NAME else texts


def semantic_hits(segments: list[str], query: str, limit: int) -> list[tuple[int, str]]:
    """렉시컬이 전부 놓쳤을 때만 부른다.

    색인을 조각 목록 기준으로 캐시한다. MCP 서버는 오래 살아 있고
    같은 회의를 연달아 묻는 일이 많다 - 매번 60개를 다시 인코딩할 이유가 없다.
    """
    ok, _ = available()
    if not ok or not segments:
        return []

    import numpy as np

    key = hash(tuple(segments))
    if key not in _cache:
        matrix = _load().encode(_prefix(segments, "passage"),
                                normalize_embeddings=True, show_progress_bar=False)
        _cache.clear()          # 회의 하나치만 들고 있는다. 메모리를 예측 가능하게 둔다.
        _cache[key] = (segments, matrix)
    _, matrix = _cache[key]

    vector = _load().encode(_prefix([query], "query"),
                            normalize_embeddings=True, show_progress_bar=False)[0]
    scores = matrix @ vector
    order = np.argsort(-scores)[:limit]
    # 임계값 아래는 버린다. 코사인은 아무것도 안 맞아도 0.7 쯤을 준다 -
    # 그대로 내보내면 "관련 없는 조각 5개" 가 모델 컨텍스트로 들어간다.
    return [(int(i), f"(의미) {query}") for i in order if float(scores[i]) >= 0.80]
