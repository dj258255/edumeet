# 02. Spring WebSocket 내부와 실시간 서버 — 조사 결과

> 조사일 2026-08-22 · 소스코드 직접 확인분은 파일 경로 명기

## 0. 3줄 요약

1. **화상강의(30명, 이력 보존)는 Spring WebSocket + STOMP + SimpleBroker로 충분하다.**
   부족한 건 WebSocket 계층이 아니라 영속화/ACK/재전송 계층이다.
2. **라이브방송(수천 명)에서 Spring 기본 구성은 실제로 무너진다.**
   무너지는 지점은 "동시 연결 수"가 아니라 **`clientOutboundChannel` 스레드 고갈 + 무한 큐**다.
   **Spring Boot 3.4+에서는 기본값 때문에 훨씬 더 빨리 터진다.**
3. **Netty 직접 구현은 신입 포트폴리오로는 함정에 가깝다.**

---

## 1. ★ 핵심 발견 — Spring Boot 3.4+ 기본값이 위험하다

### 1-1. Spring Framework 단독 기본값

`TaskExecutorRegistration` 생성자:
```java
this.taskExecutor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
```
- corePoolSize = `availableProcessors() * 2`
- maxPoolSize = `Integer.MAX_VALUE`
- **queueCapacity = `Integer.MAX_VALUE`**
- keepAliveSeconds = 60

— `spring-messaging/.../simp/config/TaskExecutorRegistration.java`

### 1-2. 그런데 Spring Boot 3.4부터 덮어써진다

`WebSocketMessagingAutoConfiguration.WebSocketMessageBrokerExecutorConfigurer`:
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    if (this.executor != null) { registration.executor(this.executor); }
}
@Override
public void configureClientOutboundChannel(ChannelRegistration registration) {
    if (this.executor != null) { registration.executor(this.executor); }
}
```
`this.executor`는 **`applicationTaskExecutor`** 빈이다.

`TaskExecutionProperties.Pool` 기본값 (Spring Boot 3.5.x):
```java
private int queueCapacity = Integer.MAX_VALUE;
private int coreSize = 8;          // ← CPU 개수와 무관하게 고정 8
private int maxSize = Integer.MAX_VALUE;
private boolean allowCoreThreadTimeout = true;
private Duration keepAlive = Duration.ofSeconds(60);
```

### 1-3. 결론

- 인바운드 메시지 처리 + **모든 클라이언트에 대한 아웃바운드 전송** + `@Async` + MVC async가
  **전부 같은 8개 스레드 풀을 공유**한다.
- 큐가 무한이라 `maxSize`는 **영원히 발동하지 않는다** → 풀은 8개에서 절대 안 커진다.
- 백로그는 힙에 무한정 쌓인다 → **OOM 경로가 열려 있다.**

Spring Boot 문서도 이 목록에 *"Spring WebSocket inbound/outbound message channels"* 를 명시한다.

> **공식 문서 어디에도 "위험하다"고 안 쓰여 있고, 한국어 블로그에도 거의 안 나온다.**

### ⚠ 정확히 — "덮어쓸 수 없다"가 아니라 "아무도 안 바꾼다"

`WebSocketMessageConverterConfiguration` 에 **`@Order(0)`** 이 붙어 있고,
사용자 `WebSocketMessageBrokerConfigurer` 는 기본이 `LOWEST_PRECEDENCE` 라 **나중에 실행된다.**
`ChannelRegistration.executor()` 는 단순 setter이므로 **나중에 호출한 쪽이 이긴다.**

**→ `configureClientOutboundChannel` 을 오버라이드하면 재정의된다.**

이 `@Order(0)` 은 [spring-boot#42924](https://github.com/spring-projects/spring-boot/issues/42924)
에서 Spring 팀이 *"We consider this a bug and we're going to add `@Order(0)`"* 라며 붙인 것이다.
근본 설계 문제는 [#44946](https://github.com/spring-projects/spring-boot/issues/44946) /
[PR#50494](https://github.com/spring-projects/spring-boot/pull/50494) 로 **아직 미해결**.

`ChannelRegistration.executor()` javadoc(6.2): *"taking precedence over a task executor
registration if any"* — `taskExecutor()` 와 `executor()` 를 동시에 쓰면 **`executor()` 가 이긴다.**

### 1-4. 공식 문서의 경고 문구 (원문)

> *"If clients are on a fast network, the number of threads should remain close to the number
> of available processors. **If they are slow or on low bandwidth, they take longer to consume
> messages and put a burden on the thread pool. Therefore, increasing the thread pool size
> becomes necessary.**"*

> *"**There is no silver bullet when it comes to performance.**"*

> (스레드풀 흔한 오해) *"Configuring core size of 10 and max of 20 with default queue capacity
> doesn't create a 10-20 thread pool — **it stays at 10 threads since all additional tasks are queued.**"*

— https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html

---

## 1-5. ★ 검증 — "10초면 정리된다"는 틀렸다

`ConcurrentWebSocketSessionDecorator.sendMessage()` 소스:

```
메시지를 내부 LinkedBlockingQueue 에 적재
  → flushLock.tryLock()   (논블로킹)
      락 잡은 스레드만  → StandardWebSocketSession.getBasicRemote().sendText()  ← 블로킹
      못 잡은 스레드는  → 즉시 리턴
```

**`checkSessionLimits()` 의 `sendTimeLimit`(10초) 체크는 블로킹 중인 그 스레드 자신이 아니라,
나중에 같은 세션에 또 send 를 시도했다가 락을 못 잡은 "다른" 스레드에서만 실행된다.**

```
경쟁이 없으면       → 10초는 아예 발동하지 않는다
실제 스레드 해제는  → Tomcat WsRemoteEndpointImplBase.sendMessageBlockInternal()
                      DEFAULT_BLOCKING_SEND_TIMEOUT = 20,000ms
                      → doClose() → 예외 전파 → finally 에서 flushLock.unlock()
```

**→ 경쟁 없는 느린 세션 1개당 스레드 점유 시간은 최악 20초다.**

같은 세션에 발행이 몰려 경쟁이 생기면 경쟁 스레드들은 10초에 즉시 실패하지만,
**원래 블로킹된 스레드가 조기 해제되는지는 소스만으로 확정할 수 없다(미확인).**

## 1-6. fan-out 이 비싼 정확한 이유

`SimpleBrokerMessageHandler` → `StompSubProtocolHandler.sendToClient()` 에서
**구독자마다 `stompEncoder.encode(headers, payload)` 를 개별 호출**한다.
구독자별 `subscription-id` 헤더가 달라 **프레임 자체가 달라서 payload 캐싱이 안 된다.**

```
구독자 N명 × 발행 r msg/s  =  초당 N×r 회 STOMP 인코딩
```

## 1-7. OOM 은 두 갈래다

| | 트리거 | 근거 |
|---|---|---|
| **(a) 백로그형** | 발행률 — 큐에 태스크 누적 | `queueCapacity = Integer.MAX_VALUE` |
| **(b) 유휴 연결형** | **순수 인원수. 발행률 무관** | 실측 **연결당 약 0.084MB** |

(b)는 **메시지를 하나도 안 보내도 연결만 1만 개면 OOM** 이다.
2Core/2GB 단일 WAS 실측 사례. 힙덤프 기준
`NioSocketWrapper` + `WsFrameServer` + `WsRemoteEndpointImplServer` 합산.

## 1-8. ★ Tomcat `maxConnections` 8192 는 실제 하드월이다

Tomcat 10.1 공식 문서:
> *"the server will accept, but not process, one further connection... the operating system
> may still accept connections based on the `acceptCount` setting."*

`AbstractEndpoint.countUpOrAwaitConnection()`(LimitLatch)이 **소켓 accept~close 생명주기 단위**로
카운트하므로 **WebSocket 업그레이드 후에도 슬롯을 계속 점유한다**(아키텍처상 강한 추정, 문서 명문 미확인).

**실측**: 8,000명째부터 막혔고 `server.tomcat.max-connections: 11000` 으로 올려서야 1만 명 성공.
**임계 약 8,192 ~ 8,292명** (`acceptCount` 100 포함).

> ⚠ **`ulimit -n` 이 먼저 걸릴 수 있다.** 관례적 기본값 1024 면 **약 1,000명**에서
> `Too many open files`. **정확한 기본값은 이번 조사에서 재검증하지 못했다(미확인).**
> 재현 시 `ulimit -n` 을 확인하고 기록할 것.

## 1-9. 실측 사례 (수치 있는 것)

| 사례 | 수치 |
|---|---|
| 10K 동시 접속 OOM + maxConnections 벽 | 연결당 heap **≈0.084MB**, 8192 에 막혀 8,000개만 연결 |
| 인원 증가에 따른 지연/유실 | **1,000명: 평균 1.2s / 최대 4s** · **3,000명: 평균 18s + 유실** |
| 블로킹 핸들러 스레드풀 고갈 | pool=50, 2,000 동시접속 → `"pool size=50, active=50, queued tasks=1471"` |
| 순수 WS+STOMP → 이벤트 기반 전환 | 스레드 70~80 에서 과부하(1.8s) → 전환 후 100스레드 0.09s |
| (반례) SimpleBroker + 튜닝 | **500~800명 안정**, 성공률 99.8% 자기보고 — 개인 프로젝트, 정성적 근거 |
| (반례) WebFlux 단일 서버 | **5,000 VU 무오류** — CPU 100%, 메모리 5GB, p99 ≈1s |

**국내 대기업 기술블로그의 Spring STOMP 채팅 장애 사례는 찾지 못했다(미확인).**

## 1-10. `WebSocketMessageBrokerStats` — 경로와 API 정정

**모듈은 `spring-websocket` 이다** (`spring-messaging` 아님).
`org/springframework/web/socket/config/WebSocketMessageBrokerStats.java`

6.2 에서 API 가 개편됐다:
`getWebSocketSessionStats()`, `getStompSubProtocolStats()`, `getStompBrokerRelayStats()`,
**`getClientInboundExecutorStatsInfo()`**, **`getClientOutboundExecutorStatsInfo()`**,
`getSockJsTaskSchedulerStatsInfo()`

`getExecutorStatsInfo()` 는 `ThreadPoolExecutor.toString()` 을 파싱해
`"pool size = #, active threads = #, queued tasks = #, completed tasks = #"` 형태로 반환한다.

> outbound executor 의 active/queued 가 **Micrometer 에 자동 노출되는지는 미확인.**
> 확실히 하려면 `WebSocketMessageBrokerStats` 를 직접 로깅하거나 커스텀 게이지를 만든다.

---

## 2. 전송은 블로킹이다 — 이게 진짜 병목

`StandardWebSocketSession`:
```java
getNativeSession().getBasicRemote().sendText(message.getPayload(), message.isLast());
```

Tomcat `Constants`:
```java
public static final long DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;  // 20초
```

> **느린 클라이언트 1명이 아웃바운드 스레드 1개를 최대 20초 점유한다.
> 8명이면 전체 채팅이 멈춘다.**

---

## 3. Spring의 방어 장치와 기본값

`ConcurrentWebSocketSessionDecorator` javadoc:

> *"Wrap a WebSocketSession to guarantee only one thread can send messages at a time.
> **If a send is slow, subsequent attempts to send more messages from other threads will not
> be able to acquire the flush lock, and messages will be buffered instead. At that time,
> the specified buffer-size limit and send-time limit will be checked, and the session will
> be closed if the limits are exceeded.**"*

`SubProtocolWebSocketHandler` 기본값:
```java
private int sendTimeLimit = 10 * 1000;              // 10초
private int sendBufferSizeLimit = 512 * 1024;       // 512KB
private static final int DEFAULT_TIME_TO_FIRST_MESSAGE = 60 * 1000;  // 60초
```

한도 초과 시 `SessionLimitExceededException` + `CloseStatus.SESSION_NOT_RELIABLE`로 **세션 강제 종료**.

`OverflowStrategy` = `TERMINATE`(기본) / `DROP`(오래된 메시지 폐기, Spring 5.1+)

> **⚠ 정정: STOMP 에서는 `DROP` 을 공식적으로 켤 수 없다.**
>
> `SubProtocolWebSocketHandler.decorateSession()` 이 **3-arg 생성자를 하드코딩**해
> 무조건 `TERMINATE` 다. `WebSocketTransportRegistration` 에 `OverflowStrategy` 설정 메서드가
> 없고, **`addDecoratorFactory` 는 `WebSocketHandler` 를 감싸지 세션을 감싸지 않는다.**
>
> DROP 을 쓰려면 `SubProtocolWebSocketHandler` 를 상속해 `decorateSession()` 을 오버라이드하고
> `subProtocolWebSocketHandler` 빈을 교체해야 한다 — **비공식 우회.**
> raw WebSocketHandler(STOMP 미사용)라면 `addDecoratorFactory` 가 정확히 그 지점이다.

**그리고 `sendTimeLimit` 초과는 전략과 무관하다.**
`checkSessionLimits()` 소스: **sendTimeLimit 초과 → 항상 예외**,
**bufferSizeLimit 초과일 때만** TERMINATE / DROP 이 갈린다.
`CloseStatus.SESSION_NOT_RELIABLE` = **코드 4500**.

**종료 감지**: `StompSubProtocolHandler.afterSessionEnded()` 가 `SessionDisconnectEvent` 를 발행한다.
`event.getCloseStatus()` 가 `SESSION_NOT_RELIABLE` 이면 느린 클라 강제 종료다 → **측정 가능.**

---

## 4. SimpleBroker의 한계

- **인메모리, 단일 JVM.** 인스턴스 2대면 절반이 메시지를 못 받는다.
- 클러스터링 불가. 공식 권장은 `enableStompBrokerRelay` + RabbitMQ/ActiveMQ

> *"The simple broker is great for getting started but supports only a subset of STOMP commands
> (it does not support acks, receipts, and some other features), relies on a simple message-sending
> loop, and **is not suitable for clustering**."*

- 구독 레지스트리 `DefaultSubscriptionRegistry`의 destination 캐시 상한 **`DEFAULT_CACHE_LIMIT = 1024`**.
  방(destination) 개수가 1024를 넘으면 LRU 축출이 반복되며 캐시 미스가 잦아진다.
  → **방이 많은 서비스에서 측정 가능한 열화 지점.** 실측 자료는 **미확인**.

---

## 5. Tomcat 상한 — 널리 퍼진 오해 정정

| 항목 | 기본값 |
|---|---|
| `maxConnections` (NIO/NIO2) | **8192** |
| `maxThreads` | 200 |
| `acceptCount` | 100 |
| `connectionTimeout` | 60000ms |
| WebSocket text/binary buffer | 8192 bytes |
| `SESSION_CLOSE_TIMEOUT` | 30000ms |

**❌ 흔한 오해**: *"Tomcat maxThreads가 200이니까 WebSocket 동시 연결은 200개가 한계다."*

**✅ 사실**: Tomcat NIO에서 **유휴 WebSocket 연결은 컨테이너 스레드를 점유하지 않는다.**
`WsHttpUpgradeHandler.upgradeDispatch(SocketEvent status)`가 `OPEN_READ`/`OPEN_WRITE`
이벤트를 받을 때만 스레드가 붙는다. 실제 상한은 **`maxConnections`(8192)와 파일 디스크립터**다.

이 오해는 websocket.org 가이드, Medium 글 등에 광범위하게 퍼져 있다.
**"통념 정정"만으로도 포트폴리오 서술 가치가 있다.**

---

## 6. "Java는 동시 연결 많으면 죽는다"는 통념 — 반례

### MigratoryData C10M (JVM)

| 항목 | 값 |
|---|---|
| 서버 | Dell PowerEdge R610 (1U), Xeon X5650 ×2 (12코어), **RAM 96GB**, 10Gbps |
| JVM | Oracle Java 8u45, **힙 54GB**, CMS GC, huge pages 60GB |
| **동시 연결** | **10,000,108** |
| 처리량 | 168,000 msgs/sec, 페이로드 512B |
| CPU | **50% 미만** |
| 지연 | median 18.71ms / p95 374.90ms / p99 585.06ms |
| 연결당 커널 메모리 | 약 **3.2KB** |

Zing JVM으로 바꾸면 튜닝 없이 p99 **585ms → 25ms**.
*"GC effects no longer dominate latency behavior."*

**캐비엇**: 벤더 자체 벤치마크, 오래된 HW/JVM, 클라이언트당 분당 1메시지의 가벼운 워크로드.

### 계층별 연결당 비용

- 커널 소켓: 약 2~4KB
- 프레임워크(Netty/ws/gorilla): 4~50KB
- **permessage-deflate 압축 켜면 폭증** — zlib 컨텍스트가 연결당 수백KB.
  `ws` 공식 문서: *"adds a **significant overhead in terms of performance and memory
  consumption** ... increased concurrency, especially on Linux, can lead to
  **catastrophic memory fragmentation**"*
  → **압축 on/off로 RSS 차이를 재면 좋은 측정 소재.**

### C10K / C10M의 현재

- C10K는 **완전히 해결된 문제**. 단일 머신 수백만 연결은 표준
- 병목이 이동했다 — epoll의 알고리즘 복잡도가 아니라 **소켓당 커널 메모리, `fs.file-max`,
  `ulimit -n`, 네트워크 처리량**이 상한
- 현재 관심사는 **syscall 오버헤드와 컨텍스트 스위칭** → io_uring, thread-per-core

> **"Java는 동시 연결에 약하다"는 2010년대 초반 BIO 커넥터 시절 이야기다.
> 지금은 연결 수가 아니라 팬아웃 처리량과 백프레셔 정책이 진짜 문제다.**

---

## 7. Netty를 직접 쓸 때

### 7-1. EventLoop

```java
DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
        "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
```

- Channel은 등록 시 **EventLoop 하나에 영구 바인딩**된다 → 핸들러 내부에 락이 필요 없다(장점)
- **함정**: 핸들러 안에서 DB 조회, 로깅 flush, `Thread.sleep`, 동기 HTTP를 하면
  **같은 EventLoop에 바인딩된 수천 개의 다른 연결이 전부 멈춘다.**
  8코어 = 16 EventLoop에 5,000 연결이면 **연결 하나가 312개를 인질로 잡는다.**
- Spring에서는 스레드풀이 흡수해 주던 실수가 Netty에서는 즉시 장애가 된다.

### 7-2. ByteBuf

- 참조 카운팅: 초기값 1, `retain()`/`release()`.
  규칙: *"the party that accesses a reference-counted object last is also responsible for the destruction."*
- `duplicate()`/`slice()`는 부모 카운트 공유, `copy()`는 별도 해제 필요
- 누수 탐지 레벨: `DISABLED` / `SIMPLE`(~1% 샘플링, 기본) / `ADVANCED` / `PARANOID`(100%)
- **PARANOID는 처리량을 크게 떨어뜨린다** (200MB/s → 25MB/s 보고 사례)
- 직접 메모리 고갈 시 `OutOfDirectMemoryError` — **힙 덤프에 안 잡혀서 가장 헤매는 오류**

### 7-3. ★ ChannelOutboundBuffer + WriteBufferWaterMark — 백프레셔의 핵심

```java
private static final int DEFAULT_LOW_WATER_MARK  = 32 * 1024;  // 32KB
private static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024;  // 64KB
```

> *"If the number of bytes queued in the write buffer exceeds the high water mark,
> `Channel.isWritable()` will start to return `false`. ... and then dropped down below
> the low water mark, `Channel.isWritable()` will start to return `true` again."*

관측 API: `ChannelOutboundBuffer.totalPendingWriteBytes()`,
`channel.bytesBeforeUnwritable()`, `channel.bytesBeforeWritable()`

> **⚠ 중요: `ChannelOutboundBuffer` 자체에는 상한이 없다.**
> 워터마크는 **신호일 뿐 강제 차단이 아니다.** `isWritable()`을 무시하고 계속 `write()` 하면
> 버퍼는 무한히 자라 OOM으로 간다. **이것이 Netty에서 slow consumer가 서버를 죽이는 정확한 경로다.**

### 7-4. Netty 백프레셔의 정석

Netty 메인테이너(Norman Maurer):
> *"Stop writing once `Channel.isWritable()` returns false and resume once it returns true again.
> You can be notified of changes by overriding `channelWritabilityChanged(...)`"*

```java
for (Channel ch : room.channels()) {
    if (ch.isWritable()) {
        ch.writeAndFlush(msg.retainedDuplicate());
    } else {
        // A) 버린다 (라이브 채팅 = 휘발 허용 → 최적)
        // B) 세션별 유한 큐, 초과 시 연결 종료
        // C) 연결 즉시 종료 (Centrifugo 방식)
        droppedCounter.increment();
    }
}

@Override
public void channelWritabilityChanged(ChannelHandlerContext ctx) {
    if (ctx.channel().isWritable()) { resumeSending(ctx); }
}
```

**절대 하지 말 것**: `isWritable()`이 true 될 때까지 EventLoop 스레드에서 폴링/대기
→ 비동기 설계를 무효화하고 다른 연결을 전부 죽인다.

**워터마크 값 선택**: 공식 가이드 없음(**미확인**).
실무 기준은 `high_water_mark × 최대_동시연결수 ≤ 가용_직접메모리`.
예: 64KB × 10,000 = **640MB**.

### 7-5. Netty가 주는 것 / 안 주는 것

**주는 것**: 프레임 코덱(`WebSocketServerProtocolHandler`), 핸드셰이크,
프래그먼트 병합, 압축 확장, close 핸드셰이크

**안 주는 것 = 직접 구현 목록**
1. 세션 레지스트리 (userId ↔ Channel, room ↔ Channel Set) + 동시성
2. 인증/인가 (Spring Security 연동 없음)
3. 구독 모델 (STOMP SUBSCRIBE/UNSUBSCRIBE 상당물)
4. 메시지 라우팅/디스패치 (`@MessageMapping` 상당물)
5. JSON 직렬화 파이프라인 + ByteBuf 생명주기 관리
6. 백프레셔 정책
7. 하트비트/유휴 타임아웃 (정책은 직접)
8. 재연결/세션 복구, 메시지 시퀀스/ACK
9. 멀티 인스턴스 팬아웃 브리지
10. 관측 (Micrometer 자동 연동 없음)
11. graceful shutdown / 드레이닝

**신입 기준 냉정한 평가**: 1~7만 "동작하는 수준"으로 **최소 2~3주**.
그리고 완성해도 **30명 화상강의에서는 Spring 대비 측정 가능한 차이가 안 나온다.**

---

## 8. 느린 클라이언트(Slow Consumer)

### 8-1. 메커니즘

```
서버 앱 버퍼 (Spring: 512KB / Netty: ChannelOutboundBuffer 무제한)
   ↑ 차오르면 세션 종료 or OOM
커널 송신 버퍼 (net.ipv4.tcp_wmem)
   ↑ 차오르면 write() 블로킹 / isWritable() false
TCP zero window  ← 클라이언트가 read()를 안 하면 여기서 시작
   ↑
클라이언트 커널 수신 버퍼 → 앱
```

원인: 모바일 3G, 백그라운드 탭(타이머 throttle), 디버거 정지,
렌더링 병목(초당 1000건 DOM 삽입), 의도적 공격

### 8-2. 시스템별 대응

| 시스템 | 정책 | 기본값 |
|---|---|---|
| Spring WebSocket | 세션별 버퍼 → 초과 시 종료(TERMINATE) 또는 폐기(DROP) | 512KB / 10s |
| Tomcat | 블로킹 write 타임아웃 | 20,000ms |
| Netty | `isWritable()` 신호만, 정책은 앱이 결정 | high 64KB / low 32KB |
| **Centrifugo** | 클라이언트별 메시지 큐, 초과 시 **연결 종료** | `client.queue_max_size` = **1MB** |
| **Discord** | GenStage 기반 demand-driven 백프레셔 + **load-shedding** | Firebase 연결당 pending 100건 |

### 8-3. ★ Discord — 팬아웃 증폭이 진짜 병목이라는 실증

> *"the wall clock time of a single `send/2` call could range from **30μs to 70μs**"*
> → *"**publishing an event from a large guild could take anywhere from 900ms to 2.1s!**"*

r/Overwatch 서버 3만 명 동접에서 발생. 해결:
- **Manifold** — 팬아웃을 원격 노드로 분산 + CPU 코어 수만큼 일관 해싱
- **FastGlobal** — ETS 7µs → 0.3µs
- **Semaphore** — 동시성 제한으로 큐 오버플로 방지

규모: 500만 동접 → **1,200만 동접, 초당 2,600만 WebSocket 이벤트**,
Elixir 머신 400~500대, **담당 엔지니어 5명**

### 8-4. Slack

Channel Server(상태 보유, 일관 해싱) / Gateway Server / Presence Server / Admin Server.
코어 서비스는 **Java**.
- 전 세계 메시지 전달 지연 **500ms**
- 피크 시 **호스트당 약 1,600만 채널**
- Channel Server 교체 시간 20초 미만

> **느린 클라이언트 처리, 백프레셔 전략, 재연결 스톰 대응에 대한 구체적 서술은 없음(미확인).**

### 8-5. 국내 자료

**LINE / 카카오 / 네이버 / 우아한형제들** — WebSocket slow consumer를 정면으로 다룬
1차 기술블로그 글은 **찾지 못했다.**

> **역으로 이건 기회다. 한국어로 이 주제를 수치와 함께 정리한 글이 거의 없다.**

---

## 9. Java 21 가상 스레드

### 9-1. 도움이 되지 않는 부분

**WebSocket 연결 자체를 유지하는 데는 아무 도움이 안 된다.**
Tomcat NIO는 이미 유휴 WebSocket 연결에 스레드를 안 쓴다(§5).
→ *"가상 스레드 덕분에 10만 WebSocket 연결 가능"* 이라는 흔한 서술은 **전제가 틀렸다.**
그건 thread-per-connection(BIO) 모델에서만 참이다.

### 9-2. 도움이 되는 부분

`spring.threads.virtual.enabled=true` → `applicationTaskExecutor`가
`ThreadPoolTaskExecutor`(core 8) → **`SimpleAsyncTaskExecutor` + 가상 스레드**로 바뀐다.
그리고 §1-2에서 확인했듯 이 executor가 **WebSocket 채널에 그대로 주입된다.**

- ✅ 느린 클라이언트가 아웃바운드 스레드를 점유하는 문제가 **사라진다**
- ✅ 인바운드 핸들러에서 블로킹 작업을 해도 다른 메시지 처리가 안 막힌다
- ❌ **대신 동시성이 무제한이 된다.** `spring.task.execution.simple.concurrency-limit`
  기본값이 null → 부하가 몰리면 **스레드가 아니라 힙이 터진다.**
  (#43 에서 실제로 확인했다 — 무한 큐는 느린 소비자를 만나면 2분 만에 OOM 이었다)

### 9-3. 위험 — 핀닝 (Netflix 프로덕션 장애)

- Java 21 + Spring Boot 3 + 내장 Tomcat, 4-vCPU → ForkJoin carrier 4개
- 트레이싱 라이브러리의 `synchronized` 블록에서 4개 가상 스레드가 전부 핀닝
- unpark 신호를 받은 5번째를 스케줄할 carrier가 없음 → **데드락**
- 증상: **JVM은 살아 있고 헬스체크는 통과하는데 트래픽이 죽고 CLOSE_WAIT 소켓 수천 개**

**현재**: JDK 24 **JEP 491**로 `synchronized` 핀닝 제거.
단 native 메서드와 FFM 호출 핀닝은 여전히 남아 있다.

### 9-4. 정리표

| 항목 | 가상 스레드 효과 |
|---|---|
| 유휴 WebSocket 연결 수용량 | **효과 없음** |
| 느린 클라이언트로 인한 스레드 고갈 | **✅ 해결** |
| 인바운드 핸들러의 블로킹 I/O | **✅ 크게 개선** |
| 팬아웃 CPU 비용(직렬화, 인코딩) | **효과 없음** (CPU 바운드) |
| 메모리 안정성 | **❌ 악화 가능** |
| Java 17 프로젝트 | **사용 불가** |

---

## 10. 재현·측정 가능한 트러블슈팅 소재

| # | 소재 | 난이도 | 소요 | 임팩트 | 지표 (before → after) |
|---|---|---|---|---|---|
| **A** | **아웃바운드 스레드 고갈 (Slow Consumer)** | 중 | 2~3일 | **최상** | 정상 사용자 p99 `2,000ms → 45ms` |
| **B** | **팬아웃 한계 곡선 + 배치 전송** | 중 | 3~4일 | **최상** | 처리량 `~40k → ~200k sends/s` |
| **C** | **무한 큐 OOM + 힙덤프** | 하~중 | 1~2일 | 상 | `4분 만에 OOM → 30분 무중단` |
| **D** | **다중 인스턴스 유실 → Redis 릴레이** | 중 | 2~3일 | 상 | 수신률 `50% → 100%` |
| **E** | **유실 방지 seq + ACK + 재전송** | 중 | 3~4일 | 상 | 재연결 100회 중 유실 `37회 → 0회` |
| F | 가상 스레드 A/B (Java 21+) | 하 | 1~2일 | 중~상 | 위 A를 재측정 |
| G | 연결 수 상한 찾기 | 중 | 2일 | 중 | 연결당 RSS 증가분 |
| H | permessage-deflate 트레이드오프 | 하 | 1~2일 | 중 | RSS·CPU·네트워크 바이트 |
| I | 재연결 폭풍 (Thundering Herd) | 중 | 2일 | 중~상 | 재연결 완료 시간, 실패율 |
| J | 좀비 커넥션 / 하트비트 | 하 | 1일 | 중 | 좀비 세션 잔존 시간 |
| ⚠ K | **Netty로 재작성** | 상 | **2~3주** | 중 | **함정** |

**추천 순서: C → A → B → D → E** (총 10~14일)
C로 계측 환경과 부하 도구를 먼저 세팅하면 A·B가 훨씬 빨라진다.

### 소재 A의 재현 트릭 (핵심)

"느린 클라이언트"는 **소켓을 열고 `read()`를 안 하는 클라이언트**다.
브라우저로는 못 만들고 raw TCP로 만든다.

```java
// 1) 정상 WebSocket 핸드셰이크 수행
// 2) 이후 socket.getInputStream() 을 절대 읽지 않는다
// 3) SO_RCVBUF 를 줄이면 TCP zero window 가 훨씬 빨리 발생
socket.setReceiveBufferSize(4096);
```

**측정 도구**
- `WebSocketMessageBrokerStats` (`setLoggingPeriod(10000)`)
  — 공식 문서가 *"indicates if clients are too slow to consume messages"* 용도를 명시
- `jcmd <pid> Thread.print` → `WsRemoteEndpointImplBase`에서 대기 중인 스택
- Micrometer `executor.active`, `executor.queued`

**결과 그래프**: X축 = 느린 클라이언트 수(0→16), Y축 = 정상 사용자 p99 지연
→ **8명 지점에서 계단식 폭증**

---

## 11. k6로 WebSocket 부하 측정

### 11-1. 모듈 선택

- ❌ `k6/ws` — 레거시. `ws.connect()`가 **블로킹**이라 **VU 1개 = 연결 1개**
- ❌ `k6/experimental/websockets` — **deprecated**
- ✅ **`k6/websockets`** — 현행 표준. **글로벌 이벤트 루프** → **VU 1개가 여러 연결 유지 가능**

### 11-2. 메트릭

| 메트릭 | 의미 |
|---|---|
| `ws_connecting` | 연결 요청 총 소요 시간 |
| `ws_session_duration` | 연결~VU 종료 |
| `ws_msgs_sent` / `ws_msgs_received` | **유실률 계산의 핵심** |
| `ws_ping` | ping→pong 왕복 |

### 11-3. k6의 한계

1. **STOMP를 모른다.** 프레임을 문자열로 조립해야 하고 널 바이트 종결(`\x00`),
   heartbeat 협상, `SUBSCRIBE` id 관리를 손으로 짜야 한다.
   → **팁: 부하용으로 SockJS를 끄고 순수 WebSocket 엔드포인트를 하나 연다.**
2. **end-to-end 지연을 기본 메트릭으로 못 잰다.** 페이로드에 서버 발행 timestamp를 넣고
   `onmessage`에서 계산해 커스텀 `Trend`에 넣어야 한다.
3. **클라이언트 머신이 먼저 병목이 된다.**
   - 로컬 포트 고갈: `sysctl net.ipv4.ip_local_port_range="1024 65535"`
   - FD: `ulimit -n 65535`
   - **macOS는 훨씬 빨리 막힌다 → Linux에서 부하 생성 권장**
   - 현실적으로 **노트북 1대에서 5,000~10,000 연결이 상한**
4. **"느린 클라이언트" 재현은 k6로 못 한다** → 별도 Java raw socket 클라이언트 필요
5. 오픈소스 k6는 분산 실행 없음

### 11-4. 대안 도구

| 도구 | WebSocket | STOMP | 한계 |
|---|---|---|---|
| **k6** (`k6/websockets`) | ✅ 1급 | ❌ 수동 | 분산 실행 없음(OSS) |
| **Gatling** | ✅ 1급 (`wsName()`으로 VU당 다중) | ❌ | Scala/Java DSL 학습 곡선 |
| **Artillery** | ✅ `engine: ws`, YAML | ❌ | 서버 푸시 수신 문서 부실 |
| JMeter + 플러그인 | ✅ | 부분 | **스레드 기반 → 수천 연결에서 JMeter가 먼저 죽음** |
| autocannon | ❌ 미지원 | ❌ | HTTP 전용 |
| 직접 작성 | ✅ | ✅ | **느린 클라이언트 재현은 이것만 가능** |

### 11-5. 추천 조합

```
정상 부하 (연결 수, 처리량, 지연)  → k6 (k6/websockets)
느린 클라이언트 재현              → Java raw socket (SO_RCVBUF 축소 + read 안 함)
네트워크 열화 시뮬레이션          → Toxiproxy
서버 측 관측                     → WebSocketMessageBrokerStats + Micrometer + jcmd + JFR
힙 문제                          → -XX:+HeapDumpOnOutOfMemoryError + Eclipse MAT
```

---

## 12. 함정 목록

| 함정 | 왜 시간만 태우나 |
|---|---|
| **Netty로 재작성** | 30명에서 측정 가능한 차이 없음. 11가지 직접 구현, 2~3주 |
| RabbitMQ STOMP relay 벤치마크 | 설치·플러그인·권한에 1~2일, 결과가 예측 가능 |
| 단일 머신 100만 연결 도전 | 클라이언트 장비가 부족 |
| WebRTC SFU까지 손대기 | 채팅 트러블슈팅을 영원히 못 함 |
| Kafka로 채팅 브로커 | 파티션당 순서 보장/컨슈머 그룹 모델이 안 맞음 |
| JMeter로 WebSocket 부하 | **JMeter가 먼저 죽는다** |
| "저수준이 좋다" 전제로 시작 | **결론이 정해진 실험은 데이터가 안 나온다** |

---

## 13. 미확인 항목

- Spring 팀 공식 STOMP 부하 벤치마크 수치
- Netty 워터마크 값 선정에 대한 메인테이너 공식 가이드
- LINE / 카카오 / 네이버 / 우아한형제들의 WebSocket slow consumer 1차 자료
- Slack의 slow client / 백프레셔 / 재연결 스톰 대응 상세
- `DefaultSubscriptionRegistry` 캐시 상한 1024 초과 시 실측 성능 열화
