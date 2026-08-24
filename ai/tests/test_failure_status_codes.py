"""실패가 상태 코드로 드러나는지 본다. (#91)

이전에는 STT 가 실패해도 HTTP 200 에 {"status": "stt_failed"} 를 담아 보냈다.
호출자는 본문을 파싱해야만 실패를 안다.

왜 문제인가
    - 재시도 정책을 상태 코드로 못 짠다. 200 은 재시도 대상이 아니다
    - 프록시·게이트웨이·모니터링이 전부 성공으로 센다
    - 5xx 알림이 안 울린다. 실패율 지표가 0 으로 보인다

무엇이 어떤 코드여야 하나
    입력이 잘못됨(파일 없음 등)   4xx  - 다시 보내도 같다
    외부 의존(STT·OpenAI) 실패    502  - 우리 잘못이 아니고 재시도 여지가 있다
"""
import os
import sys
import wave

import pytest
from fastapi.testclient import TestClient

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, AI_DIR)


@pytest.fixture
def client(monkeypatch, tmp_path):
    import main
    d = tmp_path / "audio" / "1"
    d.mkdir(parents=True)
    with wave.open(str(d / "audio_1.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
        w.writeframes(b"\x00\x00" * 800)
    monkeypatch.setattr(main, "BASE_AUDIO_DIR", str(tmp_path / "audio"))
    monkeypatch.setattr(main, "MERGE_OUT_DIR", str(tmp_path / "merged"))
    return TestClient(main.app, raise_server_exceptions=False), main


def test_stt_failure_is_not_200(client, monkeypatch):
    """★ STT 가 실패하면 5xx 다. 200 에 담아 보내지 않는다."""
    c, main = client
    monkeypatch.setattr(main, "Start_STT",
                        lambda out, cid: {"ok": False, "detail": "STT 서버 500"})

    r = c.post("/STT/1", json={"meetingId": "77"})

    assert r.status_code != 200, (
        "STT 실패인데 200 이다. 호출자가 상태 코드로 실패를 알 수 없고, "
        "모니터링·재시도·알림이 전부 성공으로 센다.")
    assert r.status_code == 502, f"외부 의존 실패는 502 여야 한다. 실제 {r.status_code}"
    # detail 은 이제 구조화된 dict 다. 무엇이 실패했는지 기계가 읽을 수 있다.
    detail = r.json()["detail"]
    assert detail["status"] == "stt_failed"
    assert detail["stt_ok"] is False


def test_missing_transcript_is_not_200(client, monkeypatch):
    """STT 는 성공했다는데 결과 경로가 없으면 그것도 실패다."""
    c, main = client
    monkeypatch.setattr(main, "Start_STT",
                        lambda out, cid: {"ok": True, "transcript_path": None})

    r = c.post("/STT/1", json={"meetingId": "77"})
    assert r.status_code == 502, f"실제 {r.status_code}"


def test_summary_failure_is_not_200(client, monkeypatch, tmp_path):
    """요약이 실패해도 마찬가지다."""
    c, main = client
    t = tmp_path / "t.txt"; t.write_text("안녕", encoding="utf-8")
    monkeypatch.setattr(main, "Start_STT",
                        lambda out, cid: {"ok": True, "transcript_path": str(t)})
    monkeypatch.setattr(main, "summarize_text_auto",
                        lambda tp, od: {"ok": False, "detail": "OpenAI 429"})

    r = c.post("/STT/1", json={"meetingId": "77"})
    assert r.status_code == 502, f"실제 {r.status_code}"


def test_success_is_200(client, monkeypatch, tmp_path):
    """성공은 200 이다. 실패만 바꾸고 성공 경로는 그대로여야 한다."""
    c, main = client
    t = tmp_path / "t.txt"; t.write_text("안녕", encoding="utf-8")
    monkeypatch.setattr(main, "Start_STT",
                        lambda out, cid: {"ok": True, "transcript_path": str(t)})
    monkeypatch.setattr(main, "summarize_text_auto",
                        lambda tp, od: {"ok": True, "summary_path": None,
                                        "summary_pdf_path": None, "clean_path": None})
    monkeypatch.setattr(main, "send_summary_to_api",
                        lambda **kw: {"ok": True, "status": 201})
    monkeypatch.setattr(main, "cleanup_class_dir", lambda d: {"ok": True})

    r = c.post("/STT/1", json={"meetingId": "77"})
    assert r.status_code == 200, f"성공 경로가 깨졌다. 실제 {r.status_code}: {r.text[:200]}"
    assert r.json()["status"] == "summary_done"


def test_no_wav_files_is_4xx(monkeypatch, tmp_path):
    """입력이 없는 것은 우리 잘못이 아니라 부른 쪽 잘못이다 - 4xx."""
    import main
    d = tmp_path / "audio" / "9"; d.mkdir(parents=True)
    monkeypatch.setattr(main, "BASE_AUDIO_DIR", str(tmp_path / "audio"))
    c = TestClient(main.app, raise_server_exceptions=False)
    r = c.post("/STT/9", json={"meetingId": "77"})
    assert 400 <= r.status_code < 500, f"실제 {r.status_code}"
