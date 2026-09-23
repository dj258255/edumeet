#!/usr/bin/env bash
set -euo pipefail

# Chromium fake-camera 입력은 실제 카메라 프레임과 같은 y4m이다. 장면별
# truth 파일도 함께 남겨 OCR 점수의 기준을 결과물과 분리한다.

OUT_DIR=${1:?usage: make-sources.sh OUT_DIR [FONT_FILE]}
FONT_PATH=${2:-}
WIDTH=${WIDTH:-1280}
HEIGHT=${HEIGHT:-720}
FPS=${FPS:-30}
SCENE_SECONDS=${SCENE_SECONDS:-10}
SCENE_COUNT=${SCENE_COUNT:-4}

mkdir -p "$OUT_DIR/truth" "$OUT_DIR/tmp"

if [[ -z "$FONT_PATH" ]]; then
  for candidate in \
    /System/Library/Fonts/AppleSDGothicNeo.ttc \
    /System/Library/Fonts/Supplemental/Arial.ttf \
    /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc \
    /usr/share/fonts/truetype/nanum/NanumGothic.ttf \
    /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf; do
    if [[ -f "$candidate" ]]; then
      FONT_PATH=$candidate
      break
    fi
  done
fi

if [[ -z "$FONT_PATH" || ! -f "$FONT_PATH" ]]; then
  echo "font file not found; pass one as the second argument" >&2
  exit 2
fi

filter_escape() {
  printf '%s' "$1" | sed "s/[\\:'']/\\\\&/g"
}

FONT_FILTER=$(filter_escape "$FONT_PATH")

cat > "$OUT_DIR/truth/scene-00.txt" <<'EOF'
강의 1: 네트워크 지연과 버퍼링
Latency budget = capture + encode + upload + player
지연 목표 1.5초, 재생률 30 fps, 샘플 001
EOF
cat > "$OUT_DIR/truth/scene-01.txt" <<'EOF'
강의 2: 적응형 비트레이트
720p 2500 kbps / 360p 700 kbps / GOP 2s
화질은 숫자와 작은 글자를 함께 보존해야 한다
EOF
cat > "$OUT_DIR/truth/scene-02.txt" <<'EOF'
강의 3: 오류 복구 순서
1. 재시도 2. 새 세그먼트 3. 재생 위치 확인
HTTP 202 OK, HTTP 409 CONFLICT, HTTP 500 ERROR
EOF
cat > "$OUT_DIR/truth/scene-03.txt" <<'EOF'
강의 4: 측정 메모
VMAF 는 자연 영상, OCR 은 슬라이드 판독성을 본다
같은 평균이어도 사람이 읽는 결과는 다를 수 있다
EOF

{
  for scene in "$OUT_DIR"/truth/scene-*.txt; do
    printf '[%s]\n' "$(basename "$scene" .txt)"
    cat "$scene"
  done
} > "$OUT_DIR/slides.truth.txt"

make_raw() {
  local name=$1
  local filter=$2
  local raw="$OUT_DIR/tmp/$name.yuv"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "color=c=white:s=${WIDTH}x${HEIGHT}:r=${FPS}:d=${SCENE_SECONDS}" \
    -vf "$filter" -frames:v "$((FPS * SCENE_SECONDS))" \
    -c:v rawvideo -pix_fmt yuv420p -f rawvideo "$raw"
  printf '%s\n' "$raw"
}

raw_files=()
for index in 0 1 2 3; do
  text_file="$OUT_DIR/truth/scene-$(printf '%02d' "$index").txt"
  text_filter="drawtext=fontfile=${FONT_FILTER}:textfile=${text_file}:fontcolor=black:fontsize=40:line_spacing=12:x=55:y=45:shadowcolor=gray:shadowx=2:shadowy=2"
  text_filter+=" ,drawtext=fontfile=${FONT_FILTER}:text='EDUMEET  #199':fontcolor=gray:fontsize=16:x=55:y=680"
  text_filter+=" ,drawbox=x='100+mod(80*t\\,1060)':y='620+20*sin(t*2)':w=14:h=14:color=red:t=fill"
  raw_files+=("$(make_raw "slide-$index" "$text_filter")")
done

cat "${raw_files[@]}" | ffmpeg -hide_banner -loglevel error -y \
  -f rawvideo -pix_fmt yuv420p -s "${WIDTH}x${HEIGHT}" -r "$FPS" -i - \
  -frames:v "$((FPS * SCENE_SECONDS * SCENE_COUNT))" \
  -f yuv4mpegpipe "$OUT_DIR/slides.y4m"

handwriting_filter="drawbox=x=100:y=140:w='min(980\\,t*190)':h=6:color=blue:t=fill"
handwriting_filter+=" ,drawbox=x=100:y=220:w='min(760\\,max(0\\,(t-3)*150))':h=6:color=blue:t=fill"
handwriting_filter+=" ,drawbox=x='100+min(860\\,max(0\\,(t-6)*130))':y='300+50*sin(t)':w=6:h=120:color=red:t=fill"
handwriting_filter+=" ,drawtext=fontfile=${FONT_FILTER}:text='handwriting proxy / 점진적으로 그려지는 선':fontcolor=black:fontsize=28:x=100:y=520"
handwriting_raw=$(make_raw handwriting "$handwriting_filter")
ffmpeg -hide_banner -loglevel error -y -f rawvideo -pix_fmt yuv420p \
  -s "${WIDTH}x${HEIGHT}" -r "$FPS" -i "$handwriting_raw" \
  -frames:v "$((FPS * SCENE_SECONDS))" -f yuv4mpegpipe "$OUT_DIR/handwriting.y4m"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=${WIDTH}x${HEIGHT}:rate=${FPS}:duration=$((SCENE_SECONDS * SCENE_COUNT))" \
  -pix_fmt yuv420p -f yuv4mpegpipe "$OUT_DIR/camera.y4m"

rm -rf "$OUT_DIR/tmp"
printf 'sources written to %s (font: %s)\n' "$OUT_DIR" "$FONT_PATH"
