# 송출 비트레이트 화질 하네스 (#199)

이 하네스는 앱의 `videoBitsPerSecond` 설정값이 아니라 브라우저 `MediaRecorder`가 실제로 만든 파일을 잰다. Chromium과 설치된 Chrome stable의 fake camera에 같은 y4m 소스를 넣고, 300 kbps부터 4 Mbps까지 같은 콘텐츠를 녹화한 뒤 자연 영상용 VMAF와 슬라이드 판독용 OCR을 함께 계산한다.

## 전제와 실행

macOS 기준으로 다음이 필요하다.

- `ffmpeg`와 `libvmaf` 필터
- `tesseract`와 `eng`, `kor` 언어 데이터
- `perf/browser/package.json`의 Playwright 의존성과 Chromium 브라우저

```sh
npm install --prefix perf/browser
./perf/bitrate/run.sh 2026-09-23-local 40
```

사용법은 `run.sh RUN_ID [RECORD_SECONDS] [FONT_PATH]`이며 두 번째 인자는 각 브라우저 녹화 시간(초)이다. 40초를 써야 네 장의 10초 슬라이드 중앙 프레임을 모두 채점한다. 결과는 `perf/bitrate/out/2026-09-23-local/` 아래에 저장되며 `table.md`에 Chromium과 Chrome 표가 각각 나온다. `actual kbps`는 요청한 ladder 값이 아니라 녹화 파일 크기와 실제 녹화 시간으로 계산한 평균이다.

## 소스와 점수

`make-sources.sh`는 1280x720, 30fps, 40초짜리 슬라이드·손글씨·움직이는 합성 카메라 소스를 만든다. 슬라이드에는 작은 글자, 숫자, 한국어/영어 문장과 움직이는 포인터가 있고 10초마다 장면이 바뀐다. `slides.truth.txt`와 `truth/scene-*.txt`는 OCR 정답이다. 폰트는 두 번째 인자로 지정할 수 있으며, 기본값은 macOS/Linux에서 찾은 첫 글꼴이다.

VMAF는 처음 2초를 버리지만 녹화본을 단순 CFR로 재생성해 맞추지 않는다. `ffprobe -show_frames`의 각 PTS로 `round(pts*30)+offset` 원본 프레임을 찾아 1:1 페어 영상을 만들고, 그 페어만 VMAF/PSNR에 넣는다. 사용되지 않은 원본 프레임은 `dropped frames`와 비율로 보고한다. ffprobe의 실제 프레임 수·평균 fps·명목 fps와 VFR 여부, 선택된 offset/PSNR을 결과에 남긴다. 짝 PSNR 평균이 40dB 초과인데 VMAF 평균이 70 미만이면 표에 `정렬 실패`를 표시한다. OCR은 각 슬라이드 중앙 프레임에서 원본과 녹화본을 각각 읽으며, 원본 OCR 점수는 인식기 자체의 ceiling이다.

## 제한과 해석

- fake camera 입력은 실제 얼굴 카메라가 아니다. `testsrc2`는 움직임과 색 변화의 대리물일 뿐 얼굴·조명·피부색을 대표하지 않는다.
- VMAF는 자연 영상에 학습됐으므로 글자 획의 보존을 잘 설명하지 못할 수 있다. 따라서 VMAF와 OCR이 같은 방향으로 움직이는지, 다르면 그 차이를 결과에 남긴다.
- MediaRecorder의 브라우저 지원 코덱을 앱의 `codecChoice.js`와 같은 순서(H264 우선)로 시도하지만 실제 `recorder.mimeType`을 결과 메타데이터에 기록한다.
