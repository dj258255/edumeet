"""Java 로 요약본을 보내는 요청이 Java 의 계약과 맞는지 본다. (#91)

왜 이 테스트가 필요했나
    Java 는 #27 에서 /api/v1/internal/** 에 X-Internal-Token 검사를 붙였다.
    파이썬 쪽은 고치지 않았다. 양쪽 다 자기 코드는 맞고, 맞춰 본 적이 없을 뿐이다.
    그래서 어느 쪽 단위 테스트로도 안 잡힌다.

무엇을 고정하나
    파이썬이 "실제로 만들어 보내는 HTTP 요청" 을 잡아서 검사한다.
    함수가 무엇을 의도했는지가 아니라 네트워크에 나가는 것을 본다.

Java 계약 (backend/.../InternalMeetingSummaryController.java)
    POST /api/v1/internal/meetings/{meetingId}/summary
    헤더  X-Internal-Token: <공유 시크릿>     -> hasRole("INTERNAL")
    본문  multipart: summary_md | summary_pdf
"""
import json
import os
import sys
import pytest
import responses

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, AI_DIR)

# ★ 계약을 손으로 적지 않는다. Java 테스트가 읽는 그 파일을 읽는다.
#   손으로 적으면 Java 가 경로를 바꿔도 이쪽 테스트는 초록으로 남는다.
#   그게 정확히 #91 이 오래 안 잡힌 이유다.
CONTRACT_PATH = os.path.join(AI_DIR, "..", "contracts", "internal-api.json")
with open(CONTRACT_PATH, encoding="utf-8") as f:
    CONTRACT = json.load(f)

SUMMARY = CONTRACT["endpoints"]["summaryUpload"]
AUTH_HEADER = CONTRACT["authHeader"]
JAVA_URL = "http://java:8080" + SUMMARY["path"]
TOKEN = "test-internal-token"


@pytest.fixture
def summary_files(tmp_path):
    pdf = tmp_path / "summary.pdf"
    pdf.write_bytes(b"%PDF-1.4 fake")
    md = tmp_path / "summary.md"
    md.write_text("# 요약", encoding="utf-8")
    return str(md), str(pdf)


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setenv("SUMMARY_UPLOAD_URL", JAVA_URL)
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)


@responses.activate
def _call(md, pdf, meeting_id="77", class_id="5"):
    """실제 요청을 가로채고 그 요청 객체를 돌려준다."""
    import main
    responses.add(responses.POST, responses.matchers  # placeholder
                  if False else "http://java:8080/api/v1/internal/meetings/77/summary",
                  json={"ok": True}, status=201)
    result = main.send_summary_to_api(
        class_id=class_id, meetingId=meeting_id, md_path=md, pdf_path=pdf)
    return result, (responses.calls[0].request if responses.calls else None)


def test_meeting_id_goes_into_the_path(env, summary_files):
    """★ meetingId 는 경로 변수다. 폼 필드가 아니다.

    Java 는 @PathVariable("meetingId") 로 받는다.
    파이썬이 classId 를 경로에 넣으면 엉뚱한 회의에 요약본이 붙거나 404 다.
    """
    md, pdf = summary_files
    result, req = _call(md, pdf, meeting_id="77", class_id="5")
    assert req is not None, "요청이 아예 나가지 않았다"
    assert "/meetings/77/summary" in req.url, (
        f"meetingId 77 이 경로에 없다. 실제 URL: {req.url}")
    assert "/meetings/5/" not in req.url, "classId 가 meetingId 자리에 들어갔다"


def test_internal_token_header_is_sent(env, summary_files):
    """★ X-Internal-Token 이 없으면 Java 가 403 으로 끊는다.

    SecurityConfig: .requestMatchers("/api/v1/internal/**").hasRole("INTERNAL")
    그 롤은 InternalApiTokenFilter 가 이 헤더로만 부여한다.
    """
    md, pdf = summary_files
    result, req = _call(md, pdf)
    assert AUTH_HEADER in req.headers, (
        f"인증 헤더가 없다. 보낸 헤더: {sorted(req.headers.keys())}")
    assert req.headers[AUTH_HEADER] == TOKEN


def test_pdf_is_sent_as_multipart(env, summary_files):
    """Java 는 @RequestParam MultipartFile 로 받는다."""
    md, pdf = summary_files
    result, req = _call(md, pdf)
    body = req.body if isinstance(req.body, bytes) else str(req.body).encode()
    field = SUMMARY["multipartFields"][1]      # summary_pdf
    assert field.encode() in body, f"{field} 파트가 없다"


def test_missing_token_is_a_configuration_error(monkeypatch, summary_files):
    """토큰이 설정되지 않았으면 요청을 보내기 전에 실패해야 한다.

    보내 봐야 403 이다. 403 을 받고 나서 "왜 실패했지" 를 찾는 것보다
    보내기 전에 설정 문제라고 말하는 편이 원인에 가깝다.
    """
    monkeypatch.setenv("SUMMARY_UPLOAD_URL", JAVA_URL)
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    md, pdf = summary_files
    import main
    result = main.send_summary_to_api(
        class_id="5", meetingId="77", md_path=md, pdf_path=pdf)
    assert result["ok"] is False
    assert "token" in result["detail"].lower() or "토큰" in result["detail"]


def test_success_status_matches_contract(env, summary_files):
    """계약이 성공으로 규정한 상태 코드를 성공으로 취급한다.

    Java 는 최초 기록이면 201, 재시도로 아무것도 안 바뀌었으면 200 을 준다.
    한쪽만 성공으로 보면 재시도가 실패로 기록된다.
    """
    md, pdf = summary_files
    import main
    for status in SUMMARY["successStatus"]:
        with responses.RequestsMock() as rsps:
            rsps.add(responses.POST, "http://java:8080/api/v1/internal/meetings/77/summary",
                     json={"ok": True}, status=status)
            r = main.send_summary_to_api(class_id="5", meetingId="77",
                                         md_path=md, pdf_path=pdf)
        assert r["ok"] is True, f"{status} 를 실패로 취급했다"
    assert 201 in SUMMARY["successStatus"] and 200 in SUMMARY["successStatus"]


def test_path_uses_the_variable_names_the_contract_declares():
    """계약이 선언한 경로 변수가 실제 경로에 있다."""
    for var in SUMMARY["pathVariables"]:
        assert "{" + var + "}" in SUMMARY["path"], (
            f"계약의 pathVariables 에 {var} 가 있는데 path 에는 없다")
