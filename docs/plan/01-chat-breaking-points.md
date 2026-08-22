# 실시간 채팅 — 인원이 늘면 어디서부터 무너지는가

> 개정 2026-08-22 · 상태: 계획
> **근거와 출처는 [`docs/research/`](../research/00-index.md) 에 있다.** 이 문서는 실행 계획이다.
> HLS 는 선택 작업이라 [`02-hls-optional.md`](02-hls-optional.md) 로 분리했다.
> 선행: [#2 세션 타입](https://github.com/dj258255/edumeet/issues/2) ·
> [#17 부하 측정](https://github.com/dj258255/edumeet/issues/17) ·
> [#19 외부 호출 타임아웃](https://github.com/dj258255/edumeet/issues/19)

## 0. 문제 정의

> **채팅방 인원이 늘면 어디서부터 무너지는가.**

기능을 만드는 것이 목적이 아니다. **깨지는 지점을 찾고 순서를 밝히는 것**이 목적이다.

이 질문이 좋은 이유가 셋이다.

1. **결과가 뭐가 나와도 성립한다.** 안 깨지면 *"N까지는 Spring 기본으로 충분하다"* 가 결론이고
   그것도 근거다
2. **깨지는 지점을 이미 알고 있다.** 사전 조사에서 나왔으니 재현만 하면 된다
3. **면접 질문을 앞질러 간다.** *"왜 이 기술을 썼나요"* 가 아니라 애초에
   *"어디서 깨지나"* 를 물은 것이라 답이 전부 측정으로 나온다

---

## 1. 붕괴 5단계 (예측 — 이것 자체가 검증 대상이다)

```
① 아웃바운드 스레드 고갈
     Spring Boot 3.4+ 가 in/outbound 채널에 8스레드 executor 를 주입
     + 전송이 블로킹(Tomcat 타임아웃 20초)
     → 느린 클라이언트 8명이면 방 크기와 무관하게 전체가 멈춘다

② fan-out CPU
     메시지 1건 × N명 = 쓰기 N회
     → 여기서 비로소 "인원"이 변수가 된다

③ 무한 큐 → 힙 OOM
     queueCapacity = Integer.MAX_VALUE
     → ②를 못 따라가면 백로그가 힙에 무한 누적

④ 단일 인스턴스 한계
     SimpleBroker 는 JVM 안에서만 동작
     → 서버를 늘리면 메시지가 갈라진다 (수신률 50%)

⑤ Redis Pub/Sub 한계
     클러스터 일반 PUBLISH 는 모든 노드에 전파
     → 훨씬 나중. 우리 규모에서는 도달하지 않을 가능성이 높다
```

### ★ 이 서사의 핵심은 ①이 인원과 무관하다는 것

대부분 *"동시접속 몇 명까지 되나요"* 를 재는데,
**실제로 먼저 죽는 것은 "느린 클라이언트 몇 명"** 이다.

이걸 수치로 보이면 **질문 자체를 바로잡는 것**이라 훨씬 강하다.

### 각 단계가 별개 실험이 아니라 하나의 이야기다

```
계측 환경을 만든다  →  ③으로 OOM 을 먼저 재현(가장 쉽고 확실)
                    →  ①로 스레드 고갈을 재현하고 executor 를 분리
                    →  ②로 한계 곡선을 그리고 배치 전송으로 개선
                    →  ④로 인스턴스를 늘려 유실을 재현하고 Redis 로 해결
                    →  ⑤는 기준에 걸리면 그때
```

**Phase 3(①)까지면 포트폴리오는 성립한다.**

### 검증할 것 — 이 순서가 정말 맞는가

다른 것이 먼저 터질 수도 있다. 후보와 확인 방법:

| 후보 | 언제 문제가 되나 | 확인 |
|---|---|---|
| Tomcat `acceptCount`(100) | 재연결 폭풍 시 | 서버 재시작 후 동시 재연결 |
| `maxConnections`(8192) / FD | 연결 수가 8천을 넘을 때 | 연결 수 스윕 |
| `DefaultSubscriptionRegistry` 캐시(1024) | **방 수**가 많을 때 (인원 아님) | 방 개수 스윕 |
| JSON 직렬화 CPU | 메시지가 클 때 | 페이로드 크기 스윕 |
| GC | 힙 압박이 스레드 고갈보다 먼저? | `-Xlog:gc*` |

**측정 결과가 예측과 다르면 그것 자체가 결과다.**
[#17 문서](../performance/06-lock-determinism.md)에서 이미 한 번 그랬다 —
*"필요할 줄 알았는데 재보니 필요 없었다"*.

---

## 2. 결론 먼저

| | |
|---|---|
| **만든다** | WebSocket 채팅(화상강의 + 라이브방송), 부하·백프레셔 측정, 다중 인스턴스 확장 |
| **만들되 범위를 자른다** | HLS — LiveKit Egress 경유. 세그먼트 길이 ↔ 지연 트레이드오프 측정까지 |
| **만들지 않는다** | Netty 직접 구현, LL-HLS, FFmpeg 파이프라인 자체 구축, 자체 HLS 파서, Kafka |
| **조건부** | NATS(사전 등록 기준 충족 시), ABR, 계측 플레이어 |

---

## 3. 현재 상태

| | |
|---|---|
| WebSocket | **없음.** 코드 0줄 |
| 채팅 | **없음** |
| 영상 | LiveKit(WebRTC SFU). 시그널링은 LiveKit이 자체 처리 |
| HLS | **없음.** VOD도 없음 |
| 세션 타입 | `INTERACTIVE` / `BROADCAST` — [#2](https://github.com/dj258255/edumeet/issues/2) |
| Java | **17** (virtual threads·Generational ZGC 사용 불가) |
| Netty | **이미 의존성에 있다** — `lettuce-core`와 AWS SDK 경유로 10개 모듈 |

마지막 항목이 중요하다. **"Netty를 쓸 것인가"가 아니라 "이미 쓰고 있는데 어디에 쓰이는지 아는가"가 질문이다.**

---

## 4. 설계 판단 ① — 두 채팅은 요구사항이 반대다

**하나를 만들어 양쪽에 붙이면 틀린다.**

| | `INTERACTIVE` 화상강의 | `BROADCAST` 라이브방송 |
|---|---|---|
| 인원 | 정원 제한 (기본 30, **향후 100 예정**) | 제한 없음 (수천) |
| fan-out | 메시지 1건 → 쓰기 **30회** | 메시지 1건 → 쓰기 **수천 회** |
| 이력 | **보존해야 함** (수업 기록·질의응답) | 휘발 (최근 N건이면 충분) |
| 메시지 유실 | **허용 안 됨** | 허용 |
| 순서 보장 | 엄격 | 느슨 |
| 저장소 | MySQL | Redis 최근분 or 저장 안 함 |
| rate limit | 느슨 | **엄격** (도배 방지) |

**전송 계층은 공유하되 정책은 `SessionType`으로 분기한다.**
#2에서 만든 `hasParticipantLimit()` / `allowsParticipantPublish()` 옆에
`persistsHistory()`, `allowsMessageDrop()`이 붙는 형태다.

이 분기가 억지가 아니라는 근거는 **Phase 2의 fan-out 수치**로 보인다.

### 실서비스도 이 구분을 한다 (참고)

치지직 내부 채팅 프로토콜은 접속 시점에 **읽기 전용 연결과 전송 가능 연결을 나눈다**
(`auth: "SEND" | "READ"`). 시청자 대부분은 READ다. 우리도 `BROADCAST`에서 같은 구분이
가능하다. — *역공학 자료 기반이므로 참고용*

---

## 5. 설계 판단 ② — Spring Boot 3.5 기본값이 지뢰다 (= 붕괴 ①의 근거)

**이번 조사에서 나온 가장 값진 발견이고, 이 작업의 중심이다.**

### 무엇이 문제인가 (소스 확인됨)

Spring Boot 3.4부터 WebSocket 메시지 채널의 executor를 `applicationTaskExecutor`로 덮어쓴다.

```java
// WebSocketMessagingAutoConfiguration
configureClientInboundChannel(reg)  → applicationTaskExecutor
configureClientOutboundChannel(reg) → applicationTaskExecutor

// TaskExecutionProperties.Pool 기본값
coreSize      = 8                    // CPU 개수와 무관하게 고정
maxSize       = Integer.MAX_VALUE
queueCapacity = Integer.MAX_VALUE    // 큐가 무한이라 maxSize 가 영원히 발동 안 함
```

**인바운드 + 모든 클라이언트 아웃바운드 + `@Async` + MVC async가 같은 8개 스레드를 공유한다.**
큐가 무한이라 풀은 8개에서 절대 커지지 않고, 백로그는 힙에 무한정 쌓인다.

여기에 전송이 **블로킹**이라는 사실이 겹친다.

```java
// StandardWebSocketSession
getNativeSession().getBasicRemote().sendText(...)   // 블로킹

// Tomcat Constants
DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;          // 20초
```

> **느린 클라이언트 1명이 아웃바운드 스레드 1개를 최대 20초 점유한다.
> 8명이면 채팅 전체가 멈춘다.**

### Spring이 가진 방어 장치와 기본값

```java
// SubProtocolWebSocketHandler
sendTimeLimit        = 10 * 1000;        // 10초
sendBufferSizeLimit  = 512 * 1024;       // 512KB
TIME_TO_FIRST_MESSAGE = 60 * 1000;       // 60초
```

한도 초과 시 `SessionLimitExceededException` + `CloseStatus.SESSION_NOT_RELIABLE`로 세션 강제 종료.
`OverflowStrategy`는 `TERMINATE`(기본) / `DROP`(오래된 메시지 폐기) 두 가지.

**`DROP`은 `WebSocketTransportRegistration`에 노출되어 있지 않다.**
`ConcurrentWebSocketSessionDecorator`를 직접 만들어 `DecoratorFactory`로 끼워야 한다.
→ 이것 자체가 좋은 소재다. `BROADCAST`는 DROP, `INTERACTIVE`는 TERMINATE로 갈린다.

### 통념 정정 — 이것도 기록 가치가 있다

| 통념 | 사실 |
|---|---|
| "Tomcat `maxThreads`가 200이라 WebSocket 200개가 한계" | **틀렸다.** NIO에서 유휴 WebSocket은 스레드를 안 잡는다. 실제 상한은 `maxConnections`(**8192**)와 파일 디스크립터 |
| "Java는 동시 연결 많으면 죽는다" | **틀렸다.** JVM에서 **1,000만 동시 연결**이 검증됐다(MigratoryData, CPU 50% 미만). 병목은 연결 수가 아니라 **fan-out 처리량과 백프레셔 정책** |

---

## 6. 설계 판단 ③ — 저수준은 구현이 아니라 설명으로

**Netty를 직접 쓰지 않는다.** 조사 결론이 명확하다.

| | |
|---|---|
| 30명 화상강의에서 | Spring 대비 **측정 가능한 차이가 안 나온다** |
| 직접 만들어야 하는 것 | 세션 레지스트리, 인증, 구독 모델, 라우팅, 직렬화, 백프레셔 정책, 하트비트, 재연결, ACK, 다중 인스턴스 브리지, 관측, graceful shutdown — **11가지** |
| 소요 | 절반만 해도 **2~3주** |
| 결과물 | Spring보다 기능이 **적다** |

그리고 시장 신호도 같은 방향이다 — **국내 "Netty" 채용 공고 10건이 전부 금융·PG·IoT의 Java 백엔드**이고
스트리밍 회사는 0건이다. Netty는 *"실시간 스트리밍 입장권"*이 아니라
*"Java 고성능 백엔드 심화 역량"*으로 읽힌다.

**대신 Netty 개념은 Spring의 동작을 설명하는 언어로 쓴다.**

| Netty 개념 | Spring에서 대응되는 것 |
|---|---|
| `Channel.isWritable()` / WriteBufferWaterMark(32KB/64KB) | `sendBufferSizeLimit`(512KB) 초과 시 세션 종료 |
| `ChannelOutboundBuffer`에 상한이 없다 | `queueCapacity = Integer.MAX_VALUE` → 힙 누적 |
| EventLoop 하나에 채널이 영구 바인딩 | 8스레드 풀을 모든 세션이 공유 |
| 핸들러 안에서 블로킹하면 같은 EventLoop의 다른 연결이 멈춤 | 느린 클라 8명이 전체를 멈춤 |

**"왜 Netty를 안 썼나요?"에 이 표로 답할 수 있으면 그것이 저수준을 아는 것이다.**

---

## 7. Java 버전 전략 — 17에서 풀고, 21에서 비교한다

### 트레이드오프

| | Java 17 (현재) | Java 21 | Java 25 (LTS) |
|---|---|---|---|
| Virtual Threads | ✗ | ✓ | ✓ |
| Generational ZGC (JEP 439) | ✗ | ✓ | ✓ |
| `synchronized` pinning | 해당 없음 | **⚠ 있음** | ✓ 해결 (JEP 491) |
| Compact Object Headers (JEP 519) | ✗ | ✗ | ✓ 힙 22%↓ |

**⚠ 21의 위험이 우리 스택 얘기다.** Netflix가 **Java 21 + Spring Boot 3 + 임베디드 Tomcat**에서
프로덕션 장애를 냈다. Tomcat이 만든 가상 스레드가 `synchronized` 블록 안에서 블로킹되며
ForkJoinPool의 모든 carrier에 pinned → **JVM은 살아있는데 트래픽이 죽고 CLOSE_WAIT 소켓 수천 개**.

### 포트폴리오 관점의 함정

**21로 올려 virtual threads를 켜면 §4의 문제가 설정 한 줄로 사라진다.**

```
Java 17  →  8스레드 공유 + 블로킹 전송 → 느린 클라 8명이 채팅 정지
             executor 를 직접 분리하고 튜닝해야 함        ← 서사 있음

Java 21  →  spring.threads.virtual.enabled=true → 문제 소멸  ← 서사 없음
```

### 결정 — 둘 다, 순서대로

```
1) Java 17 에서 문제를 재현하고 executor 분리로 해결   → 원리 이해 증명
2) Java 21 로 올려 virtual threads A/B               → 최신성 + 트레이드오프
3) 21 의 대가도 함께 기록
     - spring.task.execution.simple.concurrency-limit 기본 null = 동시성 무제한
       → 스레드 대신 힙이 터진다
     - pinning (JDK 24 JEP 491 전까지 남아 있던 결함)
```

**1을 건너뛰고 바로 21로 가지 않는다.** 그러면 무엇을 피했는지 말할 수 없다.

### 참고 — 카카오톡은 C++에서 JVM으로 갔다

**Java를 유지하는 결정의 가장 강한 근거다.** if(kakao)2022 "카카오톡 메시징 시스템
재건축 이야기"에서 relay & session manager를 **C++ → Kotlin + Netty + ZGC**로 포팅했다.

**이유 (공식)**
- *"경직된 코드 — 계층 강결합, 매크로/최적화로 가독성 저하, 메모리 버그"*
- *"파트내 인적 리소스 불균형: C++/JAVA"*
- *"10년 후에도 문제없이 유지보수가 가능할까?"*

**결과**
```
코드라인      절반 이상 감소
가용 인력     3배 증가
relay time    C++ 47ms → Kotlin 42ms   ← 오히려 개선
max GC pause  G1GC 170ms → ZGC 1ms     ← ZGC 채택 근거
```

규모는 **일 평균 500K tps, 평균 연결 세션 4,000만, 최고 6.5M tps,
일 메시지 100억 건, relay 서버 대당 50만 세션**이다.

> **"실시간 메시징은 C++이어야 한다"는 명제가 국내 최대 메신저에서 반증됐다.**
> 그리고 그 근거가 성능이 아니라 **유지보수성과 인력**이었다는 점이 더 중요하다.
> ZGC의 1ms pause가 그걸 가능하게 했다.

---

## 8. 실행 순서

각 Phase = 이슈 1개 + PR 1개.

**순서가 붕괴 순서와 다르다.** ③(OOM)을 먼저 하는 이유는 **재현이 가장 쉽고 확실하며,
여기서 세운 계측이 나머지에 그대로 쓰이기 때문**이다.

| Phase | 붕괴 단계 | 소요 |
|---|---|---|
| 0. 계측 환경 | — | 0.5일 |
| 1. 채팅 최소 동작 | — | 2일 |
| 2. 무한 큐 OOM | **③** | 1~2일 |
| 3. 백프레셔 | **①** | 2~3일 |
| — | — | **여기까지 6~8일. 멈춰도 성립** |
| 4. fan-out 한계 곡선 | **②** | 3~4일 |
| 5. 다중 인스턴스 + Redis | **④** | 2~3일 |
| (조건부) NATS | **⑤** | 사전 등록 기준 충족 시 |

### Phase 0 — 계측 환경 (0.5일)

> 붕괴 단계 대응 없음. **이후 전부의 전제.**

부하 스크립트와 관측 도구를 먼저 세운다. 이게 있으면 이후가 전부 빨라진다.

- k6는 **`k6/websockets`** 모듈을 쓴다 (`k6/ws`는 레거시, `k6/experimental/websockets`는 deprecated).
  글로벌 이벤트 루프라 **VU 1개가 여러 연결을 유지**할 수 있다 — 연결 수를 늘리는 데 결정적.
- **k6는 STOMP를 모른다.** 프레임을 문자열로 조립해야 하고 널 바이트 종결이 필요하다.
  → **부하용으로 SockJS를 끄고 순수 WebSocket 엔드포인트를 연다.**
- end-to-end 지연은 기본 메트릭에 없다 → 페이로드에 서버 발행 timestamp를 넣고
  `onmessage`에서 계산해 커스텀 `Trend`에 넣는다.
- 서버 관측: `WebSocketMessageBrokerStats`(`setLoggingPeriod(10000)`),
  Micrometer `executor.active`/`executor.queued`, `jcmd Thread.print`

### Phase 1 — 채팅 최소 동작 (2일)

> 붕괴 단계 대응 없음. **측정 대상을 만든다.**

```
브라우저 ──WebSocket──> Spring ──> 방 단위 브로드캐스트
                          └─> MySQL (INTERACTIVE 만)
```

- **STOMP + `SimpleBroker`로 시작한다.** 한계가 몇 명에서 오는지 재는 것이 Phase 2의 목적이다.
  처음부터 raw로 가면 무엇을 피했는지 말할 수 없다.
- 방 = `meetingId`. 입장 권한은 기존 `MeetingParticipant`로 검증
- `SessionType` 별 정책 분기

**측정 없음. 동작 확인만.**

### Phase 2 — 무한 큐 OOM 재현 (1~2일) — **붕괴 ③**

> **v1에서는 이걸 나중에 뒀는데 앞으로 당긴다.** 재현이 가장 쉽고 확실하며,
> 여기서 세운 계측이 Phase 3~4에 그대로 쓰인다.

- `-Xmx512m -XX:+HeapDumpOnOutOfMemoryError`로 발화율을 처리량 이상으로 유지 → 수 분 내 OOM
- **힙 덤프를 Eclipse MAT으로 열어 Dominator Tree 스크린샷** — 시각적 설득력이 가장 크다
- 해결: `spring.task.execution.pool.queue-capacity` 제한 + 거부 정책
  (`BROADCAST` = DiscardOldest, `INTERACTIVE` = CallerRuns)

**지표**: `4분 만에 OOM → 30분 무중단` (대신 메시지 X% 드롭)

### Phase 3 — 아웃바운드 스레드 고갈 / 백프레셔 (2~3일) — **붕괴 ①**

**이 작업 전체에서 가장 값이 크다.**

**느린 클라이언트를 만드는 법** — k6로는 못 만든다.

```java
// 1) 정상 WebSocket 핸드셰이크
// 2) 이후 socket.getInputStream() 을 절대 읽지 않는다
// 3) 수신 버퍼를 줄이면 TCP zero window 가 훨씬 빨리 발생
socket.setReceiveBufferSize(4096);
```

또는 #19에서 이미 띄운 **Toxiproxy로 특정 연결만 대역폭 제한**.

**결과 그래프**: X축 = 느린 클라이언트 수(0→16), Y축 = 정상 사용자 p99 지연.
**8명 지점에서 계단식 폭증**이 나온다.

**해결 3단계**
1. 아웃바운드 executor 분리 + 크기 조정
2. `sendTimeLimit` / `sendBufferSizeLimit` 조정 + `OverflowStrategy.DROP` 데코레이터
3. (Java 21 전환 후) virtual threads A/B

**지표**: 느린 클라 8명 존재 시 정상 사용자 p99 `2,000ms → 45ms` (목표치, 실측으로 대체)

### Phase 4 — fan-out 한계 곡선 (3~4일) — **붕괴 ②**

**두 채팅의 요구사항이 왜 다른지를 수치로 증명하는 단계.**

```bash
k6 run -e ROOM_SIZE=30   k6/chat-fanout.js   # 화상강의 현재
k6 run -e ROOM_SIZE=100  k6/chat-fanout.js   # 화상강의 향후 정원
k6 run -e ROOM_SIZE=300  k6/chat-fanout.js
k6 run -e ROOM_SIZE=1000 k6/chat-fanout.js
k6 run -e ROOM_SIZE=3000 k6/chat-fanout.js   # 라이브 규모
```

> **100 을 측정 구간에 넣는 이유**: 화상강의 정원을 향후 30 → 100 으로 올릴 예정이다.
> 어차피 인원을 스윕하므로 추가 비용이 0 이고, 그때 가서 근거를 다시 만들 필요가 없다.
> 정원 자체는 `ClassRoom.participantLimit` 이라 **DB 값이지 하드코딩이 아니므로
> 코드 변경 없이 올릴 수 있다.**
>
> 단 **"100 명이 전부 발행"** 은 다른 문제다. SFU 가 아니라 브라우저가 먼저 무너진다
> (비디오 100 개 디코딩, 다운링크 30Mbps). 그때는 **입장 정원과 동시 발행 정원을 분리**해야 하고,
> SDK 0.8.2 의 `updateParticipant(..., ParticipantPermission)` 으로 런타임 제어가 가능하다.
> **지금은 만들지 않는다.**

각 인원에서 발화율 10/50/100 msg/s를 스윕한다.

- **실제 처리량 vs 이론 처리량(N×M)의 괴리가 벌어지는 지점이 한계**
- p50/p95/p99 end-to-end 지연, CPU, GC 시간, 유실률

**연장**: 50ms 윈도우 **배치 전송**(메시지를 묶어 1프레임) 도입 후 재측정.

**결론 문장 예시**
> *"단일 인스턴스 4vCPU에서 1,000명 방 기준 초당 XX건까지 p99 100ms 이하.
> 그 이상에서는 큐 백로그가 선형 증가."*

**참고 기준점** — 카카오엔터 라이브채팅은 **Pod(CPU 4, MEM 8GiB) 1개당 동시접속 2,000명 +
1초 내 송수신**을 목표로 잡았다. 우리 수치를 해석할 때 비교선으로 쓴다.

### Phase 5 — 다중 인스턴스 (2~3일) — **붕괴 ④**

**먼저 버그를 재현한다.** `SimpleBroker`는 단일 JVM만 안다.

```
시청자 A ──> 인스턴스 1 ──✗──> 시청자 B (인스턴스 2)
수신률 정확히 50%
```

#19에서 대조군을 먼저 잰 것과 같은 순서다. 그 다음 **Redis Pub/Sub 릴레이**로 해결한다.
(§8 참조)

### Phase 6 — HLS (조건부, §9)

### Phase 7 — 문서 정리

---

## 9. 팬아웃 설계 (= 붕괴 ④⑤)

### 8-1. 결론과 근거

**Redis Pub/Sub으로 간다.** 근거는 다섯이다.

**① 채팅의 진실 원천은 브로커가 아니라 MySQL이다. 그래서 at-most-once로 충분하다.**

Redis 공식 Java/Lettuce 프로덕션 가이드가 이 패턴을 명시적으로 권장한다.

> *"Pub/sub is at-most-once — pair it with durable state if you need replay.
> On reconnect, consumers reconcile by **reading the durable store**, not by waiting for
> missed pub/sub messages."*

Slack도 같다 — *"every message sent in Slack is **persisted before** it's sent across the
real-time websocket stack."* 메시지는 MySQL에 저장하고, Redis Pub/Sub은
**"지금 연결된 사람에게 빨리 알리는 신호선"** 일 뿐이다. 신호가 유실되면
클라이언트가 `lastMessageId` 이후를 다시 조회한다.

> **브로커에 durability를 요구하지 않는 설계라, at-most-once가 결함이 아니라
> 요구사항에 맞는 선택이다.**

**② 새 미들웨어 0개.** Redis는 이미 있다. `RedisMessageListenerContainer` 빈 하나로 끝난다.

**③ 이 규모에서 성능이 남는다 — 수치로 말할 수 있다.**

**Centrifugo**(Go 실시간 메시징 서버)가 노드 간 통신을 Redis Pub/Sub으로 하는데,
*"one deployment served up to **500k connections** with 10 Centrifugo node pods and only
**one Redis instance** which consumed only **60% of a single processor core**"* 다.

앱 인스턴스가 N개면 **Redis 구독자도 N개(수십 개)이지 클라이언트 수만큼이 아니다.**

**④ 실시간 메시징의 사실상 업계 표준 구현체다.**
Socket.IO 공식 Redis Adapter, Centrifugo Redis Engine, LINE LIVE(Java + Akka +
Redis Cluster Pub/Sub, 100+ 인스턴스 / 피크 분당 1만 코멘트)가 모두 이 패턴이다.

**⑤ 한계에 닿는 지점을 수치로 안다.** (8-3)

### 8-2. Redis Pub/Sub 실무 함정 — 미리 알고 간다

**(a) `client-output-buffer-limit`으로 인한 구독자 강제 종료**

```
client-output-buffer-limit pubsub 32mb 8mb 60
  hard 32MB  → 즉시 연결 종료
  soft  8MB가 60초 연속 유지 → 연결 종료
```

Pub/Sub은 push 기반이라 **구독자 처리 속도 < 발행 속도면 출력 버퍼가 무한 증가**한다.
끊기면 *"all pending messages for that subscriber are lost"* 다.

> **증상: 애플리케이션 로그의 원인 불명 잦은 구독자 재접속.**
> 리스너가 무겁거나(JDBC 쓰기, 동기 HTTP) 인스턴스가 GC로 멈추면 → 버퍼 증가 →
> Redis가 그 인스턴스를 끊음 → **그 인스턴스에 붙은 전체 클라이언트가 조용히 메시지를 놓친다.**
> 이게 Redis Pub/Sub 채팅의 실제 1급 장애 시나리오다.

**(b) 리스너 콜백은 Netty 이벤트 루프 스레드에서 돈다**

Redis 공식 가이드: *"messages arrive on **Netty event loop threads**. Blocking there
ties up I/O threads and delays the next message on the same socket."*

**→ 리스너에서 무거운 작업 금지. executor로 넘긴다.**
(§4의 Spring WebSocket 스레드 고갈과 같은 종류의 함정이다.)

**(c) 구독자마다 별도 커넥션**
Lettuce는 `SUBSCRIBE` 후 커넥션을 subscribe-only 모드로 전환한다 →
`StatefulRedisPubSubConnection`을 공유하면 안 된다.

**(d) 글롭 패턴은 발행마다 전부 평가된다**
`PUBLISH`는 **O(N+M)** (N=채널 구독자, M=전체 패턴 수).
핫패스에서 `*:*:*` 같은 다중 와일드카드 금지. 채널명은 `chat:room:123` 콜론 계층으로.

### 8-3. 한계 임계점 — 어디서 무너지나

**Redis Cluster의 일반 `PUBLISH`는 모든 노드에 브로드캐스트된다.**
Redis 이슈 #2672의 계산:

```
1KB 메시지 · 10노드 · 1Gbit/s  →  12.5K RPS 한계
5KB 메시지 · 50노드            →     500 RPS
```

**노드를 늘릴수록 publish가 비싸진다. 스케일이 반대로 간다.**

**해결책: Redis 7 Sharded Pub/Sub** (`SPUBLISH` / `SSUBSCRIBE`).
채널을 키와 같은 알고리즘으로 슬롯에 해싱해 **샤드 내부로만 전파**한다.
Lettuce가 클러스터 커넥션에 `spublish()` / `ssubscribe()`를 노출한다.
제약: 한 번의 `SSUBSCRIBE` 호출의 모든 채널이 **같은 슬롯**이어야 한다.

**임계점의 실물 — 카카오엔터(2022)**

동시접속 20만 목표에서 **Redis Pub/Sub을 명시적으로 탈락**시켰다. 이유 셋:

1. Redis Cluster에서 publish 시 **모든 노드에 전파** → 노드를 늘릴수록 느려짐
   (당시 GCP Memorystore가 Redis 6.x만 지원해 Sharded Pub/Sub 사용 불가)
2. 특정 채널 발행 시 **모든 subscriber 순회** → 구독자 수 비례 선형 지연
3. 메시지 휘발성 + 전/후처리 파이프라인 자체 구현 필요

Cloud Pub/Sub vs Kafka 실측:

| | Cloud Pub/Sub | Kafka |
|---|---:|---:|
| p90 | 45~180ms | **30~65ms** |
| p99 | 500~1,000ms | **60~100ms** |

결과: Pod(CPU 4 / MEM 8GiB)당 **동시접속 2,000명 + 1초 내 송수신**.

> **그 임계점이 어디인지 알고 있고, 우리는 그 아래에 있다.**

### 8-4. 같은 문제, 정반대 결론

| | LINE LIVE (2016) | 카카오엔터 (2022) |
|---|---|---|
| 브로커 | **Redis Cluster Pub/Sub** | **Kafka** |
| 룸 배치 | 룸이 여러 서버에 걸침 | 룸을 N대에 분산 |
| 규모 | **분당 1만+ 코멘트** | **동시접속 20만** |
| 스택 | Java + Akka | Kotlin + Spring Boot + Coroutine |

**이유가 규모와 시기다.** 분당 1만이면 Redis Pub/Sub, 동시 20만이면 브로커,
동시 수백만이면 자체 라우팅(8-6).

### 8-5. NATS 채택 기준 — 사전 등록

> 재고 나서 기준을 만들면 **결과에 맞춰 논리를 짜게 된다.** 먼저 정한다.

아래 중 **하나라도** 측정에서 나오면 NATS를 도입하고 그 수치를 근거로 기록한다.

- [ ] Redis Pub/Sub 경유 전파 지연 **p95 > 50ms** (카카오엔터 Kafka p99 60~100ms 기준선)
- [ ] 부하 중 `client-output-buffer-limit pubsub`으로 **인스턴스 구독이 끊긴다**
- [ ] 메시지 유실률이 `BROADCAST` 허용치(**1%**)를 넘는다
- [ ] Redis가 채팅 때문에 세션·캐시 용도에 지장을 준다

**그런데 조사에서 NATS의 실제 약점이 나왔다.**

> **Centrifugo는 NATS를 브로커로 지원하지만 등급이 낮다** —
> *"Nats integration works only for **unreliable at most once PUB/SUB**"*,
> **history/recovery 기능 사용 불가**, 와일드카드 구독 기본 비활성.

즉 실시간 메시징 서버 관점에서 **NATS Core는 Redis보다 기능이 적다.**
이력·복구를 원하면 JetStream까지 가야 하고, 그러면 운영 부담이 Kafka 쪽으로 이동한다.
그리고 **NATS 공식 어답터 목록에 채팅/메신저 회사가 없다** (인프라·IoT·클라우드 중심).

**NATS가 정당해지는 조건** (참고로 기록)
1. `org.{id}.room.{id}.event.{type}` 같은 **subject 계층**이 도메인 요구사항이고 subject가 수만~수백만 개
2. **멀티 리전 / 엣지** 토폴로지 (gateway·leaf node)
3. 리소스 극단 제한 환경 (단일 바이너리, 메모리 20MB 미만)
4. **request-reply**가 팬아웃만큼 중요 (평균 50.87µs)
5. queue group 분배 + 브로드캐스트를 **한 시스템**에서

**우리는 다섯 중 어느 것도 아니다.** subject가 방 ID 하나뿐이고 단일 리전이다.

### 8-6. Kafka는 후보에서 뺀다 — 그리고 대형 서비스는 어디에 있나

Kafka는 **처리량을 위해 건당 지연을 의도적으로 희생**하는 설계다
(producer latency 15~30ms vs Redis Pub/Sub <1ms).
**방 하나당 토픽은 안티패턴**이고, *"throughput does still drop off when there are
more than 1,000 topics"* 다. 컨슈머 그룹은 파티션을 나눠 갖는 모델이라
"모든 인스턴스가 모든 메시지 수신"과 맞지 않는다.

**대형 서비스에서 Kafka의 실제 위치**: Slack은 Kafka를 쓰지만 **잡 큐 앞단 durable
buffer**(하루 14억 잡, 브로커 16대)다. **채팅 전달 경로가 아니다.**

**그리고 대형 플레이어는 채팅 팬아웃에 브로커를 아예 안 쓴다.**

| 회사 | 채팅 팬아웃 |
|---|---|
| Discord | Distributed Erlang + 자체 **Manifold** |
| Slack | **자체 Java pub/sub 티어** (Channel Server) |
| Twitch | **자체 Go Pubsub** |
| KakaoTalk | **relay 풀메시 직접 라우팅** (세션 위치는 Redis Cluster 조회) |
| LINE | 브로커 없음 — LEGY **notify** + 클라이언트 `fetchOps()` **pull** |

**공통 패턴이 하나 있다** — 룸/채널 단위 어피니티 + consistent hashing.
Slack(channel_id 해시), Discord(guild=프로세스 1개), 치지직(`hash%9+1`),
SOOP(서버가 호스트 지정), 카카오(session info repository).

> 핵심은 *"브로커를 쓰지 말라"* 가 아니라
> **"같은 방을 같은 곳에 모아 cross-node 팬아웃 자체를 없애라"** 다.
> 카카오엔터조차 **룸을 서버에 고정하지 "않기로" 선택했기 때문에** 브로커가 필수가 됐다.
> 인과관계가 이 방향이다.

**팬아웃 비용은 O(N²)이고 결국 "안 보내기"로 이긴다.**
Discord Maxjourney: 1,000명이 각각 1개 = 100만 알림 / 10만 명이면 **100억 알림**.
해법은 하나같이 전송량 자체를 줄이는 것 — Discord passive session 90% 스킵,
Slack 구독자 없는 GS 제외, 치지직 `READ|SEND` 분리, LINE notify만 보내고 클라가 pull.

### 8-7. Spring 다중 인스턴스 함정 — `/user/**`

`convertAndSendToUser()`는 세션 고유 목적지로 변환되는데, **다중 인스턴스에서는
유저가 다른 서버에 붙어 있으면 목적지가 해석되지 않는다.**

> *"In a multi-application server scenario, a user destination may remain unresolved
> because the user is connected to a different server. In such cases, you can configure
> a destination to **broadcast unresolved messages** so that other servers have a chance to try."*

**→ `MessageBrokerRegistry`의 `userDestinationBroadcast` + `userRegistryBroadcast` 설정 필수.**

이걸 모르고 겪는 문제가 실제 이슈로 올라왔다 — spring-framework#30347,
`No TCP connection for session [ID]` 에러. **closed as invalid** (프레임워크 버그가 아니라 설정 누락).

### 8-8. 면접 답변 — "왜 NATS 안 쓰고 Redis 썼어요?"

> 팬아웃 브로커에 durability를 요구하지 않는 설계라 at-most-once면 충분했고,
> 그러면 **NATS Core와 Redis Pub/Sub의 전달 보장은 동일합니다.**
> 오히려 Centrifugo 같은 실시간 서버는 NATS 브로커를 쓰면 history/recovery를 못 씁니다.
> Redis는 이미 프로젝트에 있어서 새 클러스터가 0개고, 이력·프레즌스까지 같은 스토어에서 처리됩니다.
> NATS의 진짜 강점인 subject 계층 수백만 개와 마이크로초 라우팅은 우리 규모에서 쓸 일이 없습니다.
>
> 대신 **Redis Pub/Sub의 한계는 알고 있습니다** — 클러스터 전 노드 브로드캐스트와
> output buffer 강제 종료요. 전자는 Redis 7 Sharded Pub/Sub으로,
> 후자는 리스너에서 블로킹 안 하는 걸로 대응했습니다.

---

## 10. HLS — 별도 문서로 분리

**시간이 남으면 하는 선택 작업이다.** → [`02-hls-optional.md`](02-hls-optional.md)

최소 범위만 적으면: `segment_duration` 4→2초 튜닝 + `hls.js` 계측 플레이어 + 스펙 위반 검증.
**3~4일.** 채팅 5단계를 끝낸 뒤에 판단한다.

---

## 11. ADR — 기각 기록을 문서로 만든다

조사에서 나온 1순위 권고다. 형식은 5칸 고정:
**컨텍스트 / 결정 / 근거 / 결과 / 기각한 대안과 이유**

| ADR | 내용 |
|---|---|
| **ADR-001 미디어 서버** | LiveKit vs mediasoup vs Janus vs P2P Mesh vs **직접 SFU 구현**. 특히 *"직접 SFU를 검토했고 왜 안 했는지"*를 반드시 적는다 |
| **ADR-002 동시성 제어** | 비관적 락 vs 낙관적 락 vs DB 제약 vs 애플리케이션 큐. [#17](https://github.com/dj258255/edumeet/issues/17) 실측 근거 있음 |
| **ADR-003 외부 호출 방어** | 타임아웃 범위, 서킷 브레이커 미도입 근거. [#19](https://github.com/dj258255/edumeet/issues/19) |
| **ADR-004 채팅 전송 계층** | raw WebSocket vs STOMP vs SSE vs 폴링. **왜 STOMP로 시작했고 언제 바꾸는지** |
| **ADR-005 팬아웃 브로커** | Redis Pub/Sub vs NATS vs Kafka vs 룸-서버 고정. §8의 사전 등록 기준 |
| **ADR-006 Java 버전** | 17 유지 vs 21 vs 25. §6의 트레이드오프 |

README에서 링크한다.

---

## 12. 우선순위와 중단 지점

```
Phase 0  계측 환경               ★★★  0.5일
Phase 1  채팅 최소 동작          ★★★  2일
Phase 2  무한 큐 OOM             ★★★  1~2일
Phase 3  백프레셔                ★★★  2~3일   ← 가장 차별화됨
────────────────────────────────────────────  여기서 멈춰도 성립
Phase 4  fan-out 한계 곡선       ★★☆  3~4일
Phase 5  다중 인스턴스 + Redis    ★★☆  2~3일
  5-1    NATS A/B                ★☆☆  조건부
Phase 6  HLS (세그먼트 튜닝 + 계측 플레이어)  ★★☆  5~7일, 조건부
Phase 7  문서                    ★★★  필수
```

**Phase 3까지 = 약 6~8일.** 그 지점에서 이미

> *"라이브 채팅을 만들고, 느린 시청자 하나가 방 전체를 막는 것을 측정으로 발견해
> 프레임워크 기본값이 원인임을 소스로 확인하고 정책으로 해결했다"*

가 성립한다.

**포트폴리오 문서와 자소서가 Phase 4보다 우선이다.** 만들어놓고 안 쓰면 0점이다.

---

## 13. 하지 않기로 정한 것 (근거 포함)

| | 이유 |
|---|---|
| **Netty 직접 구현** | 30명에서 측정 가능한 차이가 없다. 11가지를 손으로 만들어야 하고 2~3주. 결과물은 Spring보다 기능이 적다 |
| **LL-HLS** | **LiveKit egress 출력이 TS 세그먼트라 CMAF가 아니고, 부분 세그먼트를 얹을 경로가 없다.** 서버가 Blocking Playlist Reload를 포함해 5개 메커니즘을 구현해야 하는데 hls.js는 이미 `lowLatencyMode=true`라 기여 여지가 0. §9-5 |
| **Kafka** | 채팅 실시간 전파에는 지연·운영 부담이 과하다. 우리에겐 Kafka Streams가 필요한 요구사항이 없다 |
| **HLS 파서 직접 구현** | RFC 8216 태그만 수십 개 + MSE 버퍼 관리(쿼터·갭 점핑·stall 복구). *"동작한다"까지 하루, "엣지 케이스에서 안 깨진다"까지는 영원히.* 방송사 IT는 파서를 짜는 사람이 아니라 파이프라인을 운영·계측하는 사람을 뽑는다 |
| **FFmpeg 파이프라인 자체 구축** | LiveKit egress가 이미 그 파이프라인이다. 비-Docker 실행은 GStreamer + Chrome + Xvfb + PulseAudio 직접 설치. Apple 7.4 + 1.13 + 7.7을 동시에 만족시키는 GOP/세그먼터 튜닝만으로 며칠이 사라진다 |
| **RabbitMQ STOMP relay 벤치마크** | 설치·플러그인·권한에 1~2일, 결과는 예측 가능("브로커 늘어서 지연 조금 늘고 확장성 좋아짐") |
| **단일 머신 100만 연결 도전** | 클라이언트 장비가 먼저 죽는다. 노트북 1대 상한은 5,000~10,000 연결 |
| **JMeter로 WebSocket 부하** | VU당 스레드 1개 → 5,000 연결에 5,000 스레드 → **JMeter가 먼저 죽는다** |
| **서킷 브레이커** | [#19](https://github.com/dj258255/edumeet/issues/19)와 같은 이유. 3초가 문제라는 근거가 없다 |
| **NATS (현재)** | §8의 사전 등록 기준에 걸리면 그때 |

---

## 14. 서사 프레이밍

**언어는 Java를 유지한다.** 근거가 명확하다.

조사에서 확인된 경계선은 **언어가 아니라 "패킷당 하는 일"**이다.

```
[1] 픽셀/샘플을 만짐 (디코드·인코드)   → C/C++ + 손 어셈블리. 예외 없음
[2] 커널/NIC 초당 수천만 패킷          → C (eBPF/XDP/DPDK)
[3] RTP 파싱·전달만 (SFU)             → GC 언어로 충분
[4] 수십만 연결 + fan-out (채팅)       → Go/JVM/Erlang 전부 실증
[5] 시그널링·인증·API·DB              → 생태계와 팀 역량
```

**edumeet은 [5]에 있고 [1]~[3]은 LiveKit에 위임했다.**

가장 강한 증거는 **LiveKit이 자기 스택 안에 그은 경계**다.

```dockerfile
# SFU 본체 (패킷 포워딩)
CGO_ENABLED=0 go build -o livekit-server      ← C 링크 0

# Egress (녹화·트랜스코딩)
CGO_ENABLED=1 go build -o egress
FROM livekit/gstreamer:1.24.12-dev            ← C 라이브러리
```

같은 팀·같은 언어·같은 제품군인데 **"코덱을 만지느냐" 하나로 갈린다.**

### 면접 답변의 형태

```
✗ "라이브 스트리밍을 하고 싶어서 스트리밍을 만들었습니다"
✗ "Netty로 직접 만들었습니다"

✓ "SFU와 트랜스코딩은 LiveKit에 위임했습니다. LiveKit 자체가 SFU는 CGO_ENABLED=0
   순수 Go로 빌드하고 Egress만 GStreamer를 링크하는데, 그 경계가 '픽셀을 만지느냐'입니다.
   저희 서비스는 그 위 계층이라 Java가 맞다고 판단했습니다."

✓ "Spring 기본 구성으로 N명까지 측정했고, 여기서 아웃바운드 스레드가 병목이라
   확인해서 이 부분만 이렇게 해결했습니다."
```

**"저수준을 팠다"가 아니라 "경계를 안다"가 목표다.**

---

## 15. 조사에서 확인된 주요 근거

> 전체 근거와 출처는 [`docs/research/`](../research/00-index.md) 참조.

| 항목 | 출처 |
|---|---|
| Spring Boot 3.4+ WebSocket executor 덮어쓰기 | `WebSocketMessagingAutoConfiguration` 소스 |
| `coreSize=8`, `queueCapacity=Integer.MAX_VALUE` | `TaskExecutionProperties` 소스 |
| 블로킹 전송 + 20초 타임아웃 | `StandardWebSocketSession`, Tomcat `Constants` 소스 |
| Tomcat `maxConnections=8192`, 유휴 WS는 스레드 미점유 | Tomcat 문서 + `WsHttpUpgradeHandler` 소스 |
| JVM 1,000만 동시 연결 | MigratoryData 벤치마크 (벤더 자체 측정) |
| Netflix 가상 스레드 pinning 장애 | InfoQ 2024-08 |
| JDK 24 JEP 491로 pinning 해결 | OpenJDK JEP 491 |
| 카카오엔터 Redis Pub/Sub 기각 사유 + Kafka 실측 | tech.kakaoent.com 라이브채팅 2부작 |
| LINE LIVE = Redis Cluster Pub/Sub | engineering.linecorp.com |
| 치지직 `hash % 9` 서버 샤딩 | 클라이언트 역공학 (참고용) |
| LiveKit CGO 경계 | livekit/livekit, livekit/egress Dockerfile |
| FFmpeg "자동 벡터화 2x vs 손 어셈블리 8x" | FFmpeg asm-lessons |
| SOOP 개발 공고에 Java 0건 | recruit.sooplive.com 외 |
| 국내 Netty 공고 10건 전부 금융/PG/IoT | 잡코리아 |
| 신입 공고 0.8% vs 신입 지원 29.5% | 사람인·점핏 2025 상반기 리포트 |
| HLS 지연 = 3× target duration | RFC 8216 §6.3.3 + rfc8216bis HOLD-BACK + hls.js `liveSyncDurationCount` |
| 세그먼트는 IDR로 시작 MUST / IDR 2초 권장 / 6초 권장 | Apple HLS 저작 스펙 7.4 · 1.13 · 7.5-7.6 |
| LiveKit egress = TS 세그먼트 (CMAF 아님) | livekit/egress README |
| `key_frame_interval` 기본 = 세그먼트 길이 | LiveKit Egress API 문서 |
| RoomComposite 잡당 2~6 CPU, 인스턴스당 1잡 | LiveKit 자체호스팅 문서 |
| hls.js `abrBandWidthUpFactor` 0.7 vs `abrBandWidthFactor` 0.95 | hls.js API 문서 + `abr-controller.ts` |
| **`segment_duration` 기본 4초** | livekit/egress `pkg/config/output_segment.go` |
| **라이브 창 5개 하드코딩 (Apple 8.11 위반)** | livekit/egress `pkg/pipeline/sink/segments.go` |
| **PDT를 세그먼트마다 기록 / `EXT-X-PART` 없음** | livekit/egress `pkg/pipeline/sink/m3u8/writer.go` |
| SDK 0.8.2 에 `startRoomCompositeEgress(SegmentedFileOutput)` 존재 | server-sdk-kotlin v0.8.2 `EgressServiceClient.kt` |
| SFU 벤치마크 (150×150 = CPU 85%, 1×3000 = 531 MBps) | LiveKit 자체호스팅 벤치마크 문서 |
| `room_composite_cpu_cost: 3.0` | livekit/egress README |
| LL-HLS 실증 (iPhone 14 20분 후 끊김) | WINK Ultra-Low-Latency HLS Experiments 2025 |
| WHIP = RFC 9725 (2025-03 정식), WHEP = draft | rfc-editor.org / datatracker |
| `client-output-buffer-limit pubsub 32mb 8mb 60` | Redis 설정 기본값 |
| Redis Cluster PUBLISH 전 노드 전파 (1KB/10노드 = 12.5K RPS) | redis/redis#2672 |
| Redis 7 Sharded Pub/Sub (`SPUBLISH`/`SSUBSCRIBE`) | redis.io 커맨드 문서 + Lettuce wiki |
| 리스너 콜백이 Netty 이벤트 루프에서 실행 | Redis 공식 Java/Lettuce 프로덕션 가이드 |
| Centrifugo: Redis 1대로 50만 커넥션, CPU 코어 60% | centrifugal.dev/docs/server/engines |
| Centrifugo의 NATS = at-most-once만, history/recovery 불가 | 동일 |
| Spring `/user/**` 다중 인스턴스 `userDestinationBroadcast` 필수 | Spring 문서 + spring-framework#30347 |
| **카카오톡 C++ → Kotlin+Netty+ZGC (relay 47→42ms, 인력 3배)** | if(kakao)2022 슬라이드 |
| **ZGC max pause G1GC 170ms vs ZGC 1ms** | 카카오 자체 벤치마크 |
| Slack: 메시지는 팬아웃 전 MySQL 영속화 | slack.engineering/real-time-messaging |
| Kafka 브로커는 raw java.nio (Netty 아님) | apache/kafka SocketServer.scala |
| **`hls.latency` 구현 = liveEdge − currentTime (문서와 다름)** | hls.js `latency-controller.ts` |
| `targetLatency`는 스톨마다 증가 | 동일 소스 |
| PDT 측정은 "삽입 지점 → 화면" (Mux 기준 실제보다 약 1초 낮음) | mux.com/blog/live-latency-metric |
| EEL / EDL / Packager-Display 용어 | DASH-IF CR-Low-Latency-Live-r8 |
| 캘리브레이션 원칙(리그 자체 지연을 뺀다) | videoLat 논문 (ACM MM'14) |
| `drawtext` `%[1-6]N`으로 밀리초 번인 | ffmpeg 필터 공식 문서 |
| B-frame 리오더 = 1프레임 버퍼링 (24fps 42ms) | 인코딩 문헌 |
| WHIP = RFC 9725 (2025-03) / WHEP = draft-04 | rfc-editor.org, datatracker |
| SRT `SRTO_RCVLATENCY` 기본 120ms, RTT×4 | Haivision 공식 문서 |
| Mux QoE 공식 (Startup Score = 8/(8+t)×100) | mux.com/docs/guides |
