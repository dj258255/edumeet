#!/usr/bin/env bash
set -euo pipefail

DURATION="${DURATION:-30}"
FPS="${FPS:-30}"
SEGMENT="${SEGMENT:-2}"
WORKDIR="${WORKDIR:-$(mktemp -d /tmp/edumeet-hls-bench.XXXXXX)}"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing command: $1" >&2
    exit 1
  }
}

need ffmpeg
need ffprobe
need awk
need sed
need grep

SRC="$WORKDIR/src"
OUT="$WORKDIR/out"
mkdir -p "$SRC" "$OUT"

echo "# EduMeet HLS codec path benchmark"
echo
echo "- duration: ${DURATION}s"
echo "- fps: ${FPS}"
echo "- requested hls_time: ${SEGMENT}s"
echo "- workdir: ${WORKDIR}"
echo

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=${FPS}" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000" \
  -t "$DURATION" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p \
  -g "$((FPS * SEGMENT))" -keyint_min "$((FPS * SEGMENT))" -sc_threshold 0 \
  -c:a aac -b:a 96k \
  "$SRC/h264-gop-${SEGMENT}s.mp4"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=${FPS}" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000" \
  -t "$DURATION" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p \
  -g "$((FPS * 5))" -keyint_min "$((FPS * 5))" -sc_threshold 0 \
  -c:a aac -b:a 96k \
  "$SRC/h264-gop-5s.mp4"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=${FPS}" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000" \
  -t "$DURATION" \
  -c:v libvpx -deadline realtime -cpu-used 8 -b:v 1600k \
  -c:a libopus -b:a 96k \
  "$SRC/vp8-opus.webm"

run_case() {
  local label="$1"
  local playlist="$2"
  shift 2
  local dir
  dir="$(dirname "$playlist")"
  mkdir -p "$dir"

  local time_file="$dir/time.txt"
  if [[ -x /usr/bin/time ]]; then
    /usr/bin/time -p "$@" >"$dir/stdout.txt" 2>"$time_file"
  else
    {
      TIMEFORMAT=$'real %3R\nuser %3U\nsys %3S'
      time "$@" >"$dir/stdout.txt" 2>"$dir/stderr.txt"
    } 2>"$time_file"
  fi

  local real user sys segments
  real="$(awk '/^real / { print $2 }' "$time_file")"
  user="$(awk '/^user / { print $2 }' "$time_file")"
  sys="$(awk '/^sys / { print $2 }' "$time_file")"
  segments="$(segment_summary "$playlist")"
  printf '| %s | %s | %s | %s | %s |\n' "$label" "$real" "$user" "$sys" "$segments"
}

segment_summary() {
  local playlist="$1"
  awk -F: '
    /^#EXTINF/ {
      gsub(/,/, "", $2)
      value = $2 + 0
      if (n == 0 || value < min) min = value
      if (n == 0 || value > max) max = value
      sum += value
      n += 1
    }
    END {
      if (n == 0) {
        printf "-"
      } else {
        printf "n=%d min=%.3fs max=%.3fs avg=%.3fs", n, min, max, sum / n
      }
    }
  ' "$playlist"
}

echo "| path | real | user | sys | segments |"
echo "|---|---:|---:|---:|---|"

run_case "H264 remux (-c copy)" "$OUT/remux/live.m3u8" \
  ffmpeg -hide_banner -loglevel error -y \
    -i "$SRC/h264-gop-${SEGMENT}s.mp4" \
    -c copy \
    -f hls -hls_time "$SEGMENT" -hls_list_size 0 \
    -hls_segment_filename "$OUT/remux/seg_%03d.ts" \
    "$OUT/remux/live.m3u8"

run_case "VP8 -> H264 transcode" "$OUT/transcode/live.m3u8" \
  ffmpeg -hide_banner -loglevel error -y \
    -i "$SRC/vp8-opus.webm" \
    -c:v libx264 -preset veryfast -tune zerolatency \
    -g "$((FPS * SEGMENT))" -keyint_min "$((FPS * SEGMENT))" -sc_threshold 0 \
    -c:a aac -b:a 96k \
    -f hls -hls_time "$SEGMENT" -hls_list_size 0 \
    -hls_segment_filename "$OUT/transcode/seg_%03d.ts" \
    "$OUT/transcode/live.m3u8"

run_case "audio-only Opus -> AAC" "$OUT/audio/live.m3u8" \
  ffmpeg -hide_banner -loglevel error -y \
    -i "$SRC/vp8-opus.webm" \
    -vn -c:a aac -b:a 96k \
    -f hls -hls_time "$SEGMENT" -hls_list_size 0 \
    -hls_segment_filename "$OUT/audio/seg_%03d.ts" \
    "$OUT/audio/live.m3u8"

echo
echo "## keyframe proof"
echo
echo "| path | segments |"
echo "|---|---|"

mkdir -p "$OUT/gop-copy" "$OUT/gop-transcode"

ffmpeg -hide_banner -loglevel error -y \
  -i "$SRC/h264-gop-5s.mp4" \
  -c copy \
  -f hls -hls_time "$SEGMENT" -hls_list_size 0 \
  -hls_segment_filename "$OUT/gop-copy/seg_%03d.ts" \
  "$OUT/gop-copy/live.m3u8"
printf '| H264 remux, source GOP 5s | %s |\n' "$(segment_summary "$OUT/gop-copy/live.m3u8")"

ffmpeg -hide_banner -loglevel error -y \
  -i "$SRC/h264-gop-5s.mp4" \
  -c:v libx264 -preset veryfast -tune zerolatency \
  -g "$((FPS * SEGMENT))" -keyint_min "$((FPS * SEGMENT))" -sc_threshold 0 \
  -c:a copy \
  -f hls -hls_time "$SEGMENT" -hls_list_size 0 \
  -hls_segment_filename "$OUT/gop-transcode/seg_%03d.ts" \
  "$OUT/gop-transcode/live.m3u8"
printf '| re-encode with GOP %ss | %s |\n' "$SEGMENT" "$(segment_summary "$OUT/gop-transcode/live.m3u8")"

echo
echo "Remove workdir when done:"
echo
echo "    rm -rf ${WORKDIR}"
