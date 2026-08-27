"""MCP 도구가 부르는 경로가 공유 계약과 같은지 본다. (#133)

이 파일이 계약의 **세 번째 소비자**다.

    backend/    InternalApiContractTest    노출한 경로가 계약과 같은가
    ai/         test_*_contract.py         부르는 경로가 계약과 같은가
    mcp-server/ 여기                        도구가 부르는 경로가 계약과 같은가

Java 쪽 시험은 양방향이다 - 계약에 없는 내부 엔드포인트를 만들면 거기서 깨진다.
그래서 계약을 안 고치고 엔드포인트만 늘리는 길이 없다.
"""
import json
import os

import pytest
import requests

from client import EduMeetClient, InternalApiError
from contract import DEFAULT_CONTRACT_PATH, auth_header_name, build_url, endpoint, load_contract


@pytest.fixture
def contract():
    return load_contract()


def test_contract_file_is_the_repo_one():
    """계약 파일은 저장소의 그것이어야 한다 - 사본을 두면 의미가 없다."""
    assert os.path.isfile(DEFAULT_CONTRACT_PATH), DEFAULT_CONTRACT_PATH
    with open(DEFAULT_CONTRACT_PATH, encoding="utf-8") as f:
        json.load(f)


def test_tools_only_use_declared_endpoints(contract):
    """이 서버가 쓰는 엔드포인트가 계약에 있어야 한다."""
    for name in ("captionMeetings", "captionTranscript"):
        spec = endpoint(name, contract)
        assert spec["method"] == "GET"
        assert spec["path"].startswith(contract["pathPrefix"])


def test_url_is_built_from_the_contract_not_a_literal(contract):
    """경로를 코드에 적지 않는다. 계약에서 나와야 한다."""
    url = build_url("https://api.example.com", "captionTranscript", contract, meetingId=7)
    assert url == "https://api.example.com" + contract["endpoints"]["captionTranscript"]["path"].replace(
        "{meetingId}", "7"
    )
    assert "{" not in url


def test_unfilled_path_variable_fails_loudly(contract):
    """★ `{meetingId}` 를 그대로 보내면 Java 는 400 을 낸다 - 그 400 은 '없는 회의' 처럼 보인다.

    #113 에서 실제로 겪었다. 그래서 나가기 전에 여기서 죽인다.
    """
    with pytest.raises(ValueError, match="경로 변수"):
        build_url("https://api.example.com", "captionTranscript", contract)


def test_auth_header_comes_from_the_contract(contract, monkeypatch):
    """★ 헤더 이름을 계약에서 읽는다.

    #27 에서 Java 가 X-Internal-Token 을 도입했는데 파이썬이 안 따라와서
    오래 403 이었다. 문서는 CI 를 실패시키지 못한다 - 그래서 여기서 읽는다.
    """
    monkeypatch.setenv("EDUMEET_API_BASE_URL", "https://api.example.com")
    monkeypatch.setenv("EDUMEET_INTERNAL_TOKEN", "t")
    client = EduMeetClient(contract=contract)
    assert list(client._headers()) == [auth_header_name(contract)]
    assert auth_header_name(contract) == "X-Internal-Token"


def test_missing_base_url_says_what_to_fix(monkeypatch):
    """설정이 없을 때 401 을 받아서 '인증 실패' 라고 말하면 서버를 뒤지게 된다."""
    monkeypatch.delenv("EDUMEET_API_BASE_URL", raising=False)
    monkeypatch.setenv("EDUMEET_INTERNAL_TOKEN", "t")
    with pytest.raises(InternalApiError, match="EDUMEET_API_BASE_URL"):
        EduMeetClient().list_caption_meetings()


def test_missing_token_is_caught_before_the_request(monkeypatch):
    monkeypatch.setenv("EDUMEET_API_BASE_URL", "https://api.example.com")
    monkeypatch.setenv("EDUMEET_INTERNAL_TOKEN", "")
    with pytest.raises(InternalApiError, match="X-Internal-Token"):
        EduMeetClient().get_transcript(1)


def test_unexpected_status_is_reported_with_the_contract_expectation(monkeypatch, contract):
    """계약이 200 이라고 적어 뒀으면, 다른 값이 오면 그 사실을 말해야 한다."""
    class FakeResponse:
        status_code = 503
        text = "upstream down"

        def json(self):  # pragma: no cover - 호출되면 안 된다
            raise AssertionError("실패 응답을 json 으로 읽으면 안 된다")

    monkeypatch.setattr(requests, "get", lambda *a, **k: FakeResponse())
    client = EduMeetClient(base_url="https://api.example.com", token="t", contract=contract)
    with pytest.raises(InternalApiError, match="503"):
        client.get_transcript(1)


def test_transport_failure_names_the_url(monkeypatch, contract):
    def boom(*a, **k):
        raise requests.ConnectionError("연결 거부")

    monkeypatch.setattr(requests, "get", boom)
    client = EduMeetClient(base_url="https://api.example.com", token="t", contract=contract)
    with pytest.raises(InternalApiError, match="captions/transcript"):
        client.get_transcript(42)
