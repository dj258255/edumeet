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
| **방송 모드 3개 (공통/분기)** | [`plan/04-three-broadcast-modes.md`](plan/04-three-broadcast-modes.md) |

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

### 선언은 있는데 아무도 안 쓴다 — 네 번 만났다

같은 모양의 버그를 네 번 만났다. 넷 다 **테스트를 통과하고 있었고, 아무 효과가 없었다.**

```
probes.enabled                 배포 헬스체크가 계속 unhealthy
prometheus.export.enabled      /actuator/prometheus 404. 원인을 세 번 잘못 짚었다
SessionType.isAudioOnly()      참조가 테스트뿐. "오디오 전용" 이 UI 관례로만 존재
sessionType 이 요청에 없음      기능 네 개가 통째로 도달 불가
```

**테스트가 부품을 검증했지, 부품이 연결되어 있는지는 검증하지 않았다.**
테스트가 픽스처를 직접 만들면, 그 픽스처가 실제로 만들어질 수 있는지는 영원히 안 물어보게 된다.

→ [`ops/07-declared-but-unused.md`](ops/07-declared-but-unused.md)

### 방송 HLS 는 합성 대신 delivery 만 만든다

처음에는 LiveKit Egress 로 HLS 를 만들려고 했다.
하지만 `RoomComposite` 가 CPU 4를 요구하는 이유는 여러 참가자 화면을 합성하기 때문이다.
EduMeet 의 방송 모드는 발표자 한 명만 나가므로 합성할 것이 없다.

그래서 HLS 는 직접 만든다.

```
발표자 MediaRecorder 조각 → HTTP chunk → ffmpeg → live.m3u8 + .ts → nginx/hls.js
```

20초 분량 기준 OCI aarch64 2 OCPU ffmpeg 측정 결과다.

| 경로 | real | user CPU |
|---|---:|---:|
| 리먹싱(H264 복사) | 0.084초 | 0.079초 |
| 재인코딩(VP8 → H264) | 5.456초 | 8.633초 |
| 오디오 전용(Opus → AAC) | 0.245초 | 0.282초 |

오디오는 "HLS audio mode" 가 아니라 **audio-only HLS delivery** 다.
HLS 는 송출 프로토콜이 아니라 다수 시청자에게 나눠 주는 delivery 형식으로 본다.

→ [`plan/04-three-broadcast-modes.md`](plan/04-three-broadcast-modes.md),
[`plan/05-own-hls.md`](plan/05-own-hls.md),
[`plan/06-webrtc-sfu-100-policy.md`](plan/06-webrtc-sfu-100-policy.md)

HLS 를 파일로 내보내면서 캐시 경계도 같이 나눴다.
`index.html` 과 `live.m3u8` 은 최신성이 중요해서 캐시하지 않고,
해시가 붙은 Vite asset 은 1년 `immutable` 로 둔다.
반면 HLS `.ts` 는 파일명이 방송 재시작 때 재사용되므로 4초만 허용한다.

→ [`performance/11-http-cache-boundaries.md`](performance/11-http-cache-boundaries.md)

HLS 에서는 "어떤 프로토콜인가"보다 **서버가 재인코딩을 하느냐**가 먼저 비용을 가른다.
20초 720p30 합성 소스에서 H264 리먹싱은 real 0.084초, VP8→H264 재인코딩은
real 5.456초였다. 같은 HLS 여도 서버에서 약 65배 차이다.

그래서 송출 화면은 720p30/360p15/audio-only 를 선택하게 하고,
시청 화면은 hls.js 의 live latency·target latency·buffer·bandwidth estimate 를 보여 준다.
또 `hls.js` 를 동적 import 로 분리해 방송 시청 화면 JS 를 402.77KB 에서 4.91KB 로 줄였다.
시청자 부하는 `k6/hls-viewer.js` 로 playlist 와 segment 를 나눠 재도록 했다.
운영 URL 20 VU 측정에서 playlist p95 391.38ms, segment p95 372.41ms,
HTTP 실패 0/1,968 이었다.

→ [`performance/12-hls-codec-benchmark.md`](performance/12-hls-codec-benchmark.md)

### AI 자막은 STT·용어사전·LLM 을 분리한다

현재 파이썬 AI 경로는 **진짜 실시간 자막이 아니다.**
녹음이 끝난 뒤 CLOVA batch STT 결과를 받아 문장 단위로 Java 에 보내는 구조라
`realtime: false`, `approximate_timing: true` 로 남겼다.

실시간 자막에서 중요한 제약은 세 가지다.

| 축 | 판단 |
|---|---|
| 비용 | STT 는 오디오 시간 과금, LLM 은 토큰 과금이다. 단위가 다르다 |
| 속도 | 자막 hot path 에 외부 LLM 호출을 넣으면 모델 지연과 장애점이 매 조각마다 붙는다 |
| 품질 | 한국어 강의 안의 `python`, `Spring Boot`, `WebSocket` 같은 영어 기술어가 흔들린다 |

그래서 hot path 에서는 LLM 을 호출하지 않고, 결정적 용어 사전으로 `python → 파이썬`,
`websocket → WebSocket` 같은 보정만 수행한다. LLM 은 회의 후 요약·검색 색인·자막
정리처럼 지연을 허용하는 경로로 보낸다.

→ [`ops/09-realtime-caption-cost-quality.md`](ops/09-realtime-caption-cost-quality.md)

### 실시간 자막과 문서 요약은 같은 원천, 다른 경로다

자막과 요약은 모두 transcript 에서 나오지만 병목이 다르다.

| 기능 | 먼저 보는 것 | 처리 경로 |
|---|---|---|
| 실시간 자막 | 지연·장애 격리 | streaming STT → 용어 사전 → STOMP |
| 문서 요약 | 토큰·장문 문맥·재시도 | final transcript → LLM batch |
| 검색 색인 | 청킹·메타데이터 | raw/display transcript 분리 후 색인 |

LangChain/LangGraph 는 요약을 map/reduce, 액션 아이템, 검색 색인, 평가 단계로
쪼갤 때 후보로 둔다. 지금처럼 hot path 자막이나 단일 LLM 호출을 감싸는 용도로는
도입하지 않는다.

→ [`ops/10-ai-caption-summary-pipeline.md`](ops/10-ai-caption-summary-pipeline.md)

### MySQL 은 "새로 고른 DB" 가 아니라 유지한 운영 전제다

EduMeet 에서 DB 를 PostgreSQL 로 바꾸지 않은 이유는 PostgreSQL 이 부족해서가 아니다.
이미 MySQL 로 만든 팀 산출물을 인수했고, 목표는 스택 교체가 아니라 운영 경로 정리였다.

H2 로는 InnoDB 잠금, MySQL enum/FK 문법, 네트워크 왕복 비용이 드러나지 않는다.
그래서 N+1·세션 정원·Flyway baseline 은 Testcontainers(MySQL)와 k6 로 재현했다.
새 iMBC 맞춤 프로젝트라면 MS-SQL 을 고르겠지만, EduMeet 에서는 측정 가능한
운영 개선이 더 중요했다.

→ [`ops/08-database-choice.md`](ops/08-database-choice.md)

### nginx 기본값 하나가 WebSocket 을 60초마다 끊는다

같은 코드·같은 부하·같은 경로. 바뀐 것은 설정 한 줄이다.

| `proxy_read_timeout` | 유지 | 조기 종료 |
|---|---:|---:|
| 60s (기본값) | **60.9초** | 3/3 |
| 3600s | 90.1초 | 0/3 |

**에러 로그가 안 남고, 개발 중엔 안 보이고, 트래픽이 있으면 가려진다.**
그래서 이 시험은 일부러 **조용한 연결**을 만든다 — 부하를 걸면 이 버그는 사라진다.

→ [`performance/10-websocket-behind-proxy.md`](performance/10-websocket-behind-proxy.md)

### 워크로드 모양이 다르면 무너지는 곳도 다르다

같은 채팅인데 **방송형(fan-out)과 수업형(대칭)이 서로 다른 데서 무너진다.**

| | 먼저 무너지는 것 |
|---|---|
| fan-out (소수 발행 : 다수 구독) | **e2e 지연** — 200→500명에 45ms → 1,313ms (**29배**) |
| 대칭 (전원이 보내고 받는다) | **연결 수립** — 100→300명에 415ms → 1,313ms |

fan-out 500명에서 **연결은 하나도 안 끊겼다.** 무너진 것은 지연이다.
**상한은 "몇 명이 붙나" 가 아니라 "몇 명까지 채팅답나" 로 결정된다.**

→ [`performance/09-chat-capacity-oci.md`](performance/09-chat-capacity-oci.md)

### 부하 생성기를 측정 대상 안에 두지 않는다

OCI 서버는 **2코어인데 컨테이너가 14개** 돌고 있다(우리 앱은 3개, 나머지는 다른 프로젝트).
`load average 1.13` 이 기준선이다. 여기서 k6 까지 돌리면 **재는 것이 앱의 한계가 아니라
부하 생성기의 방해**가 된다.

```
앱 + MySQL + Redis  →  OCI       측정 대상
k6                  →  노트북     네트워크 너머
```

네트워크 바닥값을 먼저 쟀다 — **p50 15ms / p95 28ms.**
OCI 측정의 지연에는 이만큼이 상수로 얹혀 있다.

→ [`performance/08-network-floor-oci.md`](performance/08-network-floor-oci.md)

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
| [`research/06-media-stack-boundaries.md`](research/06-media-stack-boundaries.md) | ffmpeg·GStreamer·Bento4·RTMP·HLS·DASH·WebRTC 의 역할 경계와 도입 조건 |

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
| [`performance/11-http-cache-boundaries.md`](performance/11-http-cache-boundaries.md) | SPA asset 과 HLS playlist/segment 의 캐시 경계 분리 |
| [`performance/12-hls-codec-benchmark.md`](performance/12-hls-codec-benchmark.md) | H264 리먹싱 vs VP8 재인코딩 vs audio-only HLS 의 CPU·세그먼트 길이 비교 |
| [`performance/13-caption-archive-transcript.md`](performance/13-caption-archive-transcript.md) | final 자막만 비동기 저장해 요약 입력으로 쓰는 경로 |
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
| [`ops/08-database-choice.md`](ops/08-database-choice.md) | MySQL 유지 결정 — PostgreSQL/MS-SQL 대안과 iMBC 맥락 |
| [`ops/09-realtime-caption-cost-quality.md`](ops/09-realtime-caption-cost-quality.md) | 실시간 자막 비용·지연·품질 제약 |
| [`ops/10-ai-caption-summary-pipeline.md`](ops/10-ai-caption-summary-pipeline.md) | 자막 hot path 와 요약 batch path, LangChain/LangGraph 도입 기준 |

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
| LL-HLS | 현재 직접 HLS 는 TS 세그먼트다. 부분 세그먼트(CMAF) 경로가 아직 없다 |
| SRT/RTMP 인제스트 | 확장 방향으로 남긴다. 지금은 MediaRecorder HTTP ingest 로도 송출/배포 경계 검증이 된다 |
| Rust/C++ 미디어 서버 | **프로토콜 바이트를 직접 만지지 않는다.** LiveKit(Go)이 SFU 를 한다 |
| `spring-dotenv` | 마지막 릴리스 2023-05. **모든 값을 `${ENV:기본값}` 으로 받으면 의존성 없이 같은 효과** |

### 아직 쓰지 않는다 — 조건이 오면 도입한다

| | 지금 안 쓰는 이유 | **도입 조건** |
|---|---|---|
| **Redis Pub/Sub** | 단일 인스턴스라 서버 간 전달이 필요 없다 | **인스턴스가 2대가 되는 순간** (붕괴 ⑤) |
| **비동기 배치 저장** | 발행 경로의 DB 쓰기가 브로드캐스트 측정을 가린다 | **다시보기 채팅을 붙일 때** (#61) |
| **WebVTT 자막** | 세그먼트 단위라 지연이 구조적으로 붙는다 | **HLS 배포를 붙일 때.** WebSocket 자막을 대체하는 게 아니라 **경로별 병렬** |
| **CDN** | OCI 는 10TB 무료라 **줄일 것이 없는 구간**이다 | 시청자가 수천 명대로 올라갈 때 |

### ★ Redis 는 refresh token 저장소로만 쓴다 (#70)

**근거를 한 번 잘못 세웠다가 다시 세운 항목이다.**

처음 든 근거는 *"만료된 행을 지우는 배치가 없어 테이블이 무한히 자란다"* 였다. **틀렸다.**

```java
@UniqueConstraint(columnNames = "member_id")   // 회원당 한 행
existsByMemberId(...) -> save(existingToken)   // 갱신이지 추가가 아니다
```

행 수는 **로그인 횟수가 아니라 회원 수**에 비례한다. **배치가 필요한 상황이 아니었다.**
**기술을 놓고 문제를 찾은 것**이다. 순서가 뒤집혀 있었다.

**정직한 근거는 이것이다** — refresh token 은 **도메인 데이터가 아니라 세션 상태**다.

| | |
|---|---|
| TTL 이 본질 | 만료가 데이터의 일부다 |
| 관계형 질의가 없다 | `findByToken` · `findByMemberId` 두 개뿐. **JOIN 이 없다** |
| 영속성이 덜 중요하다 | 잃으면 재로그인하면 된다 |

**결함 수정이 아니라 설계 판단이다.** 그리고 **대가도 있다** —
관계형에서는 `token` 컬럼의 UNIQUE 가 갱신 시 옛 토큰을 자동으로 없애줬는데,
Redis 는 키가 두 벌이라 **직접 지워야 한다.** 그 부분을 테스트로 고정했다.

> **범위를 여기까지로 못 박는다.** 레이트 리밋도 후보였지만 뺐다 —
> **단일 인스턴스면 인메모리로 충분하다.**
> Redis 가 더 필요해지는 건 **다중 인스턴스가 되는 순간**이고, Pub/Sub 과 조건이 같다.
