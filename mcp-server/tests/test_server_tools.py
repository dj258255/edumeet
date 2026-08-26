"""도구가 실제로 등록되고, 실패가 사람에게 문장으로 가는가. (#133)

★ 이 파일이 있는 이유는 이 저장소가 같은 함정을 일곱 번 만났기 때문이다
(`docs/ops/07-declared-but-unused.md`).

    부품은 맞다.  그런데 연결되어 있는지는 아무도 안 물었다.

MCP 도구도 같은 모양이다 - 함수가 옳게 동작해도 `@server.tool()` 이 안 붙어
있으면 클라이언트에는 **아무것도 안 보인다.** 그래서 함수를 부르는 시험과
별개로 **등록되었는지**를 서버에 직접 묻는다.
"""
import asyncio

import pytest

import server as server_module
from client import InternalApiError

EXPECTED_TOOLS = {"list_caption_meetings", "search_transcript", "get_transcript"}


class FakeClient:
    def __init__(self, meetings=None, transcript=None, error=None):
        self._meetings = meetings or {"meetings": [], "returned": 0}
        self._transcript = transcript or {"meetingId": 1, "segmentCount": 0, "text": ""}
        self._error = error

    def list_caption_meetings(self, limit=50):
        if self._error:
            raise self._error
        return self._meetings

    def get_transcript(self, meeting_id):
        if self._error:
            raise self._error
        return self._transcript


@pytest.fixture
def fake(monkeypatch):
    def install(client):
        monkeypatch.setattr(server_module, "client", lambda: client)
        return client
    return install


def test_tools_are_actually_registered():
    """★ 함수가 옳아도 등록이 안 되면 클라이언트에는 안 보인다."""
    names = {t.name for t in asyncio.run(server_module.server.list_tools())}
    assert EXPECTED_TOOLS <= names


def test_every_tool_has_a_description():
    """설명이 없으면 모델이 언제 부를지 모른다 - 등록만 된 도구는 안 불린다."""
    for tool in asyncio.run(server_module.server.list_tools()):
        if tool.name in EXPECTED_TOOLS:
            assert tool.description and tool.description.strip()


def test_list_says_so_when_there_is_nothing(fake):
    fake(FakeClient())
    assert "없다" in server_module.list_caption_meetings()


def test_list_shows_id_title_and_segment_count(fake):
    fake(FakeClient(meetings={
        "returned": 2,
        "meetings": [
            {"meetingId": 7, "title": "스프링 부트 3주차", "segmentCount": 42, "lastSpokenAt": 2},
            {"meetingId": 3, "title": "파이썬 기초", "segmentCount": 8, "lastSpokenAt": 1},
        ],
    }))
    out = server_module.list_caption_meetings()
    assert "#7" in out and "스프링 부트 3주차" in out and "42조각" in out
    assert out.index("#7") < out.index("#3"), "최근 발화 순서를 유지해야 한다"


def test_search_returns_context_around_the_hit(fake):
    fake(FakeClient(transcript={
        "meetingId": 7, "segmentCount": 3,
        "text": "첫 문장입니다\n파이썬 을 설명합니다\n다음으로 갑니다",
    }))
    out = server_module.search_transcript(7, "파이썬")
    assert "#2" in out
    assert "첫 문장입니다" in out and "다음으로 갑니다" in out


def test_search_with_english_term_finds_normalized_text(fake):
    """★ 저장된 자막은 이미 `파이썬` 이다. `python` 으로 찾아도 걸려야 한다."""
    fake(FakeClient(transcript={
        "meetingId": 7, "segmentCount": 1, "text": "파이썬 을 설명합니다",
    }))
    out = server_module.search_transcript(7, "python")
    assert "#1" in out
    assert "파이썬" in out


def test_get_transcript_says_so_when_empty(fake):
    fake(FakeClient(transcript={"meetingId": 9, "segmentCount": 0, "text": ""}))
    assert "없다" in server_module.get_transcript(9)


@pytest.mark.parametrize("tool,args", [
    ("list_caption_meetings", ()),
    ("search_transcript", (1, "질의")),
    ("get_transcript", (1,)),
])
def test_backend_failure_becomes_a_sentence_not_a_traceback(fake, tool, args):
    """★ 도구가 예외를 던지면 클라이언트에는 '도구 실패' 로만 보인다.

    이유가 사람에게 문장으로 가야 고칠 수 있다 - 특히 설정 실수는
    서버가 아니라 MCP 클라이언트 설정에 있다.
    """
    fake(FakeClient(error=InternalApiError("EDUMEET_API_BASE_URL 이 비어 있다")))
    out = getattr(server_module, tool)(*args)
    assert out.startswith("실패:")
    assert "EDUMEET_API_BASE_URL" in out
