# EduMeet 문서

## 지금 하는 일

> **"인원이 늘 때 어디서부터 무너지는가"를 단계별로 측정하고,
> 각 단계를 프레임워크 기본값·설정·구조로 해결한다.**

측정을 하려면 그 전에 **관측·배포·시크릿**이 서 있어야 했다. 그것도 이 저장소의 일부다.

| 축 | 계획 |
|---|---|
| 채팅 붕괴 | [`plan/01-chat-breaking-points.md`](plan/01-chat-breaking-points.md) |
| 오디오 방송 (라디오 + 자막) | [`plan/03-audio-broadcast.md`](plan/03-audio-broadcast.md) |
| HLS | [`plan/02-hls-optional.md`](plan/02-hls-optional.md) — 선택 |

---

## 결론 먼저

### 비용보다 가용성이 먼저 터진다

처음엔 *"시청자 500명이 임계 구역"* 이라고 적었다. **요금을 확인하고 뒤집혔다.**

```
OCI 무료 한도 10TB 를 넘기려면   시청자 7,400명 x 1시간
그 전에                          2 OCPU / 12GB VM 이 먼저 죽는다
```

OCI 의 무료 한도는 타 클라우드의 **50~100배**, 초과 요금은 약 **1/10** 이다.
**그래서 이 프로젝트의 주제는 비용 최적화가 아니라 가용성 한계 측정이다.**

→ [`ops/02-egress-cost-model.md`](ops/02-egress-cost-model.md)

### 무한 큐가 위험해지는 조건은 "빠른 발행" 이 아니라 "느린 소비" 다

*"기본 큐가 무한이니 빠르게 발행하면 OOM"* 으로 가정하고 부하를 걸었다. **안 죽었다.**

| | 큐 최대 | 결과 |
|---|---:|---|
| 빠른 소비자 (k6 루프백) | 525 | **4분 생존** |
| **느린 소비자** (Toxiproxy 5KB/s) | **1,077,906** | **84초에 OOM** |

**같은 부하, 같은 힙.** 소비 속도 하나만 달랐다.

→ [`performance/07-chat-unbounded-queue-oom.md`](performance/07-chat-unbounded-queue-oom.md)

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
| [`plan/03-audio-broadcast.md`](plan/03-audio-broadcast.md) | **오디오 라이브 방송** — 라디오를 "볼 수 있게" 만든다 |
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
| [`performance/07-chat-unbounded-queue-oom.md`](performance/07-chat-unbounded-queue-oom.md) | **무한 큐 OOM.** 큐 107만 → 상한 2만, 84초 OOM → 444초 무중단 |
| [`refactoring/01-remove-port-adapter.md`](refactoring/01-remove-port-adapter.md) | Port/Adapter 제거. 코드 38% 감소, 최적화 쿼리가 죽어 있었음 |

### 운영

| | |
|---|---|
| [`ops/01-cicd-and-deploy.md`](ops/01-cicd-and-deploy.md) | GitHub Actions + OCI(ARM64) 배포 · Flyway · Ansible |
| [`ops/02-egress-cost-model.md`](ops/02-egress-cost-model.md) | **전송 비용 모델** — 비용보다 가용성이 먼저 터진다 |
| [`ops/03-internal-api-contract.md`](ops/03-internal-api-contract.md) | 파이썬 AI 서버 ↔ 자바 규약 (`X-Internal-Token`) |
| [`ops/04-observability.md`](ops/04-observability.md) | Prometheus·Grafana. **설정만 있고 동작 안 하던 것들** |
| [`ops/06-seven-failures.md`](ops/06-seven-failures.md) | **배포가 일곱 번 실패했다** — 처음 보는 실패를 어떻게 좁혀 들어갔는가 |
| [`ops/05-secrets.md`](ops/05-secrets.md) | Ansible Vault. 무엇이 어디에 있고 **왜 히스토리를 다시 쓰지 않았는가** |

### 규칙

| | |
|---|---|
| [`team-rules.md`](team-rules.md) | 팀 규칙 |
| [`team-convention.md`](team-convention.md) | 개발 컨벤션 |

---

## 검토했지만 쓰지 않은 것

> **채택한 기술만큼 기각한 기술도 기록한다.**
>
> 다만 **"안 쓴 것" 과 "아직 안 쓴 것" 은 다른 판단**이라 나눠 적는다.
> 섞어 놓으면 *"이 사람은 분산 시스템을 안 한다"* 로 읽히는데,
> 실제로는 **언제 도입할지의 조건을 정해둔 것**이다.

### 쓰지 않는다 — 이 규모에서 정당화되지 않는다

| | 근거 |
|---|---|
| Spring Modulith | 순환 5개 중 4개가 JPA 양방향 연관. 제거하려면 도메인 분리를 뭉개야 함 |
| Toxiproxy 상시 도입 | 잠금 버그 검출률이 지연 없이도 6/6. 지연은 결과값만 고정 |
| 서킷 브레이커 | LiveKit 장애가 3초에 끝남. **그 3초가 문제라는 근거가 없음** |
| Netty 직접 구현 | 30명에서 측정 가능한 차이 없음. 손으로 만들 것 11가지, 2~3주 |
| **Kafka** | 채팅 전파에 **지연·운영 부담이 과함.** 그리고 #43 에서 확인했듯 **병목은 브로커가 아니라 아웃바운드 큐**였다 — Kafka 를 넣어도 그 큐는 그대로 있다 |
| NATS | Core 도 at-most-once 라 Redis 와 보장 수준이 같음. **채택 기준을 사전 등록해두고 안 넘김** |
| MongoDB (채팅) | Discord 는 MongoDB 에서 **떠났다**(1억 건 → Cassandra). 우리 병목은 DB 가 아니라 **fan-out** — #43 에서 직접 측정 |
| PostgreSQL 이전 | **통합할 대상이 없다**(DB 가 하나뿐). jsonb·파티셔닝을 쓰고 있지 않다 |
| LL-HLS | LiveKit egress 가 TS 세그먼트라 CMAF 가 아님. 부분 세그먼트 경로 없음 |
| SRT/RTMP 인제스트 | LiveKit Ingress 로 되지만 **측정할 질문이 없다.** 프로토콜을 늘려도 배우는 게 없다 |
| Rust/C++ 미디어 서버 | **프로토콜 바이트를 직접 만지지 않는다.** LiveKit(Go)이 SFU 를 한다 |
| `spring-dotenv` | 마지막 릴리스 2023-05. **모든 값을 `${ENV:기본값}` 으로 받으면 의존성 없이 같은 효과** |

### 아직 쓰지 않는다 — 조건이 오면 도입한다

| | 지금 안 쓰는 이유 | **도입 조건** |
|---|---|---|
| **Redis Pub/Sub** | 단일 인스턴스라 서버 간 전달이 필요 없다 | **인스턴스가 2대가 되는 순간** (붕괴 ⑤) |
| **비동기 배치 저장** | 발행 경로의 DB 쓰기가 브로드캐스트 측정을 가린다 | **다시보기 채팅을 붙일 때** (#61) |
| **WebVTT 자막** | 세그먼트 단위라 지연이 구조적으로 붙는다 | **HLS 배포를 붙일 때.** WebSocket 자막을 대체하는 게 아니라 **경로별 병렬** |
| **CDN** | OCI 는 10TB 무료라 **줄일 것이 없는 구간**이다 | 시청자가 수천 명대로 올라갈 때 |

### ★ Redis 는 지금 놀고 있다 — 그대로 둔다

```
도입 이유    인증 코드 TTL 저장소
현재         그 기능을 껐다(#55) → 아무것도 안 한다
걷어내나?    아니다. 붕괴 ⑤ 에서 Pub/Sub 으로 필요하다
```

**놀고 있다는 사실을 숨기려고 억지 용도를 찾지 않는다.**

실제로 한 번 그럴 뻔했다 — refresh token 을 Redis 로 옮기려 했다.
근거는 *"만료된 행을 지우는 배치가 없으니 테이블이 무한히 자란다"* 였는데, **틀렸다.**

```java
@UniqueConstraint(columnNames = "member_id")   // 회원당 한 행
existsByMemberId(...) -> save(existingToken)   // 갱신이지 추가가 아니다
```

행 수는 **로그인 횟수가 아니라 회원 수**에 비례한다. **배치가 필요한 상황이 아니었다.**

> **기술이 먼저 있고 그것을 정당화할 문제를 찾은 것**이다. 순서가 뒤집혀 있었다.
> Kafka·MongoDB·Netty 를 *"규모가 정당화하지 않는다"* 로 기각해놓고
> **Redis 에만 다른 잣대를 쓸 수는 없다.**

레이트 리밋도 후보였지만 뺐다 — **단일 인스턴스면 인메모리로 충분하다.**
Redis 가 필요해지는 건 그때도 **다중 인스턴스가 되는 순간**이다. 조건이 같다.
