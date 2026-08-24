# HLS 코덱 경로 벤치마크 (#124)

## 왜 재나

HLS 방송에서 성능은 "HLS 를 쓴다" 로 결정되지 않는다.
같은 HLS 라도 서버가 하는 일이 다르다.

| 경로 | 서버가 하는 일 | 비용 |
|---|---|---|
| H264/AAC 입력 | 컨테이너만 HLS 로 바꾼다 (`-c copy`) | 낮음 |
| VP8/Opus 입력 | H264/AAC 로 다시 인코딩한다 | 높음 |
| 오디오 전용 | AAC 로 바꾸고 비디오를 버린다 | 낮음 |

그래서 발표자 브라우저가 어떤 코덱을 내는지, 그리고 그 결과 서버가 리먹싱을 하는지
재인코딩을 하는지를 화면에 노출한다.

## 공식 문서에서 확인한 경계

- FFmpeg HLS muxer 의 `hls_time` 기본값은 2초다.
- FFmpeg 는 세그먼트를 임의 프레임에서 자르지 않고, 시간이 지난 뒤 다음 키프레임에서 자른다.
- FFmpeg 문서는 인코딩할 때 closed GOP 와 세그먼트 시간에 맞는 GOP 크기를 요구한다.
- LiveKit Ingress 는 RTMP/WHIP 입력을 GStreamer 기반 파이프라인으로 변환한다. 트랜스코딩이 켜진 인그레스는 작업당 CPU 비용이 커서 별도 인그레스 인스턴스와 오토스케일링을 전제한다.
- Bento4 는 `mp4dash`, `mp4hls` 처럼 VOD/패키징·DASH/HLS·멀티비트레이트 쪽 도구다. 지금 구현은 라이브 TS HLS 라서 ffmpeg muxer 가 더 직접적이다.

## 재현 명령

```bash
DURATION=20 scripts/bench-hls-codec-paths.sh
```

스크립트는 합성 영상을 만들고 세 경로를 비교한다.

1. H264/AAC 파일을 HLS 로 리먹싱한다.
2. VP8/Opus WebM 을 H264/AAC HLS 로 재인코딩한다.
3. 오디오만 AAC HLS 로 만든다.
4. 키프레임 간격이 5초인 소스를 `hls_time=2` 로 리먹싱하면 실제 세그먼트가 5초로 남는지 확인한다.

시청자 부하는 별도로 잰다.

```bash
k6 run -e HLS_PLAYLIST_URL=https://studywithtymee.com/hls/{meetingId}/live.m3u8 k6/hls-viewer.js
```

ffmpeg 벤치마크는 **HLS 를 만드는 비용**이고, k6 시청자 부하는 **이미 만들어진 HLS 를 나눠 주는 비용**이다.
둘을 섞으면 CPU 병목인지, nginx 파일 서빙인지, 캐시/네트워크 문제인지 알 수 없다.

## 로컬 결과

환경: macOS Apple Silicon, ffmpeg 8.0, 20초 720p30 합성 소스.

| path | real | user | sys | segments |
|---|---:|---:|---:|---|
| H264 remux (-c copy) | 0.07 | 0.05 | 0.02 | n=10 min=2.000s max=2.000s avg=2.000s |
| VP8 -> H264 transcode | 1.10 | 5.45 | 0.44 | n=10 min=2.000s max=2.000s avg=2.000s |
| audio-only Opus -> AAC | 0.21 | 0.22 | 0.04 | n=11 min=0.021s max=2.005s avg=1.821s |

20초 분량 기준으로 H264 리먹싱은 user CPU 0.05초, VP8 재인코딩은 5.45초였다.
같은 HLS 라도 100배 이상 차이가 난다.

오디오 전용에서 0.021초짜리 첫 세그먼트가 나온다. 합성 WebM 의 시작 타임스탬프와
오디오 프레임 경계 때문에 첫 조각이 짧게 떨어진 것이다. 실제 서비스에서는
초기 조각 하나보다 지속 구간의 평균과 버퍼 동작을 봐야 한다.

## 키프레임 증명

| path | segments |
|---|---|
| H264 remux, source GOP 5s | n=4 min=5.000s max=5.000s avg=5.000s |
| re-encode with GOP 2s | n=10 min=2.000s max=2.000s avg=2.000s |

`hls_time=2` 를 줘도 리먹싱은 2초 세그먼트를 보장하지 않는다.
키프레임이 5초마다 있으면 세그먼트도 5초가 된다.

따라서 리먹싱은 CPU 를 크게 아끼지만, 지연 통제권을 브라우저의 키프레임 간격에 넘긴다.
재인코딩은 CPU 를 쓰는 대신 GOP 를 강제해 세그먼트 길이를 통제한다.

## OCI 서버 결과

환경: OCI VM.Standard.A1.Flex aarch64 2 OCPU, Docker `lscr.io/linuxserver/ffmpeg:latest`
(`ffmpeg 9.0`), 20초 720p30 합성 소스.

서버 호스트에는 ffmpeg 를 설치하지 않았다. 벤치마크는 같은 aarch64 서버 위에서
ffmpeg 컨테이너로 실행했다. 실제 앱 이미지는 `backend/Dockerfile` 에 ffmpeg 를 포함한다.

```bash
ssh edumeet-oci \
  'docker run --rm -e DURATION=20 \
    -v /tmp/bench-hls-codec-paths.sh:/bench.sh:ro \
    --entrypoint bash lscr.io/linuxserver/ffmpeg:latest /bench.sh'
```

| path | real | user | sys | segments |
|---|---:|---:|---:|---|
| H264 remux (-c copy) | 0.084 | 0.079 | 0.012 | n=10 min=2.000s max=2.000s avg=2.000s |
| VP8 -> H264 transcode | 5.456 | 8.633 | 0.154 | n=10 min=2.000s max=2.000s avg=2.000s |
| audio-only Opus -> AAC | 0.245 | 0.282 | 0.019 | n=11 min=0.021s max=2.005s avg=1.821s |

같은 20초 입력에서 리먹싱은 real 0.084초, 재인코딩은 real 5.456초였다.
**서버에서 약 65배 차이**다. 그래서 "HLS 를 한다"보다 "서버가 복사하는지 다시
인코딩하는지"가 더 중요하다.

키프레임 증명도 서버에서 같았다.

| path | segments |
|---|---|
| H264 remux, source GOP 5s | n=4 min=5.000s max=5.000s avg=5.000s |
| re-encode with GOP 2s | n=10 min=2.000s max=2.000s avg=2.000s |

## 운영 HLS viewer 부하

운영 서버에 60초짜리 테스트 방송을 만들었다.

| 항목 | 값 |
|---|---:|
| URL | `https://studywithtymee.com/hls/bench-20260824214911/live.m3u8` |
| 길이 | 60초 |
| 세그먼트 | 30개 |
| 세그먼트 길이 | min 2.000s / max 2.000s / avg 2.000s |
| 크기 | 15MB |

헤더도 확인했다.

| 파일 | Cache-Control | 의미 |
|---|---|---|
| `live.m3u8` | `no-cache, no-store, must-revalidate` | 계속 바뀌므로 캐시하지 않는다 |
| `seg_00000.ts` | `public, max-age=4` | 세그먼트는 짧게만 캐시한다 |

첫 실행은 실패했다.

```
ReferenceError: URL is not defined
```

k6 런타임에는 브라우저/Node 의 `URL` 전역 객체가 없었다. `k6 inspect` 는 스크립트를
실행하지 않으므로 이 문제를 못 잡았다. 그래서 실제 smoke test 를 추가했고,
URL 해석과 cache-busting 을 직접 구현했다.

운영 URL 측정:

```bash
k6 run \
  -e HLS_PLAYLIST_URL=https://studywithtymee.com/hls/bench-20260824214911/live.m3u8 \
  -e VUS=20 \
  -e RAMP=10s \
  -e HOLD=1m \
  --summary-export /tmp/edumeet-hls-k6-20260824.json \
  k6/hls-viewer.js
```

결과:

| 지표 | 값 |
|---|---:|
| checks | 3,936 / 3,936 성공 |
| playlist status | 492 / 492 성공 |
| segment status | 1,476 / 1,476 성공 |
| playlist wait p95 | 391.38ms |
| segment wait p95 | 372.41ms |
| HTTP requests | 1,968 |
| http_req_failed | 0 / 1,968 |
| data received | 727MB |
| 평균 수신 대역폭 | 8.9MB/s |

이 수치는 "동시 시청자 한계"가 아니다. 20 VU 로 운영 경로가 정상 동작하는지 본
스모크에 가까운 부하 테스트다. 더 큰 값을 주장하려면 별도 시간대, 더 긴 hold,
서버 네트워크/CPU 관측을 붙여 다시 재야 한다.

## 적용한 변경

- 운영 이미지에 `ffmpeg` 를 포함시켰다.
  - `backend/Dockerfile` 에 `ffmpeg` 와 `/usr/bin/time` 을 명시했다.
  - 검증: `docker run --entrypoint ffmpeg edumeet-backend:hls-check -version`
  - HLS 출력 디렉터리는 앱 컨테이너와 호스트 nginx 가 같은 `/var/lib/edumeet/hls` 를 본다.
  - 배포 단계에서 `sudo install -d -o 10001 -g 10001 /var/lib/edumeet/hls` 를 실행한다.
    앱 컨테이너의 `spring` 사용자가 UID/GID 10001 이기 때문이다.
- 발표자 화면에 서버 처리 경로를 노출한다.
  - `컨테이너만 변환`
  - `재인코딩`
- 송출 프로파일을 나눴다.
  - 표준 720p30
  - 데이터 절약 360p15
  - 오디오 전용
- 시청 화면에 HLS 품질 지표를 노출한다.
  - live latency
  - target latency
  - buffer
  - bandwidth estimate
  - fragment load time
  - dropped frames
- `hls.js` 를 동적 import 로 분리한다.
  - `BroadcastWatchView` JS: 402.77KB → 4.91KB
  - `hls.js`: 398.70KB 별도 청크
  - Safari 처럼 네이티브 HLS 를 쓰는 브라우저는 hls.js 를 받지 않는다.
- 백엔드 세션 상태 계약을 추가한다.
  - `GET /api/v1/meeting/{meetingId}`
  - `sessionType`, `broadcasting`, `hlsPlaylistUrl` 을 내려준다.
  - 클래스 구성원만 볼 수 있게 401/403/200 을 테스트로 고정했다.

## 운영에서 확인할 것

로컬 수치는 설계 판단용이다. 제출용 숫자는 운영과 분리해서 쓴다.

1. OCI 서버에서 같은 스크립트를 앱 이미지 안에서 다시 실행한다.
   - 목적: 2 OCPU aarch64 에서 리먹싱/재인코딩/audio-only 비용을 분리한다.
2. 운영 URL의 HLS viewer 부하를 k6 로 잰다.
   - 목적: ffmpeg CPU 가 아니라, 이미 만들어진 `.m3u8`/`.ts` 를 nginx/Cloudflare 경유로 나눠 줄 때의 응답을 본다.
3. 두 값을 섞지 않는다.
   - ffmpeg 벤치마크는 생성 비용이다.
   - k6 HLS viewer 는 배포 비용이다.

## 아직 안 한 것

### ABR

적응형 비트레이트는 delivery 구간에서 여러 화질을 동시에 만드는 것이다.
2 OCPU 서버에서 720p/480p/360p 계단을 동시에 인코딩하면 계단 수만큼 CPU 비용이 늘어난다.

그래서 지금은 ABR 이 아니라 **ingest 를 낮추는 선택지**를 둔다.
모바일·약한 네트워크 사용자는 360p15 로 덜 보내고, 서버는 덜 받는다.

도입 조건:

- 시청자가 많아 CDN 을 붙인다.
- 시청자의 네트워크 편차가 실제 지표로 보인다.
- 별도 인코딩 워커나 GPU/더 큰 CPU 인스턴스를 둘 수 있다.

### MPEG-DASH / Bento4

DASH 와 HLS 를 같이 내보내거나, VOD 를 fMP4/CMAF 로 패키징해야 하면 Bento4 가 후보가 된다.
지금은 라이브 TS HLS 이므로 ffmpeg HLS muxer 로 충분하다.

### RTMP / SRT / WHIP ingest

브라우저 발표자만 다루면 MediaRecorder + HTTP chunk 로도 송출/배포 경계를 검증할 수 있다.
OBS·외부 인코더·전문 방송 장비를 받아야 하는 순간 RTMP/SRT/WHIP ingest 를 붙인다.
그때는 GStreamer 기반 LiveKit Ingress 나 별도 ingest 서버가 후보가 된다.
