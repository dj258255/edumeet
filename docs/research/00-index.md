# 사전 조사 종합 — 실시간 채팅 / 스트리밍 / 언어 선택

> 조사일 2026-08-22 · 네 갈래 병렬 조사 결과를 정리했다.
> **실행 계획은 [`docs/plan/01-chat-breaking-points.md`](../plan/01-chat-breaking-points.md)** 에 있다.
> HLS 는 [`02-hls-optional.md`](../plan/02-hls-optional.md) 로 분리했다.
> 전체 지도는 [`docs/README.md`](../README.md) 를 먼저 보라.
> 이 폴더는 **근거와 출처 아카이브**다.

## 문서 목록

| | 내용 |
|---|---|
| [01. 포트폴리오 전략](01-portfolio-strategy.md) | 면접관이 실제로 보는 것, 감점 요인, ADR, iMBC 공고 분석 |
| [02. WebSocket 내부](02-websocket-internals.md) | Spring Boot 3.5 기본값 함정, Netty 백프레셔, 소재 목록, k6 |
| [03. 팬아웃 / 메시징](03-fanout-messaging.md) | Redis·NATS·Kafka 비교, Discord·Slack·카카오톡 실제 구현 |
| [04. 언어 경계](04-language-boundary.md) | C/C++가 필수인 지점, Netty 실사용처, 국내 채용 실태 |
| [05. 스트리밍 / HLS](05-streaming-hls.md) | LiveKit Egress, 지연 측정 4종, ABR, 방송 용어 |

---

## 1. 세 가지 질문과 답

### Q1. "라이브 스트리밍 회사는 다 C/C++를 쓰던데, 언어를 바꿔야 하나?"

**아니다. 경계는 언어가 아니라 "패킷당 하는 일"이다.**

```
[1] 픽셀/샘플을 만지는가? (디코드·인코드·믹싱)
      → C/C++ + 손으로 쓴 SIMD 어셈블리. 예외 없음
[2] 커널/NIC 수준 초당 수천만 패킷?
      → C (eBPF/XDP/DPDK). 단 제어 데몬은 Go
[3] RTP 파싱·전달만? (SFU)
      → GC 언어로 충분. Go(LiveKit), JVM(Jitsi)
[4] 수십만 연결 + fan-out (채팅)
      → Go / JVM(Netty) / Erlang 전부 실증됨
[5] 시그널링·인증·API·DB
      → 생태계와 팀 역량
```

**가장 강력한 증거 — LiveKit이 자기 스택 안에 그은 경계**
```dockerfile
# SFU 본체 (패킷 포워딩)
CGO_ENABLED=0 go build -o livekit-server      ← C 링크 0

# Egress (녹화·트랜스코딩)
CGO_ENABLED=1 go build -o egress
FROM livekit/gstreamer:1.24.12-dev            ← C 라이브러리
```
같은 팀·같은 언어·같은 제품군인데 **"코덱을 만지느냐" 하나로 갈린다.**

**★ 그리고 카카오톡은 반대로 갔다.**
relay & session manager를 **C++ → Kotlin + Netty + ZGC**로 포팅했고,
이유가 *"파트내 인적 리소스 불균형: C++/JAVA"*, *"10년 후에도 유지보수가 가능할까"* 였다.
결과는 **코드라인 절반 감소, 가용 인력 3배, relay time 47ms → 42ms(오히려 개선)**.

> **"실시간 메시징은 C++이어야 한다"가 국내 최대 메신저에서 반증됐다.**

**edumeet은 [5]에 있고 [1]~[3]은 LiveKit에 위임했다.**
*"왜 Java죠?"* 의 답은 *"Java가 빨라서"* 가 아니라
**"이 서비스는 미디어 데이터 평면을 담당하지 않기 때문"** 이다.

---

### Q2. "저수준을 어디까지 파야 하나?"

> **저수준은 "구현"이 아니라 "설명"으로 증명하면 리스크 없이 같은 인상을 얻는다.**

**근거 — F-Lab의 "localhost:8080 함정"**
```
면접관: 데이터의 크기는요?
지원자: 약 5만 건 정도였습니다.
면접관: 5만 건 정도의 데이터라면 더 쉽고 좋은 방법이 있었을 것 같은데
```
> *"자신이 갖고 있는 대안에 대해서도 함께 이야기한 후, '학습 목적'에서 그런 시도들을 했다고
> **멋지게 말할 수 있는 지원자는 많이 보지 못했다**"*

**오버엔지니어링 자체보다, 그게 오버엔지니어링임을 본인이 모르는 상태가 감점이다.**

**그리고 Netty를 직접 쓸 이유가 없다**
- 30명 화상강의에서 **측정 가능한 차이가 안 난다**
- 손으로 만들어야 할 것 **11가지**, 절반만 해도 **2~3주**, 결과물은 Spring보다 기능이 적다
- 국내 **"Netty" 채용 공고 10건이 전부 금융/PG/IoT** — 스트리밍 회사 0건
- **이미 쓰고 있다** — 이 프로젝트 fat jar에 netty 4.1.122.Final 10개 모듈
  (`lettuce-core`, AWS SDK 경유)

> **"Netty를 쓸 것인가"가 아니라 "이미 쓰고 있는데 어디에 쓰이는지 아는가"가 질문이다.**

---

### Q3. "그럼 저수준 소재는 어디서 찾나?"

**★ Spring Boot 3.5 기본값 안에 있었다.**

```java
// WebSocketMessagingAutoConfiguration (Spring Boot 3.4+)
configureClientInboundChannel  → applicationTaskExecutor
configureClientOutboundChannel → applicationTaskExecutor

// TaskExecutionProperties.Pool
coreSize      = 8                    ← CPU 개수와 무관하게 고정
queueCapacity = Integer.MAX_VALUE    ← 큐가 무한이라 maxSize 가 영원히 발동 안 함
```

```java
// StandardWebSocketSession — 전송이 블로킹
getNativeSession().getBasicRemote().sendText(...)

// Tomcat Constants
DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;   // 20초
```

> **느린 클라이언트 1명이 아웃바운드 스레드 1개를 최대 20초 점유한다. 8명이면 채팅이 멈춘다.**
> 공식 문서 어디에도 "위험하다"고 안 쓰여 있고 한국어 자료도 거의 없다.

---

## 2. 소스를 읽어야 나온 발견 셋

| # | 발견 | 소스 |
|---|---|---|
| **1** | Spring Boot 3.4+가 WebSocket 채널 executor를 8스레드·무한 큐로 덮어쓴다 | `WebSocketMessagingAutoConfiguration`, `TaskExecutionProperties` |
| **2** | **LiveKit이 Apple HLS 스펙 8.11을 위반한다** — 라이브 창 5개 vs 스펙 6개 MUST | `livekit/egress` `pkg/pipeline/sink/segments.go` |
| **3** | **`hls.latency`의 문서 설명과 구현이 다르다** | `hls.js` `src/controller/latency-controller.ts` |

**전부 재현 가능하고, 셋 다 "소스를 읽었다"의 증거다.**

---

## 3. 통념 정정

| 통념 | 사실 |
|---|---|
| "Tomcat maxThreads 200이라 WebSocket 200개 한계" | **틀림.** NIO에서 유휴 WebSocket은 스레드를 안 잡음. 실제 상한은 `maxConnections`(8192)와 FD |
| "Java는 동시 연결 많으면 죽는다" | **틀림.** JVM에서 **1,000만 동시 연결** 검증됨(MigratoryData, CPU 50% 미만) |
| "Kafka 브로커는 Netty 기반" | **틀림.** raw `java.nio`. 3.x의 netty jar는 ZooKeeper transitive CVE 회피용 |
| "Akka는 Netty" | **틀림.** Netty 3 기반 classic remoting은 **2.8.0에서 코드 삭제**. 현재는 Artery |
| "Netty는 순수 Java" | **부분적으로 틀림.** `transport-native-epoll`은 38KB의 손으로 쓴 C + JNI |
| "가상 스레드로 WebSocket 연결 수가 늘어난다" | **전제가 틀림.** 이미 NIO라 유휴 연결이 스레드를 안 씀. 효과는 **느린 클라 스레드 고갈 해소** |
| Signal이 Cassandra를 쓴다 | **사실무근.** PostgreSQL → DynamoDB → FoundationDB |
| Discord 게이트웨이가 Rust로 전환됐다 | **사실무근.** Elixir 유지. Rust는 Read States 등 일부 |

---

## 4. 팬아웃 — Redis로 가는 근거

**① 채팅의 진실 원천은 브로커가 아니라 MySQL이다. 그래서 at-most-once로 충분하다.**

Redis 공식 Java/Lettuce 프로덕션 가이드:
> *"Pub/sub is at-most-once — pair it with durable state if you need replay.
> On reconnect, consumers reconcile by **reading the durable store**."*

Slack: *"every message sent in Slack is **persisted before** it's sent across the real-time
websocket stack."*

**② NATS Core도 at-most-once라 요구사항상 차이가 없다.**
게다가 **Centrifugo는 NATS 브로커를 쓰면 history/recovery를 못 쓴다** —
실시간 메시징 서버 관점에서 **NATS Core는 Redis보다 기능이 적다.**

**③ 규모가 남는다.** Centrifugo가 **Redis 1대로 50만 커넥션**을 처리하면서
**CPU 코어 1개의 60%** 만 쓴다.

**④ 임계점을 안다.**
```
redis/redis#2672 — 클러스터 일반 PUBLISH 는 모든 노드에 전파
  1KB / 10노드 / 1Gbit  →  12.5K RPS 한계
  5KB / 50노드          →     500 RPS

client-output-buffer-limit pubsub 32mb 8mb 60
  → 느린 구독자를 Redis 가 끊고 대기 메시지를 전부 버린다
```
**해결책은 Redis 7 Sharded Pub/Sub**(`SPUBLISH`), Lettuce가 지원.

**⑤ 같은 문제에 국내 두 회사가 정반대 결론을 냈고 이유가 공개돼 있다.**

| | LINE LIVE (2016) | 카카오엔터 (2022) |
|---|---|---|
| 브로커 | **Redis Cluster Pub/Sub** | **Kafka** |
| 규모 | 분당 1만+ 코멘트 | 동시접속 20만 |

**분당 1만이면 Redis Pub/Sub, 동시 20만이면 브로커, 동시 수백만이면 자체 라우팅.**

**⑥ 대형 서비스는 브로커를 아예 안 쓴다.**
Discord(자체 Manifold), Slack(자체 Java 티어), Twitch(자체 Go), 카카오톡(relay 풀메시),
LINE(notify + 클라 pull).

공통 패턴은 **룸 단위 어피니티 + consistent hashing** —
핵심은 *"브로커를 쓰지 말라"* 가 아니라
**"같은 방을 같은 곳에 모아 cross-node 팬아웃 자체를 없애라"** 다.

---

## 5. HLS — 범위와 근거

초기 조사에서는 **LiveKit Egress 경유로 하되 LL-HLS는 제외한다**고 판단했다.
구현 중 egress 를 실제로 띄워 보니 발표자 1명 방송에는 RoomComposite 합성이 과했다.
현재 구현은 직접 HLS delivery 다.

```
발표자 MediaRecorder 조각
  → HTTP chunk ingest
  → ffmpeg HLS muxer
  → nginx / hls.js delivery
```

LL-HLS 제외 결론은 유지한다. 현재 출력은 TS HLS 이고, LL-HLS 로 가려면
CMAF/fMP4, `EXT-X-PART`, Blocking Playlist Reload 등 패키저와 서버 동작을 바꿔야 한다.
클라이언트 쪽 기여 여지가 0이다.

**실증**: WINK 팀은 200ms 파트로 900ms를 달성했지만
**iPhone 14는 20분 후 끊김, iPhone 12는 5분 만에 파트 요청 중단**, CDN이 50~200ms를 더했다.

**지연 목표가 숫자로 확정됐다** (소스에서 기본값 확인)

| 설정 | hls.js `targetLatency` |
|---|---:|
| **LiveKit 기본 (4초)** | **12초** |
| `segment_duration: 2` | **6초** ← 목표 |

**왜 3배인가** — 사양 3단이 일치한다
```
RFC 8216 §6.3.3        "SHOULD NOT choose a segment that starts less than
                        three target durations from the end"
rfc8216bis HOLD-BACK   "absence implies three times the Target Duration"
hls.js                 liveSyncDurationCount 기본값 3
```

**왜 방송은 HLS로 빼야 하나** — LiveKit 공식 벤치마크(16 vCPU 단일 노드)

| | Pub | Sub | 아웃바운드 | CPU |
|---|---:|---:|---:|---:|
| 화상회의 | 150 | 150 | 93 MBps | **85%** |
| 라이브방송 | 1 | 3,000 | **531 MBps** | 92% |

*"Each room must fit within a single node."* — **룸 하나의 상한 = 노드 하나의 상한.**
16코어로 양방향 150명이면 CPU 85% → **화상교육 룸은 대략 코어당 10명**이 현실적 상한.
1:3000 방송은 **CPU가 아니라 대역폭(4.2Gbps)이 먼저 터진다.**

---

## 6. Java 버전 — 17 vs 21 vs 25

| | Java 17 (현재) | Java 21 | Java 25 (LTS) |
|---|---|---|---|
| Virtual Threads | ✗ | ✓ | ✓ |
| Generational ZGC (JEP 439) | ✗ | ✓ | ✓ |
| `synchronized` pinning | 해당 없음 | **⚠ 있음** | ✓ 해결 (JEP 491) |
| Compact Object Headers | ✗ | ✗ | ✓ 힙 22%↓ |

**⚠ 21의 위험이 우리 스택 얘기다.** Netflix가 **Java 21 + Spring Boot 3 + 임베디드 Tomcat**에서
프로덕션 장애를 냈다 — `synchronized` 블록 안 블로킹으로 ForkJoinPool의 모든 carrier에 pinned →
**JVM은 살아있는데 트래픽이 죽고 CLOSE_WAIT 소켓 수천 개.**

**포트폴리오 관점의 함정**: 21로 올려 virtual threads를 켜면
**§Q3의 문제가 설정 한 줄로 사라져서 서사가 없어진다.**

**결정: 17에서 풀고, 21로 올려 A/B.**
그리고 **21의 대가도 함께 기록한다** — `concurrency-limit` 기본 null로 인한 동시성 무제한, pinning.

---

## 7. 서사 프레이밍

```
✗ "라이브 스트리밍을 하고 싶어서 스트리밍을 만들었습니다"
✗ "Netty로 직접 만들었습니다"

✓ "SFU와 트랜스코딩은 LiveKit에 위임했습니다. LiveKit 자체가 SFU는 CGO_ENABLED=0
   순수 Go로 빌드하고 Egress만 GStreamer를 링크하는데, 그 경계가 '픽셀을 만지느냐'입니다.
   저희 서비스는 그 위 계층이라 Java가 맞다고 판단했습니다."

✓ "Spring 기본 구성으로 N명까지 측정했고, 여기서 아웃바운드 스레드가 병목이라
   확인해서 이 부분만 이렇게 해결했습니다."

✓ "처음엔 X가 필요할 거라고 가정하고 설계했는데, k6로 재보니 병목이 거기가 아니어서
   도입을 취소했습니다. 그 판단 기록을 남겨뒀습니다."
```

**"저수준을 팠다"가 아니라 "경계를 안다"가 목표다.**

### 이미 있는 기각 기록

| 검토했지만 안 씀 | 근거 |
|---|---|
| Spring Modulith | 순환 5개 중 4개가 JPA 양방향 |
| Toxiproxy 상시 도입 | 검출률 이미 6/6 |
| 서킷 브레이커 | 3초가 문제라는 근거 없음 |

> **F-Lab이 *"많이 보지 못했다"* 고 한 그 자리에 이게 들어간다.**
> **추가로 만들어야 할 것은 새 기술이 아니라 기각 기록이다.**

---

## 8. 조사 방법과 한계

- 네 갈래 병렬 조사(면접관 관점 / WebSocket 저수준 / 팬아웃·언어 / 스트리밍)를 돌렸고,
  세션 웹서치 예산 소진 후에는 **GitHub raw 소스·pom/gradle·공식 JEP·논문 PDF를 직접 fetch**해
  검증했다. 결과적으로 2차 블로그 요약보다 1차 자료 비중이 높다.
- 각 문서 말미에 **미확인 항목**을 명시했다. **확인 못 한 것을 지어내지 않았다.**
- 특히 다음은 **인용 금지**로 표시했다:
  - Virtual Threads vs goroutines 정량 벤치마크 (신뢰 가능한 1차 출처 없음)
  - YouTube / Netflix / Twitch의 실제 ABR 래더 (1차 출처 미확인)
  - Netflix ZGC 블로그의 구체 수치 (원문 403, 2차 요약만)
