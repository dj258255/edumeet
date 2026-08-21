#!/usr/bin/env python3
"""성능 개선 전/후 비교 차트를 SVG로 생성한다.

의존성 없이 SVG를 직접 쓴다. PNG 변환은 cairosvg 또는 rsvg-convert 를 쓴다.
포트폴리오/PR 에 붙일 이미지를 재현 가능하게 만들기 위한 스크립트다.

사용:
    python3 scripts/make_perf_chart.py
"""
from pathlib import Path

FONT = "'Apple SD Gothic Neo', 'Noto Sans KR', 'Malgun Gothic', sans-serif"
RED, BLUE, GRAY, DARK, MUTED = "#dc2626", "#2563eb", "#9ca3af", "#111827", "#6b7280"


def line_chart(*, title, subtitle, x_label, y_label,
               series, x_ticks, y_max, y_ticks, out_path,
               legend_pos="upper-left"):
    """series: [(라벨, 색, [(x, y), ...]), ...]"""
    W, H = 880, 440
    PAD_L, PAD_R, PAD_T, PAD_B = 78, 34, 66, 74
    PW, PH = W - PAD_L - PAD_R, H - PAD_T - PAD_B
    x_min, x_max = 0, max(x_ticks) * 1.2

    def px(v): return PAD_L + (v - x_min) / (x_max - x_min) * PW
    def py(v): return PAD_T + PH - (v / y_max) * PH

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}" font-family="{FONT}">',
        f'<rect width="{W}" height="{H}" fill="#ffffff"/>',
        f'<text x="{PAD_L}" y="32" font-size="18" font-weight="700" fill="{DARK}">{title}</text>',
        f'<text x="{PAD_L}" y="52" font-size="12.5" fill="{MUTED}">{subtitle}</text>',
    ]

    for v in y_ticks:
        parts.append(f'<line x1="{PAD_L}" y1="{py(v):.1f}" x2="{PAD_L+PW}" y2="{py(v):.1f}" '
                     f'stroke="#e5e7eb" stroke-width="1"/>')
        parts.append(f'<text x="{PAD_L-11}" y="{py(v)+4:.1f}" text-anchor="end" '
                     f'font-size="12" fill="{MUTED}">{v}</text>')

    parts.append(f'<line x1="{PAD_L}" y1="{PAD_T}" x2="{PAD_L}" y2="{PAD_T+PH}" stroke="{GRAY}" stroke-width="1.5"/>')
    parts.append(f'<line x1="{PAD_L}" y1="{PAD_T+PH}" x2="{PAD_L+PW}" y2="{PAD_T+PH}" stroke="{GRAY}" stroke-width="1.5"/>')

    for v in x_ticks:
        parts.append(f'<text x="{px(v):.1f}" y="{PAD_T+PH+24}" text-anchor="middle" '
                     f'font-size="12.5" fill="{MUTED}">{v}건</text>')

    parts.append(f'<text x="{PAD_L+PW/2:.0f}" y="{H-20}" text-anchor="middle" '
                 f'font-size="12.5" fill="#374151">{x_label}</text>')
    parts.append(f'<text x="20" y="{PAD_T+PH/2:.0f}" text-anchor="middle" font-size="12.5" '
                 f'fill="#374151" transform="rotate(-90 20 {PAD_T+PH/2:.0f})">{y_label}</text>')

    for _, color, pts in series:
        d = " ".join(("M" if i == 0 else "L") + f"{px(a):.1f},{py(b):.1f}"
                     for i, (a, b) in enumerate(pts))
        parts.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2.6"/>')

    for _, color, pts in series:
        for a, b in pts:
            parts.append(f'<circle cx="{px(a):.1f}" cy="{py(b):.1f}" r="5.5" fill="{color}"/>')
            parts.append(f'<text x="{px(a):.1f}" y="{py(b)-14:.1f}" text-anchor="middle" '
                         f'font-size="14" font-weight="700" fill="{color}">{b}</text>')

    lw, lh = 260, 26 * len(series) + 16
    lx = PAD_L + 18 if legend_pos == "upper-left" else PAD_L + PW - lw - 10
    ly = PAD_T + 14
    parts.append(f'<g transform="translate({lx},{ly})">')
    parts.append(f'<rect width="{lw}" height="{lh}" rx="7" fill="#ffffff" fill-opacity="0.94" stroke="#e5e7eb"/>')
    for i, (label, color, _) in enumerate(series):
        yy = 24 + i * 26
        parts.append(f'<line x1="14" y1="{yy-4}" x2="38" y2="{yy-4}" stroke="{color}" stroke-width="2.6"/>')
        parts.append(f'<text x="46" y="{yy}" font-size="12.5" fill="#374151">{label}</text>')
    parts.append('</g></svg>')

    Path(out_path).write_text("\n".join(parts), encoding="utf-8")
    return out_path


if __name__ == "__main__":
    out = Path("docs/performance/images")
    out.mkdir(parents=True, exist_ok=True)

    line_chart(
        title="과제 목록 조회 — 실행 쿼리 수",
        subtitle="Hibernate Statistics · H2 인메모리 · 과제당 첨부 2건, 제출현황 3건",
        x_label="조회한 과제 건수",
        y_label="실행 쿼리 수",
        series=[
            ("개선 전 — 건수에 비례 (4배 늘 때 3.6배)", RED,  [(5, 23), (20, 83)]),
            ("개선 후 — 상수 (4배 늘 때 1.0배)",        BLUE, [(5, 5),  (20, 5)]),
        ],
        x_ticks=[5, 20], y_max=100, y_ticks=[0, 20, 40, 60, 80, 100],
        out_path=out / "01-n-plus-one-query-count.svg",
    )

    line_chart(
        title="사용자 제출상태 포함 목록 조회 — 실행 쿼리 수",
        subtitle="Hibernate Statistics · H2 인메모리 · 동일 조건",
        x_label="조회한 과제 건수",
        y_label="실행 쿼리 수",
        series=[
            ("개선 전", RED,  [(5, 18), (20, 63)]),
            ("개선 후", BLUE, [(5, 4),  (20, 4)]),
        ],
        x_ticks=[5, 20], y_max=80, y_ticks=[0, 20, 40, 60, 80],
        out_path=out / "02-n-plus-one-with-user-status.svg",
    )
    print("SVG 생성 완료")
