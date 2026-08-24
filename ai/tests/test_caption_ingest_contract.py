"""자막을 백엔드로 보내는 요청이 계약과 맞는지 본다. (#113)

파이프라인의 마지막 빈 칸이었다.

    파이썬 AI   자막 전송 코드 없음          <- 여기
    백엔드      POST .../captions -> STOMP   O
    프론트      STOMP 로 듣는다              O

★ 이것은 실시간 자막이 아니다.
  CLOVA STT 를 녹음이 끝난 뒤 파일 하나로 부르므로 전체 텍스트가 한 덩어리로 나온다.
  회의가 끝나야 텍스트가 나오는데 실시간일 수 없다.
  전송 경로를 먼저 잇는 이유는 소스를 바꿀 때 붙일 곳이 있어야 하기 때문이다.
"""
import json
import os
import sys

import pytest
import responses

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, AI_DIR)

# 계약을 손으로 적지 않는다. Java 테스트가 읽는 그 파일을 읽는다.
CONTRACT_PATH = os.path.join(AI_DIR, "..", "contracts", "internal-api.json")
with open(CONTRACT_PATH, encoding="utf-8") as f:
    CONTRACT = json.load(f)

CAPTION = CONTRACT["endpoints"]["captionIngest"]
AUTH_HEADER = CONTRACT["authHeader"]
JAVA_URL = "http://java:8080" + CAPTION["path"]
TOKEN = "test-internal-token"
TARGET = "http://java:8080/api/v1/internal/meetings/77/captions"


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setenv("CAPTION_INGEST_URL", JAVA_URL)
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)


def _bodies():
    """responses 가 받은 요청들의 JSON 본문."""
    return [json.loads(c.request.body) for c in responses.calls]


@responses.activate
def _send(text, env_ok=True, started_at=None):
    import main
    responses.add(responses.POST, TARGET, json={"ok": True}, status=200)
    result = main.send_captions_to_api("77", text, started_at_ms=started_at)
    return result, _bodies(), list(responses.calls)


def test_text_is_split_into_chunks(env):
    """★ 한 덩어리로 보내지 않는다 - 한 화면에 다 안 들어간다."""
    long_text = "안녕하세요. " * 40
    result, bodies, _ = _send(long_text)

    assert result["sent"] > 1, "긴 텍스트를 한 번에 보냈다. 자막이 화면을 넘친다"
    assert all(len(b["text"]) <= 60 for b in bodies), (
        f"60자를 넘는 조각이 있다: {[len(b['text']) for b in bodies]}")


def test_sequence_increases_from_zero(env):
    """★ sequence 가 순서를 정한다. 빠지거나 뒤섞이면 자막이 뒤엉킨다."""
    _, bodies, _ = _send("첫 문장이다. 두 번째 문장이다. 세 번째 문장이다.")
    assert [b["sequence"] for b in bodies] == list(range(len(bodies)))


def test_internal_token_header_is_sent(env):
    """★ X-Internal-Token 이 없으면 Java 가 403 으로 끊는다."""
    _, _, calls = _send("한 문장.")
    assert AUTH_HEADER in calls[0].request.headers, (
        f"인증 헤더가 없다. 보낸 헤더: {sorted(calls[0].request.headers.keys())}")
    assert calls[0].request.headers[AUTH_HEADER] == TOKEN


def test_meeting_id_goes_into_the_path(env):
    """meetingId 는 경로 변수다. #91 에서 요약 업로드가 같은 이유로 깨져 있었다."""
    _, _, calls = _send("한 문장.")
    assert "/meetings/77/captions" in calls[0].request.url
    assert "{meetingId}" not in calls[0].request.url, "치환되지 않은 채로 나갔다"


def test_body_has_the_declared_fields(env):
    """계약이 선언한 본문 필드를 전부 보낸다."""
    _, bodies, _ = _send("한 문장.")
    for field in CAPTION["jsonFields"]:
        assert field in bodies[0], f"계약의 {field} 가 본문에 없다"


def test_timing_is_reported_as_approximate(env):
    """★ 근사라는 사실을 숨기지 않는다.

    실제 발화 시각을 모르므로 균등 분배한다.
    조용히 정확한 척하면 다시보기에서 자막이 어긋나도 원인을 못 찾는다.
    """
    result, _, _ = _send("첫 문장. 두 번째 문장.")
    assert result["approximate_timing"] is True
    assert result["realtime"] is False, "실시간이 아닌데 실시간이라고 보고하면 안 된다"


def test_spoken_at_increases(env):
    """조각의 발화 시각이 순서대로 늘어난다."""
    _, bodies, _ = _send("첫 문장. 두 번째 문장. 세 번째 문장.", started_at=1_000_000)
    times = [b["spokenAt"] for b in bodies]
    assert times == sorted(times)
    assert times[0] >= 1_000_000


def test_missing_token_fails_before_sending(monkeypatch):
    """토큰이 없으면 보내기 전에 실패한다. 403 을 받고 원인을 찾는 것보다 낫다."""
    monkeypatch.setenv("CAPTION_INGEST_URL", JAVA_URL)
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    import main
    result = main.send_captions_to_api("77", "한 문장.")
    assert result["ok"] is False
    assert "INTERNAL_API_TOKEN" in result["detail"]


def test_empty_text_is_not_sent(env):
    """빈 텍스트로 요청을 만들지 않는다. STT 가 아무것도 못 알아들었을 때다."""
    import main
    result = main.send_captions_to_api("77", "   ")
    assert result["ok"] is False
    assert "자막" in result["detail"]


@responses.activate
def test_partial_failure_is_reported(env):
    """★ 일부만 실패해도 조용히 성공으로 보고하지 않는다."""
    import main
    responses.add(responses.POST, TARGET, json={"ok": True}, status=200)
    responses.add(responses.POST, TARGET, status=500)
    responses.add(responses.POST, TARGET, json={"ok": True}, status=200)

    result = main.send_captions_to_api("77", "첫 문장. 두 번째 문장. 세 번째 문장.")

    assert result["ok"] is False, "하나라도 실패하면 ok 가 아니다"
    assert result["sent"] == 2
    assert len(result["failed"]) == 1
    assert result["failed"][0]["status"] == 500
