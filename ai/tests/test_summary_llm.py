"""LLM 호출 계층. (#135)

★ 여기서 제일 중요한 것은 `ask` 의 폴백이다.

전에는 이 try/except 가 `summarize_text_auto` 안에 **세 번 똑같이** 있었고
**한 번도 시험된 적이 없었다.** `responses` 가 실패해야 도는 길인데,
471줄 안에서는 그 실패를 만들 자리가 없었기 때문이다.

클라이언트를 인자로 받게 바꾼 것도 같은 이유다. 전에는 함수 안에서
`_load_openai_clients()` 를 불러서, 시험을 돌리려면 OPENAI_API_KEY 가 필요했다.
"""
import pytest

import summary_llm
from summary_llm import (ask, clean_transcript, join_notes, map_summarize,
                         reduce_via_gms, reduce_via_openai)


class FakeResponses:
    def __init__(self, text=None, error=None):
        self.text, self.error, self.calls = text, error, []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        return type("R", (), {"output_text": self.text})()


class FakeCompletions:
    def __init__(self, text=None):
        self.text, self.calls = text, []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        message = type("M", (), {"content": self.text})()
        return type("C", (), {"choices": [type("Ch", (), {"message": message})()]})()


class FakeClient:
    def __init__(self, responses_text=None, responses_error=None, chat_text=None):
        self.responses = FakeResponses(responses_text, responses_error)
        self.chat = type("Chat", (), {})()
        self.chat.completions = FakeCompletions(chat_text)


# ── ask ──────────────────────────────────────────────────────────────

def test_uses_the_new_api_when_it_works():
    client = FakeClient(responses_text="  결과  ")
    assert ask(client, "m", "sys", "prompt", 0.2, 100) == "결과"
    assert client.chat.completions.calls == [], "성공했는데 구 API 도 불렀다"


def test_falls_back_to_chat_completions():
    """★ 한 번도 시험된 적 없던 길이다."""
    client = FakeClient(responses_error=RuntimeError("이 모델은 responses 미지원"),
                        chat_text="  폴백 결과  ")
    assert ask(client, "m", "sys", "prompt", 0.2, 100) == "폴백 결과"
    assert len(client.chat.completions.calls) == 1


def test_fallback_keeps_the_same_system_and_prompt():
    """폴백에서 프롬프트가 달라지면 결과가 조용히 달라진다."""
    client = FakeClient(responses_error=RuntimeError("x"), chat_text="ok")
    ask(client, "m", "시스템", "사용자", 0.3, 100)
    messages = client.chat.completions.calls[0]["messages"]
    assert messages[0] == {"role": "system", "content": "시스템"}
    assert messages[1] == {"role": "user", "content": "사용자"}


def test_fallback_drops_max_output_tokens():
    """구 API 는 그 인자를 모른다. 넘기면 TypeError 로 폴백까지 실패한다."""
    client = FakeClient(responses_error=RuntimeError("x"), chat_text="ok")
    ask(client, "m", "s", "p", 0.3, 2200)
    assert "max_output_tokens" not in client.chat.completions.calls[0]


def test_both_paths_fail_raises():
    """둘 다 실패하면 삼키지 않는다 - 부르는 쪽이 요약 실패로 처리해야 한다."""
    client = FakeClient(responses_error=RuntimeError("a"))
    client.chat.completions.create = lambda **k: (_ for _ in ()).throw(RuntimeError("b"))
    with pytest.raises(RuntimeError, match="b"):
        ask(client, "m", "s", "p", 0.2, 10)


# ── clean / map ──────────────────────────────────────────────────────

def test_clean_calls_once_per_chunk():
    client = FakeClient(responses_text="정제됨")
    result = clean_transcript(client, "m", "a\nb\nc\n", max_chars=2)
    assert len(client.responses.calls) == 3
    assert result == "정제됨\n\n정제됨\n\n정제됨"


def test_map_returns_one_note_per_chunk():
    client = FakeClient(responses_text="노트")
    assert map_summarize(client, "m", "a\nb\n", max_chars=2) == ["노트", "노트"]


def test_join_notes_uses_a_visible_separator():
    """구분자가 없으면 통합 프롬프트가 부분 요약들을 한 덩어리로 읽는다."""
    assert join_notes(["A", "B"]) == "A\n\n---\n\nB"


def test_empty_transcript_makes_no_call():
    client = FakeClient(responses_text="x")
    assert clean_transcript(client, "m", "", max_chars=100) == ""
    assert client.responses.calls == []


# ── reduce ───────────────────────────────────────────────────────────

class FakeHttpResponse:
    def __init__(self, status, payload=None, text=""):
        self.status_code, self._payload, self.text = status, payload, text

    def json(self):
        return self._payload


def test_gms_reduce_returns_the_text():
    def post(url, **kwargs):
        assert url.endswith("/v1/messages")
        assert kwargs["headers"]["x-api-key"] == "key"
        return FakeHttpResponse(200, {"content": [{"text": "  통합본  "}]})

    assert reduce_via_gms("노트", "https://gms.example.com/", "key", post=post) == "통합본"


@pytest.mark.parametrize("response", [
    FakeHttpResponse(500, text="서버 오류"),
    FakeHttpResponse(200, {"content": []}),
    FakeHttpResponse(200, {"content": [{"no_text": 1}]}),
    FakeHttpResponse(200, {}),
])
def test_gms_reduce_returns_none_instead_of_raising(response):
    """★ 여기서 죽이면 부분 요약을 다 만들어 놓고 전부 버리게 된다."""
    assert reduce_via_gms("노트", "https://gms.example.com", "key",
                          post=lambda *a, **k: response) is None


def test_gms_transport_failure_returns_none():
    def boom(*a, **k):
        raise ConnectionError("닿지 않음")
    assert reduce_via_gms("노트", "https://gms.example.com", "key", post=boom) is None


def test_openai_reduce_falls_back_too():
    client = FakeClient(responses_error=RuntimeError("x"), chat_text="통합 폴백")
    assert reduce_via_openai(client, "m", "노트") == "통합 폴백"


def test_openai_reduce_uses_the_korean_system_prompt():
    client = FakeClient(responses_text="ok")
    reduce_via_openai(client, "m", "노트")
    system = client.responses.calls[0]["input"][0]["content"]
    assert system is summary_llm.SYSTEM_REDUCE
