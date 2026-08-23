#!/usr/bin/env python3
"""Prometheus 스크레이프 한 장을 CSV 한 줄로 만든다. (#43)

    python3 scripts/scrape_to_csv.py <csv> <t초> <스크레이프파일>

붕괴는 형태로 봐야 한다. 마지막 값만 보면 "큐가 쌓이다가 죽었다" 를 볼 수 없다.
"""
import pathlib
import re
import sys

HEADER = "t,queued_in,queued_out,heap_used,heap_max,sessions,rooms,published"


def _matches(body: str, name: str, selector: str):
    pattern = rf'^{re.escape(name)}\{{[^}}]*{selector}[^}}]*\}}\s+([0-9.eE+-]+)$'
    return [float(m) for m in re.findall(pattern, body, re.M)]


def value(body: str, name: str, selector: str = "") -> str:
    """단일 시계열. 없으면 빈 문자열."""
    found = _matches(body, name, selector)
    return repr(found[0]) if found else ""


def total(body: str, name: str, selector: str = "") -> str:
    """여러 시계열의 합.

    jvm_memory_used_bytes{area="heap"} 는 Eden/Survivor/Old 로 나뉜다.
    첫 값만 읽으면 힙 사용량을 크게 과소평가한다.
    """
    found = _matches(body, name, selector)
    return repr(sum(found)) if found else ""


def main() -> None:
    csv_path, t, scrape_path = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
    body = scrape_path.read_text(encoding="utf-8", errors="replace")

    if not csv_path.exists():
        csv_path.write_text(HEADER + "\n", encoding="utf-8")

    row = [
        t,
        value(body, "executor_queued_tasks", 'name="clientInboundChannelExecutor"'),
        value(body, "executor_queued_tasks", 'name="clientOutboundChannelExecutor"'),
        total(body, "jvm_memory_used_bytes", 'area="heap"'),
        total(body, "jvm_memory_max_bytes", 'area="heap"'),
        value(body, "chat_sessions_active"),
        value(body, "chat_rooms_active"),
        value(body, "chat_messages_published_total"),
    ]
    with csv_path.open("a", encoding="utf-8") as f:
        f.write(",".join(row) + "\n")


if __name__ == "__main__":
    main()
