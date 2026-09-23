#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SCRIPT_DIR="$ROOT/perf/bitrate"
RUN_ID=${1:-}
RECORD_SECONDS=${2:-${RECORD_SECONDS:-40}}
FONT_PATH=${3:-${FONT_PATH:-}}
RESCORE=${RESCORE:-0}
if [[ -z "$RUN_ID" ]]; then
  echo "usage: run.sh RUN_ID [RECORD_SECONDS] [FONT_PATH]" >&2
  echo "  RECORD_SECONDS is the duration of each browser recording (use 40 to cover all four 10-second slides)." >&2
  exit 2
fi
if [[ "$RESCORE" != 0 && "$RESCORE" != 1 ]]; then
  echo 'RESCORE must be 0 or 1' >&2
  exit 2
fi

OUT="$SCRIPT_DIR/out/$RUN_ID"
SOURCES="$OUT/sources"
RECORDINGS="$OUT/recordings"
if [[ "$RESCORE" == 1 ]]; then
  RESCORE_STAMP=${RESCORE_TIMESTAMP:-$(date +%Y%m%d%H%M%S)}
  candidate_stamp=$RESCORE_STAMP
  suffix=1
  while [[ -e "$OUT/results-$candidate_stamp" || -e "$OUT/table-$candidate_stamp.md" ]]; do
    candidate_stamp="${RESCORE_STAMP}-${suffix}"
    suffix=$((suffix + 1))
  done
  RESCORE_STAMP=$candidate_stamp
  RESULTS="$OUT/results-$RESCORE_STAMP"
  TABLE_PATH="$OUT/table-$RESCORE_STAMP.md"
  mkdir -p "$OUT" "$RESULTS"
else
  RESULTS="$OUT/results"
  TABLE_PATH="$OUT/table.md"
  mkdir -p "$OUT" "$RECORDINGS" "$RESULTS"
fi

NEEDS_SCORING=1
if [[ "$RESCORE" == 1 ]]; then
  NEEDS_SCORING=0
  if [[ -d "$SOURCES" && -d "$RECORDINGS" && -f "$SOURCES/slides.y4m" ]] \
    && compgen -G "$RECORDINGS/*/*.webm" >/dev/null; then
    NEEDS_SCORING=1
  fi
fi
if [[ "$NEEDS_SCORING" == 1 ]]; then
  command -v ffmpeg >/dev/null || { echo 'blocked: ffmpeg is required' >&2; exit 2; }
  FFMPEG_FILTERS=$(ffmpeg -filters 2>/dev/null)
  [[ "$FFMPEG_FILTERS" == *libvmaf* ]] || { echo 'blocked: ffmpeg libvmaf filter is required' >&2; exit 2; }
  command -v tesseract >/dev/null || { echo 'blocked: tesseract is required' >&2; exit 2; }
  requested_ocr_lang=${OCR_LANG:-kor+eng}
  if [[ "$requested_ocr_lang" == *kor* ]]; then
    tesseract --list-langs 2>&1 | grep -qx 'kor' || {
      echo 'blocked: tesseract kor language data is required (set OCR_LANG=eng only for an English-only rescore)' >&2
      exit 2
    }
  fi
fi

if [[ "$RESCORE" == 1 ]]; then
  echo "[1/3] RESCORE=$RESCORE_STAMP: using existing sources and recordings"
else
  echo '[1/3] generating sources'
  "$SCRIPT_DIR/make-sources.sh" "$SOURCES" "$FONT_PATH"
fi

if [[ "$RESCORE" == 1 ]]; then
  echo '[2/3] RESCORE: skipping browser recording'
else
  echo '[2/3] recording Chromium and Chrome ladders'
fi
declare -a RESULT_FILES=()
FAILURE_COUNT=0

write_failure_result() {
  local result=$1
  local reason=$2
  local metadata=$3
  node "$SCRIPT_DIR/failure-result.mjs" "$result" "$reason" /dev/null /dev/null - "$metadata"
}

for browser in chromium chrome; do
  browser_recordings="$RECORDINGS/$browser"
  browser_results="$RESULTS/$browser"
  if [[ "$RESCORE" == 1 ]]; then
    mkdir -p "$browser_results"
  else
    mkdir -p "$browser_recordings" "$browser_results"
  fi
  for content in slides handwriting camera; do
    for bitrate in 300 500 700 1000 1500 2500 4000; do
      recording="$browser_recordings/${content}-${bitrate}.webm"
      source="$SOURCES/${content}.y4m"
      echo "  ${browser} ${content} ${bitrate} kbps"
      result="$browser_results/${content}-${bitrate}.json"
      if [[ ! -f "$source" ]]; then
        write_failure_result "$result" '채점 실패(source 없음)' "$recording.json"
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
        RESULT_FILES+=("$result")
        continue
      fi
      if [[ "$RESCORE" == 1 && ! -f "$recording" ]]; then
        write_failure_result "$result" '채점 실패(recording 없음)' "$recording.json"
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
        RESULT_FILES+=("$result")
        continue
      elif [[ "$RESCORE" != 1 ]]; then
        record_args=(
          --source "$source" \
          --bitrate-kbps "$bitrate" \
          --duration-s "$RECORD_SECONDS" \
          --out "$recording"
        )
        if [[ "$browser" == chrome ]]; then
          record_args+=(--channel chrome)
        fi
        node "$SCRIPT_DIR/record.mjs" "${record_args[@]}"
        if [[ ! -f "$recording" ]]; then
          write_failure_result "$result" '채점 실패(recording 없음)' "$recording.json"
          FAILURE_COUNT=$((FAILURE_COUNT + 1))
          RESULT_FILES+=("$result")
          continue
        fi
        # score.sh expects metadata adjacent to its JSON result. Keep recorder metadata
        # separate from the score so actual bitrate is never replaced by the requested one.
        cp "$recording.json" "$result.recording.json"
      fi
      score_args=(
        --source "$source"
        --encoded "$recording"
        --duration-s "$RECORD_SECONDS"
        --out "$result"
      )
      if [[ "$RESCORE" == 1 ]]; then
        score_args+=(--metadata "$recording.json")
      fi
      if [[ "$content" == slides ]]; then
        score_args+=(--ocr --truth-dir "$SOURCES/truth")
      fi
      if "$SCRIPT_DIR/score.sh" "${score_args[@]}"; then
        score_status=0
      else
        score_status=$?
      fi
      if (( score_status != 0 )); then
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
      fi
      # score.sh can terminate before it writes its result (for example, ffprobe
      # rejects a zero-byte recording). Keep one visible failure row per input.
      if [[ ! -s "$result" ]]; then
        write_failure_result "$result" "채점 실패(score.sh 종료${score_status:+ status=$score_status})" "$recording.json"
        if (( score_status == 0 )); then
          FAILURE_COUNT=$((FAILURE_COUNT + 1))
        fi
      fi
      RESULT_FILES+=("$result")
    done
  done
done

echo '[3/3] writing table'
node --input-type=module - "$TABLE_PATH" "${RESULT_FILES[@]}" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'
import { basename } from 'node:path'
const [outPath, ...files] = process.argv.slice(2)
const rows = []
const missingResult = (file) => ({
  actualKbps: null,
  wallClockKbps: null,
  actualMimeType: null,
  vmafMean: null,
  vmafP1: null,
  ocrAccuracy: null,
  ocrOriginalCeiling: null,
  source: {},
  encoded: {},
  frameBand: {},
  pairing: {},
  pairedPsnrMean: null,
  vmafLowDiagnostics: [],
  alignmentStatus: '채점 실패',
  failureReasons: ['채점 실패(결과 JSON 없음)'],
  status: '채점 실패',
})
for (const file of files) {
  let result
  try {
    result = JSON.parse(await readFile(file, 'utf8'))
  } catch {
    // Keep the table complete even if a caller was killed before writing a row.
    result = missingResult(file)
  }
  const match = file.match(/\/results(?:-[^/]+)?\/(chromium|chrome)\/([^-]+)-(\d+)\.json$/u)
  rows.push({ browser: match?.[1] ?? 'unknown', content: match?.[2] ?? basename(file), requested: Number(match?.[3]), ...result })
}
const number = (value) => value == null ? 'n/a' : Number(value).toFixed(2)
const percent = (value) => value == null ? '-' : `${(Number(value) * 100).toFixed(2)}%`
const table = (browser) => {
  const lines = [
    `## ${browser}`,
    '',
    '| content | requested kbps | actual kbps | actual mimeType | frames | avg fps | VFR | decodeFailures | dropped frames | dropped ratio | outOfRange | duplicateMappings | pair PSNR dB | alignment | status | VMAF mean | VMAF 1% | low-VMAF frames | OCR | OCR ceiling |',
    '| --- | ---: | ---: | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |',
  ]
  for (const row of rows.filter((candidate) => candidate.browser === browser)) {
    lines.push(`| ${row.content} | ${row.requested} | ${number(row.actualKbps)} | ${row.actualMimeType ?? 'n/a'} | ${row.encoded?.frames ?? 'n/a'} | ${number(row.encoded?.averageFps)} | ${row.encoded?.vfr ? 'yes' : 'no'} | ${row.frameBand?.decodeFailures ?? 'n/a'} | ${row.pairing?.droppedFrames ?? 'n/a'} | ${percent(row.pairing?.droppedRatio)} | ${row.pairing?.outOfRange ?? 'n/a'} | ${row.pairing?.duplicateMappings ?? 'n/a'} | ${number(row.pairedPsnrMean)} | ${row.alignmentStatus ?? 'n/a'} | ${row.status ?? 'n/a'} | ${number(row.vmafMean)} | ${number(row.vmafP1)} | ${row.vmafLowDiagnostics?.length ?? 0} | ${number(row.ocrAccuracy)} | ${number(row.ocrOriginalCeiling)} |`)
  }
  return lines
}
function rank(values) {
  return values.map((value) => 1 + values.filter((other) => other < value).length + (values.filter((other) => other === value).length - 1) / 2)
}
function spearman(left, right) {
  if (left.length < 2 || right.length !== left.length) return null
  const a = rank(left)
  const b = rank(right)
  const mean = (values) => values.reduce((sum, value) => sum + value, 0) / values.length
  const ma = mean(a)
  const mb = mean(b)
  const numerator = a.reduce((sum, value, index) => sum + (value - ma) * (b[index] - mb), 0)
  const denominator = Math.sqrt(a.reduce((sum, value) => sum + (value - ma) ** 2, 0) * b.reduce((sum, value) => sum + (value - mb) ** 2, 0))
  return denominator ? numerator / denominator : null
}
function directionLines(browser) {
  return ['slides', 'handwriting', 'camera'].map((content) => {
    const selected = rows.filter((row) => row.browser === browser && row.content === content).sort((a, b) => a.requested - b.requested)
    const vmaf = selected.filter((row) => Number.isFinite(row.vmafMean))
    const ocr = selected.filter((row) => Number.isFinite(row.ocrAccuracy) && Number.isFinite(row.vmafMean))
    const vmafRho = spearman(vmaf.map((row) => row.requested), vmaf.map((row) => row.vmafMean))
    if (ocr.length < 2) return `- ${browser}/${content}: VMAF Spearman ${vmafRho == null ? 'n/a' : vmafRho.toFixed(2)}; OCR는 slides만 측정`
    const rho = spearman(ocr.map((row) => row.vmafMean), ocr.map((row) => row.ocrAccuracy))
    return `- ${browser}/${content}: VMAF ladder Spearman ${vmafRho == null ? 'n/a' : vmafRho.toFixed(2)}, VMAF↔OCR Spearman ${rho == null ? 'n/a' : rho.toFixed(2)} (${rho != null && rho >= 0.5 ? '같은 방향' : '불일치/약함'})`
  })
}
const lines = [
  '# Bitrate harness',
  '',
  'The two tables use the same source and ladder. Actual bitrate and mimeType come from each browser recording; frames/fps/VFR and PSNR alignment are from ffprobe and the scoring pass.',
  '',
  ...table('chromium'),
  '',
  ...table('chrome'),
  '',
  '## VMAF/OCR direction',
  '',
  ...directionLines('chromium'),
  ...directionLines('chrome'),
  '',
  '- VMAF is trained for natural-video fidelity; OCR is the slide readability check.',
  '',
]
await writeFile(outPath, lines.join('\n'))
NODE
echo "failure rows: $FAILURE_COUNT"
if (( FAILURE_COUNT > 0 )); then
  exit 1
fi
printf 'completed: %s\n' "$OUT"
