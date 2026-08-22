# EduMeet 문서

## 지금 하는 일

> **"실시간 채팅에서 인원이 늘 때 어디서부터 무너지는가"를 5단계로 측정하고,
> 각 단계를 프레임워크 기본값·설정·구조로 해결한다.**

계획: [`plan/01-chat-breaking-points.md`](plan/01-chat-breaking-points.md)

---

## 결론 먼저

### 언어를 바꾸지 않는다

경계는 언어가 아니라 **"패킷당 하는 일"** 이다.
가장 강한 증거는 **LiveKit이 자기 스택 안에 그은 선**이다.

```dockerfile
# SFU 본체 (패킷 포워딩)
CGO_ENABLED=0 go build -o livekit-server      ← C 링크 0
# Egress (녹화·트랜스코딩)
CGO_ENABLED=1 + FROM livekit/gstreamer        ← C 라이브러리
```

같은 팀·같은 언어인데 **"코덱을 만지느냐" 하나로 갈린다.**
그리고 **카카오톡은 채팅 relay를 C++ → Kotlin + Netty + ZGC로 옮겼다**
(relay time 47ms → 42ms, 가용 인력 3배).

**EduMeet은 미디어 데이터 평면을 담당하지 않는다. 그래서 Java가 맞다.**

### 저수준은 구현이 아니라 설명으로 증명한다

**Netty를 직접 쓰지 않는다.** 30명 방에서 측정 가능한 차이가 없고,
손으로 만들 것이 11가지이며 2~3주가 든다.
국내 "Netty" 채용 공고 10건은 전부 금융/PG/IoT다.

**그리고 이미 쓰고 있다** — fat jar에 netty 10개 모듈(`lettuce-core`, AWS SDK 경유).

> **"Netty를 쓸 것인가"가 아니라 "이미 쓰고 있는데 어디에 쓰이는지 아는가"가 질문이다.**

### 저수준 소재는 Spring 기본값 안에 있었다

```java
// Spring Boot 3.4+ 가 WebSocket 채널 executor 를 덮어쓴다
coreSize      = 8                    // CPU 개수와 무관하게 고정
queueCapacity = Integer.MAX_VALUE    // 큐가 무한이라 maxSize 가 영원히 발동 안 함

// 전송은 블로킹, Tomcat 타임아웃 20초
DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;
```

**느린 클라이언트 1명이 아웃바운드 스레드 1개를 최대 20초 점유한다.**
공식 문서 어디에도 "위험하다"고 안 쓰여 있고 한국어 자료도 거의 없다.

### 검증된 붕괴 순서 (6단계)

```
① 아웃바운드 스레드 고갈       느린 클라 8명 (인원과 약한 상관)
② fan-out CPU                  1,000명 체감(평균 1.2s) · 3,000명 붕괴(평균 18s + 유실)
③ OOM — 두 갈래
     (a) 백로그형              발행률 의존
     (b) 유휴 연결 heap 점유형  연결당 ≈0.084MB · 발행률 무관, 순수 인원수
④ Tomcat maxConnections 8192   실측으로 확인된 하드월
⑤ 단일 인스턴스 한계           SimpleBroker 클러스터링 불가 → 수신률 50%
⑥ Redis Pub/Sub                훨씬 나중 (미검증)
```

**"인원"과 "부하"는 다른 축이고 둘 다 따로 재야 한다.**

```
인원 축   →  ③(b) 유휴 연결 heap  →  ④ 8192 하드월  →  ⑤ 단일 인스턴스
부하 축   →  ① 느린 클라 8명      →  ② fan-out CPU  →  ③(a) 백로그 OOM
```

---

## 문서 지도

### 계획 — 무엇을 할지

| | |
|---|---|
| [`plan/01-chat-breaking-points.md`](plan/01-chat-breaking-points.md) | **메인.** 채팅 붕괴 5단계 |
| [`plan/02-hls-optional.md`](plan/02-hls-optional.md) | HLS — 시간이 남으면 |

### 조사 — 왜 그렇게 판단했는지

| | |
|---|---|
| [`research/00-index.md`](research/00-index.md) | 조사 종합 요약 |
| [`research/01-portfolio-strategy.md`](research/01-portfolio-strategy.md) | 면접관 관점, ADR, iMBC 공고 |
| [`research/02-websocket-internals.md`](research/02-websocket-internals.md) | Spring 기본값 함정, Netty 백프레셔 |
| [`research/03-fanout-messaging.md`](research/03-fanout-messaging.md) | Redis·NATS·Kafka, Discord·Slack·카카오톡 |
| [`research/04-language-boundary.md`](research/04-language-boundary.md) | C/C++ 경계, 국내 채용 실태 |
| [`research/05-streaming-hls.md`](research/05-streaming-hls.md) | LiveKit Egress, 지연 측정, 방송 용어 |

### 측정 기록 — 이미 한 것

| | 결과 |
|---|---|
| [`performance/01-assignment-list-n-plus-one.md`](performance/01-assignment-list-n-plus-one.md) | N+1 제거, 쿼리 83 → 5 (H2) |
| [`performance/02-session-capacity-concurrency.md`](performance/02-session-capacity-concurrency.md) | 비관적 잠금으로 정원 초과 차단 (H2) |
| [`performance/03-mysql-load-test.md`](performance/03-mysql-load-test.md) | **MySQL + k6.** SQL 16.7배↓, 처리량 2.06배, p95 48%↓ |
| [`performance/04-session-capacity-mysql.md`](performance/04-session-capacity-mysql.md) | InnoDB 재검증. 잠금 없으면 150명 중 34명 입장(정원 30) |
| [`performance/05-fault-injection.md`](performance/05-fault-injection.md) | Toxiproxy. 무응답 시 3초 실패 vs 30초에도 안 돌아옴 |
| [`performance/06-lock-determinism.md`](performance/06-lock-determinism.md) | **가설 검증 후 미도입.** 지연이 검출률을 안 올림 |

### 운영

| | |
|---|---|
| [`ops/01-cicd-and-deploy.md`](ops/01-cicd-and-deploy.md) | GitHub Actions + OCI(ARM64) 배포 |
| [`ops/02-egress-cost-model.md`](ops/02-egress-cost-model.md) | **전송 비용 모델** — OCI 는 10TB 무료라 **비용보다 가용성이 먼저 터진다** |

### 규칙

| | |
|---|---|
| [`team-rules.md`](team-rules.md) | 팀 규칙 |
| [`team-convention.md`](team-convention.md) | 개발 컨벤션 |

---

## 검토했지만 쓰지 않은 것

> **채택한 기술만큼 기각한 기술도 기록한다.**

| | 근거 |
|---|---|
| Spring Modulith | 순환 5개 중 4개가 JPA 양방향 연관. 제거하려면 도메인 분리를 뭉개야 함 |
| Toxiproxy 상시 도입 | 잠금 버그 검출률이 지연 없이도 6/6. 지연은 결과값만 고정 |
| 서킷 브레이커 | LiveKit 장애가 3초에 끝남. 그 3초가 문제라는 근거가 없음 |
| Netty 직접 구현 | 30명에서 측정 가능한 차이 없음. 11가지를 손으로, 2~3주 |
| NATS | Core도 at-most-once라 Redis와 보장 수준이 같음. 채택 기준은 사전 등록 |
| Kafka | 채팅 전파에는 지연·운영 부담이 과함 |
| LL-HLS | LiveKit egress가 TS 세그먼트라 CMAF가 아님. 부분 세그먼트 경로 없음 |
