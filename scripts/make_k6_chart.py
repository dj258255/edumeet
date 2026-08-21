#!/usr/bin/env python3
"""k6 측정 결과(JSON)로 차트를 만든다.

    python3 scripts/make_k6_chart.py

docs/performance/data/k6-*.json 을 읽어 docs/performance/images 에 SVG 를 쓴다.
수치를 손으로 옮기지 않는다. 문서의 숫자와 차트가 갈라지는 것을 막기 위해서다.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from make_perf_chart import hbar_chart, RED, BLUE, GRAY  # noqa: E402

DATA = Path("docs/performance/data")
OUT = Path("docs/performance/images")
ORANGE, PURPLE = "#ea580c", "#7c3aed"

# 2x2 조합. 라벨은 실행 스크립트(run-benchmark.sh)와 맞춘다.
COMBOS = [
    ("nobatch-naive",   "개선 전",        "앱 N+1 · 배치페치 없음",  RED),
    ("nobatch-batch",   "앱 IN절만",      "IN 절 · 배치페치 없음",   ORANGE),
    ("batch100-naive",  "배치페치만",     "앱 N+1 · 배치페치 100",   PURPLE),
    ("batch100-batch",  "개선 후",        "IN 절 · 배치페치 100",    BLUE),
]


def load(name):
    p = DATA / f"k6-{name}.json"
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else None


def main():
    rows = [(label, sub, color, load(key)) for key, label, sub, color in COMBOS]
    rows = [(l, s, c, d) for l, s, c, d in rows if d]
    if not rows:
        print("측정 결과가 없다. ./scripts/run-benchmark.sh 를 먼저 실행한다.")
        return 1

    OUT.mkdir(parents=True, exist_ok=True)
    written = []
    env = "MySQL 8.0 (Docker) · k6 · VU 50 · 과제 30건/클래스 · 응답 약 404KB"

    hbar_chart(
        title="과제 목록 조회 — 요청당 실행 SQL",
        subtitle=env,
        bars=[(l, d["sql_per_request"]["avg"], c, s) for l, s, c, d in rows],
        unit="개", value_fmt="{:.0f}",
        note="앱 레벨 IN 절보다 Hibernate default_batch_fetch_size 의 기여가 컸다.",
        out_path=OUT / "03-mysql-sql-per-request.svg",
    )
    written.append(OUT / "03-mysql-sql-per-request.svg")

    hbar_chart(
        title="과제 목록 조회 — p95 지연시간",
        subtitle=env,
        bars=[(l, d["latency_ms"]["p95"], c, s) for l, s, c, d in rows],
        unit=" ms", value_fmt="{:.0f}",
        note="SQL 이 16.7배 줄어도 p95 는 1.9배만 빨라진다. 쿼리 수는 대리 지표일 뿐이다.",
        out_path=OUT / "04-mysql-p95-latency.svg",
    )
    written.append(OUT / "04-mysql-p95-latency.svg")

    hbar_chart(
        title="과제 목록 조회 — 처리량",
        subtitle=env,
        bars=[(l, d["rps"], c, s) for l, s, c, d in rows],
        unit=" req/s", value_fmt="{:.1f}",
        out_path=OUT / "05-mysql-throughput.svg",
    )
    written.append(OUT / "05-mysql-throughput.svg")

    # 정원 동시성
    sess = [(k, load(f"session-{k}")) for k in ("without-lock", "with-lock")]
    sess = [(k, d) for k, d in sess if d]
    if sess:
        bars = []
        for key, d in sess:
            label = "잠금 없음" if key == "without-lock" else "비관적 잠금"
            over = d.get("overflow") or 0
            bars.append((label, d.get("final_active") or 0,
                         RED if over > 0 else BLUE,
                         f"정원 {d.get('final_limit')}명 · 초과 {over}명"))
        cap = sess[0][1].get("final_limit") or 30
        hbar_chart(
            title="세션 정원 제어 — 동시 입장 시 실제 입장자 수",
            subtitle=f"MySQL 8.0 InnoDB · k6 · 동시 시도 {sess[0][1].get('attempts')}명 · 정원 {cap}명",
            bars=bars, unit="명", value_fmt="{:.0f}",
            x_max=max(b[1] for b in bars) * 1.25,
            note=f"정원({cap})을 넘은 만큼이 초과 입장이다. 잠금이 없으면 검사와 기록 사이에 다른 요청이 끼어든다.",
            out_path=OUT / "06-session-capacity-mysql.svg",
        )

    for f in written:
        print(f"  {f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
