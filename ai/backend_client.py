"""백엔드(Java)와 말하는 코드. (#117)

main.py 에서 분리했다. 왜 이것만 뗐는가 -

  이 함수들은 계약 테스트 23개로 덮여 있고(contracts/internal-api.json 기준)
  경계가 명확하며 나머지 코드와 공유하는 상태가 없다.

  반면 summarize_text_auto 는 471줄인데 테스트가 0개다.
  테스트 없이 쪼개면 '동작이 바뀌었는지 알 방법이 없는 변경' 이 된다.
  그런데 471줄이라 테스트를 쓸 수가 없다 - 닭-달걀이다.
  그 사실을 main.py 에 적어 두고 여기서는 건드리지 않는다.

계약
  contracts/internal-api.json 을 Java 테스트와 파이썬 테스트가 함께 읽는다.
  한쪽이 경로나 헤더를 바꾸면 반대쪽이 깨진다.
"""
import os
import time

import requests
from dotenv import load_dotenv


def _config_path() -> str:
    """이 서비스의 설정 파일. 전에는 다른 프로젝트(Express)의 .env 를 읽고 있었다. (#91)"""
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.env")


def _split_into_captions(text: str, max_chars: int = 60) -> list[str]:
    """전체 텍스트를 자막 조각으로 나눈다. (#113)

    한 화면에 60자가 넘으면 읽기 전에 다음 것으로 넘어간다.
    문장 부호를 우선 경계로 삼고, 한 문장이 너무 길면 그 안에서 다시 자른다.
    """
    import re as _re
    if not text or not text.strip():
        return []
    sentences = [x.strip() for x in _re.split(r"(?<=[.!?。！？])\s+|\n+", text) if x.strip()]

    chunks: list[str] = []
    for sentence in sentences:
        if len(sentence) <= max_chars:
            chunks.append(sentence)
            continue
        # 긴 문장은 공백 경계로 자른다. 단어 중간에서 끊으면 읽기 어렵다.
        current = ""
        for word in sentence.split(" "):
            if current and len(current) + 1 + len(word) > max_chars:
                chunks.append(current)
                current = word
            else:
                current = f"{current} {word}".strip()
        if current:
            chunks.append(current)
    return chunks


def send_captions_to_api(meeting_id, text: str, started_at_ms: int | None = None) -> dict:
    """STT 결과를 자막으로 백엔드에 보낸다. (#113)

    Java 계약 (contracts/internal-api.json 의 captionIngest)
        POST /api/v1/internal/meetings/{meetingId}/captions
        헤더  X-Internal-Token
        본문  {"text": ..., "sequence": ..., "spokenAt": ...}

    ★ 이것은 실시간 자막이 아니다.

      CLOVA STT 를 녹음이 끝난 뒤 파일 하나로 부르므로 전체 텍스트가
      한 덩어리로 나온다. 회의가 끝나야 텍스트가 나오는데 실시간일 수 없다.
      실시간으로 하려면 CLOVA 스트리밍 API 로 바꿔야 하고 그것은 별도 작업이다.

      그럼에도 지금 잇는 이유 - 소스를 바꾸는 순간 붙일 곳이 있어야 하고,
      지금도 쓸 데가 있다. 문장 단위로 보내면 다시보기 자막이 된다.

    :param started_at_ms: 회의 시작 시각(epoch ms). 조각의 발화 시각을 추정하는 기준.
                          모르면 지금 시각을 쓴다 - 다시보기 위치가 어긋나지만
                          자막이 아예 없는 것보다는 낫다.
    """
    try:
        env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.env")
        if os.path.exists(env_path):
            load_dotenv(env_path)

        url_tpl = os.getenv("CAPTION_INGEST_URL", "").strip()
        if not url_tpl:
            return {"ok": False, "detail": "CAPTION_INGEST_URL 미설정"}

        token = os.getenv("INTERNAL_API_TOKEN", "").strip()
        if not token:
            return {"ok": False,
                    "detail": "INTERNAL_API_TOKEN 미설정. /api/v1/internal/** 은 이 헤더 없이는 403 이다."}

        mid = _normalize_meeting_id(meeting_id)
        if mid is None:
            return {"ok": False, "detail": f"meetingId 가 없다(값={meeting_id!r})"}

        chunks = _split_into_captions(text)
        if not chunks:
            return {"ok": False, "detail": "보낼 자막이 없다"}

        url = url_tpl.replace("{meetingId}", str(mid)).replace("{meeting_id}", str(mid))
        headers = {"Accept": "application/json", "X-Internal-Token": token}
        base = started_at_ms if started_at_ms else int(time.time() * 1000)

        sent, failed = 0, []
        for i, chunk in enumerate(chunks):
            body = {
                "text": chunk,
                "sequence": i,
                # 실제 발화 시각을 모르므로 균등 분배한다. 이것이 근사라는 사실은
                # 반환값의 approximate_timing 으로 알린다 - 조용히 정확한 척하지 않는다.
                "spokenAt": base + i * 3000,
            }
            try:
                r = requests.post(url, headers=headers, json=body, timeout=10)
                if 200 <= r.status_code < 300:
                    sent += 1
                else:
                    failed.append({"sequence": i, "status": r.status_code,
                                   "text": (r.text or "")[:120]})
            except Exception as e:
                failed.append({"sequence": i, "detail": str(e)[:120]})

        return {
            "ok": sent > 0 and not failed,
            "sent": sent,
            "total": len(chunks),
            "failed": failed[:5],
            "approximate_timing": True,
            "realtime": False,
        }
    except Exception as e:
        return {"ok": False, "detail": f"자막 전송 실패: {e}"}


def send_summary_to_api(class_id: str, meetingId: str | None,
                        md_path: str | None, pdf_path: str | None) -> dict:
    """요약본을 Java 로 올린다.

    Java 계약 (InternalMeetingSummaryController)
        POST /api/v1/internal/meetings/{meetingId}/summary
        헤더  X-Internal-Token: <공유 시크릿>
        본문  multipart: summary_md | summary_pdf

    ★ 여기가 오래 끊겨 있었다. (#91)
      - {meetingId} 를 치환하지 않아 URL 에 문자 그대로 나갔다.
        Java 는 %7BmeetingId%7D 를 Long 으로 파싱하려다 400 을 낸다.
      - meetingId 를 폼 필드로 보냈다. Java 는 경로 변수로 받는다.
      - X-Internal-Token 이 없었다. #27 에서 Java 에 검사를 붙이며
        파이썬 쪽을 고치지 않았다. 그 상태로는 전부 403 이다.

      양쪽 다 자기 코드는 맞았다. 맞춰 본 적이 없었을 뿐이라
      어느 쪽 단위 테스트로도 안 잡혔다. 그래서 요청 자체를 테스트로 고정한다.
    """
    try:
        # 설정은 이 서비스 것을 읽는다.
        # 전에는 "../backend/.env" 를 읽었다 - 다른 프로젝트(Express)의 설정 파일이다.
        env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.env")
        if os.path.exists(env_path):
            load_dotenv(env_path)

        url_tpl = os.getenv("SUMMARY_UPLOAD_URL", "").strip()
        if not url_tpl:
            return {"ok": False, "detail": "SUMMARY_UPLOAD_URL 미설정"}

        token = os.getenv("INTERNAL_API_TOKEN", "").strip()
        if not token:
            # 보내 봐야 403 이다. 403 을 받고 원인을 찾는 것보다
            # 보내기 전에 설정 문제라고 말하는 편이 원인에 가깝다.
            return {"ok": False,
                    "detail": "INTERNAL_API_TOKEN 미설정. Java 의 /api/v1/internal/** 은 "
                              "X-Internal-Token 없이는 403 이다."}

        mid = _normalize_meeting_id(meetingId)
        if mid is None:
            return {"ok": False,
                    "detail": f"meetingId 가 없다(값={meetingId!r}). Java 는 경로 변수로 요구한다."}

        url = (url_tpl
               .replace("{meetingId}", str(mid))
               .replace("{meeting_id}", str(mid))
               .replace("{classId}", str(class_id))
               .replace("{class_id}", str(class_id)))

        headers = {
            "Accept": "application/json",
            "X-Internal-Token": token,
        }

        files = {}
        if pdf_path and os.path.isfile(pdf_path):
            files["summary_pdf"] = ("summary.pdf", open(pdf_path, "rb"), "application/pdf")
        elif md_path and os.path.isfile(md_path):
            files["summary_md"] = ("summary.md", open(md_path, "rb"),
                                   "text/markdown; charset=utf-8")
        else:
            return {"ok": False, "detail": "전송할 파일이 없습니다.(md/pdf 없음)"}

        # class_id 는 참고용으로만 남긴다. Java 가 쓰는 식별자는 경로의 meetingId 다.
        data = {"class_id": str(class_id)}

        print("[upload:url]", url)
        print("[upload:token]", token[:4] + "…")
        print("[upload:files]", list(files.keys()))

        resp = requests.post(url, headers=headers, data=data, files=files, timeout=60)

        for f in files.values():
            try:
                f[1].close()
            except Exception:
                pass

        if 200 <= resp.status_code < 300:
            # Java 는 최초 기록이면 201, 재시도로 아무것도 안 바뀌었으면 200 을 준다.
            return {"ok": True, "status": resp.status_code,
                    "already_existed": resp.status_code == 200,
                    "text": (resp.text or "")[:200]}
        if resp.status_code == 403:
            return {"ok": False, "status": 403,
                    "detail": "Java 가 403. X-Internal-Token 값이 양쪽에서 다른지 확인할 것.",
                    "text": (resp.text or "")[:200]}
        return {"ok": False, "status": resp.status_code, "text": (resp.text or "")[:200]}

    except requests.Timeout as e:
        return {"ok": False, "detail": f"업로드 타임아웃(60s): {e}"}
    except Exception as e:
        return {"ok": False, "detail": f"업로드 실패: {e}"}


def _normalize_meeting_id(mid):
    """None, 'null', 'None', 'undefined', '', 공백 등을 None으로.
       숫자/숫자문자열만 허용해서 문자열로 반환."""
    if mid is None:
        return None
    if isinstance(mid, (int, float)) and not isinstance(mid, bool):
        return str(int(mid))
    if isinstance(mid, str):
        s = mid.strip()
        if s == "" or s.lower() in {"null", "none", "undefined"}:
            return None
        return s if s.isdigit() else None
    return None
