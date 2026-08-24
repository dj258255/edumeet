# 미디어 공고 키워드가 의미하는 경계 (#124)

## 질문

방송·미디어 공고에는 `ffmpeg`, `GStreamer`, `Bento4`, `H.264`, `RTMP`, `HLS`,
`MPEG-DASH`, `WebRTC` 같은 단어가 같이 나온다.

이 단어들은 같은 층이 아니다.

```
송출자
  └─ ingest     RTMP / SRT / WHIP / WebRTC / HTTP chunk
       └─ codec H.264 / H.265 / VP8 / Opus / AAC
            └─ packaging  HLS / MPEG-DASH / CMAF / TS / fMP4
                 └─ delivery  nginx / CDN / browser player
```

공고가 보는 것은 특정 도구 이름 하나가 아니라, 이 경계에서 어디가 비용·지연·장애를 만드는지
아는가에 가깝다.

## EduMeet 에 적용한 것

| 키워드 | 지금 적용 | 판단 |
|---|---|---|
| WebRTC | 화상채팅 | 다자간 양방향 지연이 중요하므로 LiveKit SFU 유지 |
| HLS | 라이브·오디오 방송 delivery | 다수 시청자에게 HTTP 파일로 배포, CDN 확장 가능 |
| ffmpeg | HLS 패키징·재인코딩 | H264 리먹싱 / VP8 재인코딩 / audio-only 경로를 분리 |
| H.264 | 서버 비용 판단 기준 | H264 입력은 `-c copy`, VP8 입력은 H264 재인코딩 |
| RTMP/SRT/WHIP | 아직 안 함 | OBS·외부 인코더를 받아야 할 때 ingest 경로로 도입 |
| GStreamer | 아직 안 함 | LiveKit Ingress 같은 전문 ingest/transcode 파이프라인에서 의미 있음 |
| Bento4 | 아직 안 함 | VOD, fMP4/CMAF, DASH/HLS dual packaging, DRM 쪽으로 갈 때 후보 |
| MPEG-DASH | 아직 안 함 | HLS 만으로 브라우저/모바일 요구를 충족하는 현재 단계에서는 과함 |

## 왜 GStreamer 를 바로 붙이지 않았나

LiveKit Ingress 는 RTMP/WHIP 입력을 받아 GStreamer 기반 파이프라인으로 WebRTC 호환 미디어를 만든다.
공식 문서도 트랜스코딩 인그레스가 CPU 를 많이 쓰며 별도 인그레스 인스턴스와 오토스케일링을 전제한다고 설명한다.

EduMeet 의 현재 방송자는 브라우저다. 외부 OBS 나 방송 장비를 받지 않는다.
그래서 지금은 MediaRecorder 조각을 HTTP 로 받아 ffmpeg 로 HLS delivery 를 만드는 것이 더 작고 검증하기 쉽다.

도입 조건:

- OBS / 외부 인코더 입력이 필요하다.
- RTMP/SRT/WHIP ingest endpoint 가 필요하다.
- 인코딩 워커를 앱 서버와 분리할 수 있다.
- CPU/메모리/동시 ingest 수를 별도로 측정할 수 있다.

## 왜 Bento4 를 바로 붙이지 않았나

Bento4 는 MP4/fMP4 기반 패키징, DASH, HLS master playlist, encryption/DRM 같은 영역에 강하다.
하지만 지금 구현은 라이브 TS HLS 다.

도입 조건:

- VOD 다시보기 영상을 보존한다.
- HLS 와 DASH 를 동시에 내야 한다.
- CMAF/fMP4 기반 LL-HLS 로 넘어간다.
- DRM/encryption 이 요구된다.

## 왜 AI 아카이브 검색을 만들지 않았나

MBC Archive 공개 검색 화면에는 이미 AI 기반 장면 검색과 AI 기반 대본 검색이 있다.
MBC 보도자료도 방송 제작 AX 를 포스트프로덕션, 맥락 인지 AI, 미디어 온톨로지 관점에서 설명한다.

따라서 EduMeet 포트폴리오에서 "AI 아카이브 검색"을 급히 새로 붙이면,
MBC 가 이미 하고 있는 영역을 얕게 따라 한 기능처럼 보일 위험이 있다.

현재 EduMeet 에서 더 좋은 AI 포지션은 다음이다.

- Java 백엔드와 Python AI 서버의 계약을 파일 하나로 묶는다.
- 실시간이 아닌 STT/요약은 `realtime: false` 로 과장하지 않는다.
- AI 자막이 화면까지 도달하는 경로를 검증한다.
- 미디어 delivery 에서는 AI보다 코덱·지연·캐시·QoE 지표를 먼저 측정한다.

공고의 "AI 기술 기반 서비스 시스템 설계 및 구현"에 대응할 때도,
**AI 모델 자체를 크게 만드는 것보다 서비스 경계와 운영 경로를 정확히 연결한 것**을 보여 주는 편이
현재 포트폴리오의 색과 맞다.

## 한 줄 결론

화상채팅은 WebRTC/SFU, 방송 배포는 HLS/CDN, 오디오 방송은 audio-only HLS delivery 로 잡고,
RTMP/SRT/GStreamer/Bento4/DASH 는 **도입 조건을 가진 다음 단계**로 둔다.

지금 당장 보여 줄 기본기는 ffmpeg 기반 HLS 에서
리먹싱/재인코딩/키프레임/지연/캐시/플레이어 지표를 분리해 측정하는 것이다.
