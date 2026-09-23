#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: score.sh --source FILE --encoded FILE --duration-s N --out FILE [--metadata FILE] [--ocr --truth-dir DIR]" >&2
  exit 2
}

SOURCE=
ENCODED=
TRUTH_DIR=
DURATION=
OUT=
METADATA=
DO_OCR=0
SKIP_SECONDS=${SKIP_SECONDS:-2}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --source) SOURCE=$2; shift 2 ;;
    --encoded) ENCODED=$2; shift 2 ;;
    --truth-dir) TRUTH_DIR=$2; shift 2 ;;
    --duration-s) DURATION=$2; shift 2 ;;
    --out) OUT=$2; shift 2 ;;
    --metadata) METADATA=$2; shift 2 ;;
    --ocr) DO_OCR=1; shift ;;
    *) usage ;;
  esac
done
[[ -n "$SOURCE" && -n "$ENCODED" && -n "$DURATION" && -n "$OUT" ]] || usage
TRUTH_FAILURE_REASON=
if [[ "$DO_OCR" == 1 && ( -z "$TRUTH_DIR" || ! -d "$TRUTH_DIR" ) ]]; then
  TRUTH_FAILURE_REASON='채점 실패(OCR 정답 디렉터리 없음)'
fi
METADATA_PATH=${METADATA:-${OUT}.recording.json}

WORK=$(mktemp -d "${TMPDIR:-/tmp}/edumeet-bitrate-score.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
VMAF_JSON="$WORK/vmaf.json"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# 먼저 컨테이너가 말하는 실제 프레임 수·평균/명목 fps·길이를 보관한다.
# avg_frame_rate가 명목 r_frame_rate와 다르면 VFR/드롭 프레임 신호로 결과에 남긴다.
ffprobe -v error -select_streams v:0 -count_frames \
  -show_entries stream=nb_read_frames,nb_frames,width,height,r_frame_rate,avg_frame_rate,duration,start_time \
  -of json "$SOURCE" > "$WORK/source-probe.json"
ffprobe -v error -select_streams v:0 -count_frames \
  -show_entries stream=nb_read_frames,nb_frames,width,height,r_frame_rate,avg_frame_rate,duration,start_time \
  -of json "$ENCODED" > "$WORK/encoded-probe.json"
ffprobe -v error -select_streams v:0 -show_frames \
  -show_entries frame=pts_time,best_effort_timestamp_time \
  -of json "$ENCODED" > "$WORK/encoded-frames.json"

# 녹화 프레임마다 원본 프레임 번호를 새긴 16비트 띠만 먼저 읽는다. 띠는
# VMAF 입력에는 남기고 OCR 입력에서만 가린다.
BAND_X=864
BAND_Y=560
BAND_WIDTH=$((16 * 24))
BAND_HEIGHT=24
SOURCE_FRAMES=$(node -e 'const x=JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log(x.streams?.[0]?.nb_read_frames ?? x.streams?.[0]?.nb_frames ?? 0)' "$WORK/source-probe.json")
ENCODED_FRAMES=$(node -e 'const x=JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log(x.streams?.[0]?.nb_read_frames ?? x.streams?.[0]?.nb_frames ?? 0)' "$WORK/encoded-probe.json")
ENCODED_WIDTH=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).streams?.[0]?.width ?? 0)' "$WORK/encoded-probe.json")
ENCODED_HEIGHT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).streams?.[0]?.height ?? 0)' "$WORK/encoded-probe.json")
if [[ "$ENCODED_WIDTH" -ne 1280 || "$ENCODED_HEIGHT" -ne 720 ]]; then
  node "$SCRIPT_DIR/failure-result.mjs" "$OUT" "채점 실패(해상도 ${ENCODED_WIDTH}x${ENCODED_HEIGHT})" \
    "$WORK/source-probe.json" "$WORK/encoded-probe.json" - "$METADATA_PATH"
  exit 1
fi
if [[ -n "$TRUTH_FAILURE_REASON" ]]; then
  node "$SCRIPT_DIR/failure-result.mjs" "$OUT" "$TRUTH_FAILURE_REASON" \
    "$WORK/source-probe.json" "$WORK/encoded-probe.json" - "$METADATA_PATH"
  exit 1
fi
ffmpeg -hide_banner -loglevel error -y -i "$SOURCE" \
  -vf "crop=${BAND_WIDTH}:${BAND_HEIGHT}:${BAND_X}:${BAND_Y},format=gray" \
  -vsync 0 -frames:v "$SOURCE_FRAMES" -f rawvideo "$WORK/source-band.gray"
ffmpeg -hide_banner -loglevel error -y -i "$ENCODED" \
  -vf "crop=${BAND_WIDTH}:${BAND_HEIGHT}:${BAND_X}:${BAND_Y},format=gray" \
  -vsync 0 -frames:v "$ENCODED_FRAMES" -f rawvideo "$WORK/encoded-band.gray"
if ! node "$SCRIPT_DIR/decode-band.mjs" "$WORK/source-band.gray" "$SOURCE_FRAMES" "$SOURCE_FRAMES" "$WORK/source-decode.json" - source; then
  node "$SCRIPT_DIR/failure-result.mjs" "$OUT" '채점 실패(원본 프레임 띠 self-check)' \
    "$WORK/source-probe.json" "$WORK/encoded-probe.json" "$WORK/source-decode.json" "$METADATA_PATH"
  exit 1
fi
node "$SCRIPT_DIR/decode-band.mjs" "$WORK/encoded-band.gray" "$ENCODED_FRAMES" "$SOURCE_FRAMES" \
  "$WORK/encoded-decode.json" "$WORK/encoded-frames.json"
PAIR_PATH="$WORK/pairs.json"
node --input-type=module - "$WORK/encoded-decode.json" "$SOURCE_FRAMES" "$PAIR_PATH" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'

const [decodePath, sourceFramesArg, outPath] = process.argv.slice(2)
const sourceFrames = Number(sourceFramesArg)
const skipFrames = 60
const decoded = JSON.parse(await readFile(decodePath, 'utf8'))
const validFrames = decoded.frames.filter((frame) => frame.valid)
const seenAll = new Set()
const seenScoring = new Set()
const pairs = []
let duplicateMappings = 0
let lastRead = null
for (const frame of validFrames) {
  if (seenAll.has(frame.sourceIndex)) {
    duplicateMappings += 1
    continue
  }
  seenAll.add(frame.sourceIndex)
  if (frame.sourceIndex < skipFrames || frame.sourceIndex >= sourceFrames) continue
  lastRead = frame.sourceIndex
  seenScoring.add(frame.sourceIndex)
  pairs.push({ encodedIndex: frame.encodedIndex, sourceIndex: frame.sourceIndex })
}
const scoringSourceFrames = lastRead == null ? 0 : lastRead - skipFrames + 1
const droppedFrames = Math.max(0, scoringSourceFrames - seenScoring.size)
const sourceSelect = `select='${pairs.map(({ sourceIndex }) => `eq(n\\,${sourceIndex})`).join('+')}'`
const encodedSelect = `select='${pairs.map(({ encodedIndex }) => `eq(n\\,${encodedIndex})`).join('+')}'`
const result = {
  sourceFrames,
  encodedFrames: decoded.frameCount,
  pairFrames: pairs.length,
  pairs,
  droppedFrames,
  droppedRatio: scoringSourceFrames ? droppedFrames / scoringSourceFrames : null,
  scoringSourceFrames,
  outOfRange: decoded.frames.filter((frame) => frame.reason === 'out of range').length,
  duplicateMappings,
  decodeFailures: decoded.decodeFailures,
  failureCounts: decoded.failureCounts,
  ptsResidualMedian: decoded.ptsResidualMedian,
  sourceDecodeFailures: null,
  lastRead,
  sourceSelect,
  encodedSelect,
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({
  sourceFrames: result.sourceFrames,
  encodedFrames: result.encodedFrames,
  pairFrames: result.pairFrames,
  droppedFrames: result.droppedFrames,
  droppedRatio: result.droppedRatio,
  decodeFailures: result.decodeFailures,
  duplicateMappings: result.duplicateMappings,
  outOfRange: result.outOfRange,
}))
NODE
node --input-type=module - "$PAIR_PATH" "$WORK/source-decode.json" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'
const [pairPath, sourcePath] = process.argv.slice(2)
const pair = JSON.parse(await readFile(pairPath, 'utf8'))
const source = JSON.parse(await readFile(sourcePath, 'utf8'))
pair.sourceDecodeFailures = source.decodeFailures
pair.sourceFailureCounts = source.failureCounts
await writeFile(pairPath, JSON.stringify(pair, null, 2) + '\n')
NODE
PAIR_COUNT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).pairFrames)' "$PAIR_PATH")
SOURCE_SELECT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).sourceSelect)' "$PAIR_PATH")
ENCODED_SELECT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).encodedSelect)' "$PAIR_PATH")
if [[ "$PAIR_COUNT" -eq 0 ]]; then
  # 빈 결과도 호출자에게 보이는 실패 행으로 남긴다. 그래야 21개 사다리 중
  # 한 항목이 조용히 사라져 채점 성공처럼 보이지 않는다.
  node --input-type=module - "$OUT" "$WORK/encoded-probe.json" "$PAIR_PATH" "$METADATA_PATH" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'

const [outPath, encodedProbePath, pairPath, metadataPath] = process.argv.slice(2)
const encodedProbe = JSON.parse(await readFile(encodedProbePath, 'utf8')).streams?.[0] ?? {}
const pairing = JSON.parse(await readFile(pairPath, 'utf8'))
const meta = JSON.parse(await readFile(metadataPath, 'utf8').catch(() => '{}'))
const duration = Number(encodedProbe.duration)
const actualKbps = Number.isFinite(meta.bytes) && duration > 0 ? meta.bytes * 8 / duration / 1000 : null
const result = {
  requestedKbps: meta.requestedKbps ?? null,
  actualKbps,
  wallClockKbps: Number.isFinite(meta.bytes) && Number.isFinite(meta.elapsedMs) && meta.elapsedMs > 0
    ? meta.bytes * 8 / (meta.elapsedMs / 1000) / 1000
    : null,
  actualMimeType: meta.actualMimeType ?? null,
  vmafMean: null,
  vmafP1: null,
  ocrAccuracy: null,
  ocrOriginalCeiling: null,
  ocrScenes: [],
  encoded: {
    frames: Number(encodedProbe.nb_read_frames ?? encodedProbe.nb_frames) || null,
    durationSeconds: Number.isFinite(duration) ? duration : null,
  },
  pairing: {
    frames: 0,
    droppedFrames: pairing.droppedFrames ?? null,
    droppedRatio: pairing.droppedRatio ?? null,
    scoringSourceFrames: pairing.scoringSourceFrames ?? null,
    outOfRange: pairing.outOfRange ?? null,
    duplicateMappings: pairing.duplicateMappings ?? null,
  },
  pairedPsnrMean: null,
  alignmentStatus: '채점 실패',
  failureReasons: ['짝 0개'],
  status: '채점 실패',
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, failureReasons: result.failureReasons }))
NODE
  echo 'no 1:1 frame pairs could be built' >&2
  exit 1
fi

# 프레임 번호로 짝지은 두 영상을 만든다. 두 select 결과를 그대로 1:1로
# retime해 VMAF/PSNR에 넣으므로, 드롭된 원본 프레임은 두 영상에서 함께 빠진다.
ffmpeg -hide_banner -loglevel error -y -i "$ENCODED" \
  -vf "${ENCODED_SELECT},setpts=N/30/TB,format=yuv420p" -frames:v "$PAIR_COUNT" \
  -an -c:v rawvideo -r 30 -f yuv4mpegpipe "$WORK/encoded-paired.y4m"
ffmpeg -hide_banner -loglevel error -y -i "$SOURCE" \
  -vf "${SOURCE_SELECT},setpts=N/30/TB,format=yuv420p" -frames:v "$PAIR_COUNT" \
  -an -c:v rawvideo -r 30 -f yuv4mpegpipe "$WORK/source-paired.y4m"

# 실제 VMAF/PSNR 입력으로 만들어진 두 영상의 띠를 다시 읽어, select 결과가
# 중복 지점에서 서로 밀리지 않았는지 채점 전에 확인한다.
PAIR_BAND_CHECK="$WORK/pair-band-check.json"
ffmpeg -hide_banner -loglevel error -y -i "$WORK/encoded-paired.y4m" \
  -vf "crop=${BAND_WIDTH}:${BAND_HEIGHT}:${BAND_X}:${BAND_Y},format=gray" \
  -frames:v "$PAIR_COUNT" -f rawvideo "$WORK/encoded-paired-band.gray"
ffmpeg -hide_banner -loglevel error -y -i "$WORK/source-paired.y4m" \
  -vf "crop=${BAND_WIDTH}:${BAND_HEIGHT}:${BAND_X}:${BAND_Y},format=gray" \
  -frames:v "$PAIR_COUNT" -f rawvideo "$WORK/source-paired-band.gray"
node "$SCRIPT_DIR/decode-band.mjs" "$WORK/encoded-paired-band.gray" "$PAIR_COUNT" "$SOURCE_FRAMES" \
  "$WORK/encoded-paired-decode.json" - >/dev/null
node "$SCRIPT_DIR/decode-band.mjs" "$WORK/source-paired-band.gray" "$PAIR_COUNT" "$SOURCE_FRAMES" \
  "$WORK/source-paired-decode.json" - >/dev/null
node --input-type=module - "$WORK/encoded-paired-decode.json" "$WORK/source-paired-decode.json" "$PAIR_BAND_CHECK" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'

const [encodedPath, sourcePath, outPath] = process.argv.slice(2)
const encoded = JSON.parse(await readFile(encodedPath, 'utf8'))
const source = JSON.parse(await readFile(sourcePath, 'utf8'))
const length = Math.max(encoded.frames.length, source.frames.length)
const mismatches = []
for (let index = 0; index < length; index += 1) {
  const left = encoded.frames[index]
  const right = source.frames[index]
  if (!left?.valid || !right?.valid || left.sourceIndex !== right.sourceIndex) {
    mismatches.push({
      pairIndex: index,
      encoded: left?.sourceIndex ?? null,
      source: right?.sourceIndex ?? null,
    })
  }
}
const result = {
  ok: encoded.decodeFailures === 0 && source.decodeFailures === 0 && mismatches.length === 0,
  frames: length,
  mismatches: mismatches.slice(0, 20),
  mismatchCount: mismatches.length,
  encodedDecodeFailures: encoded.decodeFailures,
  sourceDecodeFailures: source.decodeFailures,
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify(result))
NODE

PSNR_LOG="$WORK/psnr.log"
ffmpeg -hide_banner -loglevel error -y -i "$WORK/encoded-paired.y4m" -i "$WORK/source-paired.y4m" \
  -lavfi "[0:v][1:v]psnr=stats_file=${PSNR_LOG}" -f null -
ffmpeg -hide_banner -loglevel error -y -i "$WORK/encoded-paired.y4m" -i "$WORK/source-paired.y4m" \
  -lavfi "[0:v][1:v]libvmaf=log_fmt=json:log_path=${VMAF_JSON}" \
  -f null -

# VMAF 1%가 거의 0이면 실제로 어떤 번호가 어긋났는지 확인할 수 있도록
# 원본/녹화 프레임을 영구 경로로 뽑는다. 정상 행에는 디렉터리를 만들지 않는다.
LOW_VMAF_JSON="$WORK/low-vmaf.json"
node --input-type=module - "$VMAF_JSON" "$PAIR_PATH" "$LOW_VMAF_JSON" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'
const [vmafPath, pairPath, outPath] = process.argv.slice(2)
const vmaf = JSON.parse(await readFile(vmafPath, 'utf8'))
const pairing = JSON.parse(await readFile(pairPath, 'utf8'))
const low = (vmaf.frames ?? []).map((frame, pairIndex) => ({
  pairIndex,
  vmaf: Number(frame.metrics?.vmaf),
  ...pairing.pairs[pairIndex],
})).filter((frame) => Number.isFinite(frame.vmaf) && frame.vmaf < 10).slice(0, 5)
await writeFile(outPath, JSON.stringify(low, null, 2) + '\n')
NODE
LOW_VMAF_COUNT=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).length)' "$LOW_VMAF_JSON")
LOW_VMAF_DIR="${OUT%.json}-vmaf-low"
if [[ "$LOW_VMAF_COUNT" -gt 0 ]]; then
  mkdir -p "$LOW_VMAF_DIR"
  while IFS=$'\t' read -r pair_index encoded_index source_index; do
    [[ -n "$pair_index" ]] || continue
    ffmpeg -nostdin -hide_banner -loglevel error -y -i "$ENCODED" \
      -vf "select='eq(n\\,${encoded_index})'" -vsync 0 -frames:v 1 \
      "$LOW_VMAF_DIR/encoded-pair-${pair_index}-frame-${encoded_index}.png"
    ffmpeg -nostdin -hide_banner -loglevel error -y -i "$SOURCE" \
      -vf "select='eq(n\\,${source_index})'" -vsync 0 -frames:v 1 \
      "$LOW_VMAF_DIR/source-pair-${pair_index}-frame-${source_index}.png"
  done < <(node --input-type=module - "$LOW_VMAF_JSON" <<'NODE'
import { readFile } from 'node:fs/promises'
const rows = JSON.parse(await readFile(process.argv[2], 'utf8'))
for (const row of rows) console.log(`${row.pairIndex}\t${row.encodedIndex}\t${row.sourceIndex}`)
NODE
  )
fi

SCENE_SECONDS=${SCENE_SECONDS:-10}
OCR_LANG=${OCR_LANG:-kor+eng}
if [[ "$DO_OCR" == 1 ]]; then
  for scene in "$TRUTH_DIR"/scene-*.txt; do
    [[ -f "$scene" ]] || continue
    index=$(basename "$scene" | sed -E 's/scene-([0-9]+)\.txt/\1/')
    timestamp=$((10#$index * SCENE_SECONDS + SCENE_SECONDS / 2))
    ffmpeg -hide_banner -loglevel error -y -ss "$timestamp" -i "$SOURCE" \
      -vf "drawbox=x=${BAND_X}:y=${BAND_Y}:w=${BAND_WIDTH}:h=${BAND_HEIGHT}:color=white:t=fill" \
      -frames:v 1 "$WORK/source-$index.png"
    ffmpeg -hide_banner -loglevel error -y -ss "$timestamp" -i "$ENCODED" \
      -vf "drawbox=x=${BAND_X}:y=${BAND_Y}:w=${BAND_WIDTH}:h=${BAND_HEIGHT}:color=white:t=fill" \
      -frames:v 1 "$WORK/encoded-$index.png"
    tesseract "$WORK/source-$index.png" "$WORK/source-$index" -l "$OCR_LANG" --psm 6 >/dev/null 2>&1
    tesseract "$WORK/encoded-$index.png" "$WORK/encoded-$index" -l "$OCR_LANG" --psm 6 >/dev/null 2>&1
  done
fi

node --input-type=module - "$VMAF_JSON" "$TRUTH_DIR" "$WORK" "$OUT" "$WORK/source-probe.json" "$WORK/encoded-probe.json" "$PAIR_PATH" "$PSNR_LOG" "$DO_OCR" "$LOW_VMAF_JSON" "$LOW_VMAF_DIR" "$METADATA_PATH" "$PAIR_BAND_CHECK" <<'NODE'
import { readdir, readFile, writeFile } from 'node:fs/promises'
import { basename, resolve } from 'node:path'

const [vmafPath, truthDir, workDir, outPath, sourceProbePath, encodedProbePath, pairPath, psnrPath, doOcrArg, lowVmafPath, lowVmafDir, metadataPath, pairBandCheckPath] = process.argv.slice(2)
const vmaf = JSON.parse(await readFile(vmafPath, 'utf8'))
const sourceProbe = JSON.parse(await readFile(sourceProbePath, 'utf8')).streams?.[0] ?? {}
const encodedProbe = JSON.parse(await readFile(encodedProbePath, 'utf8')).streams?.[0] ?? {}
const pairing = JSON.parse(await readFile(pairPath, 'utf8'))
const lowVmaf = JSON.parse(await readFile(lowVmafPath, 'utf8'))
const pairBandCheck = JSON.parse(await readFile(pairBandCheckPath, 'utf8'))
const psnrLines = (await readFile(psnrPath, 'utf8')).split('\n')
const psnrValues = psnrLines.map((line) => Number(line.match(/psnr_avg:([0-9.]+)/u)?.[1])).filter(Number.isFinite)
const psnrMean = psnrValues.length ? psnrValues.reduce((sum, value) => sum + value, 0) / psnrValues.length : null
const values = (vmaf.frames ?? []).map((frame) => Number(frame.metrics?.vmaf)).filter(Number.isFinite)
const sorted = [...values].sort((a, b) => a - b)
const p1Index = Math.max(0, Math.ceil(sorted.length * 0.01) - 1)
const vmafMean = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
const vmafP1 = values.length ? sorted[p1Index] : null
const vmafLowDiagnostics = lowVmaf.map((row) => ({
  pairIndex: row.pairIndex,
  vmaf: row.vmaf,
  encodedIndex: row.encodedIndex,
  sourceIndex: row.sourceIndex,
  encodedPng: resolve(lowVmafDir, `encoded-pair-${row.pairIndex}-frame-${row.encodedIndex}.png`),
  sourcePng: resolve(lowVmafDir, `source-pair-${row.pairIndex}-frame-${row.sourceIndex}.png`),
}))

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
    width: number(probe.width),
    height: number(probe.height),
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
const meta = JSON.parse(await readFile(metadataPath, 'utf8').catch(() => '{}'))
const sourceInfo = probeInfo(sourceProbe)
const encodedInfo = probeInfo(encodedProbe)
const actualKbps = meta.bytes != null && encodedInfo.durationSeconds > 0
  ? meta.bytes * 8 / encodedInfo.durationSeconds / 1000
  : null
const failureReasons = []
if (!pairing.pairFrames) failureReasons.push('짝 0개')
if (!values.length) failureReasons.push('VMAF 프레임 0개')
if (doOcrArg === '1' && !sceneFiles.length) failureReasons.push('OCR 정답 파일 없음')
if (!pairBandCheck.ok) failureReasons.push('정렬 실패(VMAF/PSNR 입력 띠 불일치)')
const result = {
  requestedKbps: meta.requestedKbps ?? null,
  actualKbps,
  wallClockKbps: meta.bytes != null && meta.elapsedMs > 0 ? meta.bytes * 8 / (meta.elapsedMs / 1000) / 1000 : null,
  actualMimeType: meta.actualMimeType ?? null,
  vmafMean,
  vmafP1,
  ocrAccuracy: average('encoded'),
  ocrOriginalCeiling: average('source'),
  ocrScenes: sceneScores,
  source: sourceInfo,
  encoded: encodedInfo,
  alignment: {
    method: 'frame-number-band',
    offsetFrames: null,
    offsetSeconds: null,
    psnrDb: null,
    boundary: false,
  },
  frameBand: {
    includedInVmaf: true,
    width: 384,
    height: 24,
    dataBits: 15,
    parityBit: 15,
    sourceDecodeFailures: pairing.sourceDecodeFailures,
    decodeFailures: pairing.decodeFailures,
    failureCounts: pairing.failureCounts,
    sourceFailureCounts: pairing.sourceFailureCounts,
    ptsResidualMedian: pairing.ptsResidualMedian,
  },
  pairBandCheck,
  pairing: {
    frames: pairing.pairFrames,
    droppedFrames: pairing.droppedFrames,
    droppedRatio: pairing.droppedRatio,
    scoringSourceFrames: pairing.scoringSourceFrames,
    outOfRange: pairing.outOfRange,
    duplicateMappings: pairing.duplicateMappings,
  },
  pairedPsnrMean: psnrMean,
  vmafLowDiagnostics,
  alignmentStatus: !pairBandCheck.ok ? '정렬 실패' : failureReasons.length ? '채점 실패' : '정렬 확인',
  failureReasons,
  status: failureReasons.length ? '채점 실패' : '완료',
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify(result))
if (failureReasons.length) process.exitCode = 1
NODE
