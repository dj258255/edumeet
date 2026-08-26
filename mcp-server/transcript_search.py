"""transcript 안에서 찾는다. (#133)

여기에는 네트워크도 MCP 도 없다 - 순수 함수만 둔다.
도구의 값어치는 대부분 "무엇을 돌려주는가" 에서 갈리는데, 그 판단을
서버 코드 안에 섞어 두면 시험할 자리가 없어진다.

**transcript 의 한 줄은 자막 조각 하나다.** Java 쪽이 final 자막을 `\\n` 으로
이어 붙여 만들기 때문이다(CaptionTranscriptService). 그래서 줄 번호가 곧
조각 번호이고, 앞뒤 줄이 곧 앞뒤 발화다 - 문맥을 문자 수로 자를 이유가 없다.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict

from glossary import normalize_query

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


def search(transcript: str, query: str,
           context: int = DEFAULT_CONTEXT,
           max_results: int = DEFAULT_MAX_RESULTS) -> list[Match]:
    """조각 단위로 찾아 앞뒤 문맥과 함께 돌려준다.

    상한을 두는 이유 - 결과가 그대로 모델 컨텍스트로 들어간다.
    자막 전체를 돌려줄 것이면 애초에 검색 도구일 이유가 없다.
    """
    terms = search_terms(query)
    if not terms or not transcript:
        return []

    context = max(0, context)
    limit = max(1, min(max_results, MAX_RESULTS_CEILING))

    segments = transcript.split("\n")
    lowered = [s.lower() for s in segments]
    lowered_terms = [(t, t.lower()) for t in terms]

    matches: list[Match] = []
    for index, line in enumerate(lowered):
        hit = next((original for original, low in lowered_terms if low in line), None)
        if hit is None:
            continue
        start = max(0, index - context)
        matches.append(Match(
            segment_no=index + 1,
            text=segments[index],
            context_before=segments[start:index],
            context_after=segments[index + 1:index + 1 + context],
            matched_term=hit,
        ))
        if len(matches) >= limit:
            break
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
