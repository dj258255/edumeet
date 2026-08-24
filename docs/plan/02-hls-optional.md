# HLS 스트리밍 — 선택 작업

> **이 문서는 초기 계획과 조사 기록이다.**
> 실제 구현은 LiveKit Egress 에서 직접 HLS delivery 로 바뀌었다.
> 현재 결론은 [`05-own-hls.md`](05-own-hls.md) 와
> [`../performance/12-hls-codec-benchmark.md`](../performance/12-hls-codec-benchmark.md) 를 기준으로 본다.
>
> 근거와 출처는 [`../research/05-streaming-hls.md`](../research/05-streaming-hls.md) 에 있다.

## 왜 후순위인가

| | 채팅 | HLS |
|---|---|---|
| 문제 정의 | *"사람 늘면 어디서 깨지나"* 로 자연스럽게 나옴 | *"지연 줄이기"* — 답이 정해진 튜닝 |
| 시작 비용 | 0 | **Egress 인프라 세팅에 하루~1.5일** (SYS_ADMIN·Redis 삽질) |
| 이미 가진 것 | **Spring 8스레드 함정** | 없음 |

## 그래도 하는 이유

**SBS디지털뉴스랩 웹개발 공고 우대사항에 "동영상 인코딩 및 플레이어 개발 경험자"** 가 명시돼 있고,
iMBC 에도 라이브스트리밍 직무가 실재한다. **미디어 업계에서 스트리밍 경험은 명시적 우대 사유다.**

단 **미디어 코어(코덱·트랜스코딩)는 C/C++ 트랙**이다. 우리가 하는 것은 **그 위 계층**이고,
서사도 그렇게 잡는다.

## 진행 상태 (2026-08-25 갱신)

| | |
|---|---|
| ✅ | LiveKit Egress HLS 요청 생성 + 함정 4개 테스트로 고정 ([#75](https://github.com/dj258255/edumeet/issues/75)) |
| ✅ | CPU 경계를 소스에서 확인하고 코드에 상수로 박음 |
| ✅ | 방송 세션 생성 경로 ([#76](https://github.com/dj258255/edumeet/issues/76)) — **없어서 HLS 가 실행될 수 없었다** |
| ✅ | LiveKit Egress 를 실제로 띄워 `minimumCpu: 4` 로그가 전체 실패가 아니라 일부 타입 경고임을 확인 |
| ✅ | 발표자 1명 방송에는 합성이 필요 없다고 판단하고 직접 HLS delivery 로 교체 |
| ✅ | OCI 2 OCPU 에서 ffmpeg 리먹싱/재인코딩/audio-only 비용 측정 |
| ✅ | 운영 HLS URL 을 k6 로 20 VU / 1분 hold 측정 |
| ⬜ | 라이브 창 5 vs Apple 스펙 8.11(6) 검증 |

## 최소 범위 — 이것만 한다

```
소재 1  segment_duration 4 -> 2초 튜닝 + 트레이드오프 실측   1~2일
소재 2  hls.js 계측 플레이어 (PDT 기반 지연 자동 측정)       2일
소재 3  라이브 창 5 vs Apple 8.11(6) 스펙 위반 검증          0.5일
```

**1 + 2 를 묶으면 3~4일에 완결된 스토리가 나온다** —
*"지연을 줄이는 서버 변경 → 그 효과를 자체 계측 도구로 정량 증명"*.
3 은 1 을 하다가 자연히 발견된다.

---


## 0. 왜 방송은 HLS로 빼야 하는가 — 정량적 답

LiveKit 공식 벤치마크. 전부 **GCP `c2-standard-16` (16 vCPU) 단일 노드**.

| 시나리오 | Publishers | Subscribers | Throughput | CPU |
|---|---|---|---:|---:|
| Audio rooms | 10 | 3,000 | 7.3 kBps in / 23 MBps out | 80% |
| **Large meeting** | **150** | **150** | 50 MBps in / 93 MBps out | **85%** |
| **Livestream** | **1** | **3,000** | 233 kBps in / **531 MBps out** | **92%** |

**결정적 제약**: *"Each room must fit within a single node."*
룸은 노드 분할이 안 된다 → **룸 하나의 상한 = 노드 하나의 상한.**

**읽는 법**
- 16코어로 150명 양방향 화상회의면 CPU 85% → **화상교육 룸은 대략 코어당 10명 남짓**이 현실적 상한
- 1:3000 방송은 같은 하드웨어에서 돌지만 **아웃바운드가 531 MBps(≈4.2 Gbps)** →
  CPU가 아니라 **NIC/대역폭 비용이 먼저 터진다**

**이 두 줄이 "왜 방송은 HLS로 빼야 하는가"의 정량적 답이다.**
(비교군 — mediasoup: *"a mediasoup C++ subprocess can typically handle over ~500 consumers"*)

SFU에서 CPU를 먹는 것은 패킷 포워딩·SRTP 암복호화·대역폭 추정·simulcast 레이어 선택이고
**트랜스코딩은 안 한다.** CPU가 폭발하는 순간은 **Egress를 켤 때**다 — 그때 비로소 디코드+인코드가 들어간다.

부하 측정 명령:
```bash
lk load-test --url <URL> --api-key <K> --api-secret <S> \
  --room load-test --video-publishers 150 --subscribers 150
```

## 1. 경로 선택

| 선택지 | 판단 |
|---|---|
| A. LiveKit Egress → HLS | 초기 채택 후 제외. 발표자 1명 방송에 RoomComposite 합성 비용이 과함 |
| **B. HTTP chunk ingest + FFmpeg 직접 HLS** | **현재 채택.** 브라우저 발표자만 다루므로 가장 작고 측정 가능 |
| C. 녹화 파일 → HLS 변환 (VOD) | **폴백.** A가 막히면 |
| LL-HLS | **제외.** 근거는 아래 9-5 |
| 자체 HLS 파서 | **제외.** `hls.js`로 충분 |

**Egress 타입 선택** — HLS(`SegmentedFileOutput`)를 쓰려면 **트랜스코딩이 일어나는 타입**이어야 한다.

| 타입 | HLS |
|---|---|
| RoomComposite (헤드리스 Chromium이 방 전체 합성) | 지원 |
| Participant / TrackComposite / Web | 지원 |
| **Track** (트랜스코딩 없이 그대로 export) | **미지원** |

→ 강사 1명만 내보내면 **ParticipantEgress가 RoomComposite보다 훨씬 싸다** (아래 비용 참고).

## 2. 인프라 요구사항 (자체 호스팅)

```yaml
api_key / api_secret / ws_url
redis:                      # 필수. livekit server 와 같은 주소여야 한다
  address / password / db
```

| | |
|---|---|
| Redis | **필수.** 미연결 시 증상은 `"no response from egress service"` |
| Docker | 사실상 필수 (비-Docker는 GStreamer + Chrome + Xvfb + PulseAudio 직접 설치) |
| 권한 | v1.7.6 이후 **`--cap-add=SYS_ADMIN`** 필요. 없으면 Chrome 시작 실패 |
| 자원 | 인스턴스당 최소 **4 CPU / 4GB**. RoomComposite 잡 하나가 **2~6 CPU** |
| 동시성 | **인스턴스 하나가 방 하나만** 녹화 |

**포트폴리오라면 LiveKit Cloud 무료 티어로 시작하는 것이 며칠 안에 끝내는 유일한 길이다.**

## 3. 반드시 알아야 할 필드 두 개

```proto
message SegmentedFileOutput {
  string playlist_name       = 3;   // 전체 플레이리스트 (VOD용, 세그먼트 계속 누적)
  string live_playlist_name  = 11;  // 최근 세그먼트만. 미지정 시 비활성
  uint32 segment_duration    = 4;   // 초. **기본값 4** (소스 pkg/config/output_segment.go)
}
```

> **`live_playlist_name`을 설정하지 않으면 라이브인데 VOD 플레이리스트가 나온다.**
> 재생이 방송 시작점부터 시작되고 플레이리스트가 무한 누적된다.
> **이것 자체가 Phase 6의 첫 소재다.**

```proto
double key_frame_interval = 10;  // "Default 4 for streaming;
                                 //  segment duration for segmented output"
```

**세그먼트 출력에서는 LiveKit이 keyframe interval을 세그먼트 길이에 자동으로 맞춘다.**
이유는 Apple 스펙 **7.4 "Video segments MUST start with an IDR frame"** 이다.
→ 이 값을 수동으로 세그먼트 길이와 다르게 주면 **세그먼트 경계가 IDR에 안 붙어 재생이 깨진다.**
재현 가능한 1급 소재다.

## 3b. 소스코드에서만 나오는 사실 (문서에 없다)

**이 절이 HLS 파트의 금맥이다.** 공식 문서에 없고 소스를 읽어야 나온다.

| 발견 | 소스 |
|---|---|
| **`segment_duration` 기본값 = 4초** | `pkg/config/output_segment.go` — `if conf.SegmentDuration == 0 { conf.SegmentDuration = 4 }` |
| **라이브 플레이리스트 창 = 5개 하드코딩** | `pkg/pipeline/sink/segments.go` — `defaultLivePlaylistWindow = 5` |
| **`EXT-X-PART`가 없다 → LL-HLS 미지원 확정** | `pkg/pipeline/sink/m3u8/writer.go` |
| **`EXT-X-PROGRAM-DATE-TIME`을 세그먼트마다 기록** | 같은 파일 |
| **한 Egress = 한 렌디션** | 요청 메시지의 `oneof options { preset \| advanced }` — 인코딩 설정이 하나뿐 |
| `live_playlist_name`은 `playlist_name`과 **같은 디렉터리**여야 함 | `ErrInvalidInput("live_playlist_name must be in same directory")` |
| 메인 플레이리스트는 `EXT-X-PLAYLIST-TYPE:EVENT` | writer.go |

**★ 그리고 스펙 위반을 하나 찾았다.**

```go
defaultLivePlaylistWindow = 5
```

vs **Apple HLS Authoring Spec 8.11** — *"You MUST provide at least **six** segments in a live (linear) playlist."*

> **LiveKit은 5개만 유지한다.** 실제로 생성된 m3u8을 열어 확인 가능하고,
> iOS Safari 재생 안정성 비교까지 붙이면 **오픈소스 소스를 읽고 표준과 대조해
> 불일치를 찾은 사례**가 된다. 신입에게서 보기 드문 행동이다.

## 3c. 우리 SDK 버전(0.8.2)에서 되는가 — **된다**

`server-sdk-kotlin` v0.8.2 태그의 `EgressServiceClient.kt`에 오버로드가 존재한다.

```kotlin
@JvmOverloads
fun startRoomCompositeEgress(
    roomName: String,
    output: LivekitEgress.SegmentedFileOutput,   // ← HLS
    layout: String = "",
    optionsPreset: LivekitEgress.EncodingOptionsPreset? = null,
    ...
): Call<LivekitEgress.EgressInfo>
```

v0.8.2가 고정한 protocol 서브모듈에도 `live_playlist_name`(field 11), `segment_duration`,
`key_frame_interval`이 **모두 있다.**

**단 `PASSTHROUGH` 프리셋(= 트랜스코딩 스킵)은 0.8.2 proto에 없다.**
이 버전의 프리셋은 `H264_720P_30`(기본, 1280×720/30fps/3000kbps) ~ `PORTRAIT_H264_1080P_60` 8개뿐이다.

## 3d. CPU 비용 — 몇 개까지 띄울 수 있나

> **⚠ 아래 숫자는 낡았다.** 처음 조사할 때 참고한 config 예시의 값이고,
> 구현하면서 소스를 직접 읽어 확인하니 달랐다. **정정된 값은 이 절 아래에 있다.**

```yaml
# 낡은 값 (조사 시점)
room_composite_cpu_cost:       3.0
web_cpu_cost:                  3.0
track_composite_cpu_cost:      2.0
track_cpu_cost:                1.0
audio_room_composite_cpu_cost: 1.0
```

### ★ 정정 — 소스에서 확인한 값 (#75)

```go
// pkg/config/service.go
roomCompositeCpuCost      = 4      // 3.0 이 아니다
audioRoomCompositeCpuCost = 1
webCpuCost                = 4
audioWebCpuCost           = 1
participantCpuCost        = 2
trackCompositeCpuCost     = 1
trackCpuCost              = 0.5
```

**비디오 RoomComposite 는 3코어가 아니라 4코어다.** 우리 서버(2 OCPU)와의 격차가 더 크다.

선택 로직도 확인했다.

```go
// pkg/stats/monitor.go
if r.RoomComposite.AudioOnly { costs.cpu = AudioRoomCompositeCpuCost }   // 1
else                         { costs.cpu = RoomCompositeCpuCost }        // 4

required := costs.cpu
accept   := available >= required        // 아니면 ErrNotEnoughCPU
```

| | 필요 | 우리 서버(2) | |
|---|---:|---|---|
| 비디오 방송 HLS | 4 | `2 >= 4` 거짓 | **거부** |
| 오디오 방송 HLS | 1 | `2 >= 1` 참 | **가능** |

> ## ★ 이 절은 실제로 띄워 보고 절반이 틀렸다는 것이 드러났다 (#123)
>
> `minimumCpu` 라는 로그 필드에 **최댓값**이 들어간다. `minimumCpu: 4, available: 2` 를
> 보고 *"egress 자체가 안 된다"* 로 읽었는데, 오디오는 1이라 2코어에서 돈다.
>
> 그리고 더 중요한 것 — **방송은 발표자 한 명만 나가므로 합성이 필요 없다.**
> `RoomComposite` 의 CPU 4는 합성 비용이다. 합성이 필요 없으면 egress 자체가 과잉이다.
> 지금은 걷어내고 직접 만든다. → [`05-own-hls.md`](05-own-hls.md)

**그리고 시작 시점 검사(`validateCPUConfig`)는 가장 싼 타입(`trackCpuCost = 0.5`)과만 비교한다.**
→ egress 프로세스는 정상으로 뜨는데 **비디오 방송 시작만 실패한다.**

→ 구현과 테스트: [`04-three-broadcast-modes.md`](04-three-broadcast-modes.md)

## 3e. S3 없이 로컬 디스크로 (가장 싼 경로)

클라우드 스토리지를 지정하지 않으면 **컨테이너 로컬 파일시스템**에 쓴다.

```shell
docker run --rm --cap-add SYS_ADMIN \
  -e EGRESS_CONFIG_FILE=/out/config.yaml \
  -v ~/livekit-egress:/out \
  livekit/egress
```

**주의**: *"egress is not run as the root user, write permissions will need to be enabled for all users"*

**Spring Boot가 그 디렉터리를 정적 리소스로 서빙하면 CDN도 S3도 없이 hls.js로 재생된다.**
며칠짜리 포트폴리오엔 이게 정답이다.
(MIME: `.m3u8` = `application/vnd.apple.mpegurl`, `.ts` = `video/mp2t`, CORS 헤더 필요)

## 4. 왜 지연 ≈ 3 × 세그먼트 길이인가 (사양 기반)

세 조항이 곱해진 결과다.

1. **RFC 8216 §6.3.3** — *"the client SHOULD NOT choose a segment that starts less than
   **three target durations** from the end of the Playlist file."*
2. **rfc8216bis `HOLD-BACK`** — *"Its absence implies a value of **three times** the Target Duration."*
3. **hls.js `liveSyncDurationCount` 기본값 3** — *"if set to 3, playback will start from fragment N-3"*

여기에 **패키징 지연 1세그먼트**와 **플레이리스트 전파 지연**(§6.2.1: target duration의 0.5~1.5배)이 더해진다.

```
E2E ≈ 인코딩 + 1×세그먼트(패키징) + 0~1.5×세그먼트(전파) + 3×세그먼트(HOLD-BACK) + 디코딩
```

**우리 기본값에 대입하면**

| 설정 | hls.js `targetLatency` | 비고 |
|---|---:|---|
| **LiveKit 기본 (4초)** | **12초** | 3 × 4 |
| `segment_duration: 2` | **6초** | **목표** |
| `segment_duration: 1` | 3초 | 창 5개 = 5초 → Apple 8.11 위반 심화 |

실제 구현은 2초 세그먼트를 기준으로 갔다. 리먹싱은 CPU 를 크게 아끼지만,
키프레임 간격이 길면 2초 세그먼트를 보장하지 못한다는 것도 별도로 재현했다.
→ [`../performance/12-hls-codec-benchmark.md`](../performance/12-hls-codec-benchmark.md)

**Apple 권장값** (원문): 7.5/7.6 *"Target durations SHOULD be 6 seconds"*,
1.13 *"Key frames (IDRs) SHOULD be present every two seconds"*,
7.7 *"MUST NOT exceed the target duration by more than 0.5 seconds"*.

→ IDR이 2초마다여야 하므로 **세그먼트 길이는 2의 배수**여야 경계가 IDR에 떨어진다.

## 5. LL-HLS를 제외하는 결정적 근거

**LiveKit egress 출력은 MPEG-TS 세그먼트다** (egress README: *"HLS (TS segments)"*).
이 사실 하나에서 셋이 따라 나온다.

```
TS 세그먼트  →  CMAF(fMP4) 아님
             →  HEVC/AV1 불가 (Apple 1.5/1.39: fMP4 MUST)
             →  DASH 와 세그먼트 공유 불가
             →  LL-HLS 부분 세그먼트를 얹을 실용적 경로가 없음
```

그리고 서버가 구현해야 할 것이 다섯이다 — `EXT-X-PART`, **Blocking Playlist Reload**
(`_HLS_msn`/`_HLS_part` 쿼리에 **응답 없이 커넥션을 붙잡는 서버**), `EXT-X-PRELOAD-HINT`,
`EXT-X-SKIP` 델타 업데이트, `EXT-X-RENDITION-REPORT`.

반면 **hls.js는 `lowLatencyMode` 기본값이 이미 `true`**라 클라이언트 쪽에 기여할 여지가 0이다.

> **구현하지 않되 "왜 못 하는가"를 사양 조항과 함께 서술한다.**
> *"LL-HLS로 가려면 fMP4/CMAF 패키저 교체가 선행되어야 한다"* 가 분석 결론이 된다.

## 6. 지연 측정 — 무엇을 재는지부터 정의한다

**⚠ 먼저: "glass-to-glass"라고 부르면 안 되는 것을 부르지 않는다.**

DASH-IF가 지연을 단위로 쪼개 정의한다. **면접에서 이 구분이 먹힌다.**

| 용어 | 정의 |
|---|---|
| **EEL** (End-to-End Latency) | *"captured by the camera until its visibility on the remote screen"* ← **진짜 glass-to-glass** |
| **EDL** (Encoder-Display Latency) | 인코더 출력 → 화면 |
| Packager-Display Latency | 패키저 → 화면 |
| LSD (Live Edge Start-up Delay) | 채널 체인지 타임 |

**우리가 잴 수 있는 건 EDL에 가깝다.** 방송(위성/케이블/DTT) 기준선이 **EDL 3~10초**,
스타트업 1~2초다. 리포트에 **어느 구간인지 반드시 명시한다.**

> SVTA 공식 문서(SVTA1058)의 결론:
> *"there is **no single measurement point or method** that can provide a complete
> measurement of latency through the delivery chain"*

#### 방법 A — `EXT-X-PROGRAM-DATE-TIME` (채택)

```js
hls.on(Hls.Events.FRAG_CHANGED, (e, data) => {
  if (hls.levels[data.frag.level].details.hasProgramDateTime) {
    const latencySec = (Date.now() - new Date(data.frag.programDateTime)) / 1000;
  }
});
```

**LiveKit egress는 세그먼트마다 PDT를 찍는다**(소스 확인, §9-3b). Apple 8.4가
라이브 플레이리스트에 PDT를 **MUST**로 요구하므로 표준 기반 기법이다.

**⚠ 이 방법이 재는 범위** — Mux가 자기 지표에 붙인 캐비엇이 정확하다.

> *"you should expect the latency measured for Mux Video streams to be
> **around 1 second lower than the actual glass-to-glass latency**"*
> — Mux는 카메라 캡처가 아니라 **ingest 시점**에 PDT를 찍기 때문.

**→ PDT 기반 측정은 정의상 "PDT 삽입 지점 → 화면"이다.**
우리 경우 PDT를 찍는 건 **egress 인코더**이므로, WebRTC 구간(카메라 → SFU → egress)이
빠진다. 그 구간은 §9-6c로 따로 재서 **더한다.**

#### 방법 B — `hls.latency` (교차검증용)

**⚠ 문서 설명과 실제 구현이 다르다. 소스를 확인했다.**

```
문서:  "difference between hls.playingDate and server's program-date-time"

실제:  latency-controller.ts
       computeLatency() = liveEdge - currentTime
       liveEdge = levelDetails.edge + levelDetails.age
```

즉 구현은 **"플레이리스트가 알려주는 라이브 엣지 − 현재 재생 위치"** 다.
PDT 벽시계 차가 아니다. **둘 다 알고 있어야 하고, A와 B를 같이 그리면
"총 지연 중 몇 초가 플레이어 HOLD-BACK 몫인가"를 분리할 수 있다.**

그리고 `targetLatency` 구현에 숨은 동작이 있다.

```ts
let targetLatency = lowLatencyMode ? partHoldBack || holdBack : holdBack;
...
return targetLatency + Math.min(
  this.stallCount * config.liveSyncOnStallIncrease, maxLiveSyncOnStallIncrease);
```

**스톨이 날 때마다 타깃 지연을 올린다** (최대 +targetduration).
→ 측정 중 리버퍼가 나면 **타깃 자체가 움직인다.** 리포트에 스톨 횟수를 함께 적어야 한다.

#### 방법 C — WebRTC 구간 (`getStats()`)

W3C 스펙 기준으로 세 값을 함께 리포트한다.

| 지표 | 계산 |
|---|---|
| 평균 지터버퍼 지연 | `jitterBufferDelay / jitterBufferEmittedCount` |
| 평균 수신→디코드 | `totalProcessingDelay / framesDecoded` |
| RTT | `roundTripTime` (RTCP SR 기반) |

`totalProcessingDelay` 정의가 정확하다 — *"the time from the **first RTP packet is
received** and to the time the corresponding sample or frame is **decoded**"*.

튜닝은 `receiver.jitterBufferTarget` (0~4000ms).

**→ A(HLS 구간) + C(WebRTC 구간)를 더하면 사실상 EEL이다.**
보고서에는 *"HLS 구간 X초 + WebRTC 구간 Y ms"* 로 **분해해서 쓰는 게 더 좋은 그림**이다.

#### 방법 D — 화면 시계 (교차검증 전용)

한 PC에서 ms 시계 탭을 캡처해 송출하고 다른 탭에서 재생, 두 창을 한 스크린샷에.
**같은 시스템 클록이라 NTP 문제가 없다**는 게 장점이다.

**⚠ 그런데 오차가 그대로 남는다.**

| 오차원 | 크기 |
|---|---|
| 화면 캡처 파이프라인(OBS) | 60~400ms |
| 모니터 리프레시 60Hz | 16.7ms 양자화 |
| 웹 스톱워치 자체 refresh | 43ms 보고 사례 |
| 롤링셔터 (카메라 촬영 시) | 화면 상/하단 시각 차이 |

**→ 절대값 ±수십 ms를 주장할 수 없다.** 상대 비교(설정 A vs B)나 초 단위 오더 확인용이다.

#### ★ 캘리브레이션 원칙 (videoLat)

videoLat 논문의 핵심 원칙이고, **소프트웨어-온리 측정에도 그대로 적용된다.**

> *"this delay includes the delay caused by the internal processing of videoLat itself.
> Therefore, before doing a real measurement, the operator should first do a
> **calibration run** ... This self-delay will then be **subtracted** from the real measurement."*

**→ 같은 리그로 "루프백"(캡처 → 즉시 로컬 표시)을 먼저 재서 빼야 한다.**
이걸 안 하면 측정 리그의 지연을 서비스 지연으로 착각한다.

#### 절대값이 필요하면 — ffmpeg 번인

```
%{localtime\:%H\\:%M\\:%S.%3N}
```

`drawtext`의 `localtime`은 **`%[1-6]N`으로 초의 소수부**를 찍는다(공식 문서).
`pts` 옵션의 `hms`는 밀리초 정밀도 `[-]HH:MM:SS.mmm`.
240fps 슬로모로 촬영해 프레임 카운트하면 절대값이 나온다.

OCR 없이 하려면 **stb-tester `latency-clock`** — 64비트 나노초를
**8×8px 흑백 박스**로 프레임에 그려 넣어 카메라 촬영본에서도 견고하게 디코딩된다.
GStreamer 엘리먼트 `gsttimestampoverlay` / `timeoverlayparse` 제공.

#### 리포트에 반드시 적을 것

1. **어느 구간인지** — EEL / EDL / Packager-Display (DASH-IF 용어)
2. **측정 리그 캘리브레이션값**
3. **분포** — 평균만이 아니라 p95와 표준편차
4. **스톨 횟수** (타깃 지연이 움직이므로)

## 7. ABR — 하되 2단까지만

**Apple 권장표 (H.264, 16:9)에서 교육 콘텐츠에 맞는 3단**

| 레벨 | 해상도 | 비디오 | 오디오 |
|---|---|---|---|
| 상 | 1280×720 | 3000 kbps | AAC 128 |
| 중 | 960×540 | 2000 kbps | AAC 128 |
| 하 | 640×360 | 365~730 kbps | AAC 64 |

**그런데 LiveKit egress 한 잡은 인코딩 설정 하나만 낸다.**
3단 래더 = egress 3개 = RoomComposite 기준 **6~18 CPU**.

> **실제로 3단을 다 돌리지 않는다.** 2단(720p/360p)만 돌리고 마스터 플레이리스트를 손으로 써서
> hls.js 레벨 전환을 계측한다. **그리고 "3단이면 CPU가 이만큼"이라는 계산을 문서에 적는다.**
> 그 계산 자체가 시스템 사고의 증거다.

**hls.js ABR이 보는 신호** (소스 확인)

- 대역폭: fast/slow 두 EWMA (`abrEwmaFastLive` 3.0 / `abrEwmaSlowLive` 9.0)
- 버퍼: `bufferStarvationDelay` = 재생 버퍼 고갈까지 남은 시간
- 다운로드 중 포기: 진행 중 프래그먼트 완료 예상 시간이 버퍼 고갈보다 늦으면 **받다 말고 낮은 레벨로 갈아탐**

**면접에서 쓸 디테일 하나**

```
abrBandWidthFactor    0.95   ← 유지·하향 판단
abrBandWidthUpFactor  0.70   ← 상향 판단
```

**화질을 올릴 땐 대역폭의 70%만 있다고 가정하고, 내릴 땐 95%로 민감하게 본다.**
*"리버퍼는 화질 저하보다 훨씬 나쁘다"* 는 QoE 전제가 코드에 박혀 있는 것이다.

## 8. 계측 플레이어 — `hls.js`를 감싼다

파서를 만들지 않는다. **필요한 신호를 뽑아 대시보드를 만든다.**

| 뽑을 것 | API |
|---|---|
| 레벨 전환 횟수 / 체류 시간 / 상향-하향 비율 | `Hls.Events.LEVEL_SWITCHED` |
| 대역폭 추정 vs 실측 오차 | `hls.bandwidthEstimate` + `FRAG_LOADED`의 `stats` |
| 버퍼 길이 시계열 | `hls.mainForwardBufferInfo.len` |
| 라이브 지연 | `hls.latency`, `hls.targetLatency`, `hls.drift`, `hls.playingDate` |
| 리버퍼 횟수/시간 | `ERROR` 중 `bufferStalledError` |

재현 시나리오는 Chrome DevTools throttling 또는 `tc netem`으로 대역폭을 계단식 변경.

## 9. HLS 소재 우선순위

| # | 소재 | 소요 | 임팩트 | 지표 |
|---|---|---|---|---|
| **1** | **세그먼트 6s→2s 튜닝 + 트레이드오프 실측** | 2~3일 | **상** | 지연 p50/p95 · 요청 수 3배 · 오리진 전송량 % · 리버퍼 횟수 |
| **2** | `live_playlist_name` 누락 버그 재현·수정 | 0.5~1일 | 중 | 재생 시작 위치 · 플레이리스트 누적 곡선 |
| **3** | **계측 hls.js 플레이어 + ABR 대시보드** | 3~4일 | **상** | 전환 횟수 · 추정 오차율 · 리버퍼 총합 |
| **4** | **SFU 수용 한계 측정** (`lk load-test` + Prometheus) | 1~2일 | **상** | *"4코어에서 양방향 N명 = CPU 85% → 룸 정원 N 확정"* |
| **5** | **라이브 창 5 vs Apple 8.11(6) 검증** | 0.5일 | 중 | m3u8 실측 · iOS 재생 안정성 비교 |
| 6 | Egress 실패 모드 재현 + 런북 | 2~3일 | 중~상 | MTTD · MTTR · 로그 시그니처 |
| 7 | 2단 래더 + 대역폭 제한 하 ABR 검증 | 3~4일 | 중 | 제한 시 리버퍼 · 전환 지연 · CPU 2배 정량화 |

**1 + 3을 묶으면 5~7일에 완결된 스토리가 나온다** —
*"지연을 줄이는 서버 변경 → 그 효과를 자체 계측 도구로 정량 증명"*.

**핵심은 서술 방식이다.**

```
✗ "6초 → 2초로 바꿔서 지연을 18초에서 7초로 줄였습니다"
✓ "6초 → 2초 변경 시 지연 X초 감소, 요청 수 3배, 오리진 대역폭 Y% 증가라는
   트레이드오프를 실측했습니다"
```

## 10. Egress 실패 모드 (미리 알고 가면 시간을 아낀다)

| 증상 | 원인 |
|---|---|
| RoomComposite/Web만 실패하고 Track은 됨 | **`SYS_ADMIN` 미부여** → Chrome 시작 실패 (원인 오판하기 쉬움) |
| `"no response from egress service"` | Redis 주소 불일치 |
| 라이브인데 처음부터 재생됨 | `live_playlist_name` 미설정 |
| HLS 필드가 아예 없음 | TrackEgress를 씀 |
| 두 번째 방 녹화가 큐잉/거부 | 인스턴스당 1잡 제한 |
| 세그먼트 경계에서 재생 깨짐 | `key_frame_interval`을 세그먼트 길이와 다르게 설정 |
| **(확인 필요)** 플레이어 호환성 문제 | `EncodingOptions.audio_codec` 기본이 **OPUS**인데 출력은 **TS**. AAC로 강제되는지 문서에 없음 → **직접 재생해 확인. 확인되면 그 자체가 좋은 사례** |

## 11. 도메인 적합성 (근거)

**SBS디지털뉴스랩 웹개발 공고 우대사항에 "동영상 인코딩 및 플레이어 개발 경험자"가 명시**돼 있고,
iMBC에도 라이브스트리밍 직무가 실재한다. **미디어 업계에서 스트리밍 경험은 명시적 우대 사유다.**

단 **미디어 코어(코덱·트랜스코딩)는 C/C++ 트랙**이라는 것도 사실이다(§13).
우리가 하는 것은 **그 위 계층**이고, 서사도 그렇게 잡는다.

## 12. 면접 대비 개념

**GOP / IDR과 세그먼트 길이**

Apple 7.4가 *"Video segments MUST start with an IDR frame"* 이므로
**세그먼트 길이는 GOP 길이의 정수배여야 한다.** 1.13이 2초 IDR을 권장하므로
**2초 GOP + 6초 세그먼트(=3 GOP)** 가 스펙 정합적 조합이다.
어기면 세그먼트 첫 프레임이 P프레임이 되어 디코딩 불가 → 화면 깨짐.
ABR에서는 **모든 레벨의 IDR이 같은 시각에 정렬**되어야 전환 시 끊김이 없다.

**RTMP vs SRT**

| | RTMP | SRT |
|---|---|---|
| 전송 | TCP (레거시) | UDP 기반 |
| 손실 복구 | TCP 재전송 (지연 누적, HOL 블로킹) | **ARQ** |
| 암호화 | 없음 (RTMPS는 TLS 래핑) | **AES 128/256 내장** |
| 방화벽 | 인바운드 포트 필요한 경우 많음 | 아웃바운드 연결 지원 |
| 위치 | 인제스트 사실상 표준 | 불안정 회선의 현대적 대안 |

**RTMP/SRT는 컨트리뷰션(카메라→서버), HLS/DASH는 디스트리뷰션(서버→시청자).
계층이 다르므로 같은 층위로 비교하면 안 된다.**
LiveKit egress의 `StreamOutput`은 **RTMP(s)와 SRT를 모두 지원**한다.

**CMAF / HLS vs DASH**

CMAF = fMP4 기반 세그먼트 표준. **HLS와 DASH가 같은 세그먼트를 공유하고 매니페스트만 두 벌** 두면 되게 만든 것.
iOS Safari는 DASH를 지원하지 않으므로 **iOS를 지원해야 하면 HLS는 선택이 아니라 필수**다.

**B-frame의 지연 비용** — 저지연 설계의 핵심

디코드 순서 ≠ 표시 순서라 **리오더 하나가 곧 1프레임 버퍼링**이다.
24fps면 약 42ms, 60fps면 약 17ms. B-pyramid(B가 B를 참조)는 압축을 10~15% 개선하지만
디코더 버퍼링을 GOP/2 수준까지 요구한다.

```
HLS/DASH VOD   →  closed GOP + B-pyramid 허용
LL-HLS         →  closed GOP, B-pyramid 최소
WebRTC         →  B-frame off
```

> *"B-frame은 압축 이득을 주지만 **리오더링 버퍼가 곧 레이턴시**다.
> 그래서 WebRTC에서는 B를 끄고, LL-HLS에서도 B-pyramid를 줄인다."*

**Open GOP vs Closed GOP**

Open GOP는 GOP 시작부 B-frame이 직전 GOP를 참조해 비트레이트를 1~3% 절약하지만,
**ABR 전환 시 참조 프레임이 달라져 문제가 생긴다.** Apple 7.4(세그먼트는 IDR로 시작)가
사실상 HLS에서 closed GOP를 강제한다. 품질 차이는 VMAF 측정상 미미하다.

**WHIP / WHEP 표준화 현황** (정확히 알면 좋음)

```
WHIP  = RFC 9725 (2025-03, Proposed Standard). RFC 8840·8842 업데이트
WHEP  = draft-ietf-wish-whep-04, WG Last Call. 아직 RFC 아님
WG 이름 = WISH (WebRTC Ingest Signaling over HTTPS)
```

WHIP 흐름: `POST` + `application/sdp` → `201 Created` + SDP answer + **`Location` 헤더** →
종료는 그 URL로 `DELETE`. STUN/TURN은 201 응답의 `Link` 헤더 `rel="ice-server"`.

**Enhanced RTMP** — RTMP가 안 죽는 이유

RTMP는 스펙상 HEVC/AV1을 공식 지원하지 않지만 **인코더·OBS·CDN 인제스트가 전부 이미 지원**해서
실질 표준으로 남았다. 업계는 버리는 대신 **Enhanced RTMP**로 확장했다 —
FourCC 코덱 시그널링, HEVC/AV1/VP9, Opus/FLAC, 멀티트랙, **나노초 타임스탬프**.
참여: Adobe, YouTube, Twitch, Amazon, FFmpeg, OBS, Dolby, Intel 등.

**SRT 튜닝 숫자** (면접에서 물어볼 수 있음)

```
SRTO_RCVLATENCY  기본 120ms (Live mode)
rule of thumb    SRT Latency = RTT × 4
범위             20 ~ 8000 ms
양단 설정 시     둘 중 큰 값 사용
TSBPD 공식       PTS[x] = ETS[x] + LATENCY + DRIFT
```

**CMAF가 저지연에 유리한 진짜 이유**

```
Chunk     moof + mdat 한 쌍. 참조 가능한 최소 단위
Fragment  chunk 1개 이상
Segment   fragment 1개 이상
```

**chunk는 키프레임으로 시작할 필요가 없다.** 그래서 **세그먼트를 줄이지 않고도** 저지연을
달성한다 — 세그먼트를 줄이면 IDR이 늘어 대역폭이 증가하는데, chunk는 그 대가가 없다.
단 **CTE(chunked transfer encoding)를 오리진→CDN→플레이어 전 구간이 흘려야** 동작한다.

> **CMAF 자체는 지연을 줄이지 않는다.** chunk + CTE 조합이 줄이는 것이다.

**Apple 스펙에서 외워둘 조항 넷**

```
2.4   "You SHOULD NOT use HE-AAC if your audio bit rate is above 64 kbit/s"
7.4   "Video segments MUST start with an IDR frame"
10.1  "The server MUST deliver playlists using gzip content-encoding"
13.7  "MUST use an encrypt:skip pattern of 1:9 (10% partial encryption)"   ← cbcs 패턴의 실체
13.11 "Encryption with SAMPLE-AES-CTR SHALL NOT be used on Apple devices"  ← Apple = CBC 계열
```

**QoE 지표 — Mux 공식 정의와 공식**

```
Startup Time Score  = 8 / (8 + startup_seconds) × 100
Smoothness Score    = [1/√(1+(rebuffer_count/2)²) 와 e^(-10×rebuffer_pct) 의 평균] × 100
Viewer Experience   = Playback Success Score × (트레이드오프 3항 평균)
```

**중요도 서열**: `Playback Success(곱셈 계수) > Smoothness > Startup Time > Quality`
— *"derived from user research across millions of video views"*

**SMPTE ST 2110** (방송사 IT 면접 단골)

SDI가 비디오+오디오+ANC를 한 케이블에 묶던 것과 달리 **에센스를 분리**해
각각 다른 경로로 IP 전송한다. `-20` 비압축 비디오, `-30` PCM 오디오(AES67),
`-40` ANC 데이터. 타이밍은 **IEEE 1588 PTP**(방송 프로파일 ST 2059).

**한 줄 요약 세트**

- *"HLS 지연이 약 3배 세그먼트인 이유는 RFC 8216 §6.3.3의 3 target duration 규칙이고,
  hls.js `liveSyncDurationCount` 기본값 3이 그대로 구현한 것"*
- *"세그먼트 길이는 GOP의 정수배여야 한다 — Apple 7.4가 세그먼트 시작을 IDR로 강제하기 때문"*
- *"TS냐 fMP4냐가 CMAF·HEVC·LL-HLS 가능 여부를 한 번에 결정한다"*
- *"RTMP/SRT는 인제스트, HLS/DASH는 배포. 계층이 다르다"*
- *"B-frame의 리오더링 버퍼가 곧 레이턴시다. 그래서 WebRTC는 B를 끈다"*
- *"CMAF chunk는 키프레임으로 시작할 필요가 없어서, 세그먼트를 안 줄이고도 지연을 줄인다"*
- *"WHIP은 RFC 9725로 표준화됐고 WHEP은 아직 IETF 드래프트다"*

---

---

## 자막은 전달 경로별로 나뉜다 (#65 에서 연결)

처음에 이걸 *"HLS 로 가면 자막도 WebVTT 로 <b>바뀌어야</b> 한다"* 고 적었다. **틀렸다.**

**대체가 아니라 병렬이다.** WebRTC 경로는 WebSocket 자막을 그대로 유지한다.

| 전달 경로 | 자막 | 이유 |
|---|---|---|
| **WebRTC** (저지연 방송·화상) | **WebSocket** | 목적이 초저지연이고, **시청자가 이미 붙어 있다** |
| **HLS** (대규모 배포) | **WebVTT 사이드카** | 영상이 CDN 을 타므로 자막도 타야 한다 |

### HLS 에서 WebSocket 자막을 쓰면 안 되는 이유

```
영상   viewer 1..N ──> CDN ──> 세그먼트         오리진 부담 일정
자막   viewer 1..N ──> 오리진 WebSocket         오리진 부담이 시청자 수에 비례
```

**CDN 으로 줄인 부담을 자막이 도로 만든다.**

### 그렇다고 지금부터 WebVTT 로 통일하면 안 된다

> **처음부터 "자막 = 무조건 WebVTT" 로 만들면 WebRTC 의 초저지연 요구를 HLS 방식에 맞추는 셈이다.**
> WebVTT 는 세그먼트 단위라 세그먼트 길이만큼의 지연이 구조적으로 붙는다.
> 화상강의 자막에 그 지연을 넣을 이유가 없다.

**전달 경로가 늘어나면 자막 파이프라인도 하나 늘어난다.** 그게 맞는 모양이다.

Apple HLS 스펙 기준 자막은 CEA-608 · CEA-708 · **WebVTT** · IMSC1 이다.
WebVTT 는 `X-TIMESTAMP-MAP` 이 필수다 ([조사](../research/05-streaming-hls.md)).
