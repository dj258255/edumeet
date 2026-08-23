#!/usr/bin/env python3
"""무한 큐 OOM 재현 결과를 차트로 만든다. (#43)

    python3 scripts/make_chat_oom_chart.py

입력은 부하 시험이 남긴 시계열이다.
    build/chat-oom/metrics-slow.csv    개선 전 (큐 무한)
    build/chat-oom/metrics-after.csv   개선 후 (큐 상한)

수치를 손으로 옮기지 않는다. 다시 측정하면 차트도 다시 그려진다.
"""
import csv
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from make_perf_chart import line_chart, hbar_chart, RED, BLUE  # noqa: E402

SRC = pathlib.Path("build/chat-oom")
OUT = pathlib.Path("docs/performance/images")
MB = 1048576


def series(name):
    path = SRC / f"metrics-{name}.csv"
    if not path.exists():
        raise SystemExit(f"{path} 가 없다. ./scripts/run-chat-oom.sh 를 먼저 돌린다.")
    rows = []
    with path.open() as f:
        for r in csv.DictReader(f):
            if not r.get("heap_used"):
                continue
            def num(key):
                try:
                    return float(r[key])
                except (TypeError, ValueError):
                    return 0.0
            rows.append({
                "t": int(r["t"]),
                "queued_out": num("queued_out"),
                "heap": num("heap_used") / MB,
                "sessions": num("sessions"),
            })
    return rows


before, after = series("slow"), series("after")

# 개선 전은 OOM 으로 끝난다. 그 시점까지만 그린다 - 그 뒤는 데이터가 없다.
span = max(before[-1]["t"], 240)


def points(rows, key, limit):
    return [(r["t"], r[key]) for r in rows if r["t"] <= limit]


# ── 1. 큐 길이. 이 차트가 이 실험의 전부다 ──────────────────────────
# 개선 전은 천장 없이 오르다 끊긴다(=OOM). 개선 후는 상한에서 평평해진다.
line_chart(
    title="STOMP 아웃바운드 큐 길이",
    subtitle="구독자 150명 · 발행 4x60/s · 클라이언트 대역 5KB/s(Toxiproxy) · 힙 512MB",
    x_label="경과 (초)",
    y_label="큐에 쌓인 작업 수",
    series=[
        ("개선 전 — 상한 없음 (84초에 OOM)", RED, points(before, "queued_out", span)),
        ("개선 후 — 상한 20,000", BLUE, points(after, "queued_out", span)),
    ],
    x_ticks=[0, 60, 120, 180, 240],
    y_max=1_100_000,
    y_ticks=[0, 250_000, 500_000, 750_000, 1_000_000],
    x_unit="s", markers=False, y_fmt=lambda v: f"{v:,.0f}",
    out_path=OUT / "11-chat-queue-growth.svg",
)

# ── 2. 힙. 큐가 힙을 밀어 올린다 ────────────────────────────────────
line_chart(
    title="힙 사용량",
    subtitle="개선 전은 상한(512MB)에 닿아 OOM. 개선 후는 톱니를 유지한다",
    x_label="경과 (초)",
    y_label="힙 사용 (MB)",
    series=[
        ("개선 전", RED, points(before, "heap", span)),
        ("개선 후", BLUE, points(after, "heap", span)),
    ],
    x_ticks=[0, 60, 120, 180, 240],
    y_max=560,
    y_ticks=[0, 128, 256, 384, 512],
    x_unit="s", markers=False,
    out_path=OUT / "12-chat-heap.svg",
)

# ── 3. 결과 요약 ────────────────────────────────────────────────────
hbar_chart(
    title="같은 부하, 큐 상한만 다르다",
    subtitle="구독자 150명 · 발행 4x60/s · 클라이언트 대역 5KB/s · 힙 512MB",
    bars=[
        ("개선 전 — 큐 무한", before[-1]["t"], RED,
         f"큐 최대 {max(r['queued_out'] for r in before):,.0f} · OOM"),
        ("개선 후 — 큐 상한 20,000", after[-1]["t"], BLUE,
         f"큐 최대 {max(r['queued_out'] for r in after):,.0f} · 무중단"),
    ],
    unit="초", value_fmt="{:.0f}",
    note="무한 큐는 아무도 버리지 않고 다 같이 죽는 선택이었다. 상한은 느린 쪽을 버리고 나머지가 산다.",
    out_path=OUT / "13-chat-survival.svg",
)

for name in ("11-chat-queue-growth", "12-chat-heap", "13-chat-survival"):
    print(f"  {OUT / (name + '.svg')}")

print()
print(f"  개선 전  큐 최대 {max(r['queued_out'] for r in before):,.0f}  "
      f"힙 최대 {max(r['heap'] for r in before):.0f}MB  생존 {before[-1]['t']}초")
print(f"  개선 후  큐 최대 {max(r['queued_out'] for r in after):,.0f}  "
      f"힙 최대 {max(r['heap'] for r in after):.0f}MB  생존 {after[-1]['t']}초")
