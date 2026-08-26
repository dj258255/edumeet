"""장문을 모델에 넣을 수 있는 크기로 자른다. (#135)

`summarize_text_auto` 안에 중첩 함수로 있던 것을 그대로 뗐다.
동작을 바꾸지 않았다 - 먼저 시험을 씌우려고 자리만 옮겼다.

**줄 경계로만 자른다.** 문장 중간에서 자르면 정제·요약 프롬프트가
잘린 문장을 완성하려 들면서 없는 말을 만든다.
"""
from __future__ import annotations


def chunk_text(text: str, max_chars: int) -> list[str]:
    """줄 단위로 모아 `max_chars` 를 넘기 직전에 끊는다.

    한 줄이 그 자체로 `max_chars` 보다 길면 자르지 않고 통째로 하나가 된다.
    STT 결과는 줄이 문장 단위라 실제로는 거의 일어나지 않고, 여기서 다시 쪼개면
    문장 중간이 잘린다 - 그쪽이 더 나쁘다.
    """
    chunks: list[str] = []
    buf: list[str] = []
    for line in text.splitlines(keepends=True):
        if sum(len(x) for x in buf) + len(line) > max_chars and buf:
            chunks.append("".join(buf))
            buf = []
        buf.append(line)
    if buf:
        chunks.append("".join(buf))
    return chunks
