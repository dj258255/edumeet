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

사용법은 `run.sh RUN_ID [RECORD_SECONDS] [FONT_PATH]`이며 두 번째 인자는 각 브라우저 녹화 시간(초)이다. 40초를 써야 네 장의 10초 슬라이드 중앙 프레임을 모두 채점한다. 결과는 `perf/bitrate/out/2026-09-23-local/` 아래에 저장되며 `table.md`에 Chromium과 Chrome 표가 각각 나온다. `actual kbps`는 요청한 ladder 값이 아니라 파일 크기 × 8 / ffprobe video stream duration으로 계산하며, 벽시계 기반 값은 참고용으로만 보관한다. 실패 행이 있으면 마지막에 수를 출력하고 0이 아닌 코드로 끝난다.

## 소스와 점수

`make-sources.sh`는 1280x720, 30fps, 40초짜리 슬라이드·손글씨·움직이는 합성 카메라 소스를 만든다. 슬라이드에는 작은 글자, 숫자, 한국어/영어 문장과 움직이는 포인터가 있고 10초마다 장면이 바뀐다. 손글씨도 40초 길이이며 10초 주기의 획 그리기를 반복한다. `slides.truth.txt`와 `truth/scene-*.txt`는 OCR 정답이다. 화면에 실제로 그린 `EDUMEET #199` footer도 각 truth에 포함하고, 본문 textfile에서는 중복해서 그리지 않는다. 폰트는 두 번째 인자로 지정할 수 있으며, 기본값은 macOS/Linux에서 찾은 첫 글꼴이다.

세 원본의 모든 프레임 오른쪽 아래에는 16개의 24x24 셀로 된 프레임 번호 띠가 있다. 앞 15비트가 원본 프레임 번호이고 마지막 비트는 even parity다. 흰색은 1, 검은색은 0이며 회색 테두리가 있어 저비트레이트에서도 셀을 분리한다. 슬라이드 장면을 이어 붙일 때도 프레임 번호는 전역으로 증가한다. `score.sh`는 녹화 프레임마다 이 띠를 직접 읽어 원본 번호를 얻으므로 PTS/PSNR 오프셋 탐색을 하지 않는다. 칸 평균이 임계값 128의 ±40 안이면 `uncertain`으로 버리고, parity·범위·단조성 검사 실패도 각각 `failureCounts`로 센다. 읽은 번호와 `round(pts*30)`의 잔차 중앙값에서 3프레임을 넘는 `ptsMismatch`도 버린다. 같은 번호가 다시 나오면 첫 프레임만 쓰며 `duplicateMappings`로 센다. 원본도 같은 디코더로 검사하며 모든 프레임이 정확히 자기 번호로 읽히지 않으면 실패 행을 남긴다.

VMAF는 띠를 포함한 동일한 번호의 프레임 쌍으로 계산한다(`frameBand.includedInVmaf: true`). 처음 2초인 원본 프레임 번호 0~59는 채점에서 제외하고, 마지막으로 읽힌 원본 번호까지를 분모로 하여 `dropped frames`와 비율을 계산한다. OCR 입력에서는 띠 영역을 흰색으로 마스킹한다. VMAF 1%가 10 미만이면 해당 pair의 녹화/원본 프레임을 `*-vmaf-low/`에 PNG로 보존하고 결과에 두 경로를 남긴다. 원본 OCR 점수는 인식기 자체의 ceiling이다.

녹화 해상도가 1280x720이 아니거나 OCR truth 디렉터리·원본 self-check가 없으면 crop 전에 `채점 실패(...)` 행을 기록한다. 표의 null 비율은 0이 아니라 `-`로 표시하며, VMAF↔OCR 상관은 두 값이 모두 있는 행만 사용한다.

## 제한과 해석

- fake camera 입력은 실제 얼굴 카메라가 아니다. `testsrc2`는 움직임과 색 변화의 대리물일 뿐 얼굴·조명·피부색을 대표하지 않는다.
- VMAF는 자연 영상에 학습됐으므로 글자 획의 보존을 잘 설명하지 못할 수 있다. 따라서 VMAF와 OCR이 같은 방향으로 움직이는지, 다르면 그 차이를 결과에 남긴다.
- MediaRecorder의 브라우저 지원 코덱을 앱의 `codecChoice.js`와 같은 순서(H264 우선)로 시도하지만 실제 `recorder.mimeType`을 결과 메타데이터에 기록한다.
