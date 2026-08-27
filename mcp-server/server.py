"""EduMeet 강의 자막 MCP 서버. (#133)

    MCP 클라이언트 (Claude Code / Desktop)
        | stdio
    이 서버  --- HTTP + X-Internal-Token --->  Java  --->  caption_segment
                (contracts/internal-api.json)

★ 왜 DB 를 직접 읽지 않는가

`CaptionSegmentRepository.findTranscriptSegments` 의 정렬 규칙이 단순하지 않다 -
sequence 가 없는 조각을 뒤로 미루고, sequence -> spokenAt -> id 순으로 정렬한다.
그 JavaDoc 이 직접 경고한다. *"여기서 순서가 흔들리면 요약 결과도 흔들린다."*

DB 를 직접 읽으면 그 규칙을 여기서 한 번 더 구현해야 하고, 두 구현은 반드시
갈라진다. 그래서 이미 있는 내부 API 를 경유한다. 결과적으로 계약 파일 하나를
**Java · 파이썬 · MCP 셋이 함께 읽는다.**

★ 도구를 세 개로 나눈 이유

`get_transcript` 하나만 두면 모델이 자막 전체를 컨텍스트에 넣게 된다.
한 시간짜리 강의면 그것만으로 예산이 끝난다. 그래서 고르는 도구(list),
찾는 도구(search), 통째로 읽는 도구(get) 를 나눴다.
"""
from __future__ import annotations

import os

from mcp.server.mcpserver import MCPServer

from client import EduMeetClient, InternalApiError
from glossary import GLOSSARY_AVAILABLE
from transcript_search import DEFAULT_CONTEXT, DEFAULT_MAX_RESULTS, render, search

server = MCPServer(
    name="edumeet-transcript",
    title="EduMeet 강의 자막",
    instructions=(
        "EduMeet 에 저장된 강의 자막을 찾고 읽는다. "
        "회의 번호를 모르면 list_caption_meetings 로 먼저 고른다. "
        "자막 전체가 필요한 게 아니면 get_transcript 대신 search_transcript 를 쓴다."
    ),
)

_client: EduMeetClient | None = None


def client() -> EduMeetClient:
    """설정을 서버 기동이 아니라 첫 호출 때 읽는다.

    기동 때 읽고 죽으면 MCP 클라이언트에는 "서버가 안 뜬다" 로만 보인다.
    도구 호출에서 실패하면 이유가 사람에게 문장으로 간다.
    """
    global _client
    if _client is None:
        _client = EduMeetClient()
    return _client


@server.tool(
    description=(
        "저장된 자막이 있는 회의 목록을 최근 발화 순으로 돌려준다. "
        "자막이 없는 회의는 나오지 않는다."
    )
)
def list_caption_meetings(limit: int = 20) -> str:
    try:
        data = client().list_caption_meetings(limit=limit)
    except InternalApiError as exc:
        return f"실패: {exc}"

    meetings = data.get("meetings", [])
    if not meetings:
        return "저장된 자막이 있는 회의가 없다."

    lines = [f"자막이 있는 회의 {data.get('returned', len(meetings))}건 (최근 발화 순)", ""]
    for m in meetings:
        lines.append(
            f"  #{m['meetingId']}  {m['title']}"
            f"  — {m['segmentCount']}조각"
        )
    return "\n".join(lines)


@server.tool(
    description=(
        "회의 하나의 자막에서 말을 찾아 앞뒤 문맥과 함께 돌려준다. "
        "영어 기술어로 찾아도 된다 - 저장된 한국어 표기로 바꿔서도 같이 찾는다."
    )
)
def search_transcript(meeting_id: int, query: str,
                      context: int = DEFAULT_CONTEXT,
                      max_results: int = DEFAULT_MAX_RESULTS) -> str:
    try:
        data = client().get_transcript(meeting_id)
    except InternalApiError as exc:
        return f"실패: {exc}"

    transcript = data.get("text", "")
    matches = search(transcript, query, context=context, max_results=max_results)
    result = render(matches, query, data.get("segmentCount", 0))

    if not GLOSSARY_AVAILABLE:
        result += (
            "\n\n※ 용어 사전을 못 찾았다(ai/caption_normalizer.py). "
            "영어 기술어는 저장된 한국어 표기로 다시 찾아야 할 수 있다."
        )
    return result


@server.tool(
    description=(
        "회의 하나의 final 자막 전문을 돌려준다. 길 수 있으므로, "
        "찾는 것이 정해져 있으면 search_transcript 를 먼저 쓴다."
    )
)
def get_transcript(meeting_id: int) -> str:
    try:
        data = client().get_transcript(meeting_id)
    except InternalApiError as exc:
        return f"실패: {exc}"

    text = data.get("text", "")
    if not text:
        return f"#{meeting_id} 에 저장된 final 자막이 없다."
    return f"#{meeting_id} — {data.get('segmentCount', 0)}조각\n\n{text}"


def main() -> None:
    server.run(transport=os.environ.get("EDUMEET_MCP_TRANSPORT", "stdio"))


if __name__ == "__main__":
    main()
