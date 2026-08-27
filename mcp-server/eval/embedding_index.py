"""로컬 임베딩 검색. (#140)

★ 이것을 붙일지 말지는 measure.py 가 정한다.

  이 파일이 있다는 것이 "도입했다" 는 뜻이 아니다. 비교 대상을 만든 것뿐이다.
  렉시컬 베이스라인으로 충분하면 이 경로는 채택하지 않는다.

★ 왜 로컬 모델인가

  유료 키가 필요 없다. 이 저장소가 AI 서비스를 못 띄우는 이유 세 개 중 하나가 사라진다.
  대신 다른 비용이 생긴다 - 모델 다운로드, 메모리, 색인 시간. 그 값을 같이 잰다.
"""
from __future__ import annotations

import os
import time

import numpy as np

from corpus import SEGMENTS
from baselines import rank_char_ngram

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from glossary import normalize_query  # noqa: E402

#: 2 OCPU 에서 돌릴 것이므로 작은 것부터 본다.
MODEL_NAME = os.environ.get("EDUMEET_EMBED_MODEL", "intfloat/multilingual-e5-small")

_model = None
_matrix: np.ndarray | None = None
_index_seconds = 0.0


def _load():
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer
        _model = SentenceTransformer(MODEL_NAME, device="cpu")
    return _model


def _prefix(texts: list[str], kind: str) -> list[str]:
    """e5 계열은 접두어를 요구한다. 안 붙이면 품질이 눈에 띄게 떨어진다."""
    if "e5" in MODEL_NAME:
        return [f"{kind}: {t}" for t in texts]
    return texts


def build_index() -> float:
    """자막 조각을 벡터로 만든다. 걸린 시간(초)을 돌려준다."""
    global _matrix, _index_seconds
    model = _load()
    started = time.perf_counter()
    _matrix = model.encode(_prefix(SEGMENTS, "passage"),
                           normalize_embeddings=True, show_progress_bar=False)
    _index_seconds = time.perf_counter() - started
    return _index_seconds


def _ensure() -> np.ndarray:
    if _matrix is None:
        build_index()
    return _matrix


def rank_embedding(query: str, k: int = 5) -> list[int]:
    matrix = _ensure()
    vec = _load().encode(_prefix([normalize_query(query)], "query"),
                         normalize_embeddings=True, show_progress_bar=False)[0]
    scores = matrix @ vec
    return [int(i) + 1 for i in np.argsort(-scores)[:k]]


def rank_hybrid(query: str, k: int = 5) -> list[int]:
    """렉시컬과 임베딩을 순위로 합친다(RRF).

    점수를 더하지 않는 이유 - 두 점수의 척도가 다르다. 코사인 0.8 과 2-gram 0.3 을
    더하면 스케일이 큰 쪽이 이긴다. 순위만 쓰면 그 문제가 없다.

    렉시컬을 버리지 않는 이유 - 용어형에서 렉시컬이 100% 다.
    임베딩만 쓰면 잘하던 것을 잃는다.
    """
    lex = rank_char_ngram(query, k * 2)
    emb = rank_embedding(query, k * 2)
    scores: dict[int, float] = {}
    for ranking in (lex, emb):
        for pos, seg in enumerate(ranking, 1):
            scores[seg] = scores.get(seg, 0.0) + 1.0 / (60 + pos)
    return [s for s, _ in sorted(scores.items(), key=lambda kv: -kv[1])[:k]]


def model_info() -> str:
    model = _load()
    dim = model.get_sentence_embedding_dimension()
    params = sum(p.numel() for p in model.parameters())
    if _matrix is None:
        build_index()
    mb = _matrix.nbytes / 1024 / 1024
    return (f"{MODEL_NAME} · {dim}차원 · 파라미터 {params/1e6:.0f}M · "
            f"조각 {len(SEGMENTS)}개 색인 {_index_seconds:.2f}초 / {mb:.2f}MB")
