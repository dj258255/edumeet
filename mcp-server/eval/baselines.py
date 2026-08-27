"""비교할 검색 방식들. (#140)

★ 왜 (B) 토큰 매칭을 넣는가.

  지금 구현 (A)는 **질의 문자열 전체**를 부분문자열로 찾는다.

      "비관적 잠금"                 -> 1건
      "비관적 잠금 어디서 설명했지"   -> 0건

  질문 형태는 무조건 0건이다. **의미 때문이 아니라 구현 때문이다.**

  여기에 임베딩을 붙여 "0% -> 80%" 라고 적으면 그건 측정이 아니라 허수아비다.
  토큰 단위로만 바꿔도 상당 부분이 해결될 수 있고, 그러면 모델을 넣을 이유가 없다.

  그래서 (B)를 먼저 만든다. **(B)가 충분하면 임베딩은 도입하지 않는다.**
"""
from __future__ import annotations

import math
import re
from collections import Counter

from corpus import SEGMENTS
from queries import tokens

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from glossary import normalize_query          # noqa: E402
from transcript_search import search as current_search   # noqa: E402
from corpus import TRANSCRIPT                 # noqa: E402


def rank_current(query: str, k: int = 5) -> list[int]:
    """(A) 지금 구현 — 질의 전체를 부분문자열로 찾는다."""
    return [m.segment_no for m in current_search(TRANSCRIPT, query, max_results=k)]


def rank_token_or(query: str, k: int = 5) -> list[int]:
    """(B) 토큰 매칭 — 겹치는 토큰 수로 점수를 매긴다. 모델 없음.

    정규화를 질의에 적용하는 것은 (A)와 같다. 그 부분은 비교 대상이 아니다.
    """
    qs = tokens(normalize_query(query))
    if not qs:
        return []
    scored = []
    for i, seg in enumerate(SEGMENTS, 1):
        hits = sum(1 for t in qs if t in seg)
        if hits:
            # 짧은 조각에서 맞은 것이 더 강한 신호다
            scored.append((hits / math.sqrt(len(seg)), i))
    scored.sort(reverse=True)
    return [i for _, i in scored[:k]]


def _char_ngrams(text: str, n: int = 2) -> Counter:
    t = re.sub(r"\s+", "", text)
    return Counter(t[i:i + n] for i in range(max(0, len(t) - n + 1)))


_SEG_NGRAMS = [_char_ngrams(s) for s in SEGMENTS]
_DF = Counter()
for _g in _SEG_NGRAMS:
    _DF.update(set(_g))
_N = len(SEGMENTS)


def rank_char_ngram(query: str, k: int = 5) -> list[int]:
    """(B') 문자 2-gram 코사인 — 한국어 조사·활용을 어느 정도 흡수한다. 모델 없음.

    형태소 분석기 없이 "먹었습니다/먹는다" 를 부분적으로 잇는 값싼 방법이다.
    이것으로 충분하면 임베딩은 더더욱 필요 없다.
    """
    q = _char_ngrams(normalize_query(query))
    if not q:
        return []
    scored = []
    for i, g in enumerate(_SEG_NGRAMS, 1):
        num = sum(q[t] * g[t] * (math.log(_N / (1 + _DF[t])) ** 2) for t in q if t in g)
        if num <= 0:
            continue
        qn = math.sqrt(sum((q[t] * math.log(_N / (1 + _DF[t]))) ** 2 for t in q))
        gn = math.sqrt(sum((g[t] * math.log(_N / (1 + _DF[t]))) ** 2 for t in g))
        if qn and gn:
            scored.append((num / (qn * gn), i))
    scored.sort(reverse=True)
    return [i for _, i in scored[:k]]


def rank_shipped(query: str, k: int = 5) -> list[int]:
    """(E) 실제로 넣은 구현 — 정확 일치 우선, 없으면 2-gram 순위.

    베이스라인만 재고 다른 것을 넣으면 측정이 문서 장식이 된다.
    **배포되는 코드를 그대로 부른다.**
    """
    return [m.segment_no for m in current_search(TRANSCRIPT, query, max_results=k)]
