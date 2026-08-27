"""transcript 안에서 찾는다. (#133, #140)

여기에는 네트워크도 MCP 도 없다 - 순수 함수만 둔다.
도구의 값어치는 대부분 "무엇을 돌려주는가" 에서 갈리는데, 그 판단을
서버 코드 안에 섞어 두면 시험할 자리가 없어진다.

**transcript 의 한 줄은 자막 조각 하나다.** Java 쪽이 final 자막을 `\\n` 으로
이어 붙여 만들기 때문이다(CaptionTranscriptService). 그래서 줄 번호가 곧
조각 번호이고, 앞뒤 줄이 곧 앞뒤 발화다 - 문맥을 문자 수로 자를 이유가 없다.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict

import math

from glossary import normalize_query
from semantic import semantic_hits

DEFAULT_CONTEXT = 1
DEFAULT_MAX_RESULTS = 20
MAX_RESULTS_CEILING = 100


@dataclass(frozen=True)
class Match:
    """찾은 조각 하나.

    :param segment_no: 1부터 세는 조각 번호. 사람이 "몇 번째 발화" 로 읽는다
    :param matched_term: 실제로 걸린 말. 질의와 다를 수 있다(용어 사전)
    """
    segment_no: int
    text: str
    context_before: list[str]
    context_after: list[str]
    matched_term: str

    def to_dict(self) -> dict:
        return asdict(self)


def search_terms(query: str) -> list[str]:
    """무엇으로 찾을 것인가.

    질의 원문과 정규화형을 **둘 다** 쓴다. 하나만 쓰면 반쪽이 된다 -

      * 원문만: 저장된 자막이 `파이썬` 이라 `python` 이 안 걸린다
      * 정규화형만: 사전이 놓친 영어 표기가 자막에 남아 있으면 그것이 안 걸린다

    순서를 유지하는 이유는 어떤 말로 걸렸는지 결과에 적어 주기 위해서다.
    """
    if not query or not query.strip():
        return []
    raw = query.strip()
    terms = [raw]
    normalized = normalize_query(raw)
    if normalized != raw:
        terms.append(normalized)
    return terms


def _exact_hits(segments: list[str], terms: list[str], limit: int) -> list[tuple[int, str]]:
    """질의 문자열 전체가 그대로 들어 있는 조각. 조각 순서를 유지한다."""
    lowered_terms = [(t, t.lower()) for t in terms]
    hits: list[tuple[int, str]] = []
    for index, line in enumerate(segments):
        low = line.lower()
        term = next((original for original, l in lowered_terms if l in low), None)
        if term is not None:
            hits.append((index, term))
            if len(hits) >= limit:
                break
    return hits


def _ranked_hits(segments: list[str], query: str, limit: int) -> list[tuple[int, str]]:
    """문자 2-gram 유사도로 순위를 매긴다. (#140)

    ★ 왜 이 단계가 생겼는가.

      전에는 exact 단계 하나뿐이었다. 그래서 **질문 형태는 무조건 0건**이었다 -
      의미 때문이 아니라 "질의 문자열 전체" 를 찾았기 때문이다.

          "비관적 잠금"                 1건
          "비관적 잠금 어디서 설명했지"   0건

      30개 질의로 재 보니 질문형 재현율이 **0%** 였다.
      2-gram 으로 바꾸자 **54%** 가 됐다. 모델 없이.

    ★ 왜 형태소 분석기가 아니라 2-gram 인가.

      "끊깁니다 / 끊김 / 끊어" 를 이으려면 활용을 알아야 한다.
      형태소 분석기를 넣으면 의존성이 하나 늘고 그 품질이 검색 품질에 섞인다.
      문자 2-gram 은 사전 없이 그 일부를 흡수한다. 값이 싸고 설명이 짧다.
    """
    query_grams = _char_bigrams(normalize_query(query))
    if not query_grams:
        return []

    seg_grams = [_char_bigrams(seg) for seg in segments]
    document_freq: dict[str, int] = {}
    for grams in seg_grams:
        for gram in set(grams):
            document_freq[gram] = document_freq.get(gram, 0) + 1
    total = len(segments) or 1

    def idf(gram: str) -> float:
        return math.log(total / (1 + document_freq.get(gram, 0)))

    scored: list[tuple[float, int, str]] = []
    for index, grams in enumerate(seg_grams):
        shared = [g for g in query_grams if g in grams]
        if not shared:
            continue
        numerator = sum(query_grams[g] * grams[g] * idf(g) ** 2 for g in shared)
        if numerator <= 0:
            continue
        qn = math.sqrt(sum((query_grams[g] * idf(g)) ** 2 for g in query_grams))
        sn = math.sqrt(sum((grams[g] * idf(g)) ** 2 for g in grams))
        if qn and sn:
            # 가장 많이 겹친 토큰을 "걸린 말" 로 보여 준다
            token = max((t for t in normalize_query(query).split() if t and t in segments[index]),
                        key=len, default=query)
            scored.append((numerator / (qn * sn), index, token))

    scored.sort(key=lambda item: (-item[0], item[1]))
    return [(index, token) for _, index, token in scored[:limit]]


def _merge_with_semantic(segments: list[str], query: str,
                         lexical: list[tuple[int, str]],
                         limit: int) -> list[tuple[int, str]]:
    """렉시컬 순위와 의미 순위를 RRF 로 합친다. 꺼져 있으면 렉시컬 그대로.

    ★ 점수를 더하지 않고 순위만 쓰는 이유.

      코사인 0.83 과 2-gram 0.21 을 더하면 척도가 큰 쪽이 항상 이긴다.
      두 값은 단위가 다르다. 순위는 단위가 없다.

    ★ 렉시컬을 버리지 않는 이유.

      용어형 15개에서 렉시컬이 재현율 100% 다. 임베딩만 쓰면 잘하던 것을 잃는다.
      실제로 임베딩 단독은 용어형 MRR 이 0.97 로 오히려 낮았다.
    """
    semantic = semantic_hits(segments, query, limit * 2)
    if not semantic:
        return lexical

    scores: dict[int, float] = {}
    labels: dict[int, str] = {}
    for ranking in (lexical, semantic):
        for position, (index, token) in enumerate(ranking, 1):
            scores[index] = scores.get(index, 0.0) + 1.0 / (60 + position)
            labels.setdefault(index, token)

    ordered = sorted(scores.items(), key=lambda kv: (-kv[1], kv[0]))
    return [(index, labels[index]) for index, _ in ordered[:limit]]


def _char_bigrams(text: str) -> dict[str, int]:
    compact = "".join(text.split()).lower()
    grams: dict[str, int] = {}
    for i in range(max(0, len(compact) - 1)):
        gram = compact[i:i + 2]
        grams[gram] = grams.get(gram, 0) + 1
    return grams


def search(transcript: str, query: str,
           context: int = DEFAULT_CONTEXT,
           max_results: int = DEFAULT_MAX_RESULTS) -> list[Match]:
    """조각 단위로 찾아 앞뒤 문맥과 함께 돌려준다.

    두 단계다. (#140)

      ① 질의 문자열 전체가 그대로 있는 조각 — 조각 순서로
      ② 없으면 문자 2-gram 유사도 순위로

    ★ 단계를 나눈 이유.

      용어로 찾을 때 사람은 **정확 일치**를 기대한다. "Redis" 를 넣었는데
      비슷한 조각이 위에 오면 도구가 이상해 보인다. 실제로 용어형 15개는
      어느 방식이든 재현율 100% 라 순위를 바꿀 이유가 없다.

      ②는 ①이 아무것도 못 찾을 때만 돈다. 질문형 재현율 0% -> 54% 를
      담당하는 것이 이 단계다.

    상한을 두는 이유 - 결과가 그대로 모델 컨텍스트로 들어간다.
    자막 전체를 돌려줄 것이면 애초에 검색 도구일 이유가 없다.
    """
    terms = search_terms(query)
    if not terms or not transcript:
        return []

    context = max(0, context)
    limit = max(1, min(max_results, MAX_RESULTS_CEILING))
    segments = transcript.split("\n")

    hits = _exact_hits(segments, terms, limit)
    if not hits:
        hits = _ranked_hits(segments, query, limit)
        # ★ 의미 검색을 "렉시컬이 0건일 때" 로 두면 영원히 안 돈다. (#140)
        #
        #   처음에 그렇게 짰다가 한 번도 실행되지 않는 것을 측정에서 발견했다 -
        #   2-gram 은 겹치는 조각이 하나만 있어도 결과를 내므로 그 조건이 성립하지 않는다.
        #   "선언은 있는데 아무도 안 쓴다" 를 또 만들 뻔했다.
        #
        #   측정에서 이긴 것은 폴백이 아니라 **두 순위를 합치는 것**이었다.
        #     2-gram 단독  질문형 54%
        #     하이브리드    질문형 70%, 0건 0개
        hits = _merge_with_semantic(segments, query, hits, limit)

    matches: list[Match] = []
    for index, term in hits:
        start = max(0, index - context)
        matches.append(Match(
            segment_no=index + 1,
            text=segments[index],
            context_before=segments[start:index],
            context_after=segments[index + 1:index + 1 + context],
            matched_term=term,
        ))
    return matches


def render(matches: list[Match], query: str, total_segments: int) -> str:
    """사람과 모델이 같이 읽을 형태로 만든다.

    빈 결과에 그냥 "없음" 을 주지 않는다. **무엇으로 찾았는지**를 같이 적는다 -
    용어 사전이 질의를 바꿨는데 그 사실을 안 알려 주면, 0건이 "그런 말을 안 했다"
    로 잘못 읽힌다.
    """
    terms = search_terms(query)
    if not terms:
        return "질의가 비어 있다."

    header = f"'{query}' — 찾은 말: {', '.join(terms)} / 전체 {total_segments}조각"
    if not matches:
        return header + "\n\n걸린 조각이 없다."

    lines = [header, ""]
    for m in matches:
        for before in m.context_before:
            lines.append(f"      {before}")
        lines.append(f"  #{m.segment_no}  {m.text}")
        for after in m.context_after:
            lines.append(f"      {after}")
        lines.append("")
    return "\n".join(lines).rstrip()
