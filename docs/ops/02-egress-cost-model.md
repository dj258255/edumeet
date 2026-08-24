# 전송 비용 모델 — 어디서 돈이 터지나

> 작성 2026-08-22 · 요금은 **2026-08 공식 가격 페이지 확인 기준**이다(§6).
> **요금은 자주 바뀐다.** 재확인: [OCI price list](https://www.oracle.com/cloud/price-list/)

## 0. 한 줄 결론

> **대역폭은 프로토콜이 아니라 "화질 × 시청자 수"로 결정된다.**
> WebRTC 와 HLS 의 차이는 **총 전송량이 아니라 그 부담을 누가 지느냐**다.

---

## 1. 기준점 — 유일한 실측치

**LiveKit 공식 벤치마크** (GCP `c2-standard-16`)

```
1 publisher × 3,000 subscriber  →  outbound 531 MBps
```

여기서 도출한다.

```
시청자당 대역폭     531 MB/s × 8 ÷ 3,000        = 1.42 Mbps
시청자당 시간당      1.42 Mbps × 3600 ÷ 8 ÷ 1024 = 0.622 GB
```

> **1.42 Mbps 는 720p 3 Mbps 보다 낮다.** LiveKit 의 adaptive stream 이
> 화면에 안 보이는 참가자의 구독을 이미 끊고 있기 때문이다.
> 즉 이 수치는 **최적화가 적용된 뒤의 실제값**이다.

**이 문서에서 실측은 위 한 줄뿐이고 나머지는 전부 계산이다.**

---

## 2. 임계 구역 — 시청자 수와 무료 한도

![무료 한도 소진](../performance/images/08-egress-free-tier-exhaustion.svg)

| 시청자 | 1시간 egress | 무료 10TB 소진까지 | 하루 2시간 방송 기준 |
|---:|---:|---:|---:|
| 30 | 19 GB | 549시간 | **274일** |
| 100 | 62 GB | 165시간 | 82일 |
| 300 | 187 GB | 55시간 | 27일 |
| **500** | **311 GB** | **33시간** | **16일** |
| 1,000 | 622 GB | 16시간 | 8일 |
| 3,000 | 1.82 TB | 5시간 | **3일** |
| 5,000 | 3.04 TB | 3시간 | 2일 |

> **500명이 임계 구역이다.** 여기를 넘으면 하루 2시간 방송으로 **한 달을 못 버틴다.**
>
> 그리고 **화상강의(30~100명) 규모에서는 전송 비용이 문제가 되지 않는다.**
> 274일이면 사실상 무제한이다. **비용 설계가 필요해지는 건 방송 쪽이다.**

---

## 3. 채팅은 영상의 2% 다

![채팅 vs 영상](../performance/images/09-chat-vs-video-bandwidth.svg)

메시지 200B 기준.

| 시나리오 | 영상 | 채팅 | 비율 |
|---|---:|---:|---:|
| 화상강의 30명 · 초당 5건 | 18.7 GB/h | **0.10 GB/h** | **0.54%** |
| 라이브방송 3,000명 · 초당 20건 | 1,867 GB/h | **40.2 GB/h** | **2.16%** |

> **이게 "채팅 서버는 집에서도 되는데 영상 서버는 안 되는" 이유의 정량적 답이다.**
>
> 그리고 **채팅의 병목은 대역폭이 아니라 CPU·스레드**다.
> 40 GB/h 는 평균 11 MB/s 로 회선에 부담이 안 되지만,
> **fan-out 은 초당 6만 회 쓰기**(3,000명 × 20건)라 [붕괴 ②](../plan/01-chat-breaking-points.md)에 걸린다.

---

## 4. ★ CDN 이 바꾸는 것 — 총량이 아니라 부담 주체

![CDN 오프로드](../performance/images/10-cdn-cache-offload.svg)

**시청자 3,000명이 1시간 시청하면 총 전송량은 1.82 TB 로 프로토콜과 무관하게 같다.**
달라지는 것은 **오리진이 그중 얼마를 지느냐**다.

| 방식 | 오리진 egress | 무료 한도로 |
|---|---:|---:|
| **WebRTC** (캐시 불가) | **1,867 GB** | 5시간 |
| HLS + CDN 히트율 50% | 933 GB | 11시간 |
| HLS + CDN 히트율 80% | 373 GB | 27시간 |
| **HLS + CDN 히트율 95%** | **93 GB** | **110시간** |
| HLS + CDN 히트율 99% | 19 GB | 549시간 |

**95% 히트면 오리진 부담이 20배 줄고 임계가 5시간 → 110시간이 된다.**

### 왜 WebRTC 는 캐시가 안 되나

```
WebRTC   커넥션마다 SRTP 키가 다르고 서버가 상태를 보유
         → 같은 바이트를 재사용할 수 없다 → CDN 캐싱 불가
HLS      세그먼트가 그냥 정적 파일
         → 같은 파일을 N명이 받는다 → CDN 이 흡수
```

> **이게 "왜 방송은 WebRTC 말고 HLS+CDN 이냐"의 진짜 답이다.**
> CPU 도 지연도 아니고 **오리진 대역폭이 먼저 터지기 때문**이다.

### 다만 라이브는 VOD 보다 히트율이 낮다

```
플레이리스트(.m3u8)   EXT-X-ALLOW-CACHE:NO · target duration 마다 갱신 → 거의 매번 오리진
세그먼트(.ts)         한 번 만들어지면 불변 → 잘 캐시된다
```

**VOD 는 같은 파일을 반복 요청하므로 히트율이 훨씬 높다.**
그래서 **CDN 의 효과는 라이브보다 VOD 에서 크다.**

---

## 5. VOD 는 저장보다 전송이 비싸다

```
저장   동영상 1GB          →  Storage 요금 (한 번)
전송   1GB × 시청 횟수      →  Egress 요금 (볼 때마다)
```

| 영상 크기 | 시청 1,000회 | 시청 10,000회 |
|---:|---:|---:|
| 100 MB | 100 GB | 1 TB |
| 500 MB | 500 GB | 5 TB |
| 1 GB | **1 TB** | **10 TB** |

**1GB 영상을 1만 명이 보면 무료 한도(10TB)를 정확히 소진한다.**

그래서 **Spring Boot 가 영상 바이트를 직접 내려주면 안 된다.**

```
✗  GET /videos/123  →  Spring Boot 가 byte[] 로 응답
✓  GET /videos/123  →  권한 확인 후 서명된 URL 발급
                       실제 전송은 Object Storage / CDN
```

애플리케이션 서버는 **"볼 자격이 있나"** 만 판단하고,
**수백 MB 전송은 스토리지·CDN 에 맡긴다.**

---

## 6. 클라우드·CDN 요금 (2026-08 공식 페이지 확인)

**§2 의 "10TB 무료" 가정은 확인됐다.** 그리고 확인 결과가 예상보다 중요했다.

| 클라우드 | 무료 한도(월) | 초과 요금 |
|---|---:|---:|
| **OCI** | **10 TB** | **$0.0085/GB** |
| AWS EC2 | 100 GB | $0.09/GB (첫 10TB) |
| GCP Standard Tier | 200 GiB | $0.085/GB |
| GCP Premium Tier | 1 GB | $0.12/GB |
| Azure (북미/EU) | 100 GB | $0.087/GB |

> **OCI 의 무료 한도는 타 클라우드의 50~100배, 초과 요금은 약 1/10 이다.**
> 같은 3,000명 1시간 방송(1.82 TB)이 AWS 에서는 약 $164, OCI 에서는 **$0** 이다.
> 출처: [OCI price list](https://www.oracle.com/cloud/price-list/), [Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)

Object Storage egress 와 Compute egress 는 **같은 요율·같은 무료 한도 풀을 공유**한다(별도 요율 없음).

### 🔴 Cloudflare 무료 CDN 은 비디오 서빙을 약관으로 제한한다

> *"Cloudflare reserves the right to disable or limit your access to or use of the CDN … if you use or are suspected of using the CDN without such Paid Services to **serve video** or a disproportionate percentage of pictures, audio files, or other large files."*
> — [Cloudflare Service-Specific Terms](https://www.cloudflare.com/service-specific-terms-application-services/), CDN (Free, Pro, or Business)

**"Cloudflare 무료 플랜으로 영상을 뿌리면 된다"는 설계는 약관 위반 리스크를 안는다.**
트래픽이 늘면 계정이 제한되고, 그 순간 **모든 시청자 트래픽이 오리진(OCI VM)으로 되돌아온다.**
정식으로 비디오를 서빙하려면 Cloudflare Stream 또는 별도 계약이 필요하다.

### CDN 선택지

| | 요금 |
|---|---|
| Cloudflare Free CDN | $0 — **단 비디오 약관 리스크** |
| Cloudflare Stream | 저장 $5/1,000분 + 전송 $1/1,000분 (대역폭 포함) |
| Bunny.net (EU/NA) | $0.01/GB |
| Bunny.net (아시아·오세아니아) | $0.03/GB |
| **OCI 자체 CDN** | **없음** — 서드파티를 붙여야 한다 |

### ★ 역설 — 우리 규모에선 CDN 이 오히려 돈이 더 나간다

720p(3 Mbps) 1시간, 시청자 50명 = 67.5 GB 기준:

| | 비용 |
|---|---:|
| OCI 직송 | **$0** (무료 한도 내) |
| Bunny.net | $0.68 |
| Cloudflare Stream | $3.00 (50명 × 60분) |

**CDN 은 "오리진 egress 를 줄이는 대신 CDN 요금을 낸다".**
OCI 는 egress 가 이미 10TB 까지 공짜라서, **줄일 것이 없는 구간에서는 CDN 이 순수 비용 추가**다.

> 앞선 §4 의 "CDN 이 오리진 부담을 20배 줄인다"는 여전히 참이다.
> 다만 **그 20배가 돈으로 의미를 갖는 건 10TB 를 넘긴 뒤**다.

### ★ LiveKit Cloud 로 냈다면 얼마인가

2026-08-25 기준 LiveKit Cloud Ship 플랜은 downstream data transfer 250GB 포함,
초과분 $0.12/GB 다. Recording/export 의 video transcode 는 포함분 이후
$0.02/min, audio-only 는 $0.005/min 이다.

환율은 2026-08-24 USD/KRW 1,382원으로 계산한다.

| 시나리오 | 전송량 | LiveKit downstream 비용<br />(포함분 무시) | 250GB 포함 후 초과 비용 | OCI 직송 |
|---|---:|---:|---:|---:|
| 50명 × 1시간 | 31GB | $3.73 ≈ **5,200원** | 0원 | 0원 |
| 500명 × 1시간 | 311GB | $37.32 ≈ **51,600원** | $7.32 ≈ **10,100원** | 0원 |
| 3,000명 × 1시간 | 1,867GB | $224.04 ≈ **309,600원** | $194.04 ≈ **268,200원** | 0원 |

같은 3,000명 1시간 방송을 LiveKit Cloud paid path 로 처리하면
downstream 만 **약 27만~31만원/시간** 구간이다. 반면 OCI 는 첫 10TB/month 까지
public internet egress 가 무료라, 이 규모의 단발 방송은 네트워크 청구액이 0원이다.

단, 이 문장은 **"실제로 청구서를 줄였다"가 아니다.**
우리 서비스는 유료 LiveKit Cloud 로 운영하지 않았다. 정확한 표현은
**"같은 트래픽을 유료 LiveKit Cloud downstream 으로 처리했을 때 발생했을 비용을
직접 HLS + OCI 무료 egress 로 회피했다"** 이다.

그리고 transcode 비용은 bandwidth 에 비하면 작다.

| 항목 | 단가 | 1시간 |
|---|---:|---:|
| LiveKit video transcode | $0.02/min | $1.20 ≈ **1,660원** |
| LiveKit audio-only transcode | $0.005/min | $0.30 ≈ **410원** |

그래서 방송 비용의 큰 축은 **인코딩 분당 과금보다 viewer-hour × GB 단가**다.
우리가 LiveKit RoomComposite Egress 를 걷어낸 이유도 "분당 1,660원을 아껴서"가
아니라, 2 OCPU 서버에서 합성 비용 때문에 비디오 방송 자체가 막혔기 때문이다.

출처:

- LiveKit pricing: <https://livekit.com/pricing>
- LiveKit quotas and limits: <https://docs.livekit.io/deploy/admin/quotas-and-limits/>
- OCI public internet egress 10TB/month free: <https://www.oracle.com/cloud/networking/virtual-cloud-network/pricing/>
- 환율 가정: <https://kr.investing.com/currencies/usd-krw-historical-data>

---

## 7. ★ 결론 반전 — 비용보다 가용성이 먼저 터진다

무료 한도 10TB 를 실제로 넘기려면:

```
우리 실측(1.42 Mbps)   10,240 GB ÷ 0.622 GB/h  =  약 16,400 시청자-시간
720p (3 Mbps) 기준     10,000 GB ÷ 1.35 GB/h   =  약  7,400 시청자-시간
```

**시청자 7,400명이 1시간을 봐야 무료 한도를 소진한다.**
그런데 그 전에 **2 OCPU / 12 GB VM 이 먼저 죽는다.**

```
1위  VM 자체의 한계 (CPU · WebSocket 커넥션 · NIC 대역폭)   ← 여기가 먼저 터진다
2위  Cloudflare 무료 CDN 비디오 약관 → 차단 리스크
3위  라이브 HLS 의 낮은 캐시 히트율 → CDN 을 붙여도 기대만큼 안 줄어든다
──────────── 여기까지 오는 동안 egress 요금은 계속 $0 ────────────
4위  egress 요금 (시청자 7,400명 × 1시간 이상)
```

> **이것이 이 프로젝트의 방향을 정한다.**
> 주제는 **비용 최적화가 아니라 가용성 한계 측정**이다.
> [`plan/01-chat-breaking-points.md`](../plan/01-chat-breaking-points.md) 의 붕괴 5단계가 정확히 그 작업이다.

**화상강의(30~100명)·채팅은 비용도 가용성도 문제가 아니다.**
문제가 시작되는 건 **라이브 방송 + 다중 인스턴스** 구간이다.

---

## 8. 국내 맥락 — 왜 이게 남의 일이 아닌가

한국은 **발신자 종량제(상호접속고시 2016)** 라 CP 가 ISP 에 트래픽 종량 요금을 낸다.
미국·EU 는 망 중립성이라 이 구조가 아니다.

그래서 국내 플랫폼들이 **그리드 딜리버리(P2P CDN)** 로 갔다.

| | |
|---|---|
| 아프리카TV(SOOP) | 2007년부터 고화질 시청에 적용. 보도 기준 망사용료 **연 900억 → 150억** |
| 치지직 | **2024년 6월** 도입 |
| 트위치 | *"국내 망사용료가 다른 나라보다 높다"* → **한국 철수** |

**시청자 PC 가 받은 세그먼트를 다른 시청자에게 릴레이해 오리진 부담을 줄이는 방식**이다.
WebRTC mesh 가 아니라 **배포 계층의 최적화**다.

> **우리 규모에선 그리드가 무의미하다**(트래픽이 없다).
> 다만 **"대역폭이 곧 돈"이라는 제약이 아키텍처를 결정한다**는 것 자체가 이 문서의 요지다.

### 가정용 회선은 왜 대안이 아닌가

| | |
|---|---|
| 약관 | 가정용 인터넷은 통상 **영리 목적 서버 운영 금지** 조항이 있다 |
| 업로드 | 국내 기가인터넷은 **비대칭**이 많다 (다운 500Mbps / 업 10~30Mbps 사례) |
| 고정 IP | 가정용은 미제공 — 비즈니스 요금제 전환 필요 |

시청자 50명 × 3 Mbps = **150 Mbps 업로드**가 지속 발생한다.
**업로드 대역폭만으로도 가정용 회선은 라이브 오리진이 될 수 없다.**

> 이 항목은 검색 요약 기반이며 **ISP 약관 원문은 미확인**이다.

자세한 근거: [`research/03-fanout-messaging.md`](../research/03-fanout-messaging.md),
[`research/05-streaming-hls.md`](../research/05-streaming-hls.md)

---

## 9. 재현

```bash
python3 scripts/make_cost_chart.py
```

수치를 손으로 옮기지 않는다. 실측 기준점 하나(`531 MBps`)만 바꾸면 전부 다시 계산된다.

---

## 10. 미확인 / 한계

- **단위 규약** — 본 문서의 GB 는 **GiB(÷1024)** 기준이다. 클라우드 청구는 보통 **십진 GB(÷1000)** 라
  실제 청구량은 위 표보다 **약 7% 많다**. 임계 구역 판단에는 영향 없다.
- Always Free 계정과 Pay-as-you-go 계정의 10TB 정책이 **완전히 동일한지** — 정황상 일치, 공식 명문 미확인
- OCI 리전 간(inter-region) 전송의 별도 요율 — 가격표에서 분리 항목 미발견
- Cloudflare R2 의 요청(Class A/B) 과금이 **라이브 HLS 에서 무료 구간을 넘는지** — R2 가격표 직접 미확인
- 라이브 HLS 캐시 오프로드율 **실측 사례** — 1차 출처 미발견. §4 의 95% 는 업계 통설(대형 이벤트 전제)
- 가정용 회선 ISP 약관 원문 (KT 외 SKB/LGU+ 미확인)
