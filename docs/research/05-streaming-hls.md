# 05. 라이브 스트리밍 — WebRTC / HLS / LiveKit Egress / 지연 측정

> 조사일 2026-08-22 · Apple 스펙·RFC·LiveKit 소스·hls.js 소스 직접 확인분 다수

## 0. 한 줄 결론

> **LiveKit Egress는 표준 HLS만 뽑는다** (LL-HLS 미지원, 소스 확인).
> 그래서 며칠짜리 소재는 "LL-HLS 구현"이 아니라
> **`segment_duration` 튜닝 → PDT 기반 지연 자동 측정 → 수치 개선**이다.

---

## 1. WebRTC vs HLS

### 1-1. 구조적 차이

| 축 | WebRTC | HLS |
|---|---|---|
| 전송 | UDP/SRTP, stateful PeerConnection | HTTP(TCP) 파일 GET, **stateless** |
| 혼잡제어 | 브라우저 내장 대역폭 추정(GCC/transport-cc) | TCP + 플레이어 ABR |
| 손실 복구 | NACK/PLI/FEC (프레임 버림 허용) | TCP 재전송 + 플레이어 버퍼 |
| 확장 | 미디어 서버가 커넥션마다 상태 보유 → **CDN 캐싱 불가** | 세그먼트가 그냥 파일 → **CDN이 전부 흡수** |
| 지연 | 100~300ms | 5~30초 |

LiveKit 공식 블로그 수치:
- HLS baseline **5~30초**, 세그먼트는 *"typically a six-second video file"*
- LL-HLS **2~5초**
- WebRTC *"upper bound end-to-end latency of **300ms**"*, 지구 최장 구간에서도 66ms
- 확장성: **WebRTC 서버 `< 1k viewers`** / HLS `millions of concurrent viewers`
- 비용 예시(10,000 viewers × 1시간 × 1.7Mbps 720p):
  LiveKit(H.264) **$912**, Cloudflare HLS **$600**, Amazon IVS HLS **$750**
- 코덱 절감: VP9 평균 **-15%**, AV1 평균 **-40%**

### 1-2. ★ SFU 확장 한계 — 참가자 수 vs CPU

LiveKit 공식 벤치마크. 전부 **GCP `c2-standard-16` (16 vCPU) 단일 노드**.

| 시나리오 | Publishers | Subscribers | Throughput | CPU |
|---|---:|---:|---|---:|
| Audio rooms | 10 | 3,000 | 7.3 kBps in / 23 MBps out | **80%** |
| **Large meeting** | **150** | **150** | 50 MBps in / 93 MBps out | **85%** |
| **Livestream** | **1** | **3,000** | 233 kBps in / **531 MBps out** | **92%** |

**결정적 제약**: *"**Each room must fit within a single node.**"*
→ 룸은 노드 분할 불가 → **룸 하나의 상한 = 노드 하나의 상한**

부하 테스트 명령:
```bash
lk load-test --url <URL> --api-key <K> --api-secret <S> \
  --room load-test --video-publishers 150 --subscribers 150
```

**읽는 법**
- 16코어로 150명 양방향 화상회의면 CPU 85% → **화상교육 룸은 대략 코어당 10명 남짓**이 현실적 상한
- 1:3000 방송은 같은 하드웨어에서 돌지만 **아웃바운드가 531 MBps(≈4.2 Gbps)**
  → CPU가 아니라 **NIC/대역폭 비용이 먼저 터진다**

> **이 두 줄이 "왜 방송은 HLS로 빼야 하는가"의 정량적 답이다.**

**비교군 (mediasoup)**: *"a mediasoup C++ subprocess can typically handle over ~**500 consumers**"*,
*"If there are more than **200-300 viewers** (so 400-600 consumers), the capabilities of a
single mediasoup router could be exceeded"*

**SFU에서 CPU를 먹는 것**: 패킷 포워딩, SRTP 암복호화, 대역폭 추정, simulcast 레이어 선택.
**트랜스코딩은 안 한다.** CPU가 폭발하는 순간은 **Egress를 켤 때** — 그때 디코드+인코드가 들어간다.

### 1-3. 하이브리드가 이 프로젝트의 정답

```
강사/발표자 --WebRTC--> LiveKit 룸 --Egress가 룸을 구독--> HLS 세그먼트 --> 시청자
```
LiveKit이 공식 지원하는 구조다.

---

## 2. LiveKit Egress로 HLS 뽑기

### 2-1. Egress 타입과 HLS 지원

| 타입 | 동작 | HLS(SegmentedFileOutput) |
|---|---|---|
| **RoomComposite** | 헤드리스 Chromium이 웹 레이아웃 렌더링 → 방 전체 합성 | 지원 |
| **Web** | LiveKit 방과 무관하게 임의 웹페이지 녹화 | 지원 |
| **Participant** | 특정 참가자의 오디오+비디오 (TrackComposite의 신형 대체) | 지원 |
| **TrackComposite** | 오디오 1 + 비디오 1 싱크/먹싱 | 지원 |
| **Track** | 개별 트랙을 **트랜스코딩 없이** export | **미지원** |

**HLS를 뽑으려면 반드시 트랜스코딩이 일어나는 타입을 써야 한다.**
강사 한 명만 내보내면 **ParticipantEgress가 RoomComposite보다 훨씬 싸다.**

### 2-2. 인프라 요구사항 (자체 호스팅)

```yaml
api_key / api_secret / ws_url
redis:                      # 필수. livekit server 와 같은 주소
  address / password / db
```

```shell
docker run --rm \
  --cap-add SYS_ADMIN \
  -e EGRESS_CONFIG_FILE=/out/config.yaml \
  -v ~/livekit-egress:/out \
  livekit/egress
```

| 항목 | 내용 |
|---|---|
| Redis | **필수.** 미연결 시 증상은 `"no response from egress service"` |
| Docker | 사실상 필수 (비-Docker는 GStreamer + Chrome + Xvfb + PulseAudio 직접 설치) |
| 권한 | **`--cap-add SYS_ADMIN`** — *"all egress deployments (even local) require"* |
| 자원 | *"At least **4 CPUs and 4 GB of memory**"* per instance |
| 동시성 | **인스턴스 하나가 방 하나만** 녹화 |

**CPU 비용 (공식 config 기본값)**
```yaml
room_composite_cpu_cost: 3.0      # 룸 합성 HLS 1개 = 3코어
web_cpu_cost: 3.0
track_composite_cpu_cost: 2.0
track_cpu_cost: 1.0
audio_room_composite_cpu_cost: 1.0
```
**→ 4코어 인스턴스 = RoomComposite HLS 동시 1개. ABR 3단이면 Egress 3개 = 9코어.**

### 2-3. HLS 출력 설정 (proto 원문)

```protobuf
message SegmentedFileOutput {
  SegmentedFileProtocol protocol = 1;
  string filename_prefix = 2;
  string playlist_name = 3;
  string live_playlist_name = 11;  // (optional, disabled if not provided)
  uint32 segment_duration = 4;     // in seconds (optional)
  SegmentedFileSuffix filename_suffix = 10;  // (default INDEX)
  bool disable_manifest = 8;
  oneof output { S3Upload s3=5; GCPUpload gcp=6; AzureBlobUpload azure=7; AliOSSUpload aliOSS=9; }
}
```

**EncodingOptions 기본값**
```protobuf
int32 width = 1;                 // default 1920
int32 height = 2;                // default 1080
int32 framerate = 4;             // default 30
AudioCodec audio_codec = 5;      // default OPUS
int32 audio_bitrate = 6;         // default 128
VideoCodec video_codec = 8;      // default H264_MAIN
int32 video_bitrate = 9;         // default 4500
double key_frame_interval = 10;  // "Default 4 for streaming;
                                 //  segment duration for segmented output"
```

> ★ **`key_frame_interval`이 세그먼트 출력에서 자동으로 세그먼트 길이에 맞춰진다.**
> 이유는 Apple 스펙 **7.4 "Video segments MUST start with an IDR frame"**.
> **수동으로 어긋나게 설정하면 세그먼트 경계가 IDR에 안 붙어 재생이 깨진다.**

### 2-4. ★ 소스코드에서만 나오는 사실 — 여기가 금맥

| 발견 | 소스 |
|---|---|
| **`segment_duration` 기본값 = 4초** | `pkg/config/output_segment.go` — `if conf.SegmentDuration == 0 { conf.SegmentDuration = 4 }` |
| **라이브 플레이리스트 창 = 5개 하드코딩** | `pkg/pipeline/sink/segments.go` — `defaultLivePlaylistWindow = 5` |
| **`EXT-X-PART`가 없다 → LL-HLS 미지원 확정** | `pkg/pipeline/sink/m3u8/writer.go` |
| **`EXT-X-PROGRAM-DATE-TIME`을 세그먼트마다 기록** | 같은 파일 |
| PDT 기준 시각 = **egress 서버 wall clock** | `segmentStartTime := s.startTime.Add(t - s.startRunningTime)` |
| **한 Egress = 한 렌디션** | `oneof options { preset \| advanced }` — 인코딩 설정이 하나뿐 |
| `live_playlist_name`은 `playlist_name`과 **같은 디렉터리**여야 함 | `ErrInvalidInput("must be in same directory")` |
| 메인 플레이리스트는 `EXT-X-PLAYLIST-TYPE:EVENT`, 라이브는 타입 없음 | writer.go |

**생성되는 매니페스트 태그**
```
#EXTM3U
#EXT-X-VERSION:4
#EXT-X-ALLOW-CACHE:NO
#EXT-X-TARGETDURATION:<n>
#EXT-X-MEDIA-SEQUENCE:<n>
#EXT-X-PROGRAM-DATE-TIME:<...>   ← 세그먼트마다
#EXTINF:<duration>,
```

### 2-5. ★ 스펙 위반을 하나 찾았다

```go
defaultLivePlaylistWindow = 5
```

vs **Apple HLS Authoring Spec 8.11**
> *"You MUST provide at least **six** segments in a live (linear) playlist."*

**LiveKit은 5개만 유지한다.** 실제로 관측 가능하고, 보고서에 쓸 수 있는 진짜 발견이다.

### 2-6. 우리 SDK 버전(0.8.2)에서 되는가 — **된다**

`server-sdk-kotlin` v0.8.2 태그의 `EgressServiceClient.kt`:
```kotlin
@JvmOverloads
fun startRoomCompositeEgress(
    roomName: String,
    output: LivekitEgress.SegmentedFileOutput,   // ← HLS
    layout: String = "",
    optionsPreset: LivekitEgress.EncodingOptionsPreset? = null,
    optionsAdvanced: LivekitEgress.EncodingOptions? = null,
    audioOnly: Boolean = false,
    videoOnly: Boolean = false,
    customBaseUrl: String = ""
): Call<LivekitEgress.EgressInfo>
```

v0.8.2가 고정한 protocol 서브모듈에도 `live_playlist_name`(field 11),
`segment_duration`, `key_frame_interval`이 **모두 존재**.

**단, `PASSTHROUGH` 프리셋(트랜스코딩 스킵)은 0.8.2 proto에 없다.**
이 버전의 프리셋은 `H264_720P_30`(기본) ~ `PORTRAIT_H264_1080P_60` **8개뿐**.

### 2-7. S3 없이 로컬 디스크로 (가장 싼 경로)

클라우드 스토리지를 지정하지 않으면 **컨테이너 로컬 파일시스템**에 쓴다.
- 볼륨 마운트: `-v ~/livekit-egress:/out/`, 파일명은 `/out/...`
- 주의: *"egress is not run as the root user, **write permissions will need to be enabled for
  all users**"*

> **Spring Boot가 그 디렉터리를 정적 리소스로 서빙하면 CDN도 S3도 없이 hls.js로 재생된다.**
> MIME: `.m3u8` = `application/vnd.apple.mpegurl`, `.ts` = `video/mp2t`, CORS 헤더 필요.

### 2-8. 흔한 실패 지점

| 증상 | 원인 |
|---|---|
| RoomComposite/Web만 실패, Track은 됨 | **`SYS_ADMIN` 미부여** → Chrome 시작 실패 (원인 오판하기 쉬움) |
| `"no response from egress service"` | Redis 주소 불일치 (`bind` / `protected-mode`) |
| Egress가 룸 조인 실패 | `ws_url`에 localhost — 도커에서 접근 가능한 IP로 |
| 세그먼트 0개 | 볼륨 권한 (egress는 non-root) |
| 라이브인데 처음부터 재생됨 | `live_playlist_name` 미설정 → VOD 플레이리스트 |
| HLS 필드가 아예 없음 | TrackEgress를 씀 |
| 두 번째 방 녹화가 큐잉/거부 | 인스턴스당 1잡 제한 |
| 세그먼트 경계에서 재생 깨짐 | `key_frame_interval`을 세그먼트 길이와 다르게 설정 |
| 매니페스트엔 22개, 버킷엔 2개 | 세그먼트 업로드 유실 (v1.8.0 "#509", v1.10.x 패닉 수정) |
| **(미확인)** 플레이어 호환성 | `audio_codec` 기본이 **OPUS**인데 출력은 **TS**. AAC 강제 여부 문서에 없음 |

**난이도 평가**: 도커 컴포즈로 LiveKit + Redis + Egress 띄우고 HLS 나오는 것까지
**하루~1.5일**. 첫날의 8할은 `SYS_ADMIN`/Redis/`ws_url` 삽질이다.

### 2-9. 연동에 유용한 것

- **Webhook**: `egress_started`, `egress_updated`, `egress_ended`. Java SDK에 `WebhookReceiver`
  ```java
  WebhookReceiver r = new WebhookReceiver("apiKey", "secret");
  LivekitWebhook.WebhookEvent e = r.receive(postBody, authHeader);
  ```
- **레이아웃**: `grid`, `speaker`, `single-speaker` (+`-light`). `customBaseUrl`로 자체 템플릿
- 커스텀 템플릿은 렌더 완료 후 콘솔에 **`START_RECORDING`** 을 로그해야 녹화 시작,
  **`END_RECORDING`** 으로 종료. 로컬 검증은 `lk egress test-template`
- **Prometheus**: LiveKit config에 `prometheus_port: 6789` → `:6789/metrics`
- **RTMP 인제스트가 필요하면 Ingress** (별도 서비스, RTMP 1935 / WHIP 7885 UDP, Redis 필요)

---

## 3. 세그먼트 길이 ↔ 지연

### 3-1. "3배" 규칙의 스펙 근거 — 3단으로 일치

**RFC 8216 §6.3.3**
> *"If the EXT-X-ENDLIST tag is not present and the client intends to play the media normally,
> the client **SHOULD NOT choose a segment that starts less than three target durations from
> the end of the Playlist file.** Doing so can trigger playback stalls."*

**rfc8216bis `HOLD-BACK`**
> *"Its absence implies a value of **three times** the Target Duration."*

**hls.js `liveSyncDurationCount` 기본값 3**
> *"edge of live delay, expressed in multiple of `EXT-X-TARGETDURATION`.
> if set to 3, playback will start from fragment N-3"*

### 3-2. Apple 권장값 (현행 스펙 원문)

```
7.5.  Target durations SHOULD be 6 seconds.
7.6.  Segment durations SHOULD be nominally 6 seconds (NTSC 29.97 은 6.006초)
7.7.  세그먼트는 target duration 을 0.5초 초과 금지
7.4.  Video segments MUST start with an IDR frame
1.13. Key frames (IDRs) SHOULD be present every two seconds
8.4.  EXT-X-PROGRAM-DATE-TIME MUST be present in every live (linear) Media Playlist
8.11. 최소 6개 세그먼트
8.12. 라이브 플레이리스트에 최소 15분 분량 권장
```

(과거 10초 권장 → 현재 6초)

### 3-3. 실제 지연 수치 표

| 방식 | 지연 |
|---|---|
| WebRTC (LiveKit SFU) | ~250~300ms |
| WebRTC (Red5 릴리스 6.0 실측) | 200ms |
| **LL-HLS (200ms 파트, 전용 인프라)** | Chrome/hls.js **900ms**, Safari macOS 1100ms, iPhone 1400ms, iOS 최악 2000ms+ |
| LL-HLS (OvenMedia 실측 3구성) | **1.034s / 1.934s / 3.734s** |
| LL-HLS (AWS) | 3~5초 |
| LL-HLS (Mux) | *"closer to **5 seconds** than 30 seconds"* |
| Chunked CMAF (LL-DASH, Bitmovin 실측) | **≈1.8초**, *"a **7 times** improvement"* |
| **LiveKit Egress 기본값(4초 세그)** | **hls.js targetLatency = 3 × 4 = 12초** + 인코딩/업로드 |
| **LiveKit Egress `segment_duration: 2`** | **targetLatency = 6초** |
| **LiveKit Egress `segment_duration: 1`** | targetLatency = 3초 (단 창 5개 = 5초, Apple 8.11 위반 심화) |
| 표준 HLS (AWS) | **18~30초** |
| 표준 HLS (Wowza, 10초 청크) | 30~45초 |
| 지상파/케이블/위성 **EDL** | **3~10초** (스타트업 1~2초) |

> **마지막 세 줄이 곧 포트폴리오 소재다.** 코드 한 줄(`segmentDuration`) 바꾸고
> 지연을 측정해 **12초 → 6초**를 보이면 된다.

### 3-4. 세그먼트를 줄일 때의 부작용

**(a) 요청 수 폭증** — RFC 8216 §6.2.2는 새 플레이리스트를
*"no earlier than one-half the target duration ... no later than 1.5 times the target duration"*
안에 내놓아야 한다고 규정. 즉 클라이언트는 대략 target duration마다 플레이리스트를 다시 받는다.

| segment_duration | 세그먼트 req/분/시청자 | 플레이리스트 req/분 | 합계 | 배수 |
|---|---:|---:|---:|---:|
| 6초 | 10 | 10 | **20** | 1× |
| **4초 (LiveKit 기본)** | 15 | 15 | **30** | 1.5× |
| **2초** | 30 | 30 | **60** | 3× |
| 1초 | 60 | 60 | **120** | 6× |

**(b) 인코딩 효율 저하** — 세그먼트는 IDR로 시작해야 하므로 짧게 하면 **키프레임 간격도 짧아진다.**
LiveKit proto가 이를 명시한다(`key_frame_interval` = segment duration).
같은 비트레이트에서 화질이 떨어진다. (정확한 % 저하는 **미확인**)

**(c) CDN 캐시 효율 저하** — 오브젝트 수가 배수로 늘고,
플레이리스트는 `EXT-X-ALLOW-CACHE:NO`(LiveKit이 붙임)라 매번 오리진까지 갈 수 있다.
WINK 실험: *"traditional CDNs added **50-200ms** of latency"*

**(d) 리버퍼링 위험** — hls.js: `liveSyncDurationCount` *"decreasing this value is likely to
cause playback stalls"*. WINK 실측: 200ms 파트에서
**iPhone 14는 20분 후 끊김 시작, iPhone 12는 5분 만에 파트 요청 중단**

**(e) Apple 최소 세그먼트 수 위반** — LiveKit은 창이 5로 고정이라
`segment_duration=1`이면 **플레이리스트가 5초짜리**가 된다.

### 3-5. LL-HLS 스펙 요구사항 (Apple §14)

```
14.2c. The RECOMMENDED Part Target Duration is one second.
14.2a. Part Target Duration MUST be at least the P95 RTT
14.2b. SHOULD be at least three times the P95 RTT
14.3.  PART-HOLD-BACK MUST be at least three times the Part Target Duration
14.4.  플레이리스트 창이 2분 넘으면 Playlist Delta Updates 제공 권장
```

→ 스펙만 따르면 **LL-HLS 지연 하한은 대략 3초**(1초 파트 × 3).

**서버가 구현해야 할 5가지**: `EXT-X-PART`, **Blocking Playlist Reload**
(`_HLS_msn`/`_HLS_part` 쿼리에 **응답 없이 커넥션을 붙잡는 서버**),
`EXT-X-PRELOAD-HINT`, `EXT-X-SKIP` 델타 업데이트, `EXT-X-RENDITION-REPORT`

**반면 hls.js는 `lowLatencyMode` 기본값이 이미 `true`** 라 클라이언트 쪽 기여 여지가 0이다.

---

## 4. 지연 측정 — 무엇을 재는지부터 정의한다

### 4-1. DASH-IF 용어 (면접에서 이 구분이 먹힌다)

| 용어 | 정의 |
|---|---|
| **EEL** (End-to-End Latency) | *"captured by the camera until its visibility on the remote screen"* ← **진짜 glass-to-glass** |
| **EDL** (Encoder-Display Latency) | 인코더 출력 → 화면 |
| Packager-Display Latency | 패키저 → 화면 |
| **LSD** (Live Edge Start-up Delay) | 채널 체인지 타임 |
| SSD (Seek Start-up Delay) | 시크 후 재개 |

DASH-IF의 `ProducerReferenceTime@type`이 **`encoder` 또는 `captured`** 인 것이
이 구분을 명시적으로 만든다. **`captured`면 EEL, `encoder`면 EDL.**

**SVTA 공식 문서(SVTA1058)의 결론**
> *"there is **no single measurement point or method** that can provide a complete measurement
> of latency through the delivery chain"*
> — 클라이언트 SDK + 인코더/트랜스코더/패키저/네트워크 각 지점 프로브를 **조합**해야 한다

### 4-2. ★ 방법 A — `EXT-X-PROGRAM-DATE-TIME` (채택)

```js
hls.on(Hls.Events.FRAG_CHANGED, (e, data) => {
  if (hls.levels[data.frag.level].details.hasProgramDateTime) {
    const latencySec = (Date.now() - new Date(data.frag.programDateTime)) / 1000;
  }
});
```

또는 더 단순히:
```js
setInterval(() => {
  if (hls.playingDate) {
    const latencyMs = Date.now() - hls.playingDate.getTime();
  }
}, 500);
```

**왜 되는가**: LiveKit Egress는 세그먼트마다 PDT를 찍고(writer.go 확인),
그 값은 **egress 서버의 wall clock 기준 세그먼트 시작 시각**이다.
그리고 hls.js가 `hls.playingDate`로 노출한다.

> ⚠ **이 방법이 재는 범위** — Mux가 자기 지표에 붙인 캐비엇이 정확하다.
> *"you should expect the latency measured for Mux Video streams to be
> **around 1 second lower than the actual glass-to-glass latency**"*
> — Mux는 카메라 캡처가 아니라 **ingest 시점**에 PDT를 찍기 때문.
>
> **→ PDT 기반 측정은 정의상 "PDT 삽입 지점 → 화면"이다.**

**전제 조건**: Egress 서버와 시청 브라우저의 **시계가 맞아야 한다.**
같은 머신에서 도커로 돌리면 자동 충족. 분리했다면 NTP 동기화 확인 후 오차를 명시할 것.

Apple 스펙 **8.4가 라이브 플레이리스트에 PDT를 MUST로 요구**하므로,
이건 LiveKit 전용 꼼수가 아니라 **표준 기반 범용 기법**이다.

### 4-3. ★ 방법 B — `hls.latency` (문서와 구현이 다르다)

**문서**
| 프로퍼티 | 문서 원문 |
|---|---|
| `hls.latency` | *"the current live latency in seconds (**difference between `hls.playingDate` and server's program-date-time**)"* |
| `hls.targetLatency` | *"the target latency that the player is attempting to maintain"* |
| `hls.maxLatency` | `liveMaxLatencyDuration` 또는 `liveMaxLatencyDurationCount` 기반 |
| `hls.drift` | *"the **difference between the server's program-date-time and the playhead progression**"* |
| `hls.liveSyncPosition` | *"the position that the player is synced to (around the live edge)"* |

**실제 소스** (`src/controller/latency-controller.ts`)
```
computeLatency() = liveEdge - this.currentTime
liveEdge         = levelDetails.edge + levelDetails.age
```

> **구현은 "PDT 벽시계 차"가 아니라 "플레이리스트가 알려주는 라이브 엣지 − 현재 재생 위치"다.
> 문서 설명과 구현이 다른 관점이므로 둘 다 알아둘 것.**

**`targetLatency` 실제 코드 — 숨은 동작**
```ts
let targetLatency = lowLatencyMode ? partHoldBack || holdBack : holdBack;
if (this._targetLatencyUpdated || userConfig.liveSyncDuration ||
    userConfig.liveSyncDurationCount || targetLatency === 0) {
  targetLatency = liveSyncDuration !== undefined
    ? liveSyncDuration
    : liveSyncDurationCount * targetduration;
}
const maxLiveSyncOnStallIncrease = targetduration;
return targetLatency + Math.min(
  this.stallCount * config.liveSyncOnStallIncrease, maxLiveSyncOnStallIncrease);
```

> **LL-HLS 모드면 `PART-HOLD-BACK`, 아니면 `HOLD-BACK`을 타깃으로 삼고,
> 스톨이 날 때마다 타깃 레이턴시를 올린다** (최대 +targetduration).
>
> **→ 측정 중 리버퍼가 나면 타깃 자체가 움직인다. 스톨 횟수를 함께 기록해야 한다.**

기타: `edgeStalled = Math.max(levelDetails.age - maxLevelUpdateAge, 0)`,
`liveSyncPosition ≈ liveEdge - targetLatency - edgeStalled`

**→ `hls.latency`(실측) vs `hls.targetLatency`(이론값)를 같이 그리면**
*"이론상 12초인데 실측 13.4초, 차이 1.4초는 업로드+플레이리스트 갱신 지연"* 같은 분석이 나온다.

### 4-4. 방법 C — WebRTC 구간 (`getStats()`)

W3C "Identifiers for WebRTC's Statistics API" 원문 기준.

| 필드 | 스펙 정의 |
|---|---|
| `jitterBufferDelay` | *"The sum of the time each audio sample or a video frame takes from the time the **first packet is received by the jitter buffer** to the time it **exits the jitter buffer**."* |
| `jitterBufferEmittedCount` | 지터버퍼에서 나온 샘플/프레임 총수 |
| `totalProcessingDelay` | *"the time from the **first RTP packet is received** and to the time the corresponding sample or frame is **decoded**"* |
| `totalAssemblyTime` | 첫 RTP 패킷 수신 ~ 프레임의 마지막 RTP 패킷 수신 |
| `totalDecodeTime` / `framesDecoded` | 디코딩 총 시간 / 프레임 수 |
| `roundTripTime` | RTCP SR 기반 (RFC 3550 §6.4.1) |
| `freezeCount` / `totalFreezesDuration` | 프리즈. 정의: 프레임 간격 ≥ `Max(3 × avg_frame_duration, avg + 150ms)` |
| `estimatedPlayoutTimestamp` | 추정 플레이아웃 시각 |

**계산 공식**
```
평균 지터버퍼 지연  = jitterBufferDelay / jitterBufferEmittedCount
평균 수신→디코드    = totalProcessingDelay / framesDecoded
평균 디코드 시간    = totalDecodeTime / framesDecoded
원웨이 근사        = stats.timestamp - stats.remoteTimestamp
```

> ⚠ MDN 경고: `remoteTimestamp`는 *"the clock may not be synchronized with the local clock,
> and that both the current time and the pace at which the clock runs may differ"*

**`RTCRtpReceiver.getSynchronizationSources()`** — 재생 타이밍에 가장 가까운 값
- `timestamp`: *"the most recent time at which a frame originating from this source was
  **delivered to the receiver's `MediaStreamTrack`**"*
- `rtpTimestamp`: *"the time at which the media in this packet ... was **initially sampled or generated**"*
- → `timestamp − (rtpTimestamp를 벽시계로 환산)` 이 **소스 샘플링→플레이아웃 지연**.
  단 RTP 타임스탬프는 랜덤 오프셋이라 RTCP SR로 앵커링 필요

**지연 제어**: `RTCRtpReceiver.jitterBufferTarget` — 범위 **0~4000ms**,
*"influence the tradeoff between playout delay and the risk of running out of audio or video
frames due to network jitter"*. **Limited availability(non-Baseline)**

> **→ A(HLS 구간) + C(WebRTC 구간)를 더하면 사실상 EEL이다.**
> 보고서에는 *"HLS 구간 X초 + WebRTC 구간 Y ms"* 로 **분해해서 쓰는 게 더 좋은 그림**이다.

### 4-5. 방법 D — 화면 시계 (교차검증 전용)

한 PC에서 ms 시계 탭을 캡처해 송출, 다른 탭에서 재생, 두 창을 한 스크린샷에.
**같은 시스템 클록이라 NTP 문제가 없다**는 게 장점.

**⚠ 남는 오차**

| 오차원 | 크기 |
|---|---|
| 화면 캡처 파이프라인(OBS) | **60~400ms** |
| 모니터 리프레시 60Hz | 16.7ms 양자화 |
| 웹 스톱워치 자체 refresh | **43ms** 보고 사례 |
| 롤링셔터 (카메라 촬영 시) | 화면 상/하단 시각 차이 |
| 캡처 프레임레이트 미스매치 | 59.94 vs 60Hz → 15초마다 스터터 |

**→ 절대값 ±수십 ms를 주장할 수 없다.** 상대 비교나 초 단위 오더 확인용이다.

Vay(원격주행)는 스톱워치-사진법을 **아예 부적합으로 판정**하고
**카메라 렌즈에 LED, 화면에 phototransistor**를 붙여 수백 회 측정하는 방식으로 갔다.

### 4-6. ★ 캘리브레이션 원칙 (videoLat)

videoLat (CWI, ACM MM'14 논문)의 핵심 원칙:

> *"this delay includes the delay caused by the internal processing of videoLat itself.
> Therefore, before doing a real measurement, the operator should first do a
> **calibration run** ... This self-delay will then be **subtracted** from the real measurement."*

**→ 같은 리그로 "루프백"(캡처 → 즉시 로컬 표시)을 먼저 재서 빼야 한다.**
이걸 안 하면 측정 리그의 지연을 서비스 지연으로 착각한다.

동작 원리: **QR 코드**를 표시 → 카메라가 잡아 전송 → 원격에서 되비춰 되돌아옴 →
다시 QR 인식하면 딜레이 계산 → **새 QR로 반복, 수천 회 후 평균/표준편차 산출**.
macOS/iOS 전용(AVFoundation이 *"the only technology that produced acceptable timestamps"*).
한계: *"one-way measurements are not supported as well as round-trip measurements"*

### 4-7. 절대값이 필요하면 — ffmpeg 번인

**`drawtext` 필터 (공식 문서 원문)**
- **`localtime` / `gmtime`**: *"The format string is **extended to support the variable `%[1-6]N`
  which prints fractions of the second with optionally specified number of digits**"*
  → **밀리초 벽시계는 `%3N`**
- **`pts`**: 기본 `flt`(마이크로초 정밀도), **`hms`** 는 `[-]HH:MM:SS.mmm` **밀리초 정밀도**
- **`n` / `frame_num`**: 프레임 번호 (프레임 단위 검증용)
- `timecode` + `timecode_rate`로 SMPTE `hh:mm:ss:ff` 번인 가능
  (rate가 실제 프레임레이트와 다르면 드리프트)

**OCR 없이 하려면 — stb-tester `latency-clock`**
사람이 읽는 시계 대신 **"64-bit nanoseconds binary in 8x8px black and white boxes"** 를
프레임에 그려 넣어 **카메라 촬영본에서도 견고하게 디코딩**된다.
GStreamer 엘리먼트 `gsttimestampoverlay` / `timeoverlayparse` 제공. LGPLv2.1

**in-band 타임스탬프 주입 (SEI)**
`h264_metadata` BSF의 **`sei_user_data`** — *"Insert a string as SEI unregistered user data."*
→ 인코딩 시점 UTC를 SEI로 넣고 플레이어에서 뽑아 비교하면 **소프트웨어-온리 in-band 측정**.
단 브라우저 MSE 경로에서 SEI를 뽑으려면 hls.js `FRAG_PARSING_USERDATA` 훅 필요.

### 4-8. hls.js timed metadata (참고)

- **`Hls.Events.FRAG_PARSING_METADATA`** — *"fired when parsing metadata is completed (ID3 / CMAF KLV)"*
  - **주의: `pts`/`dts` 는 relative(초)** 다. 절대 벽시계로 쓰려면 PDT 와 결합해야 한다
- **`Hls.Events.FRAG_PARSING_USERDATA`** — *"fired when parsing **sei** text is completed"*
- config (모두 기본 `true`): `enableID3MetadataCues`, `enableEmsgMetadataCues`,
  `enableDateRangeMetadataCues` / `enableEmsgKLVMetadata`(기본 `false`)

> ⚠ **알려진 함정**: 라이브 HLS에서 `FRAG_PARSING_METADATA`가 실제 영상 마커보다
> **30~45초 먼저** 발화한 사례. 이벤트는 **"프래그먼트 파싱 시점"** 에 나므로
> **버퍼에 들어간 시점 ≠ 재생 시점**이다.
> 레이턴시 측정에 쓰려면 반드시 cue의 `startTime`을 `video.currentTime`과 비교해야지,
> **이벤트 발화 시각을 쓰면 안 된다.**

### 4-9. 리포트에 반드시 적을 것

1. **어느 구간인지** — EEL / EDL / Packager-Display (DASH-IF 용어)
2. **측정 리그 캘리브레이션값**
3. **분포** — 평균만이 아니라 p95와 표준편차
4. **스톨 횟수** (타깃 지연이 움직이므로)

---

## 5. ABR 래더

### 5-1. Apple 공식 권장 래더 (H.264/AVC, 16:9)

| 해상도 | 비트레이트(kbps) | 프레임레이트 |
|---|---:|---|
| 416 × 234 | 145 | ≤ 30 fps |
| 640 × 360 | 365 | ≤ 30 fps |
| 768 × 432 | 730 | ≤ 30 fps |
| 768 × 432 | 1100 | ≤ 30 fps |
| 960 × 540 | 2000 | source와 동일 |
| 1280 × 720 | 3000 | source와 동일 |
| 1280 × 720 | 4500 | source와 동일 |
| 1920 × 1080 | 6000 | source와 동일 |
| 1920 × 1080 | 7800 | source와 동일 |

**HEVC/H.265** (SDR / HDR)

| 해상도 | SDR | HDR |
|---|---|---|
| 640 × 360 | 145 | 160 |
| 960 × 540 | 600 / 900 / 1600 | 730 / 1090 / 1930 |
| 1280 × 720 | 2400 / 3400 | 2900 / 4080 |
| 1920 × 1080 | 4500 / 5800 | 5400 / 7000 |
| 3840 × 2160 | 11600 / 16800 | 13900 / 20000 |

**부가 규칙**
- **1.32. 기본 변형은 2000 kbit/s 변형** (multivariant playlist의 첫 항목)
- 1.26/1.27. VOD는 실측 평균/피크가 `AVERAGE-BANDWIDTH`/`BANDWIDTH` 대비 **±10%** 이내
- 1.28/1.29. 라이브는 1시간 평균이 `AVERAGE-BANDWIDTH`의 **110% 미만**, 피크는 **125% 미만**
- 1.23. *"Clients SHOULD NOT be required to switch codecs"*
- *"24 fps HEVC content should use bit rates reduced by about **20%**"*

계단 간격은 대략 **1.4~2.0배씩** 올라간다(365→730→1100→2000→3000→4500→6000→7800).

> **실서비스(YouTube/Netflix/Twitch)의 공식 래더 표는 1차 출처 확인 실패 — 인용 금지.**
> **포트폴리오에는 Apple 표만 인용하는 게 안전하다.**

### 5-2. hls.js ABR 알고리즘 (문서 + 소스)

**기본값 전체**
```js
abrEwmaFastLive: 3.0,          // 라이브 fast EWMA half-life(초)
abrEwmaSlowLive: 9.0,          // 라이브 slow EWMA half-life
abrEwmaFastVoD: 3.0,
abrEwmaSlowVoD: 9.0,
abrEwmaDefaultEstimate: 500000,     // 초기 추정 500kbps
abrEwmaDefaultEstimateMax: 5000000,
abrBandWidthFactor: 0.95,      // 유지/하향 판정 계수
abrBandWidthUpFactor: 0.7,     // 상향 판정 계수
abrMaxWithRealBitrate: false,
maxStarvationDelay: 4,
maxLoadingDelay: 4,
capLevelToPlayerSize: false,
maxBufferLength: 30,
liveSyncDurationCount: 3,
lowLatencyMode: true,
```

**판정 규칙**
- 하향/유지: `abrBandWidthFactor(0.95) × 대역폭평균 > level.bitrate`
- **상향: `abrBandWidthUpFactor(0.7) × 대역폭평균 > level.bitrate` 이어야 상향**

> ★ **비대칭이 핵심이다.** 화질을 올릴 땐 대역폭의 **70%만** 있다고 가정(보수적),
> 내릴 땐 **95%**(민감). **"리버퍼는 화질 저하보다 훨씬 나쁘다"는 QoE 전제가 코드에 박혀 있다.**

**다운로드 중 포기 (`_abandonRulesCheck`)**
진행 중인 프래그먼트의 `fragLoadedDelay`(완료 예상 시간)를 `bufferStarvationDelay`와 비교
→ *"Only emergency switch down if it takes less time to load a new fragment at lowest level
instead of continuing"* → **세그먼트를 다 받기 전에 중단하고 낮은 레벨로 갈아탄다.**

`maxStarvationDelay`(4초): *"ABR algorithm will always try to choose a quality level that
should avoid rebuffering"*

`capLevelToPlayerSize`: 기본 `false`. **켜면 `<video>` 크기 이상 레벨을 안 쓴다**
— 대역폭 절감 실험 소재로 좋음

### 5-3. LiveKit에서 ABR을 만들려면

**Egress 1개 = 렌디션 1개**이므로:
1. 같은 룸에 대해 Egress를 N개 시작 (각각 다른 프리셋, 다른 `filenamePrefix`)
2. 각각의 `playlist_name`을 가리키는 **master playlist(`#EXT-X-STREAM-INF`)를 직접 작성**
   — Spring Boot에서 문자열 조립 30줄
3. 비용: `room_composite_cpu_cost 3.0` × N

3단(360p/720p/1080p)이면 **9코어**. 노트북/단일 VM에서는 **2단(360p + 720p, 6코어)이 현실적.**

> 이건 "FFmpeg 파이프라인 직접 구축"이 아니라 **"API 3번 호출 + 텍스트 파일 1개 생성"** 이므로
> 신입 포트폴리오로 안전하다.

### 5-4. 계측 플레이어 — `hls.js`를 감싼다

| 뽑을 것 | API |
|---|---|
| 레벨 전환 횟수 / 체류 시간 / 상향-하향 비율 | `Hls.Events.LEVEL_SWITCHED` (+`LEVEL_SWITCHING`) |
| 대역폭 추정 vs 실측 오차 | `hls.bandwidthEstimate` + `FRAG_LOADED`의 `stats` |
| 버퍼 길이 시계열 | `hls.mainForwardBufferInfo.len` |
| 라이브 지연 | `hls.latency`, `targetLatency`, `drift`, `playingDate` |
| 리버퍼 횟수/시간 | `ERROR` 중 `bufferStalledError` |
| 레벨 제어 | `hls.currentLevel`, `nextLevel`, `loadLevel`, `autoLevelCapping` |

재현 시나리오는 Chrome DevTools throttling 또는 `tc netem`으로 대역폭 계단식 변경.

---

## 6. 소재 우선순위

| # | 소재 | 난이도 | 소요 | 임팩트 | 지표 |
|---|---|---|---|---|---|
| **1** | **`segment_duration` 4→2초 튜닝 + 트레이드오프 실측** | 하 | 1~2일 | **상** | 지연 `12.4s → 6.3s (-49%)`, 요청 수 `20→30/min`, 세그먼트 총량 +N% |
| **2** | **hls.js QoE 계측 파이프라인** (→ Spring Boot → 대시보드) | 하~중 | 2일 | **상** | startup time, rebuffering ratio, 평균 비트레이트, 지연 p50/p95 |
| **3** | **SFU 수용 한계 측정** (`lk load-test` + Prometheus) | 중 | 1~2일 | **상** | *"4코어 VM에서 양방향 N명 = CPU 85% → 룸 정원 N 확정"* |
| **4** | **`live_playlist_name` 누락 버그 재현·수정** | 하 | 0.5~1일 | 중 | 재생 시작 위치, 플레이리스트 누적 곡선 |
| **5** | **라이브 창 5 vs Apple 8.11(6) 검증** | 하 | 0.5~1일 | 중 | m3u8 실측 + 소스 추적 + iOS 재생 안정성 |
| 6 | ABR 2단 래더 + 스로틀링 하 전환 검증 | 중 | 2~3일 | 중~상 | 전환 소요 시간, `abrBandWidthUpFactor` 튜닝 효과 |
| 7 | Egress 장애 복구 (webhook 기반) | 중 | 1~2일 | 중 | MTTD, MTTR, egress 성공률 |
| 8 | WebRTC `getStats()` 프리즈/지터 계측 | 중 | 1~2일 | 중 | `freezeCount`, `jitterBufferDelay` |

**추천 조합**: **1 + 2**를 묶으면 *"지연을 줄이는 서버 변경 → 그 효과를 자체 계측 도구로
정량 증명"* 이라는 완결된 스토리가 **3~4일**에 나온다. 3은 독립적으로 하루. 5는 1을 하다가 자연히 발견된다.

**서술 방식이 핵심이다**
```
✗ "6초 → 2초로 바꿔서 지연을 18초에서 7초로 줄였습니다"
✓ "6초 → 2초 변경 시 지연 X초 감소, 요청 수 3배, 오리진 대역폭 Y% 증가라는
   트레이드오프를 실측했습니다"
```

---

## 7. 절대 하지 말 것

### 함정 1 — FFmpeg 트랜스코딩 파이프라인 직접 구축

- **LiveKit Egress가 이미 GStreamer + Chrome으로 이걸 한다.** 다시 만들면
  *"Egress 문서를 안 읽었다"* 는 신호가 된다
- **Twitch조차** FFmpeg가 *"may be suboptimal using only a single thread"* 라서
  자체 트랜스코더를 만들었다 — **이 영역은 팀이 몇 년 쓰는 곳이다**
- 시간 소모: A/V 싱크 드리프트, 프로세스 좀비, 백프레셔, 재시작 시 타임스탬프 연속성 —
  각각이 며칠짜리. **최소 2~3주**, 결과물은 *"돌아가긴 하는데 30분 후 싱크가 밀린다"*
- **예외**: `ffprobe`로 생성된 세그먼트를 **검증**하는 용도(GOP 길이, 키프레임 위치 확인)는
  반나절이고 매우 유용. **생성이 아니라 검증에만 쓸 것.**

### 함정 2 — LL-HLS 직접 구현

- **LiveKit Egress는 `EXT-X-PART`를 만들지 않는다**(소스 확인) → **패키저 자체를 교체**해야 한다
- 스펙 요구가 무겁다: blocking playlist reload(**HTTP 장기 대기 → 서버 커넥션 모델 변경**),
  preload hint, `PART-HOLD-BACK ≥ 3× part duration`, Playlist Delta Updates
- **실증**: WINK 팀은 200ms 파트로 900ms를 달성했지만
  **iPhone 14는 20분 후 끊김, iPhone 12는 5분 만에 파트 요청 중단**,
  일반 CDN은 50~200ms를 더했고 결국 **전용 인프라**가 필요했다
- **결말**: 크롬에서만 되고 아이폰에서 안 되는 데모. 면접에서 *"iOS에서 되나요?"* 를 맞으면 끝
- **정확히 이 지연대가 필요하면 답은 LL-HLS가 아니라 WebRTC다.** LiveKit이 이미 250ms를 준다

### 함정 3 — 자체 HLS 파서 / 플레이어 / 코덱 / DRM

- hls.js는 `EXT-X-DATERANGE` 기반 Interstitials, EWMA ABR, 지연 컨트롤러,
  5종 이상 에러 복구까지 구현돼 있다(API.md 3,120줄)
- 자체 파서는 `EXT-X-DISCONTINUITY`, `EXT-X-MAP`, 자막 렌더링, MSE `SourceBuffer` 시간축 관리에서
  **반드시 깨진다**
- 코덱: NAL 유닛, Annex-B vs AVCC, `avc1` vs `avc3` 파라미터셋 배치 — **2주가 사라지고
  남는 건 "비트 몇 개 읽는 코드"**
- DRM: 라이선스 서버 계약, 디바이스 프로비저닝, 플랫폼별 CDM 필요 — **개인은 불가능**
- **개념은 알되 코드는 쓰지 말 것.** 면접에서 GOP/IDR/프로파일을 설명하면 충분하다

### 함정 4 — 멀티 CDN / 자체 오리진 실드

인프라 비용과 트래픽이 없으면 "구성했다"만 남고 측정할 게 없다.
**측정 불가 = 포트폴리오 가치 0.**

---

## 8. 방송/미디어 업계 용어 (면접 대비)

### 8-1. GOP / IDR / 세그먼트 정렬

- **I-frame**: 자기 정보만으로 완결. **P-frame**: 과거 참조. **B-frame**: 과거+미래 양방향 참조
- **IDR vs I-frame — 반드시 구분**: *"Every IDR frame is an I-frame, but not vice versa"*.
  IDR은 디코더 참조 버퍼를 비워 이후 프레임이 그 이전을 참조하지 못하게 함
  → **진짜 랜덤 액세스 포인트**
- **세그먼트 = GOP의 정수배여야 하는 이유**: Apple **7.4 "Video segments MUST start with an
  IDR frame"**. 1.13이 IDR 2초를 권장하므로 **2초 GOP + 6초 세그먼트(=3 GOP)** 가 스펙 정합적
- 어기면: 세그먼트 첫 프레임이 P프레임 → 디코딩 불가 → 화면 깨짐
- **ABR에서는 모든 레벨의 IDR이 같은 시각에 정렬**되어야 전환 시 끊김이 없다
  (Apple 8.22: *"all audio/video variants and renditions SHOULD have segment boundaries at
  the same points in time"*)
- **인코더 관점(Bitmovin)**: *"segments are created and closed **upon keyframes**"*.
  오디오는 키프레임이 없어 프레임 단위로 채움 — AAC 48kHz는 프레임당 **21.333ms**
  → 4초에 정수로 안 떨어져 세그먼트 길이가 미세 진동.
  **SSAI 워크플로에서는 "the keyframe interval chosen for the video will need to be an exact
  multiple of the audio frame duration"**

### 8-2. Closed GOP vs Open GOP

- **Closed**: GOP 밖을 참조하지 않음
- **Open**: GOP 시작부 B-frame이 **직전 GOP의 마지막 P-frame**을 참조 → 비트레이트 **1~3% 절약**
- **ABR에서 Closed가 필수인 이유**: *"when the player switches from a 360p stream to a 1080p
  stream, the frames before the I-frame at the start of the segment are different than the
  frames referred to while encoding, **which can cause problems**"*
- 품질 차이는 실측상 미미: **VMAF 기준 "insignificant"**
- Apple 스펙이 IDR 2초 + 세그먼트 IDR 시작을 요구하므로 **HLS에서는 사실상 closed GOP 강제**

### 8-3. ★ B-frame의 지연 비용

디코드 순서 ≠ 표시 순서 → **"Each reorder costs one frame of buffering latency"**.
24fps에서 약 **42ms/프레임**, 60fps에서 약 **17ms/프레임**.
**B-pyramid**(B가 B를 참조)는 압축 **10~15%** 개선하지만 디코더 버퍼링을 GOP/2 수준까지 요구.

```
HLS/DASH VOD 24fps  →  48프레임(2s) closed + B-pyramid
LL-HLS              →  24~60프레임(1s) closed, B-pyramid 최소
WebRTC              →  B-frame off
```

> **"B-frame은 압축 이득을 주지만 리오더링 버퍼가 곧 레이턴시다.
> 그래서 WebRTC/초저지연에서는 B를 끄고, LL-HLS에서도 B-pyramid를 줄인다."**

### 8-4. 컨트리뷰션 프로토콜

| | RTMP | SRT | RIST | WHIP |
|---|---|---|---|---|
| 전송 | TCP (Flash 유산) | UDP + ARQ | UDP + NACK ARQ | WebRTC over HTTP |
| 암호화 | 없음(RTMPS는 TLS 래핑) | **AES 128/192/256** | Main 프로파일부터 | DTLS-SRTP |
| 표준화 | Adobe → **Enhanced RTMP** | MPL-2.0 오픈소스 | **VSF TR-06-1/-2/-3** | **RFC 9725 (2025-03)** |
| 위치 | 인제스트 사실상 표준 | 불안정 회선 중계 | 방송사 인프라 거버넌스 | 현대적 대안 |

**Enhanced RTMP — RTMP가 안 죽는 이유**
> *"despite RTMP's widespread use, it has shown signs of aging, particularly in the lack of
> support for contemporary video codecs like VP8, VP9, HEVC, and AV1"*

참여: **Adobe, YouTube, Twitch, Amazon, VideoLan, FFmpeg, OBS, Ant Media, Dolby, Intel,
Luxoft, XSplit, Red5, mirillis, OpenIPC**
추가 기능: **FourCC 코덱 시그널링**, VP8/VP9/**HEVC**/**AV1** + HDR, AC-3/E-AC-3/Opus/FLAC,
멀티채널 오디오, **멀티트랙**, **나노초 타임스탬프 정밀도**, **Reconnect Request**
v2 문서: `v2-2026-01-31-r2`

> **답변 요지**: *"RTMP는 스펙상 HEVC를 공식 지원하지 않고 TCP라 손실 환경에 불리하지만,
> **인코더/OBS/CDN 인제스트 엔드포인트가 전부 이미 지원**해서 실질 표준으로 남아 있다.
> 업계는 이를 버리는 대신 Enhanced RTMP로 FourCC 기반 코덱 확장을 붙이는 길을 택했다."*

**SRT 튜닝 숫자**
```
SRTO_RCVLATENCY   기본 120ms (Live mode), 0 (File mode)
SRTO_PEERLATENCY  기본 0
범위              20 ~ 8000 ms
rule of thumb     SRT Latency = RTT × 4  (패킷손실 0.1~0.2% 전제)
양단 설정 시      "The higher of the two values is used"
TSBPD 공식        PTS[x] = ETS[x] + LATENCY + DRIFT
FEC + ARQ         arq:never / arq:always / arq:onreq
FEC 최소 지연     N = (R * (C-1)) + 2 패킷
```
⚠ SRT의 "latency"는 `srt_sendmsg2` ~ `srt_recvmsg2` 구간만이며
**인코딩/디코딩/표시 지연은 포함하지 않는다.**

**RIST TR-06-1 권장 기본값**
```
RTCP 전송 주기    ≤ 100ms, 대역 ≤ 미디어 평균의 5%
수신 버퍼         1000ms
송신 버퍼         ≥ 수신 버퍼
Reorder Section   70ms
재전송 요청       패킷당 7회
재전송 요청 간격  (1000 - 70) / 7 = 132ms
```
2020 개정에서 **RTT Echo Request/Response** 추가 → 수신단이 RTT를 측정해 재전송 타이밍 최적화

**WHIP / WHEP 표준화 현황**
```
WHIP  = RFC 9725, "WebRTC-HTTP Ingestion Protocol", 2025년 3월, Proposed Standard
        RFC 8840, 8842 를 업데이트
        POST + application/sdp → 201 Created + SDP answer + Location 헤더
        종료는 그 URL로 DELETE. STUN/TURN 은 Link 헤더 rel="ice-server"

WHEP  = draft-ietf-wish-whep-04, WG Last Call. 아직 RFC 아님
WG 이름 = WISH (WebRTC Ingest Signaling over HTTPS)
```

### 8-5. CMAF

**ISO/IEC 23000-19 (MPEG-A Part 19).** Apple + Microsoft가 2016년 공동 제안, 2018년 1월 발행.

CMAF 이전엔 HLS는 `.ts`, DASH는 `.m4s`로 **같은 콘텐츠를 두 벌** 인코딩/저장해야 했다.
Akamai 인용: *"These same files ... cost twice as much to package, cost twice as much to store on origin"*

**계층 구조 (정확히 외울 것)**
```
Chunk     "The smallest referenceable media unit, containing a moof and mdat atom"
Fragment  "A collection of one or more chunks"
Segment   "A collection of one or more fragments"
```

> ★ **Chunk는 키프레임으로 시작할 필요가 없다.**
> → **세그먼트를 줄이지 않고도 저지연을 달성한다.**
> 세그먼트를 줄이면 IDR이 늘어 대역폭이 증가하는데 chunk는 그 대가가 없다.
> chunk는 *"**500 milliseconds or lower** depending on encoder configurations"*

**CTE (Chunked Transfer Encoding)**
> *"HTTP chunked transfer encoding **must at least be supported up from the ingest into the
> packager up to the CDN edge**"* — 오리진→CDN→플레이어 전 구간이 CTE를 흘려야 한다.
> DASH-IF: *"The system is designed to be workable with the **standard HTTP/1.1**"*,
> *"**low-latency content is fully cacheable**"*

> **CMAF 자체는 레이턴시를 줄이지 않는다** — *"CMAF itself does not reduce latency"*.
> **chunk + CTE 조합이 줄이는 것이다.**

**LiveKit egress는 TS 세그먼트라 CMAF가 아니다.**
→ HEVC/AV1 불가(Apple 1.5/1.39: fMP4 MUST), DASH와 세그먼트 공유 불가, LL-HLS 사실상 불가.

### 8-6. HLS 암호화 / DRM

**RFC 8216 §4.3.2.4 `EXT-X-KEY` METHOD**
- **`NONE`** — 암호화 없음
- **`AES-128`** — *"Media Segments are **completely encrypted** using AES with a 128-bit key,
  **Cipher Block Chaining (CBC)**, and **PKCS7 padding**"* ← 세그먼트 전체
- **`SAMPLE-AES`** — 샘플(NAL) 단위 부분 암호화
- rfc8216bis에는 **`SAMPLE-AES-CTR`** 도 존재

**"clear key"**: `URI`로 평문 키를 HTTPS로 그냥 내려주는 방식.
AWS MediaPackage는 이를 *"Clear Key AES-128"* DRM으로 분류하지만,
**키가 클라이언트에 평문 노출되므로 진짜 DRM이 아니라 "접근 통제" 수준**이다.

**FairPlay (Apple 오서링 스펙 §13)**
```
13.1. Content protection SHOULD follow the FairPlay Streaming (FPS) specification
13.2. FPS 로 암호화하면 method MUST be SAMPLE-AES
13.3. key format MUST be "com.apple.streamingkeydelivery"
13.5. HD 해상도는 HDCP-LEVEL of TYPE-0
13.6. HD 초과는 HDCP-LEVEL of TYPE-1
13.7. Common Encryption 은 encrypt:skip 패턴 1:9 (10% partial encryption)  ← cbcs 패턴의 실체
13.9. content sensitive encryption 금지
13.11. SAMPLE-AES-CTR SHALL NOT be used on Apple devices               ← Apple = CBC 계열
```

**CENC**: ISO/IEC 23001-7. 4개 보호 스킴 `cenc`(AES-CTR), `cbc1`, `cens`, `cbcs`(AES-CBC + pattern).
**원문 인용 실패 — 미확인.** 다만 위 13.7의 `1:9` 요구가 **cbcs 패턴 암호화 정의와 정확히 일치**하고,
`SAMPLE-AES-CTR` 금지 조항이 *"Apple은 CTR이 아니라 CBC"* 라는 통설을 뒷받침한다.

**Widevine L1/L2/L3, PlayReady SL150/2000/3000, EME/CDM 플로우, 멀티DRM 벤더** — **미확인**

### 8-7. 포렌식 워터마킹

**DASH-IF가 매니페스트 조작 유스케이스로 "A/B watermarking"을 명시적으로 열거**한다:
> *"This covers use cases where the manifest has to be customized for each end-user...
> with such manipulations as ad insertion, bitrate filtering, **A/B watermarking** or content
> replacement for blackout use cases. The manifest manipulation operations are not expected to
> significantly impact the end-to-end latency, at least not more than the duration of a single
> media segment."*

**원리**: 인코더가 각 세그먼트의 **A/B 두 변종**을 만들고,
**세션별로 A/B 시퀀스를 다르게 조합한 매니페스트**를 내려주면 그 시퀀스 자체가 세션 ID다.
유출본의 A/B 패턴을 읽으면 어느 세션에서 샜는지 역추적 가능.
**조작이 매니페스트 레벨에서만 일어나므로 레이턴시 페널티가 세그먼트 1개 길이 이내** —
이게 스포츠 라이브에서 쓸 수 있는 이유.

**가시적 워터마크(방송사 로고 버그)**: LiveKit에서는 **RoomComposite 커스텀 템플릿에 CSS로
얹으면 끝.** 30분이면 되고 방송사 도메인에 딱 맞는 데모다.

MovieLabs ECP의 4K/UHD 포렌식 워터마킹 요구, 벤더(NAGRA NexGuard, Verimatrix, Irdeto,
Friend MTS, Synamedia) — **미확인**

### 8-8. 광고 / SSAI

**SCTE-35**: 방송 스트림 내 광고 삽입 마커(cue-out/cue-in).
HLS에서는 **`EXT-X-DATERANGE`의 `SCTE35-OUT` / `SCTE35-IN` / `SCTE35-CMD`** 로 전달.

**SSAI vs CSAI**: SSAI는 **매니페스트를 서버가 조작해 광고를 본편처럼 이어붙임**
→ 애드블록 회피, 끊김 없음. 방송사 OTT의 주 수익 모델.

AWS MediaTailor 정의:
> *"a scalable **ad insertion and channel assembly** service ... serve targeted ad content
> to viewers ... while maintaining broadcast quality in OTT"*
> Channel assembly는 *"a **manifest-only** service ... MediaTailor **never touches your content
> segments**"*  ← **SSAI의 본질은 매니페스트 조작**

Apple 광고 요건: *"3.2. Inserted media SHOULD be encoded with the **same codecs**"*,
*"3.3. ... **same aspect ratio**"*
Apple은 별도로 **HLS Interstitials**(`X-ASSET-URI` / `X-ASSET-LIST`)를 지원하며
hls.js에도 `InterstitialsManager`가 구현돼 있다.

### 8-9. QoE 지표 — Mux 공식 정의와 공식

**Smoothness**
```
Rebuffer Percentage  = 리버퍼 시간 / 시청 시간
Rebuffer Frequency   = 리버퍼가 얼마나 자주
Smoothness Score     = [1/√(1+(rebuffer_count/2)²)  와  e^(-10 × rebuffer_pct)] 의 평균 × 100
```

**Startup Time**
```
Video Startup Time    = 페이지 로드·플레이어 준비 후 재생까지
Player Startup Time   = 플레이어 초기화 ~ 준비 완료
Page Load Time        = 요청 ~ 플레이어 첫 초기화
Aggregate Startup     = 위 셋의 합  ← 사용자가 체감하는 총 대기시간
Startup Time Score    = 8 / (8 + startup_seconds) × 100
```

**Playback Success**
```
Playback Failure %          = fatal error 로 재생 실패한 뷰 비율
Video Startup Failure %     = 첫 프레임을 못 본 비율
Exits Before Video Start    = "클릭했는데 재생이 시작되지 않은" 뷰 (실패 케이스 제외)
```

**Video Quality**
```
Video Quality Score = e^(-0.33 × (0.15×U_max + 0.85×U_avg)) × 100
Upscale %           = (플레이어 크기 - 스트림 해상도) / 스트림 해상도, 시간 가중
Weighted Avg Bitrate = Σ(bitrate × time) / total time
```

**Overall**
```
Viewer Experience Score = Playback Success Score × (T_Sm,Q + T_Sm,Su + T_Su,Q) / 3
```
> **중요도 서열: Playback Success(곱셈 계수) > Smoothness > Startup Time > Quality**
> — *"derived from user research across millions of video views"*

### 8-10. 코덱 현황

| 코덱 | 브라우저 지원 |
|---|---|
| **H.264/AVC** | 사실상 보편. Apple 1.12도 *"some video content SHOULD be encoded with H.264"* 유지 |
| **HEVC/H.265** | Safari 13+, Samsung 21+ 완전 / **Chrome 107+, Edge, Firefox 137+ 는 partial**. caniuse 코멘트: *"complex and expensive to license"* |
| **AV1** | Chrome 70+, Firefox 67+, Edge 121+, Safari 17.0+ partial. 전역 커버리지 **약 94.28%** |
| VVC/H.266 | 사실상 없음 |

> ★ **Chrome의 HEVC는 소프트웨어 디코딩이 아니라 하드웨어 디코더가 있을 때만 동작한다.**
> Chrome Platform Status 원문: *"Enables support for decoding HEVC video on platforms where
> **hardware** ... for decoding HEVC is available (Android 5.0+, macOS 11+, with supported
> hardware on Windows 8+ and ChromeOS)"*

WebRTC에서의 H.265는 **Chrome 136**에서 기본 활성화.
WebCodecs의 HEVC 인코딩은 Chromium **M130**, MediaRecorder는 **M136**.

**Apple HLS가 허용하는 코덱**
```
1.1   All video MUST be encoded using H.264/AVC, HEVC/H.265, Dolby Vision, or AV1
1.2   H.264 컨테이너는 fMP4 또는 MPEG-TS
1.5   HEVC 는 fMP4 필수
1.39  AV1 도 fMP4 필수
1.3b  H.264 ≤ High Profile Level 5.2
1.6b  HEVC ≤ Main 10 Level 5.1 High Tier
1.7   HDR 은 HDR10, HLG, Dolby Vision
1.10  파라미터셋은 sample description 에 (avc1/hvc1/dvh1, avc3/hev1/dvhe 말고)
1.19  60fps 초과 금지
```

**오디오 (Apple §2)**
```
2.3   Stereo audio in AAC, HE-AAC v1, or HE-AAC v2 MUST be provided
2.4   ★ You SHOULD NOT use HE-AAC if your audio bit rate is above 64 kbit/s   ← 면접 단골
2.5   멀티채널: APAC, AAC-LC, HE-AAC v1, ALAC, FLAC, Dolby Digital(AC-3),
      Dolby Digital Plus(E-AC-3, ec-3), Dolby Digital Plus JOC(ec+3)  ← JOC 가 Atmos
2.24  HE-AAC in fMP4 MUST use explicit signaling of SBR data
2.25  xHE-AAC / ALAC / FLAC / APAC 은 fMP4 필수
비트레이트: 스테레오 AAC 32~160, 5.1 AAC 320 / DD 384 / DD+ 192, 7.1 DD+ 384
라우드니스: fMP4 는 ludt 박스, AC-3/E-AC-3 은 dialnorm, AAC 는 prog_ref_level
```

**자막 (Apple §4~5)**: CEA-608, CEA-708, WebVTT, IMSC1(text profile only).
IMSC1은 fMP4 필수, WebVTT는 `X-TIMESTAMP-MAP` 필수.

### 8-11. HLS 전달 요건 (Apple §10~12)

```
10.1  The server MUST deliver playlists using gzip content-encoding
10.2  stream failover 지원 SHOULD (Multivariant Playlist 에 중복 스트림 나열)
8.18  미디어 요청은 HTTP redirect 를 쓰면 안 된다 (광고 콘텐츠는 예외)
8.24  라이브 플레이리스트 갱신은 Last-Modified 가 Date 로부터 3 target duration 넘으면 무효
MIME  .m3u8 → application/vnd.apple.mpegurl,  .ts → video/mp2t,  fMP4 → video/mp4
보안  TLS 1.2+ with forward secrecy, RSA 2048 / EC 256
12.4  The URLs for Media Segments SHOULD NOT be completely static
```

**Content Steering**: `EXT-X-CONTENT-STEERING` + `PATHWAY-ID` (§9.18)
← **멀티CDN 스위칭의 HLS 표준 메커니즘**, MIME `application/vnd.apple.steering-list`

### 8-12. SMPTE ST 2110 (방송사 IT 면접 단골)

- **-10** 시스템 타이밍/공통 요건, **-20** 비압축 비디오(RTP), **-21** 트래픽 셰이핑,
  **-30** PCM 오디오(AES67 참조), -31 AES3, **-40** ANC 데이터(SMPTE ST 291-1)
- ★ **에센스 분리(essence separation)**: *"Each stream is individually timed by the ST 2110
  system and can take **different routes** over the networked fabric"*
  ← SDI가 비디오+오디오+ANC를 한 케이블에 묶던 것과의 결정적 차이
- 타이밍: **IEEE 1588 PTP** (방송 프로파일이 SMPTE ST 2059)
- 2025 Emmy Award 수상

---

## 9. 면접용 한 줄 요약 세트

- *"HLS 지연이 약 3배 세그먼트인 이유는 RFC 8216 §6.3.3의 3 target duration 규칙이고,
  hls.js `liveSyncDurationCount` 기본값 3이 그대로 구현한 것"*
- *"세그먼트 길이는 GOP의 정수배여야 한다 — Apple 7.4가 세그먼트 시작을 IDR로 강제하기 때문"*
- *"TS냐 fMP4냐가 CMAF·HEVC·LL-HLS 가능 여부를 한 번에 결정한다"*
- *"RTMP/SRT는 인제스트, HLS/DASH는 배포. 계층이 다르다"*
- *"B-frame의 리오더링 버퍼가 곧 레이턴시다. 그래서 WebRTC는 B를 끈다"*
- *"CMAF chunk는 키프레임으로 시작할 필요가 없어서, 세그먼트를 안 줄이고도 지연을 줄인다"*
- *"WHIP은 RFC 9725로 표준화됐고 WHEP은 아직 IETF 드래프트다"*
- *"SFU는 픽셀을 안 만져서 GC 언어로도 되지만, MCU/트랜스코딩은 SIMD의 세계다"*

---

## 10. 미확인 항목

- `SegmentedFileOutput.segment_duration`의 문서상 명시적 기본값 (소스로는 4초 확인)
- 세그먼트 출력 시 **오디오 코덱이 실제로 무엇인지** (기본은 OPUS인데 출력은 TS)
- LL-HLS의 구체적 달성 지연 초 수치 (Apple 문서는 *"range of standard television broadcasts"* 만)
- Apple이 6초를 권장하는 명시적 이유 (스펙에 근거 서술 없음)
- **YouTube / Netflix / Twitch의 실제 ABR 래더** — **인용 금지**
- CENC 4개 보호 스킴 원문, Widevine/PlayReady 레벨, EME/CDM 플로우, 멀티DRM 벤더
- 포렌식 워터마킹 벤더·MovieLabs ECP 요구사항
- Conviva QoE 지표 공식 정의
- SCTE-35 정식 지정번호·연도, splice_insert vs time_signal 구조, SCTE-104
- 짧은 GOP의 압축 효율 저하 정량치
- 한국 OTT/방송사(wavve, TVING, 치지직, SOOP)의 구체적 스트리밍 스택
- Jitsi 성능 평가 페이지의 측정 시기 (게시일 없음)
