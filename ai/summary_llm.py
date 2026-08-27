"""정제 → 부분 요약 → 통합. LLM 을 부르는 부분만 모았다. (#135)

★ 여기 있는 것 중 제일 중요한 것은 폴백이다.

`summarize_text_auto` 안에서 이 모양이 **세 번 똑같이** 반복되고 있었다.

    try:
        resp = oai.responses.create(...)      # 신 API
        ...output_text
    except Exception:
        comp = oai.chat.completions.create(...)   # 구 API
        ...choices[0].message.content

세 벌이라 고칠 때 하나를 빠뜨리기 쉽고, **한 번도 시험된 적이 없었다.**
`responses` 가 실패해야 도는 길인데 그 실패를 만들 자리가 없었기 때문이다.
한곳으로 모으고 가짜 클라이언트로 두 갈래를 다 지나가게 했다.

★ 클라이언트를 인자로 받는다.

전에는 함수 안에서 `_load_openai_clients()` 를 불렀다. 그러면 시험이 돌 때마다
`OPENAI_API_KEY` 를 요구하고, 없으면 예외가 나서 **요약 로직을 한 줄도 못 재본다.**
부르는 쪽이 만들어 넘기면 가짜를 넣을 수 있다.
"""
from __future__ import annotations

import json
import os

import requests

from transcript_chunker import chunk_text

SYSTEM_CLEAN = (
    "너는 한국어 전사 텍스트를 정제하는 도우미다. "
    "원문 의미를 보존하고 환각을 피한다. "
    "해야 할 일: 문장 경계/문장부호 복원, 띄어쓰기·맞춤법 보정, 중복/잡음 최소화. "
    "불명확하면 [불명확]로 표기하고 임의로 보충하지 않는다."
)

SYSTEM_SUMMARIZE = (
    "너는 정확한 한국어 필기자다. 환각 없이 핵심을 구조화하고, "
    "수식은 입력에 실제 언급된 경우에만 ```math 블록을 사용한다."
)

SYSTEM_REDUCE = """
                        내가 한국어로 작성된 방대한 텍스트를 너에게 줄게. 
                        텍스트 내용은 선생님이 학생들에게 가르친 내용, 즉 수업 내용이야. 
                        따라서 텍스트는 수학, 언어, 과학, 역사, 경제, 공학 등 초,중,고, 대학교를 포함해 주식, 부동산 등 다양한 내용일 수 있어. 
                        텍스트는 정말 많은 단어를 포함하고 있기 때문에 나는 텍스트를 잘 요약해서 학생들에게 주고 싶어. 
                        따라서 텍스트에 있는 수업 내용만을 포함하고, hallucinations를 피하고, 한국어 깔끔한 한국어 Markdown을 작성해줘. 
                        """

SYSTEM_REDUCE_FALLBACK = (
    "You are a senior Korean technical writer. "
    "Merge partial notes into one coherent Markdown document."
)


def ask(client, model: str, system: str, prompt: str,
        temperature: float, max_output_tokens: int) -> str:
    """신 API 를 먼저 부르고, 실패하면 구 API 로 내려간다.

    두 API 의 응답 모양이 다르다 - `output_text` 와 `choices[0].message.content`.
    호출부가 그것까지 알 필요는 없어서 여기서 문자열 하나로 맞춘다.

    `except Exception` 이 넓은 것은 의도다. SDK 버전·프록시·모델에 따라
    나오는 예외 종류가 제각각이고, 어느 쪽이든 할 일은 같다 - 구 API 로 간다.
    """
    try:
        response = client.responses.create(
            model=model,
            input=[{"role": "system", "content": system},
                   {"role": "user", "content": prompt}],
            temperature=temperature,
            max_output_tokens=max_output_tokens,
        )
        return response.output_text.strip()
    except Exception:
        completion = client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": system},
                      {"role": "user", "content": prompt}],
            temperature=temperature,
        )
        return completion.choices[0].message.content.strip()


def clean_transcript(client, model: str, raw: str, max_chars: int | None = None) -> str:
    """STT 원문을 문장으로 되돌린다. 사실을 더하거나 빼지 않는다."""
    limit = max_chars or int(os.getenv("O3_CHUNK_CHARS", "9000"))
    parts = []
    for chunk in chunk_text(raw, limit):
        prompt = (
            "아래 한국어 텍스트를 의미 왜곡 없이 정리하세요.\n"
            "- 문장부호/문장 경계 복원, 띄어쓰기/맞춤법 보정\n"
            "- 명백한 중복/잡음은 간단히 정리(사실 추가/삭제 금지)\n"
            "- 고유명사가 한국어 음역일 때, 맥락이 명확하면 원어(예: C++)로 복원\n"
            "- 불명확하면 [불명확] 표기\n\n"
            f"{chunk}"
        )
        parts.append(ask(client, model, SYSTEM_CLEAN, prompt, 0.2, 2000))
    return "\n\n".join(parts)


def map_summarize(client, model: str, cleaned: str, max_chars: int | None = None) -> list[str]:
    """조각마다 부분 요약을 만든다 (map).

    통합(reduce)과 나눈 이유는 컨텍스트다. 정제본 전체를 한 번에 넣으면
    긴 강의에서 앞부분이 잘린다 - 잘렸다는 신호 없이 조용히 빠진다.
    """
    limit = max_chars or int(os.getenv("OAI_SUMMARY_CHARS", "8000"))
    notes = []
    for chunk in chunk_text(cleaned, limit):
        prompt = (
            "아래 텍스트를 한국어 강의 노트로 요약하세요.\n"
            "- 핵심 포인트 3~6개 불릿\n"
            "- 수학/과학/공학 등에서 실제 언급된 공식이 있으면 ```math 블록으로 표기\n"
            "- 입력에 없는 사실 금지, 불명확하면 [불명확]\n\n"
            f"{chunk}"
        )
        notes.append(ask(client, model, SYSTEM_SUMMARIZE, prompt, 0.3, 2200))
    return notes


def join_notes(notes: list[str]) -> str:
    return "\n\n---\n\n".join(notes)


def reduce_via_gms(notes_joined: str, gms_base: str, gms_key: str,
                   post=requests.post, timeout: int = 120) -> str | None:
    """SSAFY GMS 프록시로 통합을 시도한다. 실패하면 None - 예외를 올리지 않는다.

    여기서 죽이면 **부분 요약까지 만들어 놓고 전부 버리게 된다.**
    부르는 쪽이 None 을 받으면 OpenAI 로 통합하면 된다.
    """
    payload = {
        "model": "claude-3-7-sonnet-latest",
        "max_tokens": 4500,
        "system": ("너는 한국어 기술 문서 작성자다. 부분 요약들을 하나의 일관된 마크다운 "
                   "문서로 통합하라. 중복 제거, 용어/표기 통일, 사실 보존, 환각 금지."),
        "messages": [{"role": "user", "content":
                      "다음 '부분 요약 노트'를 통합해 하나의 강의 문서를 만들어라.\n"
                      "- 섹션: # 요약(5~8문장), ## 핵심 개념(불릿으로 리스트), "
                      "## 수식/정의(수학/과학 등 수식이 있는 경우만, ```math)\n"
                      "- 중복 제거, 용어 일관성 유지, 사실 추가/삭제 금지\n\n"
                      f"{notes_joined}"}],
    }
    try:
        response = post(
            f"{gms_base.rstrip('/')}/v1/messages",
            headers={"Content-Type": "application/json",
                     "x-api-key": gms_key,
                     "anthropic-version": "2023-06-01"},
            data=json.dumps(payload), timeout=timeout,
        )
    except Exception as err:
        print("[GMS] 호출 실패:", err)
        return None

    if response.status_code != 200:
        print("[GMS] HTTP", response.status_code, response.text[:200])
        return None

    content = response.json().get("content", [])
    if content and isinstance(content, list) and isinstance(content[0], dict) \
            and "text" in content[0]:
        return content[0]["text"].strip()
    print("[GMS] 응답 모양이 예상과 다르다")
    return None


def reduce_via_openai(client, model: str, notes_joined: str) -> str:
    prompt = (
        "다음 요약 노트 묶음을 하나의 문서로 통합하세요. "
        "중복 제거, 용어 일관성 유지, 사실추가 금지. "
        "출력은 Markdown으로 하고 아래 섹션을 포함:\n"
        "1) 요약(5~8문장)\n"
        "2) 핵심 개념 리스트\n"
        "3) 수학, 과학, 공학과 같이 공식이 필요, 언급 되거나 공식이 있으면 설명이 잘 된다면 수식을 표기해줘\n"
        f"{notes_joined}"
    )
    try:
        response = client.responses.create(
            model=model,
            input=[{"role": "system", "content": SYSTEM_REDUCE},
                   {"role": "user", "content": prompt}],
            temperature=0.3,
            max_output_tokens=3000,
        )
        return response.output_text.strip()
    except Exception:
        completion = client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": SYSTEM_REDUCE_FALLBACK},
                      {"role": "user", "content": prompt}],
            temperature=0.3,
        )
        return completion.choices[0].message.content.strip()
