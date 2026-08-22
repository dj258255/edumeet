# 03. 채팅 팬아웃 — Redis / NATS / Kafka와 실서비스 사례

> 조사일 2026-08-22 · 1차 자료(공식 문서·소스·컨퍼런스 원문) 우선

## 0. 종합 비교표

| 옵션 | 전달 보장 | 지연 | 팬아웃 비용 | 운영 부담 | 언제 / 언제 안 |
|---|---|---|---|---|---|
| **Redis Pub/Sub** | **at-most-once** | tail p99.99 **1.2~1.5ms** (1~5KB) | `PUBLISH` = **O(N+M)** | **매우 낮음** | 이미 Redis 있고 원본이 DB에 있는 채팅 → 적합. 브로커가 유일한 전달 보장이어야 하면 부적합 |
| **Redis Streams** | **at-least-once** (PEL+XACK) | Pub/Sub보다 느림 | 컨슈머 그룹은 **로드밸런싱** | 낮음~중간 | 재생·이력 필요 시. **브로드캐스트에는 부적합** |
| **NATS Core** | **at-most-once** | request-reply 평균 **50.87µs**, 1pub/5sub 16B에서 **5,730,851 msgs/s** | subject **수백만 개 오버헤드 사실상 0** | 중간 | subject 계층 라우팅 필요. Java 통합은 약함 |
| **NATS JetStream** | **at-least-once** + `Nats-Msg-Id` 중복제거(기본 2분) | p99 **3.2ms** (3자 벤치) | RAFT 복제 비용 | 중간~높음 | 이력·재생 필요하면서 Kafka는 과할 때 |
| **Kafka** | at-most/least/exactly-once | producer **15~30ms**, e2e p99 **12.5ms** | 파티션 단위. **토픽 1,000개 초과 시 처리량 하락** | **높음** | 이벤트 소싱·감사 로그. **방당 토픽 = 안티패턴** |
| **RabbitMQ** | at-least-once | 소형 다량은 느림, **1MB 대형은 14~126배 우수** | STOMP `/topic` 구독마다 **auto-delete 큐 1개 생성** | 중간~높음 | 복잡 라우팅 + 유실 불가 |
| **Hazelcast/Ignite** | ITopic 베스트에포트 / Reliable Topic은 Ringbuffer | 미확인 | 클러스터 전체 브로드캐스트 | 중간 | 이미 그리드가 있을 때만 |
| **Spring StompBrokerRelay** | 브로커에 위임 | 브로커 + 릴레이 홉 | RabbitMQ와 동일 | 중간 | STOMP 유지하며 확장. `/user/**` 별도 설정 필수 |
| **직접 인스턴스 간 통신** | 직접 구현 | 홉 1회, 최소 | O(인스턴스 수) 풀메시 | **가장 높음** | 초대형 규모의 마지막 수단 |

> **지연 수치 주의**: Redis/NATS/Kafka/RabbitMQ tail latency는 bravenewgeek.com
> (2016, m4.xlarge) 값이고, 저자 본인이 *"interpret these benchmark results with a critical eye"*
> 라며 coordinated omission 문제를 경고했다. **면접에서 인용할 때 출처와 연식을 붙일 것.**

---

## 1. Redis Pub/Sub

### 1-1. 전달 보장 (공식 원문)

> *"Redis' Pub/Sub exhibits **at-most-once** message delivery semantics. ... If the subscriber
> is unable to handle the message (for example, due to an error or a network disconnect)
> **the message is forever lost**."*
> — https://redis.io/docs/latest/develop/pubsub/

### 1-2. ★ `client-output-buffer-limit`으로 인한 강제 종료

```
client-output-buffer-limit pubsub 32mb 8mb 60
  hard 32MB → 즉시 연결 종료
  soft  8MB가 60초 연속 유지 → 연결 종료
```

- 원인: Pub/Sub은 push 기반 → **구독자 처리 속도 < 발행 속도면 출력 버퍼가 무한 증가**
- 결과: *"When [Redis] closes a connection because the buffer limit is exceeded,
  **all pending messages for that subscriber are lost**."*
- **증상: 애플리케이션 로그의 원인 불명 잦은 구독자 재접속**

> **실무 함의**: Spring 앱에서 리스너가 무겁거나(JDBC 쓰기, 동기 HTTP) 인스턴스가 GC로 멈추면
> → 버퍼 증가 → Redis가 그 인스턴스를 끊음 → **그 인스턴스에 붙은 전체 클라이언트가
> 조용히 메시지를 놓친다. 이게 Redis Pub/Sub 채팅의 실제 1급 장애 시나리오다.**

### 1-3. 클러스터 확장의 구조적 한계 (redis/redis#2672)

일반 `PUBLISH`는 클러스터 버스로 **모든 노드에 브로드캐스트**된다.

```
1KB 메시지 · 10노드 · 1Gbit/s  →  12.5K RPS 제한
5KB 메시지 · 50노드            →     500 RPS
```

**노드를 늘릴수록 publish가 비싸진다. 스케일이 반대 방향으로 간다.**

### 1-4. 해결책 — Redis 7 Sharded Pub/Sub

`SPUBLISH` / `SSUBSCRIBE` / `SUNSUBSCRIBE`.
채널을 키와 같은 알고리즘으로 슬롯에 해싱 → **샤드 내부로만 전파**.

> *"the amount of data passing through the cluster bus is limited in comparison to
> global Pub/Sub where each message propagates to each node"*

**제약**: 한 번의 `SSUBSCRIBE` 호출의 모든 채널이 **같은 슬롯**이어야 한다.
**Java**: Lettuce가 `spublish()` / `ssubscribe()`를 클러스터 커넥션에 노출.
페일오버 시 재구독 이슈 보고 있음(lettuce#3213).

### 1-5. ★ Redis 공식 Java/Lettuce 프로덕션 가이드

https://redis.io/docs/latest/develop/use-cases/pub-sub/java-lettuce/ 의 "Production usage":

1. **"Pub/sub is at-most-once — pair it with durable state if you need replay"**
   원본을 primary store에 쓰고 나서 `PUBLISH`. 재접속 시 컨슈머는
   **놓친 메시지를 기다리지 말고 durable store에서 재조회**
2. **구독자마다 별도 `StatefulRedisPubSubConnection`**
   Lettuce는 `SUBSCRIBE` 후 커넥션을 subscribe-only 모드로 전환 → 공유 금지
3. **리스너 콜백에서 무거운 작업 금지**
   메시지는 **Netty 이벤트 루프 스레드**에서 도착. 블로킹하면 I/O 스레드가 묶이고
   같은 소켓의 다음 메시지가 대기
4. **콜론 계층 네이밍** (`chat:room:123`).
   단 *"Glob patterns are evaluated for every published message"*
   → 핫패스에서 `*:*:*` 같은 다중 와일드카드 금지
5. 클러스터면 sharded pub/sub 사용

### 1-6. 실제 운영 사례 — 장난감이 아니다

- **Centrifugo** (Go 실시간 메시징 서버): Redis 엔진이 노드 간 통신을 Redis Pub/Sub으로 수행.
  > *"one deployment served up to **500k connections** with 10 Centrifugo node pods and only
  > **one Redis instance** which consumed only **60% of a single processor core**"*
- **Socket.IO Redis Adapter** (Node 생태계 사실상 표준)
- **LINE LIVE** (2016): Java + Akka Actor + **Redis Cluster Pub/Sub** + MySQL.
  100+ 인스턴스, 피크 분당 1만+ 코멘트

### 1-7. at-most-once를 메우는 표준 패턴 (Centrifugo recovery)

> *"PUB/SUB brokers deliver at-most-once, so a message can be dropped without the connection
> noticing."* → 채널마다 **offset(단조 증가 uint64) + epoch(스트림 세대 식별자)** 를 유지.
> 재접속 시 클라이언트가 마지막 offset/epoch를 제시 → 갭이 이력에 남아 있으면 `recovered: true`,
> 아니면 `recovered: false`로 **앱 DB에서 다시 읽으라고 알림**.
>
> *"**History is a cache, not your source of truth**… your application database stays the
> source of truth, and history is the fast shortcut that saves you from hitting it on every reconnect."*

### 1-8. 결론

> Redis Pub/Sub은 **"팬아웃 전용 신호선"** 으로 쓸 때 정당하다.
> 원본은 MySQL, 팬아웃은 Redis, 재접속 복구는 `lastMessageId` 기반 조회.
> **브로커가 유일한 진실 원천이 되는 순간 부적합해진다.**

---

## 2. Redis Streams

| | Pub/Sub | Streams |
|---|---|---|
| 영속성 | 없음 | append-only 로그 |
| 팬아웃 | 모든 구독자 수신 | 독립 컨슈머는 전부 / **컨슈머 그룹은 로드밸런싱** |
| 이력 | 없음 | 전체 조회·재생 |
| ACK | 없음 | 있음 (at-least-once) |

- `XREADGROUP` → PEL 등록 → `XACK`. 컨슈머 사망 시 `XCLAIM`/`XAUTOCLAIM`으로 인계
- 메모리: `XADD ... MAXLEN ~ 10000`으로 **쓰기와 동시에 트리밍**이 표준.
  `~`(근사)는 radix-tree 매크로 노드 단위 삭제가 훨씬 싸기 때문
- **함정**: `MAXLEN ~`의 `LIMIT` 기본값 때문에 쓰기 폭주 시 단일 `XADD`가 임계치까지 못 줄일 수 있음.
  컨슈머 그룹 lag 무시하고 트리밍하면 미처리 엔트리가 사라짐

### 채팅 팬아웃에 부적합한 이유

컨슈머 그룹은 **"일감 분배"용**이다. 채팅은 **모든 앱 인스턴스가 같은 메시지를 받아야** 하므로
인스턴스마다 별도 그룹을 만들거나 `XREAD`를 써야 하고, 그러면 **방 수 × 인스턴스 수**만큼
블로킹 리드가 필요하다. **Pub/Sub 대비 이득 없이 복잡도만 올라간다.**

**쓸 만한 지점**: 팬아웃이 아니라 **"메시지 저장 + 최근 N건 캐시 + 재접속 복구"**.

---

## 3. NATS

### 3-1. Core NATS

> *"A message reaches every interested subscriber that's connected at the moment of publish,
> **at most once**. If a subscriber is offline, restarting, or not subscribed yet, it never sees that message."*

- **Subject 계층**: `.` 구분. `*`=단일 토큰, `>`=다중 토큰(맨 끝만). 권장 ~16 토큰 / 256자
- **핵심 강점**: *"Creating new subjects has virtually no overhead — NATS efficiently handles
  **millions of unique subjects**"*, 라우팅 테이블은 **활성 구독자가 있는 subject만** RAM 유지,
  **trie 기반 와일드카드 매칭**
- 성능(공식): *"millions of messages per second with sub-millisecond latencies"*, 서버 메모리 **20MB 미만**
- 실측(공식 CLI 벤치): 10M msgs / 16B / 1 pub / 5 sub → **5,730,851 msgs/s (~87.45 MB/s)**,
  request-reply 평균 **50.87µs**

### 3-2. JetStream

- **at-least-once**, 재시작을 견디고 재생 가능
- **exactly-once의 실체**: `Nats-Msg-Id` 헤더 기반 **서버 사이드 중복제거 윈도우**(기본 2분)
  + durable consumer의 명시적 ack. **"중복 효과 없음"이지 마법이 아니다.**
  윈도우가 크면 dedup 맵이 GB로 자람(엔트리당 약 130~150 bytes)

### 3-3. ★ 실제 채팅 도입 사례 — 여기가 약점이다

- **NATS 공식 어답터 목록에 채팅/메신저 회사가 없다**:
  Baidu, Capital One, Cloud Foundry, Comcast, Ericsson, GE, HTC, Netlify, Samsung, VMware —
  **인프라·IoT·클라우드 중심**
- **Centrifugo가 NATS를 지원하지만 등급이 낮다**:
  > *"Nats integration works only for **unreliable at most once PUB/SUB**"*,
  > **history/recovery 기능 사용 불가**, 와일드카드 구독 기본 비활성

> **즉 "NATS = at-most-once + 이력 없음"이라, 실시간 메시징 서버 관점에서는
> Redis보다 기능이 적다.** 이력·복구를 원하면 JetStream까지 가야 하고,
> 그러면 운영 부담이 Kafka 쪽으로 이동한다.

### 3-4. Java 생태계 성숙도

`nats.java`(jnats)는 Maven Central에 있고 활발히 유지되지만,
공식 `nats-io/spring-nats`(Spring Cloud Stream 바인더)는 문서가 오래되어 있고
**Spring Boot 3.5 자동설정은 직접 붙여야 한다.** Spring Data Redis 수준의 1급 통합은 없다.

### 3-5. NATS가 정당해지는 조건

1. `org.{id}.room.{id}.event.{type}` 같은 **subject 계층**이 요구사항이고 subject가 수만~수백만 개
   (Redis `PSUBSCRIBE`는 **`PUBLISH`마다 전체 패턴 M개를 평가**하므로 패턴이 많아지면 선형 증가)
2. **멀티 리전 / 엣지** 토폴로지 (gateway·supercluster·leaf node)
3. 리소스 극단 제한 환경 (단일 바이너리, 메모리 20MB 미만)
4. **request-reply**가 팬아웃만큼 중요 (평균 50.87µs)
5. queue group 분배 + 브로드캐스트를 **한 시스템**에서
6. 이력·재생이 필요한데 Kafka는 과할 때 → JetStream

---

## 4. Kafka

### 4-1. 왜 실시간 채팅 팬아웃에 과한가

1. **설계 목표가 다름** — 배칭 + 순차 쓰기 + zero-copy 읽기 →
   **처리량을 위해 건당 지연을 의도적으로 희생.**
   producer latency **15~30ms** vs Redis Pub/Sub **<1ms**
2. **토픽/파티션 폭발** — 방 하나당 토픽은 안티패턴.
   *"throughput does still drop off when there are more than **1,000 topics**"*
3. **팬아웃 모델 불일치** — 컨슈머 그룹은 파티션을 나눠 갖는 구조.
   "모든 인스턴스가 모든 메시지 수신"은 인스턴스마다 별도 `group.id`가 필요
4. **운영 부담** — 브로커 클러스터, 파티션 재배치, 리밸런싱, 리텐션, lag 모니터링

### 4-2. ★ 그런데 적합한 경우 — 카카오엔터 (2022)

MMA 2022 생중계(동시접속 20만 목표, 송수신 max 1,000ms)에서
**Redis Pub/Sub을 명시적으로 탈락시키고 Kafka를 채택**했다.

**Redis Pub/Sub 탈락 이유 (원문 요지)**
1. Redis Cluster에서 publish 시 **모든 노드에 전파** → 노드를 늘릴수록 느려짐
   (당시 GCP Memorystore가 Redis 6.x만 지원해 Sharded Pub/Sub 사용 불가)
2. 특정 채널 발행 시 **모든 subscriber 순회** → 구독자 수 비례 선형 지연
3. 메시지 휘발성 + 전/후처리 파이프라인 자체 구현 필요

**Cloud Pub/Sub vs Kafka 실측**

| | Cloud Pub/Sub | Kafka |
|---|---:|---:|
| p90 | 45~180ms | **30~65ms** |
| p99 | 500~1,000ms | **60~100ms** |

**최종 스택**: Kotlin + Spring Boot + Coroutine + Kafka + Kafka Streams + Redis(캐시) + MongoDB(영구) + ES(지표)

**기술 선택 이유 (원문)**

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | **Kotlin** | Java/Go/Erlang 비교. Go·Erlang은 러닝커브 리스크. **Coroutine**이 결정타 |
| 프레임워크 | **Spring Boot** | Ktor 검토했으나 프로덕션 사례/트러블슈팅 문서 부족 |
| 전송 | **WebSocket** | HTTP Polling(YouTube LIVE 방식)은 폴링 주기 지연 + 화면 불일치로 탈락 |
| 룸 배치 | **룸을 분산된 N대에 배치** | 룸-서버 고정은 ①룸 정보 관리 부담 ②트래픽 편차로 리소스 불균형 → 탈락. **단 이 선택 때문에 브로커가 필수가 됨** |
| 금칙어 | **Trie** (`PatriciaTrie`) | 50만+ 사전. 대다수 메시지가 미포함이라 미스 시 즉시 종료 가능한 Trie가 유리 |
| 로드밸런싱 | **Consistent Hashing** | GCP 기본 Round Robin이 WebSocket에서 균등 분산 실패 |
| 부하 테스트 | **k6** | JMeter/Grinder/Gatling 비교 후 |

**성능 목표/달성**: **Pod(CPU 4, MEM 8GiB) 1개당 동시접속 2,000명 + 1초 내 송수신**

### 4-3. 대형 서비스에서 Kafka의 실제 위치

**Slack**은 Kafka를 쓰지만 **잡 큐 앞단 durable buffer**(하루 14억 잡, 브로커 16대)다.
**채팅 전달 경로가 아니다.** LINE도 Kafka를 하루 1,500억 건 쓰지만 CDC/데이터 파이프라인이다.

---

## 5. RabbitMQ / Spring StompBrokerRelay

### 5-1. RabbitMQ

- 대형 메시지에 강함: 1MB 페이로드에서 Kafka/RabbitMQ가 NATS/Redis 대비 **14~126배** 나은 tail latency
- **채팅에서의 구조적 부담**: STOMP 플러그인의 `/topic/{name}`은 `amq.topic` exchange에 발행하고
  **구독마다 auto-delete non-durable 큐를 생성**해 바인딩한다.
  웹소켓 클라이언트가 N명이면 **큐 N개 + 바인딩 N개.**
  RabbitMQ 공식: *"A single queue replica is limited to a single CPU core on its hot code path"*
- `/topic` 목적지는 **구독자가 없으면 메시지를 버린다** — Redis Pub/Sub과 같은 성질

### 5-2. Spring StompBrokerRelay

- 의존성: **`reactor-netty` + `netty-all`** (Spring이 TCP 커넥션 관리에 Netty를 씀)
- RabbitMQ는 `rabbitmq_stomp` 플러그인 필요

### 5-3. ★ 다중 인스턴스 함정 — `/user/**` 목적지

`convertAndSendToUser()`는 세션 고유 목적지(`/queue/xxx-user123`)로 변환된다. 그런데:

> *"In a multi-application server scenario, **a user destination may remain unresolved because
> the user is connected to a different server**. In such cases, you can configure a destination
> to broadcast unresolved messages so that other servers have a chance to try."*

**→ `MessageBrokerRegistry`의 `userDestinationBroadcast` + `userRegistryBroadcast` 설정 필수.**

이걸 모르고 겪는 문제가 실제 이슈로 올라왔다 — **spring-framework#30347**,
`No TCP connection for session [ID]` 에러. **closed as invalid**(프레임워크 버그가 아니라 설정 누락).

### 5-4. 평가

STOMP를 유지하며 확장하는 정공법이지만 **관리 지점이 4~5개 늘어난다**
(브로커 + STOMP 플러그인 + 릴레이 재접속 + 유저 목적지 브로드캐스트 + 비활성 큐 정리).
**Redis Pub/Sub + SimpleBroker 조합은 이 전부를 "채널 하나 구독"으로 대체한다.**

---

## 6. Hazelcast / Ignite

**Hazelcast**
- `ITopic`: 베스트에포트
- `ReliableTopic`: **토픽마다 전용 Ringbuffer**(기본 동기 백업 1) + 전용 executor.
  *"Events are not lost since the Ringbuffer is configured with one synchronous backup by default"*
- 느린 컨슈머: `TopicOverloadPolicy` = `DISCARD_OLDEST` / `DISCARD_NEWEST` / `BLOCK`(기본) / `ERROR`

**Ignite**
- `IgniteMessaging`: `send()` / `sendOrdered()`, `localListen()` / `remoteListen()`
- 주의: *"any new node joining the cluster automatically gets subscribed to all the topics"*

**평가**: 이미 그리드가 있으면 자연스럽지만, **메시징만을 위해 도입하면
JVM 클러스터 멤버십·스플릿브레인·힙 관리라는 새 운영 부담이 생긴다.**

---

## 7. 실서비스 사례 — 대형 플레이어는 브로커를 안 쓴다

| 회사 | 채팅 팬아웃 미들웨어 |
|---|---|
| **Discord** | **Distributed Erlang + 자체 Manifold** (외부 브로커 없음) |
| **Slack** | **자체 Java pub/sub 티어** (Channel Server) |
| **Twitch** | **자체 Go Pubsub** (Edge / Broker / Control) |
| **KakaoTalk** | **relay 풀메시 직접 라우팅** (세션 위치는 Redis Cluster 조회) |
| **LINE** | 브로커 없음 — LEGY(Erlang) **notify** + 클라이언트 `fetchOps()` **pull** |
| **WhatsApp** | `pg2` + 자체 `wandist` (Erlang) |
| **LINE LIVE (2016)** | **Redis Cluster Pub/Sub** ← 규모가 맞으면 정답이라는 반례 |
| **카카오엔터 (2022)** | **Kafka** (Redis Pub/Sub 명시적 탈락) |

### 7-1. Discord

```
Client --WS--> Session Process (유저 1명 = Elixir 프로세스 1개)
                    ▲ Manifold
              Relay Process (릴레이당 최대 15,000 세션)
                    ▲
              Guild Process (서버 1개 = 프로세스 1개, 중앙 라우팅)
```

- **Manifold**: PID를 원격 노드별로 묶어 노드당 `send/2` 1회 → 각 노드 Partitioner가
  `:erlang.phash2/2`로 코어 수만큼 샤딩. **배포 직후 packets/sec 절반 감소**
- **FastGlobal**: 해시링 조회 **7µs → 0.3µs**, 기동 17.5초 → 750ms
- **Passive Sessions**: 대형 서버에서 user-guild 연결의 **약 90%가 passive**
  → 권한 체크·전송 자체 스킵. 게이트웨이 트래픽 비중 **35% → 5%**
- 선택 이유: *"In terms of real time communication, the **Erlang VM is the best tool for the job**."*
- **Kafka/Redis를 왜 안 썼는지에 대한 공식 진술은 없음 — 미확인**
- 규모: **1,200만 동접, 초당 2,600만 WebSocket 이벤트**, Elixir 머신 400~500대, **엔지니어 5명**

### 7-2. Slack

```
Client --WS--> Envoy --> Gateway Server
                            │ (channel_id consistent hash 구독)
Client --HTTP--> Webapp --> Admin Server --> Channel Server
                                                │ broadcast
                                    구독 중인 GS에만 --> 클라이언트
```

- **Channel Server는 stateful/in-memory**, 채널 ID consistent hash.
  "channel"은 추상 개념 — Slack 채널뿐 아니라 **유저·팀·엔터프라이즈·파일·허들이 전부 channel**
- **CHARM**(자체 Consistent Hash Ring Manager) → **CS 교체 20초 내 완료**
- **메시지는 팬아웃 전에 반드시 MySQL에 먼저 영속화**:
  *"every message sent in Slack is **persisted before** it's sent across the real-time websocket stack"*
- **Flannel**(엣지 캐시): 동시 400만 커넥션, 초당 60만 쿼리, 페이로드 **7배~44배 축소**
- 선택 이유 (QCon SF 2018, Mike Demmer):
  *"the real-time message bus was **custom software built in a Java tier** where all of the
  pub/sub distribution of the messaging product happened."*
- 규모: **CS 호스트당 피크 1,600만 채널**, 전세계 **500ms 내 전달**

### 7-3. Twitch

| 컴포넌트 | 역할 |
|---|---|
| Edge | 클라이언트 접속. **IRC 프로토콜을 raw TCP + WebSocket 양쪽으로** |
| Pubsub | Edge 노드 간 분배. **계층형 분배로 massive fanout** |
| Pubsub Edge/Control/Broker (2023) | Edge=구독 수락·전달, Control=인가, Broker=엣지 전체 팬아웃 |

- 2013년 말 **Python → Go 재작성** (Go 1.2 pre-release)
- **커넥션당 goroutine 3개** → 프로세스당 약 **150만 goroutine**
- **물리 호스트 1대당 동시 50만 유저**
- Go GC pause가 최대 병목이었음을 공식 인정:
  Go 1.2~1.4 **수 초** → 1.5 ~200ms → 1.6 ~100ms → **1.7 ~1ms**

### 7-4. LINE — push가 아니라 notify + pull

```
1. 가장 가까운 LEGY 찾기
2. sendMessage("Bob","Hello!")   3. talk-server로 프록시
4. 스토리지에 write
5. LEGY가 수신자에게 "메시지 도착" notify   ← 본문 아님
6~8. 수신자가 fetchOps()로 본문을 당겨감
```

- **LEGY** (LINE Event Delivery Gateway): **Erlang 자체 개발**.
  채택 이유는 *"Zero latency code hot swapping w/o closing client connections"* —
  **커넥션 안 끊고 무중단 핫스왑**
- talk-server: **Java 8 + Spring + Thrift RPC**. 캐시 Redis→Valkey, 저장 **HBase**, 파이프라인 Kafka
- **Armeria가 메시징 경로에 실제로 쓰인다** (공식 한국어 블로그):
  *"2018년에는 **푸시 발송 컴포넌트와 HTTP 통신 부분을 Armeria로 전환**했습니다."*
- 규모: 2017년 **메시지 250억 건/일**, 신년 피크 **420,000 msg/sec**,
  **LEGY 인스턴스당 커넥션 100K+**

### 7-5. ★ KakaoTalk — C++에서 JVM으로

```
relay & session manager | session info repository | config
private api server      | business servers        | media cache
```

- relay 서버들이 **풀메시로 서로 연결**, 세션이 붙은 서버로 직접 릴레이.
  세션 위치는 **Redis Cluster**로 조회
- *"500K sessions / 15~30K tps, max 100K tps / 풀메시에서 한 대씩 신규 서버 적용"*
- 향후 과제로 **"대규모 채팅방 릴레이 로직 개선"** 명시 → 1:N 릴레이 증폭이 숙제

**★ C++ → Kotlin + Netty + ZGC 포팅**

**이유 (공식)**
- *"경직된 코드(계층 강결합, 매크로/최적화로 가독성 저하, 메모리 버그)"*
- *"**파트내 인적 리소스 불균형: C++/JAVA**"*
- *"10년 후에도 문제없이 유지보수가 가능할까?"*

**결과**
```
코드라인       절반 이상 감소
가용 인력      3배 증가
relay time     C++ 47ms → Kotlin 42ms    ← 오히려 개선
max GC pause   G1GC 170ms → ZGC 1ms      ← ZGC 채택 근거
```

**규모**: 일 평균 **500K tps / 평균 연결 세션 4,000만 / 최고 6.5M tps**,
일 평균 메시지 **100억 건**, relay 서버 대당 **50만 세션**

### 7-6. 치지직 (CHZZK) — 미들웨어 미확인, 설계는 읽힌다

**공식 자료 없음.** DEVIEW 2023 세션 목록 전수 확인, d2.naver.com 검색 → 채팅 기술 글 없음.

**Open API 공식 문서에서 확인되는 것**
- 세션 게이트웨이 = **Socket.IO** (`ssio{NN}.nchat.naver.com`, WebSocket transport 강제)
- **CHAT / DONATION / SUBSCRIPTION이 동일 세션 채널로 팬아웃**
- 제한: Client 인증 최대 10 연결, 유저당 3 연결, **세션당 최대 30개 이벤트 구독**

**역공학으로 확인된 내부 프로토콜 (3자, 실행 코드 기반)**

★ **클라이언트 측 결정론적 해시 샤딩**
```js
const serverId = Math.abs(
    chatChannelId.split("").map(c => c.charCodeAt(0)).reduce((a,b) => a+b)
) % 9 + 1
ws = new WebSocket(`wss://kr-ss${serverId}.chat.naver.com/chat`)
```
**→ 같은 방송의 모든 시청자가 항상 동일 클러스터로 수렴 →
클러스터 간 cross-server 팬아웃이 원천적으로 불필요**

기타 설계 포인트
- `CONNECT.bdy.auth = "SEND" | "READ"` → **읽기 전용 연결과 전송 가능 연결을 접속 시점에 분리**
- `REQUEST_RECENT_CHAT(5101)` / `RECENT_CHAT(15101)`, 기본 50건
  → 서버가 채널별 최근 메시지 버퍼 유지
- 앱 레벨 `PING(0)/PONG(10000)` — WS 프레임 ping이 아님 (LB idle timeout 회피 추정)
- `svcid:"game"` 필드 존재 → **chat.naver.com은 치지직 전용이 아니라 사내 공용 채팅 플랫폼으로 보임**

**규모**: 2024 베타 동접 24~25만, LCK 결승 30만, 2025년 연간 채팅 **40억 건**,
2026 월드컵 최고 동접 **493만 8000명**('같이보기' 포함)

### 7-7. SOOP — 공식 자료 자체가 없음

기술 블로그·GitHub org 모두 존재하지 않음. 채팅 서버 언어·미들웨어 **전부 미확인**.

**역공학 문서(3자)로 확인된 것**
- 방송 단위로 서버가 채팅 서버 호스트+포트 지정(`CHDOMAIN`, `CHPT`)
  → **룸-서버 어피니티를 서버가 결정**
- `CHPT`와 `CHPT+1` 인접 → 레거시 raw TCP 프로토콜을 같은 서버가 인접 포트에서 WebSocket으로도 서비스
- **자체 바이너리 프레이밍**: 14바이트 헤더(`ESC 0x1B` + `TAB 0x09` + opcode 4B + 길이 6B + flags 2B),
  필드 구분자 form feed(0x0C) → **1990~2000년대 TCP 채팅 프로토콜 계보**
- opcode 약 101종 — 후원/구독/미션/자막이 **전부 채팅 소켓 한 줄기로 팬아웃**

**규모**: 최고 동시접속 **54만**(2024 LoL 월즈 4강), 2024년 총 채팅 45억 회,
2024 Q1 월평균 4억 회 → **평균 초당 약 154건** (피크는 미공개)

---

## 8. 공통 패턴 — 면접에서 쓸 재료

**① 대형 플레이어는 채팅 팬아웃에 Redis Pub/Sub을 안 쓴다. Kafka도 거의 안 쓴다.**
Kafka는 어디에나 있지만 거의 항상 **비동기 잡 큐 / CDC / 데이터 파이프라인**이지 채팅 핫패스가 아니다.

**② 그런데 예외 2건이 교과서다.**
- **LINE LIVE (2016)**: Redis Cluster Pub/Sub — **피크 분당 1만 코멘트**
- **카카오엔터 (2022)**: 동시접속 20만 → Redis Pub/Sub 탈락, Kafka 채택

> **같은 문제에 정반대 결론을 낸 이유가 규모와 시기다.**
> 분당 1만이면 Redis Pub/Sub, 동시 20만이면 브로커, 동시 수백만이면 자체 라우팅.

**③ 채널/룸 단위 어피니티 + consistent hashing이 전 사례 공통.**
Slack(channel_id 해시), Discord(guild=프로세스 1개), 치지직(`hash%9+1`),
SOOP(서버가 호스트 지정), 카카오(session info repository).

> 핵심은 *"브로커를 쓰지 말라"* 가 아니라
> **"같은 방을 같은 곳에 모아 cross-node 팬아웃 자체를 없애라"** 다.
> **카카오엔터조차 룸을 서버에 고정하지 "않기로" 선택했기 때문에 브로커가 필수가 된 것** —
> 인과관계가 이 방향이다.

**④ 팬아웃 비용은 O(N²)이고, 결국 "안 보내기"로 이긴다.**
Discord Maxjourney: 1,000명 동접이 각각 1개 = 100만 알림 / 10만 명 = **100억 알림**.
해법은 하나같이 전송량 자체를 줄이는 것 — Discord passive session 90% 스킵,
Slack 구독자 없는 GS 제외, 치지직 `READ|SEND` 분리, LINE notify만 보내고 클라가 pull.

**⑤ 저장은 "쓰기 먼저, 팬아웃은 그 다음"이 표준.**
Slack — *"every message sent in Slack is persisted before it's sent across the real-time
websocket stack"*. LINE도 write → notify.

**⑥ GC pause가 실시간 채팅의 1급 적이고, 그게 언어 선택을 결정한다.**
Twitch(Go 1.7까지 기다림), Discord(Go→Rust), 카카오(C++→Kotlin+ZGC).

---

## 9. 정정된 통념

| 통념 | 사실 |
|---|---|
| Signal이 Cassandra를 쓴다 | **사실무근.** PostgreSQL → DynamoDB → FoundationDB |
| Discord 게이트웨이가 Rust로 전환됐다 | **사실무근.** Elixir 유지. Rust는 Read States 등 일부 서비스 |
| LINE의 "Kaleidoscope" | **근거 없음** |
| Kafka 브로커가 Netty 기반 | **틀림.** raw `java.nio`. 3.x의 netty jar는 ZooKeeper transitive |

---

## 10. 미확인 항목

- 치지직 / SOOP의 채팅 팬아웃 미들웨어·언어·저장소 — **전부 미확인**
- Discord가 Kafka/Redis를 왜 안 썼는지에 대한 공식 진술
- Slack의 slow client / 백프레셔 / 재연결 스톰 대응 상세
- Twitch PubSub의 slow consumer 정책
- Hazelcast/Ignite의 지연 실측
