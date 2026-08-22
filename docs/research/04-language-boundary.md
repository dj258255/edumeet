# 04. 언어 선택의 경계 — Netty / Go / C++와 국내 채용 실태

> 조사일 2026-08-22 · GitHub raw 소스·pom/gradle·공식 JEP·논문 PDF 직접 확인분 다수

## 0. 한 줄 결론

> **경계선은 "언어"가 아니라 "패킷당 하는 일"이다.**

```
[1] 픽셀/샘플을 만지는가? (디코드·인코드·믹싱·필터)
      → C/C++ + 손으로 쓴 SIMD 어셈블리. 예외 없음.

[2] 커널/NIC 수준에서 초당 수천만 패킷?
      → C(eBPF/XDP) 또는 C(DPDK), 혹은 Rust(aya).
        단 로더·제어 데몬은 Go

[3] RTP를 파싱·재작성·전달만? (SFU)
      → GC 언어로 충분. Go(LiveKit/Pion), JVM(Jitsi JVB)

[4] 수십만~수백만 연결 유지 + fan-out (채팅/게이트웨이)
      → Go, JVM(Netty), Erlang/Elixir 전부 실증됨

[5] 시그널링·인증·REST API·비즈니스 로직·DB
      → 생태계와 팀 역량으로 결정
```

---

## 1. ★ 가장 강력한 증거 — LiveKit이 자기 스택에 그은 경계

| 컴포넌트 | 빌드 플래그 | 베이스 이미지 |
|---|---|---|
| **SFU 본체** (패킷 포워딩) | `CGO_ENABLED=0 GOOS=linux ... go build -a -o livekit-server` | 없음 — **C 링크 0** |
| **Egress** (녹화/트랜스코딩) | `CGO_ENABLED=1 ... go build -a -o egress` | `FROM livekit/gstreamer:1.24.12-dev` + go-gst |

**같은 팀, 같은 언어(Go), 같은 제품군인데 "코덱을 만지느냐" 하나로 C 링크 여부가 갈린다.**

— https://github.com/livekit/livekit/blob/master/Dockerfile
— https://github.com/livekit/egress/blob/main/build/egress/Dockerfile

### 보조 증거 2건

**rav1d — 언어를 바꿔도 어셈블리는 못 바꾼다**
dav1d를 c2rust로 Rust 포팅한 rav1d는 *"incorporating dav1d's asm-optimized functions"* 방식이고
결과는 *"about 5% slower"*. 프로파일링에서 양쪽 모두 동일 어셈블리 함수 호출.
**→ 진짜 경계는 "C/C++ vs GC 언어"가 아니라 "SIMD 어셈블리 커널을 호출할 수 있는 언어인가"다.**

**Ant Media — Java 미디어 서버조차 코덱은 C로 내려간다**
GitHub Languages는 `Java 6,463,197`로 사실상 100% Java.
그런데 `pom.xml`에 `org.bytedeco:ffmpeg-platform`, `cuda-platform`, `srt-platform`, `libndi.so.6`,
`-Djava.library.path=.../native-linux-x86_64`.

---

## 2. 코덱 — 어셈블리가 고급 언어보다 많다

GitHub Languages API 실측 (2026-08-22, 바이트)

| 프로젝트 | C | C++ | **Assembly** |
|---|---:|---:|---:|
| FFmpeg | 70,096,980 | 233,836 | **6,563,662** |
| **dav1d** (AV1 디코더) | 2,616,543 | — | **9,265,102** ← C의 3.5배 |
| **x265** | 288,316 | 5,466,880 | **6,639,329** ← C++보다 많음 |
| x264 | 3,675,795 | — | 1,911,035 |
| libvpx | 12,882,606 | 1,683,059 | 919,039 |
| SVT-AV1 (%) | 87.89% | 6.57% | 3.52% + 0.57% |

### ★ FFmpeg 공식 asm-lessons (인용 가치 최상)

> *"**It's very common to get a 10x or more speed improvement from writing assembly code**"*
> *"**intrinsics are typically around 10-15% slower than hand-written assembly**"*
> *"**recent tests in e.g. the dav1d project showed around a 2x speedup from this automatic
> vectorisation, while the hand-written versions could reach 8x.**"*
— https://github.com/FFmpeg/asm-lessons/blob/main/lesson_01/index.md

- libwebrtc: *"**WebRTC is written in C++20**, but with some restrictions"*
- libaom: 빌드 필수 요구사항에 *"yasm or a recent version (2.14 or later) of **nasm**"*
  — **어셈블러가 빌드 의존성**

### GC 언어 코덱의 현실

**jcodec (Java)** README가 스스로 한계를 적어놓는다:
H.264는 **main profile 디코더만 / 인코더는 baseline만**, VP8은 디코더·인코더 모두 **"I frames only"**(인터 예측 없음).
"Future development"에 *"Improve H.264 encoder: **CABAC**, rate control"*,
*"**Performance optimize** H.264 decoder"* 가 미해결로 등재. 최신 릴리스 **2018-02-01**.

**pion/opus (Go)** README의 동기 4가지: *"empower interesting use cases / **learning** / safety / **inspire**"*
— **성능이 목록에 없고**, 저자가 *"Go is a power language, but **lacking in media libraries**"* 라고 직접 쓴다.

> **프로덕션 실시간 트랜스코딩에 쓰이는 GC 언어 코덱 구현체는 찾지 못했다.**

---

## 3. 미디어 서버 — 컴포넌트별 언어

| 서버 | 미디어 데이터 플레인 | 제어/시그널링 | 트랜스코딩 |
|---|---|---|---|
| SRS | C++ (ANSI C++98 + ST 코루틴) | 동일 프로세스 HTTP API | ✗ |
| **Janus** | C (4.97MB) | JSON over HTTP/WS/RabbitMQ/MQTT | ✗ |
| **mediasoup** | **C++ worker 서브프로세스** (4.71MB) | **Node.js/TS 또는 Rust** (1.39MB) | ✗ — *"Does mediasoup transcode? **No.**"* |
| **Pion** | **순수 Go 1.78MB — C/C++/Assembly 0바이트** | Go | ✗ |
| **LiveKit** | **순수 Go 3.24MB (CGO_ENABLED=0)** | Go | Egress만 별도 |
| **Jitsi JVB** | **Kotlin 3.39MB / Java 0.62MB (JVM)** | Jicofo(Kotlin) + Prosody(Lua) | ✗ |
| **Kurento** | C 2.89MB + C++ 2.03MB (GStreamer) | **Java 2.67MB** | ○ |
| Ant Media | Java + **JNI 네이티브 FFmpeg** | Java | ○ |
| Red5 | Java 5.12MB | Java | 미확인 |
| Wowza | JVM | Java 모듈 API | ○ (내부 미확인) |
| MediaMTX / Galène / ion-sfu | Go (Galène 빌드가 `CGO_ENABLED=0`) | Go | ✗ |
| (대조) OvenMediaEngine (한국 AirenSoft) | **C++ 7.61MB** (Java는 3,876B) | — | ○ |

### ★ Jitsi Videobridge — JVM SFU 프로덕션 반례

> *"**Jitsi Videobridge relays video, rather than mixing.**"*

공식 성능 평가: 단일 컨퍼런스 **비디오 1,056 스트림 + 오디오 1,056 스트림**,
평균 **550.4 Mbps**, **CPU 평균 20.3%**, JVM 힙 3GB 제한에 **RSS 1,500MB 미만**,
하드웨어 quad-core Xeon E5-1620 v2 @ 3.70GHz.

> **캐비엇**: 게시일이 없고 CPU 세대(2013년 출시)로 보아 오래된 측정.
> 또한 **JVB는 이미 Kotlin이 Java의 5.4배** — "Java SFU"라는 통념은 갱신이 필요하다.

### SFU vs MCU — 이 논증의 기술적 핵심

- **SFU**: *"forwards a copy of each stream... **without manipulating any underlying packets**"*
- **MCU**: *"**composites the output streams**... **re-encodes them as one feed**"*
- 용량: MCU *"10's of ports or low 100's at best"* vs SFU *"**1,000's of ports or low 10,000's**"*
  (애널리스트 추정 범위 캐비엇)

> SFU는 RTP를 파싱·재작성·전달만 하고 **픽셀을 만지지 않는다.**
> 패킷당 수십~수백 나노초 규모이고 페이로드는 통과만 하므로 **수 ms GC 일시정지를 지터 버퍼가 흡수한다.**
> MCU/트랜스코더는 매 프레임을 디코드+인코드해야 하고 이는 매크로블록 단위 SIMD의 세계다.

### mediasoup이 밝힌 C++ 채택 이유 — 성능만이 아니다

> *"mediasoup launches a set of C++ child processes (media workers)... This approach leads to
> **a media worker design not tied to the internals of Node.js or V8** (which change in every new release)."*

그리고 저장소 구조가 무엇이 교체 가능한지 보여준다 —
`Cargo.toml` workspace members = `["rust", "rust/types", "worker"]`.
**Rust 레이어는 C++ worker를 대체하지 않고 Node.js 제어 레이어를 대체한다.**

### 반대로 "관성/선호"에 가까운 지점

Janus 공식 문서는 C 선택 이유를 *"a small footprint (**hence a C implementation**)"* 라고 적는다
— **성능이 아니라 풋프린트**다. 같은 일(RTP 릴레이 + JSON 시그널링)을
Pion/LiveKit/Galène/ion-sfu/MediaMTX가 Go로 프로덕션에서 하고 있으므로
**SFU 릴레이 자체에는 C가 필수가 아니다.**

---

## 4. 패킷/커널 레벨

- **DPDK**: C 92.7MB / C++ 27.8KB. PMD가 *"bypassing the kernel's network stack"*
- **eBPF/XDP 정확한 서술**: 커널 프로그램의 사실상 표준 소스 언어는
  **제약된 C → LLVM/clang → BPF 바이트코드**(verifier + JIT). **Rust(aya)가 유일한 실질 대안**.
  **로더·제어 코드는 임의 언어** — `cilium/ebpf` README:
  *"ebpf-go is a **pure Go library** that provides utilities for loading, compiling, and
  debugging eBPF programs"*
- **Katran (Meta)**: *"Katran is a **C++ library and BPF program**"*. 제어 플레인은 thrift/gRPC
- ★ **Cloudflare Unimog** — 경계 논증의 네트워크 버전 완벽 사례
  > *"**Although the XDP programs are written in C, xdpd itself is written in Go.**"*
  > *"the core C code needed to implement an XDP-based L4LB is relatively modest
  > (**about 1000 lines of C**, both for Unimog and Katran)"*
  > *"**Unimog costs less than 1% of the processor utilization**"*

  **→ 패킷당 실행되는 1,000줄만 C, 그걸 관리·배포·상태관리하는 데몬 전체는 Go.**
- **Cilium**: Go 23.07MB(에이전트/오퍼레이터/CLI) : C 2.63MB(eBPF 데이터패스) ≈ **8.8:1**

### XDP vs DPDK 실측 (CoNEXT '18 논문 원문 PDF)

Høiland-Jørgensen et al., *"The eXpress Data Path"*, ACM CoNEXT '18.
Xeon E5-1650 v4 @3.60GHz, ConnectX-5 Ex 100Gbps, Linux 4.18-pre / DPDK 18.05, **64바이트 패킷**.

| 시스템 | 단일 코어 Mpps |
|---|---:|
| **DPDK** | **43.5** |
| **XDP** | **24** |
| Linux raw (iptables raw) | 4.8 |
| Linux conntrack | 1.8 |

- 두 시스템 모두 코어 수에 선형 스케일하다 **PCI 버스 한계 115 Mpps**에서 수렴
- 패킷당 **XDP 41.6ns vs DPDK 22.9ns** — 차이 18.7ns = 3.6GHz에서 **67 클럭 사이클**
- 저자: *"every micro-optimisation counts; for example, we measure an overhead of
  **1.3 nanoseconds for a single function call**"*
- DPDK는 busy polling이라 *"its CPU usage is always pegged at 100%"*
- 포워딩에서 same-NIC 조건이면 **2코어 이상부터 XDP가 DPDK를 추월**

> **67 클럭 사이클이 승부를 가르는 레이어에서는 GC나 런타임이 논의 대상조차 되지 않는다.
> 이것이 C/C++ 필수 경계의 하한선이다.**

---

## 5. Netty 실사용처 — 검증표

### ★ 먼저 오해 정정: Kafka 브로커는 Netty를 쓰지 않는다

| 검증 | 결과 |
|---|---|
| `gradle/dependencies.gradle` (trunk) | `netty` **0건**. 대신 `jetty: "12.0.37"` |
| `build.gradle` | `netty` **0건** |
| `LICENSE-binary` | jetty-* 10줄, **netty 0줄** → 배포 바이너리에 미포함 |
| `SocketServer.scala` | `import java.nio.channels.{Selector => NSelector, _}` / `NSelector.open()` |
| `clients/.../network/Selector.java` (71KB) | 순수 NIO |
| Netty 도입 KIP | **0건** |

**뉘앙스**: Kafka 3.x 배포판에는 netty jar가 있었다. 3.9 `build.gradle` 주석이 이유를 밝힌다 —
`// be explicit about the Netty dependency version instead of relying on the version set by
ZooKeeper (potentially older and containing CVEs)`.
**ZooKeeper 클라이언트의 transitive 의존성**을 CVE 회피용으로 고정한 것이고,
4.0에서 ZooKeeper 모드가 제거되며 사라졌다.

### 확인된 사용처

| 시스템 | 판정 | 컴포넌트 | Netty | 기본값? |
|---|---|---|---|---|
| **Elasticsearch** | 확인 | `modules/transport-netty4` / `Netty4Plugin` | 4.1.135 | HTTP + 노드간 **둘 다 기본** |
| **OpenSearch** | 확인 | `Netty4ModulePlugin` (+HTTP/3·QUIC) | 4.2.17 | 둘 다 기본 |
| **gRPC-Java** | 확인 | `grpc-netty`, `grpc-netty-shaded` | 4.2.16 | **main transport** |
| **Cassandra** | 확인 | `transport/Server.java`(CQL) + internode(4.0+) | 4.1.130 | 기본 |
| **Spring WebFlux** | 확인 | Reactor Netty | 4.2.17 | **기본 임베디드 서버** |
| **Apache Pulsar** | 확인 | `BrokerService` (+io_uring) | 4.2.17 | 기본 |
| **Vert.x** | 확인 | vertx-core 전체 | 4.2.17 | 기본 |
| **Akka** | **제거됨** | Netty 3.x classic remoting → 2.6 deprecated → **2.8.0에서 파일 삭제** | — | Artery로 대체 |
| **HBase** | 확인 | `RpcServerFactory` → `NettyRpcServer` | 4.1.135 | **기본 RPC 서버** |
| Hadoop | 부분 | MR `ShuffleHandler`, HDFS `DatanodeHttpServer` | 4.1.137 | **코어 RPC는 NIO** |
| ZooKeeper | **옵션** | `NettyServerCnxnFactory` | 4.1.136 | **기본은 `NIOServerCnxnFactory`** |
| **Armeria (LINE)** | 확인 | 전체 | 4.2.16 | — |
| **Netflix Zuul 2** | 확인 | `com.netflix.zuul.netty.server.Server` | 4.2.16 | 기본 |
| **Twitter Finagle** | 확인 | 전체 | 4.1.100 | 기본 |
| **Google Cloud Java** | 확인 | `gax-grpc` → `grpc-netty-shaded` | shaded | 모든 `google-cloud-*` |
| **Minecraft Java Edition** | 확인 | Mojang 공식 런처 메타데이터로 검증 | **4.2.15** | 네이티브 epoll/kqueue 번들 |
| Apache Spark | 확인 | `network-common` block transfer | 4.2.17 | `-Dio.netty.allocator.type=pooled` |
| Apache Flink | 확인 | `flink-shaded-netty` | shaded | — |

**공식 Adopters 132개** — Apple, Google, Netflix, LinkedIn, Uber, **Kakao Talk**(Android 푸시 서버),
**Facebook Instagram IG Direct**, Sina Weibo IM 게이트웨이, LeanCloud, Layer, Minecraft

### 거버넌스

| 항목 | 상태 |
|---|---|
| 창시자 **Trustin Lee(이희승)**, JBoss 기원 | 확인 |
| **Norman Maurer = Apple Distinguished Engineer, Apple Cloud Services** | 확인 (본인 GitHub 프로필) |
| swift-nio README: *"It's like Netty, but written for Swift."* | 확인 |
| Facebook **Nifty**(Netty 기반 Thrift) | archived (2018) |
| 최신 릴리스 4.2.17.Final(2026-08-04) / 4.1.137.Final(2026-08-06) | **Netty 5는 5.0.0.Alpha5(2022-09) 이후 사실상 중단** |

### ★ 두 가지 추가 사실

**(a) Netty도 커널 경계에서는 C로 내려간다.**
`transport-native-epoll/src/main/c/netty_epoll_native.c` = **38,589바이트의 손으로 쓴 C + JNI**.
pom.xml이 hawtjni로 `CFLAGS=-O2 -pipe -Werror -fvisibility=hidden -D_FORTIFY_SOURCE=2 ...` 를 걸어
공유 라이브러리를 빌드한다.

**(b) 이 프로젝트는 이미 Netty를 쓰고 있다.**
`EduMeet-0.0.1-SNAPSHOT.jar`(172개 jar) 안에 **netty 4.1.122.Final 10개 모듈** —
`buffer / codec / codec-http / codec-http2 / common / handler / resolver / transport /
transport-classes-epoll / transport-native-unix-common`.
경로는 `spring-boot-starter-data-redis → lettuce-core 6.6.0.RELEASE`와
`AWS SDK → netty-nio-client 2.32.9`. HTTP 서버는 `tomcat-embed-core 10.1.42`.

> **"Netty를 쓸 것인가"가 아니라 "이미 쓰고 있는데 어디에 쓰이는지 아는가"가 질문이다.**

---

## 6. Go vs Java

### 6-1. 구조적 근거

**JEP 444가 Java의 한계를 직접 서술**
> *"the number of available threads is limited because the JDK implements threads as wrappers
> around operating system (OS) threads. OS threads are costly... the number of threads often
> becomes the limiting factor long before other resources, such as CPU or network connections,
> are exhausted."*

그래서 Java는 **비동기(=Netty)** 로 우회했고, JEP 444는 그 대가도 인정한다:
> *"it comes at a high price... **Stack traces provide no usable context, debuggers cannot step
> through request-handling logic, and profilers cannot associate an operation's cost with its caller.**"*

**Go 쪽 1차 출처**
- 고루틴 초기 스택 `stackMin = 2048` (`src/runtime/stack.go` L78, master 직접 확인)
- FAQ: *"little overhead beyond the memory for the stack, which is just a few kilobytes...
  practical to create **hundreds of thousands of goroutines**"* / *"creates statically-linked
  binaries by default"*
- 설계 배경(Rob Pike, Go at Google): 2007년 구글 C++ 바이너리 측정 —
  소스 4.2MB가 `#include` 전개 후 **8GB**, *"impractical to build Google server binaries on a
  single computer"*

### 6-2. GC 특성 비교

| | Go | JVM |
|---|---|---|
| 알고리즘 | concurrent tri-color mark-sweep, **non-moving**, **비세대형** | 세대형 G1 / **Generational ZGC** / Shenandoah |
| STW | Go 1.8부터 *"usually under 100 microseconds and often as low as 10 microseconds"* | JEP 333(JDK11) ≤10ms → JEP 376(JDK16) → **sub-millisecond** |
| 강제 GC | **`forcegcperiod = 2 * 60 * 1e9`** — 현재 master `proc.go` L6528에 그대로. **힙 증가와 무관하게 2분마다** | 없음 (할당률 기반) |
| 최신 | Go 1.26 **Green Tea GC 기본 활성**, *"10—40% reduction in GC overhead"* | JDK 21 JEP 439 → JDK 24 JEP 490 |

### 6-3. 마이그레이션 사례

**Twitch (Python → Go)**
> *"Our IRC-based chat system was first written in Go in late 2013... **over 500,000 concurrent
> users from each physical host without special tuning**."*
프로세스당 고루틴 **150만 개**. GC pause **1.2 수십 초 → 1.5 ~200ms → 1.6 ~100ms → 1.7 ~1ms**
(1.5→1.7 **100배**). 병목: TCP finalizer, mark termination 중 stack shrinking, NUMA 마이그레이션.

**★ Discord (Go → Rust, Read States)**
- 초당 수십만 캐시 업데이트, 초당 수만 Cassandra 쓰기
- **2분마다 정확히** 레이턴시 스파이크
- 원인: *"the garbage collector needed to scan the entire LRU cache in order to determine if
  the memory was truly free from references"* — **쓰레기가 많아서가 아니라 강제 GC가 캐시 전체를 스캔**
- Go **1.9.2**(1.8/1.10도 무효). Rust 후 *"average time is now measured in microseconds"*,
  LRU를 **800만 건**으로 확대
- **`forcegcperiod = 2분`은 2026년 현재 Go master에도 그대로 있다.**

**★ Discord의 채팅 게이트웨이는 Go가 아니라 Elixir/Erlang이다.**

**기타**: Iron.io 서버 **30 → 2대** / Malwarebytes 분당 100만 요청, **100 → 4× c4.large** /
Uber geofence가 1년 뒤 사내 최고 QPS, **p99 < 100ms** / Dropbox **Go 약 20만 줄** /
Cloudflare PGO로 3,000+ 코어에서 2.3~3.5% 절감

### 6-4. 연결당 메모리 — "Go면 2KB"는 신화

**Go — VK(Mail.Ru), 300만 WebSocket**
연결당 고루틴 2개(4KB) = **24GB** + 읽기버퍼 **24GB** + 쓰기버퍼 **12GB**
→ *"아무것도 안 하는 앱에 이미 72GB"*.
netpoll 직접 사용 + sync.Pool + 워커 128개 + zero-copy upgrade로 **48GB + 24GB 절감**.

**Java — MigratoryData (벤더 벤치마크)**
- 2013: **1,200만 동시 WebSocket**, JRE 1.7, JVM 54GB → 연결당 약 **4.5KB**(도출값)
- 2016: **10,000,100 연결**, **Zing JVM 무튜닝**, 18억 메시지 측정 —
  **median 13.8ms / p95 21.2ms / p99 24.4ms / max 126ms**. 표준 JVM(CMS) 대비 **p99 585ms → 25ms**

**Erlang**: WhatsApp 2012년 서버당 **200만 TCP 연결**, 피크 280만

**Hashrocket websocket-shootout (2016)**: C++ 100% 기준
Clojure(JVM) 82%, Elixir 73%, Go 73%, Node 39%, Ruby <2%

### 6-5. 반론 — 현대 JVM

| JEP | JDK | 내용 |
|---|---|---|
| 333 | 11 | ZGC 실험, *"pause times should not exceed 10ms"* |
| 376 | 16 | 스레드 스택 처리를 safepoint 밖 concurrent로 → **sub-ms 진입** |
| 377 | 15 | 프로덕션화. 단 *"the default GC... remains G1"* |
| **439** | **21** | **Generational ZGC** |
| **490** | **24** | 비세대 ZGC 제거 |
| **519** | **25** | Compact Object Headers 정식. SPECjbb2015 *"22% less heap space and 8% less CPU time"* |

Oracle: *"supporting heaps up to **16TB**... while maintaining **sub-millisecond pause times**"*.
Cassandra 벤치마크에서 비세대 ZGC는 동시 클라이언트 75에서 allocation stall, 세대형은 275까지 유지.

**Netflix**: JDK 21+ 기본 GC를 G1 → Generational ZGC로 전환.
**캐비엇: 원문 403이라 본문 미확인.** 세부 수치는 2차 요약만 확보 — 인용 시 명시 필요.

**Virtual Threads**: JEP 444 — 플랫폼 스레드 풀 200개로 200 tasks/s인 워크로드가
virtual thread로 **약 10,000 tasks/s**, 100만이면 **약 1,000,000 tasks/s**.
Brian Goetz: *"it would take **millions of virtual threads to use even 1G of memory**"*

- **JDK 21의 결함 = pinning.** Netflix가 실제로 당했다 (§02 문서 참조)
- **JDK 24 JEP 491이 해결**: *"This will eliminate nearly all cases of virtual threads being
  pinned to platform threads."*
- Helidon Níma = *"the first Java microservices framework based on virtual threads"*,
  *"**replaces Netty** in the Helidon ecosystem"*

> **Virtual Threads vs goroutines 정량 비교: 신뢰 가능한 1차 출처 없음.**
> 검색에 나오는 "Java 125MB vs Go 28MB" 류는 전부 재현 불가한 Medium 개인 글 — **인용 비권장**

### 6-6. 정리

Go의 실질적 강점은 "GC가 빨라서"가 아니라
① **동시성 모델의 언어 내장** (JDK 21+가 정면 겨냥, JDK 24에서 마지막 결함 제거)
② **운영 단순성** (정적 단일 바이너리, JVM 플래그 없음, 작은 이미지, 빠른 컴파일)
③ **warm-up 없음** (JDK 25 JEP 514/515 AOT가 겨냥)

반대로 **Go GC는 비세대형·non-moving**이라 거대한 장수 in-memory 캐시에 취약함이
Discord로 실증됐고, **JVM은 정확히 그 지점에서 강하다.**

---

## 7. 국내 채용 실태 (2026-08 기준)

### 7-1. SOOP (구 아프리카TV)

| 직무 | 요구 언어 | 경력 |
|---|---|---|
| LIVE 방송 서버 개발 | **C, C++** (IOCP/EPOLL/SELECT, P2P) | 5년+ |
| LIVE 방송 시스템 개발 | **C, C++** | 3년+ |
| LIVE/VOD 미디어 서버개발 | **C, C++** (Transcoder/Packetizer, 신규 코덱) | 2년+ (만료) |
| 미디어 기술 개발 | **C, C++** (Socket, 스트리밍·트랜스코딩) | 3~10년 |
| C++ 설치형 앱 | **C++** (Qt, FFmpeg, WebRTC, RTMP/SRT/HLS) | 5년+ |
| Global 서비스 Back-end | **Node.js, TS** (NestJS) | 3년+ |
| AI Serving Engineer | Node.js, Python (K8s, gRPC, Kafka) | 4년+ |
| SRE / Senior DevOps | Python, Go, Rust(우대) | 5년+ |

> **핵심 발견 — SOOP 공고 전체에서 Java/Kotlin/Spring 요구 공고 0건.**
> 스택은 **C/C++(미디어·방송 코어) + Node.js/NestJS(글로벌 백엔드) + Python/Go(인프라)**.

**SOOP은 연 1회 신입 공채에 스트리밍 직무를 포함한다.**
2025 공채 15개 직무 중 개발직 = PC/Global P2P 클라이언트, **LIVE 방송 서버 개발,
미디어 서버 개발**, iOS, Android, 모바일 SDK, Global Back-end(NestJS).
**단 C/C++ 트랙이며 Java 트랙은 없다.**

### 7-2. 네이버 / 치지직

- **치지직 전담 백엔드/서버 공고 — 미확인.** recruit.navercorp.com은 JS SPA라 검색 실패
- NAVER Vietnam VVX팀 Middle/Senior Backend: **Java, Kotlin** + Spring, Kafka, K8s (4년+)
  — 단 치지직 전담팀이 아니라 팀 소개문에 치지직이 언급된 것. **단정 불가**
- NAVER Clip/SmartID Backend: **Java, Kotlin** + Spring (2~3년+)

### 7-3. 카카오 계열 — 확인 8건 전부 Java/Kotlin + Spring, C++ 0건

- **카카오엔터프라이즈 지식그래프 백엔드**: Java/Kotlin + Spring, 경력 **"신입~15년"** ← 신입 가능
- 카카오엔터프라이즈 검색플랫폼 서버: Java, Spring, Kotlin (3~12년)
- 카카오커머스 서버: Java 필수, Kotlin 우대 (5년+)
- 카카오엔터테인먼트(카카오TV) 서버: Kotlin, MongoDB

### 7-4. LINE / LY Corporation

| 직무 | 언어 | 스택 | 경력 |
|---|---|---|---|
| **Messaging Backend Engineer** | **Java, Kotlin, Scala** | Spring, Redis, HBase, **Kafka**, MongoDB, K8s, Prometheus/Zipkin | 3년+ |
| Backend Engineer Senior/Staff (Messaging) | Java, Kotlin, Scala | 동일 | 시니어 |

> **"실시간 메시징 플랫폼"을 명시적으로 JVM으로 뽑는 가장 명확한 사례.**
> C/C++·Go는 대안 언어일 뿐 필수가 아니다.

### 7-5. 하이퍼커넥트 (아자르)

**Senior SWE, Backend — Seoul Studios**: **Java, Kotlin** + Spring/WebFlux, Redis, DynamoDB,
Kafka, K8s / **우대: "WebSocket·WebRTC 기반 실시간"**, Flink, Spark, ES (5년+)

> **Java 개발자가 실시간 도메인을 만지는 구체적 접점.**

기술블로그 미디어 서버 글 2편을 직접 읽었으나 **구현 언어를 명시하지 않는다.**
백엔드가 JVM인 것은 Spring Transactional, WebClient OOM, Java Stomp Client 성능 개선 글로 확인.

### 7-6. OTT

| 회사 / 직무 | 언어 | 경력 |
|---|---|---|
| 티빙 백엔드 | **Java, Kotlin** + Spring, JPA, gRPC, Oracle, Redis | 3년+ |
| 티빙 Backend (Account/API/Billing) | Rust, Go, Kotlin, Java 중 1+ / **우대: Low Latency** | 3년+ |
| **웨이브 미디어 서버 개발자** | **Node.js, Go** / **HLS, DASH 등 미디어 전송 프로토콜** | 6~15년 |
| 웨이브 백엔드 (상품/빌링) | Go, Java, Node.js, Kotlin, Python 중 1+ | 3년+ |
| 쿠팡플레이 Staff Back-end | Go, Java, JS, Node.js / **우대: 스트리밍 미디어 엔지니어링** | **10년+** |
| 쿠팡플레이 Staff Video Live Streaming | 미명시 / **H.264/HEVC/VP9/AV1/AAC 코덱, DRM** | 미확인 |
| **왓챠 서버 개발 (주니어)** | 언어 미지정, "익숙한 언어 2개 이상" | **신입** |

> **웨이브 미디어 서버 개발자가 Node.js/Go를 요구한다는 점이 중요하다.**
> "미디어 서버 = C++"도, "미디어 서버 = Java"도 아니다.
> **HLS/DASH 패키징·전달 계층은 언어 선택이 열려 있다.**

### 7-7. 게임사 — 이중 구조

| 회사 / 직무 | 언어 | 경력 |
|---|---|---|
| 넥슨 [메이플스토리] 서버 | **C++** (C#·MS-SQL·Redis 병기) | 3년+ |
| 엔씨소프트 [Project NL] 차세대 리니지 서버 | **Modern C++**, MS-SQL, Windows | 3년+ |
| 펄어비스/넷텐션 (ProudNet) | **C++**, 멀티스레딩, 소켓 | 2년+ |
| 크래프톤 Server Programmer | C++ 또는 **C#(.NET)** | 5년+ |
| **넷마블 서버 개발자** | **Java + Spring Boot**, MySQL/MSSQL, Docker/K8s | 5년+ |

> **"게임 시뮬레이션 루프 = C++, 그 주변 플랫폼 = JVM"의 이중 구조.**

### 7-8. Netty 채용 수요

**잡코리아 기준 "Netty" 공고 총 10건 — 전부 금융/PG/IoT/무선인증 등 Java 백엔드.
스트리밍 회사 0건.**

> **Netty는 "실시간 스트리밍 입장권"이 아니라 "Java 고성능 백엔드 심화 역량"으로 읽힌다.**

기타 검색: **"FFmpeg" 29건** — 영상처리/CCTV/임베디드/AI 비전 중심, 언어 명시된 건 전부 C++,
**Java 명시 0건**, 대부분 3~5년+ / **"RTMP" 3건** 전부 경력직 /
**"WebRTC" 39건** 대부분 중소기업 IP카메라·CCTV·임베디드

---

## 8. 레이어별 언어 매핑 (종합)

| 레이어 | 하는 일 | 지배 언어 | 국내 사례 | Java 존재? |
|---|---|---|---|---|
| **미디어 코어** (코덱·트랜스코딩) | FFmpeg, x264, SIMD | **C / C++** | SOOP 미디어 서버개발, 쿠팡플레이 Video Live Streaming | 사실상 없음 |
| **실시간 방송 세션 서버** | IOCP/EPOLL, P2P | **C / C++** | SOOP LIVE 방송 서버 (5년+) | 없음 |
| **게임 시뮬레이션 서버** | 틱 루프, 상태 동기화 | **C++** (일부 C#) | 넥슨, 엔씨, 펄어비스 | 없음 |
| **미디어 전달·패키징** (HLS/DASH origin) | 세그먼트 생성·CDN | **혼재** (Node.js, Go, C++) | 웨이브 미디어 서버 | 드묾 |
| **SFU / 시그널링** | WebRTC 라우팅 | C++ · Go · **JVM 가능** | 하이퍼커넥트(언어 미확인) | 가능 (Jitsi) |
| **실시간 메시징·채팅** | 소켓 유지, 팬아웃 | **Java / Kotlin** | **LINE Messaging, 우아한형제들 SSE, 카카오톡 relay** | **핵심 영역** |
| **서비스 백엔드** | API, 트랜잭션 | **Java / Kotlin + Spring** | 카카오 전 계열, 티빙, 하이퍼커넥트, 넷마블 | **압도적** |
| **인프라 / SRE** | K8s, IaC | Go, Python | SOOP SRE | 없음 |

---

## 9. Java를 유지하며 실시간 도메인으로 가는 경로 — 실재한다

1. **채팅/세션 서버 자체가 JVM으로 만들어진다.**
   카카오톡 relay & session manager는 **C++에서 Kotlin+Netty로 갔다** — 반대 방향이 아니다.
   이유가 명시적으로 *"파트내 인적 리소스 불균형: C++/JAVA"* 와 *"10년 후 유지보수"* 였고,
   결과가 **가용 인력 3배 증가**였다.
2. **Slack의 실시간 메시지 버스가 Java 티어다** (QCon 2018 원문).
3. **LINE 메시징 서버가 Java + Spring + Armeria**이고, Armeria는 LINE이 만든 Netty 기반 프레임워크다.
4. **미디어 서버조차 Java 구현체가 프로덕션에 있다** — Jitsi Videobridge, Ant Media Server.
   즉 **"스트리밍 = C++"은 코덱 계층에만 참이다.**
5. **Netty가 다리다.** gRPC-Java, Elasticsearch, Spark, Pulsar, Cassandra, Zuul, Finagle,
   Armeria, Lettuce, Minecraft, Instagram IG Direct, 카카오톡 푸시 서버가 전부 Netty다.

### 단, 도착지가 갈린다

**Java/Kotlin으로 갈 수 있는 곳**: 실시간 메시징·채팅·시그널링·알림·구독/빌링 플랫폼
**미디어 코어(트랜스코딩·코덱·방송 세션 서버)는 C/C++** 이며 채용 공고가 이를 반복 확인해준다.

**신입에게 가장 현실적인 순서**
```
(a) Java/Kotlin + Spring 으로 백엔드 취업
(b) 실시간 기능(WebSocket/SSE/Kafka 팬아웃)을 다루는 팀으로 이동
(c) 스트리밍 회사의 플랫폼 백엔드로 이직
```
미디어 코어가 목표라면 애초에 **C/C++ 트랙(SOOP 신입 공채 등)으로 진입**해야 하며,
이는 Java 경로와 다른 트랙이다.

---

## 10. 흔한 함정 3가지 (정정)

1. **"Kafka는 Netty"** — **틀림.** 브로커는 raw NIO, 나머지는 Jetty
2. **"Akka는 Netty"** — **틀림.** Netty 3 기반 classic remoting은 **2.8.0에서 코드 삭제**. 현재는 Artery
3. **"Netty는 순수 Java"** — **부분적으로 틀림.** `transport-native-epoll`은 38KB의 손으로 쓴 C + JNI

---

## 11. 미확인 항목

- 치지직 백엔드/서버 전담 공고 및 언어
- Wowza Streaming Engine의 구현 언어 (비공개 소스)
- 하이퍼커넥트 미디어 서버/WebRTC 엔지니어의 구현 언어
- Cloudflare Realtime SFU 데이터 플레인 언어
- Twilio / Agora / Zoom 서버 미디어 스택
- Virtual Threads vs goroutines 정량 벤치마크 (**인용 비권장**)
- TechEmpower R23 프레임워크별 RPS
- 국내 신입 백엔드 채용의 Java/Spring 비중 통계
