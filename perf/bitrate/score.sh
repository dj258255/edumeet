#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: score.sh --source FILE --encoded FILE --duration-s N --out FILE [--ocr --truth-dir DIR]" >&2
  exit 2
}

SOURCE=
ENCODED=
TRUTH_DIR=
DURATION=
OUT=
DO_OCR=0
SKIP_SECONDS=${SKIP_SECONDS:-2}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --source) SOURCE=$2; shift 2 ;;
    --encoded) ENCODED=$2; shift 2 ;;
    --truth-dir) TRUTH_DIR=$2; shift 2 ;;
    --duration-s) DURATION=$2; shift 2 ;;
    --out) OUT=$2; shift 2 ;;
    --ocr) DO_OCR=1; shift ;;
    *) usage ;;
  esac
done
[[ -n "$SOURCE" && -n "$ENCODED" && -n "$DURATION" && -n "$OUT" ]] || usage
if [[ "$DO_OCR" == 1 && -z "$TRUTH_DIR" ]]; then usage; fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/edumeet-bitrate-score.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
VMAF_JSON="$WORK/vmaf.json"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# 먼저 컨테이너가 말하는 실제 프레임 수·평균/명목 fps·길이를 보관한다.
# avg_frame_rate가 명목 r_frame_rate와 다르면 VFR/드롭 프레임 신호로 결과에 남긴다.
ffprobe -v error -select_streams v:0 -count_frames \
  -show_entries stream=nb_read_frames,nb_frames,r_frame_rate,avg_frame_rate,duration,start_time \
  -of json "$SOURCE" > "$WORK/source-probe.json"
ffprobe -v error -select_streams v:0 -count_frames \
  -show_entries stream=nb_read_frames,nb_frames,r_frame_rate,avg_frame_rate,duration,start_time \
  -of json "$ENCODED" > "$WORK/encoded-probe.json"
ffprobe -v error -select_streams v:0 -show_frames \
  -show_entries frame=pts_time,best_effort_timestamp_time \
  -of json "$ENCODED" > "$WORK/encoded-frames.json"

# PTS가 가리키는 원본 프레임을 찾을 수 있도록 두 영상을 160x90 회색
# 프리뷰로 만든다. pair-frames.mjs가 round(pts*30)+offset 후보를 비교한다.
ALIGN_WIDTH=160
ALIGN_HEIGHT=90
SOURCE_FRAMES=$(node -e 'const x=JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log(x.streams?.[0]?.nb_read_frames ?? x.streams?.[0]?.nb_frames ?? 0)' "$WORK/source-probe.json")
ENCODED_FRAMES=$(node -e 'const x=JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log(x.streams?.[0]?.nb_read_frames ?? x.streams?.[0]?.nb_frames ?? 0)' "$WORK/encoded-probe.json")
ffmpeg -hide_banner -loglevel error -y -i "$SOURCE" \
  -vf "scale=${ALIGN_WIDTH}:${ALIGN_HEIGHT},format=gray" \
  -frames:v "$SOURCE_FRAMES" -f rawvideo "$WORK/source-all.gray"
ffmpeg -hide_banner -loglevel error -y -i "$ENCODED" \
  -vf "scale=${ALIGN_WIDTH}:${ALIGN_HEIGHT},format=gray" \
  -frames:v "$ENCODED_FRAMES" -f rawvideo "$WORK/encoded-all.gray"
PAIR_PATH="$WORK/pairs.json"
node "$SCRIPT_DIR/pair-frames.mjs" "$WORK/encoded-frames.json" "$SOURCE_FRAMES" \
  "$WORK/source-all.gray" "$WORK/encoded-all.gray" "$ALIGN_WIDTH" "$ALIGN_HEIGHT" "$PAIR_PATH"
PAIR_COUNT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).pairFrames)' "$PAIR_PATH")
SOURCE_SELECT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).sourceSelect)' "$PAIR_PATH")
PAIR_OFFSET_FRAMES=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).offsetFrames)' "$PAIR_PATH")
PAIR_OFFSET=$(awk -v offset="$PAIR_OFFSET_FRAMES" 'BEGIN { printf "%.6f", offset / 30 }')
if [[ "$PAIR_COUNT" -eq 0 ]]; then
  echo 'no 1:1 frame pairs could be built' >&2
  exit 2
fi

# PTS로 짝지은 두 영상을 만든다. 원본 select에는 녹화 프레임마다 대응하는
# 원본 프레임 번호가 들어가므로, 중간에 드롭된 원본 프레임은 두 영상에서 함께 빠진다.
ffmpeg -hide_banner -loglevel error -y -i "$ENCODED" \
  -vf "setpts=N/30/TB,format=yuv420p" -frames:v "$PAIR_COUNT" \
  -an -c:v rawvideo -r 30 -f yuv4mpegpipe "$WORK/encoded-paired.y4m"
ffmpeg -hide_banner -loglevel error -y -i "$SOURCE" \
  -vf "${SOURCE_SELECT},setpts=N/30/TB,format=yuv420p" -frames:v "$PAIR_COUNT" \
  -an -c:v rawvideo -r 30 -f yuv4mpegpipe "$WORK/source-paired.y4m"

PSNR_LOG="$WORK/psnr.log"
ffmpeg -hide_banner -loglevel error -y -i "$WORK/encoded-paired.y4m" -i "$WORK/source-paired.y4m" \
  -lavfi "[0:v][1:v]psnr=stats_file=${PSNR_LOG}" -f null -
ffmpeg -hide_banner -loglevel error -y -i "$WORK/encoded-paired.y4m" -i "$WORK/source-paired.y4m" \
  -filter_complex "[0:v]trim=start=${SKIP_SECONDS}:duration=${DURATION},setpts=PTS-STARTPTS[dist];[1:v]trim=start=${SKIP_SECONDS}:duration=${DURATION},setpts=PTS-STARTPTS[ref];[dist][ref]libvmaf=log_fmt=json:log_path=${VMAF_JSON}" \
  -f null -

SCENE_SECONDS=${SCENE_SECONDS:-10}
OCR_LANG=${OCR_LANG:-kor+eng}
if [[ "$DO_OCR" == 1 ]]; then
  for scene in "$TRUTH_DIR"/scene-*.txt; do
    [[ -f "$scene" ]] || continue
    index=$(basename "$scene" | sed -E 's/scene-([0-9]+)\.txt/\1/')
    timestamp=$((10#$index * SCENE_SECONDS + SCENE_SECONDS / 2))
    encoded_timestamp=$(awk -v timestamp="$timestamp" -v offset="$PAIR_OFFSET" 'BEGIN { value = timestamp - offset; if (value < 0) value = 0; printf "%.6f", value }')
    ffmpeg -hide_banner -loglevel error -y -ss "$timestamp" -i "$SOURCE" -frames:v 1 "$WORK/source-$index.png"
    ffmpeg -hide_banner -loglevel error -y -ss "$encoded_timestamp" -i "$ENCODED" -frames:v 1 "$WORK/encoded-$index.png"
    tesseract "$WORK/source-$index.png" "$WORK/source-$index" -l "$OCR_LANG" --psm 6 >/dev/null 2>&1
    tesseract "$WORK/encoded-$index.png" "$WORK/encoded-$index" -l "$OCR_LANG" --psm 6 >/dev/null 2>&1
  done
fi

node --input-type=module - "$VMAF_JSON" "$TRUTH_DIR" "$WORK" "$OUT" "$WORK/source-probe.json" "$WORK/encoded-probe.json" "$PAIR_PATH" "$PSNR_LOG" <<'NODE'
import { readdir, readFile, writeFile } from 'node:fs/promises'
import { basename } from 'node:path'

const [vmafPath, truthDir, workDir, outPath, sourceProbePath, encodedProbePath, pairPath, psnrPath] = process.argv.slice(2)
const vmaf = JSON.parse(await readFile(vmafPath, 'utf8'))
const sourceProbe = JSON.parse(await readFile(sourceProbePath, 'utf8')).streams?.[0] ?? {}
const encodedProbe = JSON.parse(await readFile(encodedProbePath, 'utf8')).streams?.[0] ?? {}
const pairing = JSON.parse(await readFile(pairPath, 'utf8'))
const psnrLines = (await readFile(psnrPath, 'utf8')).split('\n')
const psnrValues = psnrLines.map((line) => Number(line.match(/psnr_avg:([0-9.]+)/u)?.[1])).filter(Number.isFinite)
const psnrMean = psnrValues.length ? psnrValues.reduce((sum, value) => sum + value, 0) / psnrValues.length : null
const values = (vmaf.frames ?? []).map((frame) => Number(frame.metrics?.vmaf)).filter(Number.isFinite)
const sorted = [...values].sort((a, b) => a - b)
const p1Index = Math.max(0, Math.ceil(sorted.length * 0.01) - 1)
const vmafMean = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
const vmafP1 = values.length ? sorted[p1Index] : null

function normalize(value) {
  return value.normalize('NFKC').replace(/\s+/gu, '')
}
function distance(left, right) {
  const a = [...normalize(left)]
  const b = [...normalize(right)]
  let previous = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i += 1) {
    const current = [i]
    for (let j = 1; j <= b.length; j += 1) {
      current[j] = Math.min(
        current[j - 1] + 1,
        previous[j] + 1,
        previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1),
      )
    }
    previous = current
  }
  return previous[b.length]
}

function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}
function rate(value) {
  if (typeof value !== 'string' || !value.includes('/')) return number(value)
  const [numerator, denominator] = value.split('/').map(Number)
  return denominator ? numerator / denominator : null
}
function probeInfo(probe) {
  const nominalFps = rate(probe.r_frame_rate)
  const averageFps = rate(probe.avg_frame_rate)
  return {
    frames: number(probe.nb_read_frames ?? probe.nb_frames),
    durationSeconds: number(probe.duration),
    nominalFps,
    averageFps,
    vfr: nominalFps != null && averageFps != null && Math.abs(nominalFps - averageFps) > 0.01,
    startTime: number(probe.start_time),
  }
}

const sceneFiles = truthDir ? (await readdir(truthDir)).filter((name) => /^scene-\d+\.txt$/.test(name)).sort() : []
const sceneScores = []
for (const name of sceneFiles) {
  const index = name.match(/\d+/u)[0]
  const truth = await readFile(`${truthDir}/${name}`, 'utf8')
  const sourceText = await readFile(`${workDir}/source-${index}.txt`, 'utf8')
  const encodedText = await readFile(`${workDir}/encoded-${index}.txt`, 'utf8')
  const length = Math.max([...normalize(truth)].length, 1)
  sceneScores.push({
    scene: basename(name, '.txt'),
    source: Math.max(0, 1 - distance(sourceText, truth) / length),
    encoded: Math.max(0, 1 - distance(encodedText, truth) / length),
  })
}
const average = (key) => sceneScores.length ? sceneScores.reduce((sum, row) => sum + row[key], 0) / sceneScores.length : null
const meta = JSON.parse(await readFile(`${outPath}.recording.json`, 'utf8').catch(() => '{}'))
const alignmentFailed = psnrMean != null && psnrMean > 40 && vmafMean != null && vmafMean < 70
const result = {
  requestedKbps: meta.requestedKbps ?? null,
  actualKbps: meta.actualKbps ?? null,
  actualMimeType: meta.actualMimeType ?? null,
  vmafMean,
  vmafP1,
  ocrAccuracy: average('encoded'),
  ocrOriginalCeiling: average('source'),
  ocrScenes: sceneScores,
  source: probeInfo(sourceProbe),
  encoded: probeInfo(encodedProbe),
  alignment: {
    offsetFrames: pairing.offsetFrames,
    offsetSeconds: pairing.offsetFrames / 30,
    psnrDb: pairing.previewPsnrDb,
  },
  pairing: {
    frames: pairing.pairFrames,
    droppedFrames: pairing.droppedFrames,
    droppedRatio: pairing.droppedRatio,
    outOfRange: pairing.outOfRange,
    duplicateMappings: pairing.duplicateMappings,
  },
  pairedPsnrMean: psnrMean,
  alignmentStatus: alignmentFailed ? '정렬 실패' : '정렬 확인',
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify(result))
NODE
