# 방송 모드 세 개 — 송출과 배포를 분리해서 본다

화상채팅, 라이브 영상 방송, 오디오 방송은 모두 "실시간 미디어"처럼 보인다.
하지만 설계 기준은 다르다.

| 모드 | 송출 구간 | 배포 구간 | 채팅 |
|---|---|---|---|
| `INTERACTIVE` 화상채팅 | 사용자 ↔ 사용자 | WebRTC SFU | STOMP/WebSocket |
| `BROADCAST` 라이브 영상 방송 | 발표자 → 서버 | HLS delivery | STOMP/WebSocket |
| `AUDIO_BROADCAST` 오디오 방송 | DJ/강사 → 서버 | audio-only HLS delivery | STOMP/WebSocket |

핵심은 **HLS 를 송출용으로 보지 않는 것**이다.
HLS 는 서버가 다수 시청자에게 나눠 주기 좋은 delivery 형식이다.
방송자가 서버로 보내는 ingest 구간은 별도 문제다.

---

## 1. 화상채팅 — WebRTC SFU

화상채팅은 양방향이고 지연 목표가 1초 미만이다.
1:1 이면 P2P WebRTC 로도 충분하지만, 수십 명 이상이면 mesh 구조가 맞지 않는다.
참가자가 늘수록 각 브라우저가 유지해야 하는 연결과 업로드 부담이 급격히 커진다.

그래서 `INTERACTIVE` 는 WebRTC SFU 를 쓴다.
EduMeet 에서는 LiveKit 이 이 역할을 맡는다.

```
참가자 브라우저
  ↕ WebRTC
LiveKit SFU
  ↕ WebRTC
다른 참가자 브라우저
```

방송 HLS 에서 LiveKit Egress 를 걷어냈다고 해서 LiveKit 을 제거한 것이 아니다.
**LiveKit 은 화상채팅의 SFU 로 남고, HLS 방송 배포에서는 빠진다.**

### 화상채팅에서 합성 비용이 드는가

일반 화상채팅 경로에서는 **화면 합성 비용이 들지 않는다.**
SFU 는 각 참가자의 RTP 트랙을 받아 필요한 구독자에게 전달한다.

합성 비용이 드는 것은 `RoomComposite` 같은 egress/녹화/송출 경로다.
여러 참가자 화면을 하나의 레이아웃으로 렌더링하고 다시 인코딩해야 하기 때문이다.

따라서 구분은 이렇다.

| 경로 | 합성 | 이유 |
|---|---|---|
| 화상채팅 SFU | 없음 | 트랙을 전달한다 |
| RoomComposite 녹화/방송 | 있음 | 여러 화면을 하나의 영상으로 만든다 |
| EduMeet 직접 HLS 방송 | 없음 | 발표자 한 명의 조각만 받는다 |

---

## 2. 라이브 영상 방송 — HLS delivery

라이브 방송은 양방향 회의가 아니다.
한 명이 송출하고 다수가 본다.

현재 구현은 포트폴리오 범위에 맞춰 브라우저 `MediaRecorder` 조각을 HTTP 로 올린다.

```
발표자 브라우저 MediaRecorder(2초 조각)
  → POST /api/v1/meeting/{id}/broadcast/chunk
백엔드 순서 버퍼 + 유계 큐
  → ffmpeg stdin
ffmpeg
  → .ts 세그먼트 + live.m3u8
nginx /hls/
  → 시청자 <video> + hls.js
```

이 구조에서 WebSocket 은 미디어에 쓰지 않는다.
채팅만 STOMP/WebSocket 을 쓴다.
미디어까지 WebSocket 에 얹으면 방송이 밀릴 때 채팅도 같이 밀린다.

### 현재 구현과 확장 방향

| 구간 | 현재 | 확장 방향 |
|---|---|---|
| ingest | MediaRecorder HTTP chunk | RTMP/SRT/WebRTC ingest |
| packaging | ffmpeg HLS | 동일 |
| delivery | nginx + hls.js | CDN + HLS/LL-HLS |

Spoon Echo 의 구조도 같은 방향이다.
DJ/VJ 가 서버로 보내는 구간은 SRT/RTMP 로 받고, 서버가 다수 청취자에게 나눠 주는 구간은 HLS 로 둔다.
즉 HLS 는 ingest 가 아니라 delivery 다.

---

## 3. 오디오 방송 — audio-only HLS delivery

오디오 방송도 영상 방송과 같은 구조를 따른다.
다만 비디오 트랙 없이 오디오만 HLS 세그먼트로 내보낸다.

정확한 표현은 **"HLS audio mode" 가 아니라 "audio-only HLS delivery"** 다.
HLS 에 별도 오디오 모드가 있는 것이 아니라,
AAC 같은 오디오 트랙만 담은 세그먼트와 `m3u8` 플레이리스트를 제공하는 것이다.

현재 구현은 다음과 같다.

```
DJ/강사 브라우저 MediaRecorder(audio)
  → HTTP chunk
백엔드
  → ffmpeg -vn + AAC
nginx /hls/
  → audio-only HLS
```

오디오 방송은 접근성 관점에서도 의미가 있다.
라디오는 청각장애인에게 원천적으로 닫힌 매체인데,
EduMeet 에는 이미 STT 자막 경로가 있으므로 오디오 방송에 자막을 붙일 수 있다.

---

## 4. 공통 기능 — 채팅과 자막

채팅과 자막은 세 모드 모두에서 백엔드 STOMP 경로를 탄다.

```
구독   /topic/rooms/{id}
       /topic/rooms/{id}/captions

발행   /app/rooms/{id}/send
```

LiveKit DataChannel 로 채팅과 자막을 보내지 않는다.
서버를 거치지 않으면 다시보기 저장도, AI 자막 중계도, 권한 검사도 같은 계약으로 묶을 수 없다.

---

## 5. 지금 된 것과 안 된 것

| 항목 | 상태 |
|---|---|
| 화상채팅 WebRTC SFU | 구현됨. LiveKit 사용 |
| 화상채팅 100명 최적화 정책 | 일부만. `adaptiveStream`/`dynacast` 를 켰고, 역할·active speaker·가시 영역 기반 구독은 별도 과제 |
| 라이브 영상 송출/시청/채팅 | 구현됨. MediaRecorder HTTP ingest + HLS delivery |
| 오디오 송출/청취/채팅 | 구현됨. audio-only HLS delivery |
| RTMP/SRT ingest | 아직 안 함. 확장 방향 |
| CDN 배포 | 아직 안 함. nginx 파일 서빙까지 |
| VOD 다시보기 영상 | 아직 안 함. 현재 HLS 는 `delete_segments` 로 오래된 세그먼트를 지운다 |

이 문서의 기준은 단순하다.

> 화상채팅은 WebRTC/SFU, 방송 배포는 HLS/CDN, 오디오 방송은 audio-only HLS delivery.
> 채팅과 자막은 별도의 STOMP/WebSocket 계약으로 둔다.
