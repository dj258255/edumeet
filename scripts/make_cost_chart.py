#!/usr/bin/env python3
"""데이터 전송 비용 모델 차트를 만든다.

    python3 scripts/make_cost_chart.py

수치의 출처는 LiveKit 공식 벤치마크 실측치 하나뿐이고 나머지는 전부 계산이다.
어느 것이 실측이고 어느 것이 계산인지 문서에 명시한다.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from make_perf_chart import hbar_chart, RED, BLUE  # noqa: E402

OUT = Path("docs/performance/images")
ORANGE, PURPLE, GREEN = "#ea580c", "#7c3aed", "#059669"

# ── 실측 기준점 ───────────────────────────────────────────────
# LiveKit 공식 벤치마크: 1 publisher x 3,000 subscriber = 531 MBps out
MBPS_PER_VIEWER = 531 * 8 / 3000            # 1.42 Mbps
GB_PER_VIEWER_HOUR = MBPS_PER_VIEWER * 3600 / 8 / 1024   # 0.622 GB

FREE_GB = 10 * 1024      # OCI Always Free outbound 가정치 (조사 확인 전)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    written = []

    # ① 시청자 수별 무료 한도 소진 시간 — 임계 곡선
    bars = []
    for n in [30, 100, 300, 500, 1000, 3000]:
        gb_h = n * GB_PER_VIEWER_HOUR
        hours = FREE_GB / gb_h
        color = GREEN if hours > 100 else (ORANGE if hours > 20 else RED)
        amount = f"{gb_h:.0f} GB/h" if gb_h < 1024 else f"{gb_h/1024:.2f} TB/h"
        bars.append((f"{n:,}명", hours, color, amount))
    hbar_chart(
        title="시청자 수와 무료 전송 한도 — 며칠 만에 소진되나",
        subtitle="LiveKit 실측(1×3,000 = 531 MBps)에서 도출 · 시청자당 1.42 Mbps · 무료 한도 10TB 가정",
        bars=bars, unit="시간", value_fmt="{:.0f}", x_max=650,
        note="500명을 넘으면 하루 2시간 방송으로 한 달을 못 버틴다. 여기가 임계 구역이다.",
        out_path=OUT / "08-egress-free-tier-exhaustion.svg",
    )
    written.append(OUT / "08-egress-free-tier-exhaustion.svg")

    # ② 채팅 vs 영상
    bars = []
    for n, rate, label in [(30, 5, "화상강의 30명"), (3000, 20, "라이브방송 3,000명")]:
        chat = 200 * n * rate * 3600 / 1024**3
        video = n * GB_PER_VIEWER_HOUR
        bars.append((f"{label} · 영상", video, RED, f"{video:.1f} GB/h"))
        bars.append((f"{label} · 채팅", chat, BLUE,
                     f"{chat:.2f} GB/h ({chat/video*100:.1f}%)"))
    hbar_chart(
        title="채팅과 영상의 대역폭 차이",
        subtitle="메시지 200B 기준 · 채팅은 계산값, 영상은 실측 기반 도출",
        bars=bars, unit=" GB/h", value_fmt="{:.1f}", x_max=2200,
        note="채팅은 영상의 0.5~2.2% 다. 이게 '채팅 서버는 되는데 영상 서버는 안 되는' 이유다.",
        out_path=OUT / "09-chat-vs-video-bandwidth.svg",
    )
    written.append(OUT / "09-chat-vs-video-bandwidth.svg")

    # ③ CDN 캐시 히트율에 따른 오리진 egress
    total = 3000 * GB_PER_VIEWER_HOUR
    bars = []
    for hit, label in [(0.0, "WebRTC — 캐시 불가"), (0.5, "HLS+CDN 히트 50%"),
                       (0.8, "HLS+CDN 히트 80%"), (0.95, "HLS+CDN 히트 95%")]:
        origin = total * (1 - hit)
        bars.append((label, origin,
                     RED if hit == 0 else (ORANGE if hit < 0.8 else BLUE),
                     f"무료 한도로 {FREE_GB/origin:.0f}시간"))
    hbar_chart(
        title="CDN 캐시 히트율이 오리진 전송량을 얼마나 줄이나",
        subtitle="시청자 3,000명 1시간 기준 · 총 전송량은 같고 오리진 부담만 달라진다",
        bars=bars, unit=" GB", value_fmt="{:.0f}", x_max=2200,
        note="WebRTC 는 stateful 이라 CDN 캐싱이 안 된다. 이게 방송을 HLS 로 빼는 진짜 이유다.",
        out_path=OUT / "10-cdn-cache-offload.svg",
    )
    written.append(OUT / "10-cdn-cache-offload.svg")

    for f in written:
        print(f"  {f}")


if __name__ == "__main__":
    main()
