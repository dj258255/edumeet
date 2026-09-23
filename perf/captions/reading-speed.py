#!/usr/bin/env python3
"""자막 읽기 속도 — 재생 속도 r 로 따라잡을 때 읽기 상한을 넘는 자막이 얼마나 되는가. (#233)

상한의 근거 (한국어)
  Netflix 한국어 Timed Text Style Guide (partnerhelp.netflixstudios.com, 2025-07-07 개정)
    I.15 읽기 속도      성인 12자/초 · 아동 9자/초
    II.3 SDH 읽기 속도  성인 14자/초 · 아동 11자/초
  최소 표시 시간은 Netflix 일반 요구사항의 이벤트당 5/6초(0.833초)를 쓴다.

  ★ 우리 자막은 한국어 강의에 한국어 자막(intralingual)이라 SDH 쪽에 가깝다 - 14자/초.
    하지만 지금 서비스되는 자막은 SDH 표기(화자·효과음)까지 하지 않으므로 12자/초도 함께 낸다.
    두 값 사이 어디를 고르든 판단이 바뀌지 않는지 보려는 것이다.

  ※ 이슈가 출발점으로 적은 "17자/초" 는 Netflix 의 **일반(대부분 언어)** 값이다.
    한국어 가이드는 그보다 낮다(12 · SDH 14). 한국어 자료로 재려면 한국어 값으로 재야 한다.

표본 한계 (결과에 반드시 함께 읽을 것)
  강의 하나를 전사한 결과뿐이다(구간 5개 · 395자). 구간이 10~15초로 길어
  **배치 전사 그대로** 재면 실시간 자막보다 느슨하다 - 실제 실시간 자막은 더 짧은 단위로 나간다.
  그래서 문장 부호로 나눈 **실시간에 가까운 분할**도 함께 낸다(구간 시간을 글자 수 비례로 배분).

  분할 방식 두 가지
    배치   : 전사가 준 구간 그대로
    실시간 : 문장 부호(. ? !) 뒤에서 나눔, 시간은 글자 수 비례

사용
  python3 perf/captions/reading-speed.py [--json <경로>] [--out <경로>]
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_SOURCE = HERE / 'clova-lecture-sample.json'
DEFAULT_OUT = HERE / 'out' / 'reading-speed.md'

# 따라잡기 후보. hls.js maxLiveSyncPlaybackRate 로 넣을 수 있는 값과 같게 둔다.
RATES = [1.0, 1.05, 1.1, 1.25, 1.5, 2.0]

# 한국어 읽기 상한 (Netflix 한국어 가이드)
LIMITS = [('12자/초', 12.0), ('14자/초(SDH)', 14.0)]
MIN_DISPLAY_S = 5 / 6  # Netflix 일반 요구사항: 이벤트당 최소 5/6초

SENTENCE_END = re.compile(r'(?<=[.?!])\s+')


def load_segments(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding='utf-8'))
    segments = data.get('segments')
    if not segments:
        raise SystemExit(f'구간이 없다: {path}')
    return [
        {'start': int(s['start']), 'end': int(s['end']), 'text': str(s['text']).strip()}
        for s in segments
        if s.get('text')
    ]


def split_live(segments: list[dict]) -> list[dict]:
    """문장 부호로 나누고 구간 시간을 글자 수 비례로 배분한다.

    ★ 왜 나누나. 배치 전사는 호흡이 긴 구간으로 나온다(10~15초). 실시간 자막은 그보다 훨씬 짧은
      단위로 화면에 뜬다. 긴 구간을 그대로 재면 상한을 넘는 자막을 과소평가한다.
    """
    pieces: list[dict] = []
    for seg in segments:
        parts = [p.strip() for p in SENTENCE_END.split(seg['text']) if p.strip()]
        if len(parts) <= 1:
            pieces.append(dict(seg))
            continue
        total = sum(len(p) for p in parts)
        span = seg['end'] - seg['start']
        cursor = seg['start']
        for i, part in enumerate(parts):
            share = len(part) / total
            end = seg['end'] if i == len(parts) - 1 else cursor + round(span * share)
            pieces.append({'start': cursor, 'end': end, 'text': part})
            cursor = end
    return pieces


def chars(text: str) -> tuple[int, int]:
    """(공백 제외, 공백 포함). Netflix 는 공백·문장부호를 0.5자로 세지만
    국내 관행과 맞추기 위해 여기서는 글자 수를 그대로 센다 - 두 값을 다 낸다."""
    without_spaces = len(re.sub(r'\s', '', text))
    return without_spaces, len(text)


def stats(pieces: list[dict], rate: float) -> dict:
    """r 로 돌릴 때 이 자막 묶음이 어떤 모양이 되는가."""
    cps_no_space: list[float] = []
    cps_with_space: list[float] = []
    over = {label: 0 for label, _ in LIMITS}
    short = 0

    for piece in pieces:
        display_s = (piece['end'] - piece['start']) / 1000 / rate
        if display_s <= 0:
            continue
        no_space, with_space = chars(piece['text'])
        cps_no_space.append(no_space / display_s)
        cps_with_space.append(with_space / display_s)
        for label, limit in LIMITS:
            if no_space / display_s > limit:
                over[label] += 1
        if display_s < MIN_DISPLAY_S:
            short += 1

    total = len(cps_no_space) or 1
    return {
        'r': rate,
        'count': len(cps_no_space),
        'over': {label: over[label] / total for label, _ in LIMITS},
        'overCount': dict(over),
        'short': short / total,
        'shortCount': short,
        'cpsP50': statistics.median(cps_no_space) if cps_no_space else None,
        'cpsMax': max(cps_no_space) if cps_no_space else None,
        'cpsP50WithSpace': statistics.median(cps_with_space) if cps_with_space else None,
    }


def format_table(batch: list[dict], live: list[dict]) -> str:
    lines: list[str] = []
    lines.append('| 재생 속도 r | 자막 수 | 12자/초 초과 (배치) | 12자/초 초과 (실시간) | 14자/초 초과 (배치) | 14자/초 초과 (실시간) | 최소표시 미달 (배치) | 최소표시 미달 (실시간) | 중앙 CPS (배치) | 중앙 CPS (실시간) | **최대 CPS (배치/실시간)** |')
    lines.append('|---|---|---|---|---|---|---|---|---|---|---|')
    for b, l in zip(batch, live):
        mark = lambda value: f'**{value * 100:.0f}%**' if value > 0.05 else f'{value * 100:.0f}%'
        lines.append(
            f"| {b['r']:g} | {b['count']} | {mark(b['over']['12자/초'])} | {mark(l['over']['12자/초'])} | "
            f"{mark(b['over']['14자/초(SDH)'])} | {mark(l['over']['14자/초(SDH)'])} | "
            f"{mark(b['short'])} | {mark(l['short'])} | "
            f"{b['cpsP50']:.1f} | {l['cpsP50']:.1f} | "
            f"{b['cpsMax']:.1f} / {l['cpsMax']:.1f} |"
        )
    return '\n'.join(lines)


def scanning_rates(start: float = 1.0, stop: float = 3.0, step: float = 0.01) -> list[float]:
    """결정용으로 촘촘히 훑는 r. 표에 쓰는 후보(RATES)와 따로 둔다."""
    rates = []
    value = start
    while value <= stop + 1e-9:
        rates.append(round(value, 2))
        value += step
    return rates


def decision(segments: list[dict], live_pieces: list[dict]) -> dict:
    """판단 기준을 지키는 최대 r. 기준: 상한 초과 ≤ 5% 이고 최소표시 미달 ≤ 5%.

    두 분할 방식 중 **더 나쁜 쪽**이 기준을 넘으면 안 된다(안전한 쪽으로 정한다).
    """
    best = None
    for rate in scanning_rates():
        batch = stats(segments, rate)
        live = stats(live_pieces, rate)
        over = max(batch['over']['12자/초'], live['over']['12자/초'])
        over_sdh = max(batch['over']['14자/초(SDH)'], live['over']['14자/초(SDH)'])
        short = max(batch['short'], live['short'])
        if over <= 0.05 and short <= 0.05:
            best = {'r': rate, 'over12': over, 'over14': over_sdh, 'short': short,
                    'cpsMax': max(batch['cpsMax'], live['cpsMax'])}
        else:
            break
    first = None
    for rate in scanning_rates():
        batch = stats(segments, rate)
        live = stats(live_pieces, rate)
        over = max(batch['over']['14자/초(SDH)'], live['over']['14자/초(SDH)'])
        short = max(batch['short'], live['short'])
        if over > 0.05 or short > 0.05:
            first = {'r': rate, 'over14': over, 'short': short}
            break
    return {'maxRate12': best, 'firstViolation14': first}


def build_report(source: Path, segments: list[dict], batch: list[dict], live: list[dict]) -> str:
    live_pieces = split_live(segments)
    parts: list[str] = []
    parts.append('# 자막 읽기 속도 — 따라잡기 재생 속도별 상한 초과 (#233)')
    parts.append('')
    parts.append(f'- 표본: `{source.relative_to(source.parents[2])}` — 구간 {len(segments)}개 · '
                 f'글자 {sum(chars(s["text"])[0] for s in segments)}자(공백 제외)')
    parts.append(f'- 구간 길이: 중앙 {statistics.median((s["end"] - s["start"]) / 1000 for s in segments):.1f}초')
    parts.append(f'- 실시간 분할: 문장 부호로 나눠 {len(live_pieces)}조각 (배치 {len(segments)}구간)')
    parts.append('')
    parts.append('## 판단 기준 (근거)')
    parts.append('')
    parts.append('| 기준 | 값 | 출처 |')
    parts.append('|---|---|---|')
    parts.append('| 읽기 속도 (성인) | 12자/초 | Netflix 한국어 Timed Text Style Guide I.15 |')
    parts.append('| 읽기 속도 (성인 · SDH) | 14자/초 | 같은 문서 II.3 — 한국어→한국어 자막은 이쪽에 가깝다 |')
    parts.append('| 최소 표시 시간 | 5/6초 (0.833초) | Netflix 일반 요구사항 |')
    parts.append('')
    parts.append('> 이슈가 출발점으로 적은 17자/초는 Netflix 의 **일반(대부분 언어)** 값이다. '
                 '한국어 가이드는 12(SDH 14)로 더 낮아서, 한국어 자료를 그 값으로 재면 상한 초과를 과소평가한다.')
    parts.append('')
    parts.append('## 결과')
    parts.append('')
    parts.append(format_table(batch, live))
    parts.append('')
    parts.append(f'- 굵은 값은 5% 초과 — 판단 기준("상한을 넘는 자막이 5% 를 넘지 않는다")을 어긴 칸이다.')
    parts.append(f'- 최소 표시 미달은 표시 시간이 {MIN_DISPLAY_S:.3f}초 아래로 내려간 자막의 비율이다.')
    parts.append('')
    parts.append('### 공백 포함 글자로 센 값 (참고)')
    parts.append('')
    parts.append('| 재생 속도 r | 중앙 CPS (배치) | 중앙 CPS (실시간) | 12자/초 초과 (실시간) |')
    parts.append('|---|---|---|---|')
    for b, l in zip(batch, live):
        parts.append(f"| {b['r']:g} | {b['cpsP50WithSpace']:.1f} | {l['cpsP50WithSpace']:.1f} | "
                     f"{l['over']['12자/초'] * 100:.0f}% |")
    parts.append('')
    parts.append('### 결정 — 기준을 지키는 최대 재생 속도')
    parts.append('')
    decision_result = decision(segments, live_pieces)
    best = decision_result['maxRate12']
    if best is None:
        parts.append('- **1.00 에서도 기준을 넘는다** — 따라잡기를 쓸 수 없다.')
    else:
        parts.append(f"- 12자/초 기준(더 엄격): **r = {best['r']:.2f} 까지** 상한 초과 "
                     f"{best['over12'] * 100:.0f}% · 최소표시 미달 {best['short'] * 100:.0f}% "
                     f"(이때 최대 CPS {best['cpsMax']:.1f})")
    first = decision_result['firstViolation14']
    if first is not None:
        parts.append(f"- 14자/초(SDH) 기준: r = {first['r']:.2f} 에서 처음 넘는다 "
                     f"(상한 초과 {first['over14'] * 100:.0f}% · 최소표시 미달 {first['short'] * 100:.0f}%)")
    parts.append('- 판단 기준은 "상한을 넘는 자막이 5% 이하" 이므로, 자막 수가 적은 이 표본에서는 '
                 '한 구간만 넘어도 위반이다.')
    parts.append('')
    parts.append('## 한계')
    parts.append('')
    parts.append('- **표본이 하나다.** 강의 한 편(5구간·395자)의 전사 결과뿐이라 말하는 사람·주제·속도가 하나로 고정돼 있다.')
    parts.append('- 배치 전사 구간은 10~15초로 길다. 실시간 자막이 실제로 나가는 단위보다 느슨해서, '
                 '실시간 분할을 함께 냈다(문장 부호 · 글자 수 비례 배분). 실제 분할은 문장 의미 단위를 따르므로 이 근사와 다를 수 있다.')
    parts.append('- 자막이 "화면에 떠 있는 시간" 만 쟀다. 읽는 사람이 실제로 다 읽었는지는 재지 않았다(사람 대상 실험 없음).')
    parts.append('')
    return '\n'.join(parts) + '\n'


def main() -> int:
    parser = argparse.ArgumentParser(description='자막 읽기 속도 분석 (#233)')
    parser.add_argument('--source', type=Path, default=DEFAULT_SOURCE)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--json', type=Path, default=None, help='계산 결과를 JSON 으로도 남긴다')
    args = parser.parse_args()

    segments = load_segments(args.source)
    batch = [stats(segments, r) for r in RATES]
    live = [stats(split_live(segments), r) for r in RATES]

    report = build_report(args.source, segments, batch, live)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(report, encoding='utf-8')
    print(report, end='')
    print(f'\n저장: {args.out}')

    if args.json:
        args.json.write_text(
            json.dumps({'source': str(args.source), 'rates': RATES, 'batch': batch, 'live': live},
                       ensure_ascii=False, indent=2) + '\n',
            encoding='utf-8',
        )
    return 0


if __name__ == '__main__':
    sys.exit(main())
