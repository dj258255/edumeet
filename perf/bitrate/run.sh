#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SCRIPT_DIR="$ROOT/perf/bitrate"
RUN_ID=${1:-}
RECORD_SECONDS=${2:-${RECORD_SECONDS:-40}}
FONT_PATH=${3:-${FONT_PATH:-}}
if [[ -z "$RUN_ID" ]]; then
  echo "usage: run.sh RUN_ID [RECORD_SECONDS] [FONT_PATH]" >&2
  echo "  RECORD_SECONDS is the duration of each browser recording (use 40 to cover all four 10-second slides)." >&2
  exit 2
fi

OUT="$SCRIPT_DIR/out/$RUN_ID"
SOURCES="$OUT/sources"
RECORDINGS="$OUT/recordings"
RESULTS="$OUT/results"
mkdir -p "$OUT" "$RECORDINGS" "$RESULTS"

command -v ffmpeg >/dev/null || { echo 'blocked: ffmpeg is required' >&2; exit 2; }
FFMPEG_FILTERS=$(ffmpeg -filters 2>/dev/null)
[[ "$FFMPEG_FILTERS" == *libvmaf* ]] || { echo 'blocked: ffmpeg libvmaf filter is required' >&2; exit 2; }
command -v tesseract >/dev/null || { echo 'blocked: tesseract is required' >&2; exit 2; }
tesseract --list-langs 2>&1 | grep -qx 'kor' || {
  echo 'blocked: tesseract kor language data is required (eng alone is not enough for the Korean slides)' >&2
  exit 2
}

echo '[1/3] generating sources'
"$SCRIPT_DIR/make-sources.sh" "$SOURCES" "$FONT_PATH"

echo '[2/3] recording Chromium and Chrome ladders'
declare -a RESULT_FILES=()
for browser in chromium chrome; do
  browser_recordings="$RECORDINGS/$browser"
  browser_results="$RESULTS/$browser"
  mkdir -p "$browser_recordings" "$browser_results"
  for content in slides handwriting camera; do
    for bitrate in 300 500 700 1000 1500 2500 4000; do
      recording="$browser_recordings/${content}-${bitrate}.webm"
      echo "  ${browser} ${content} ${bitrate} kbps"
      record_args=(
        --source "$SOURCES/${content}.y4m" \
        --bitrate-kbps "$bitrate" \
        --duration-s "$RECORD_SECONDS" \
        --out "$recording"
      )
      if [[ "$browser" == chrome ]]; then
        record_args+=(--channel chrome)
      fi
      node "$SCRIPT_DIR/record.mjs" "${record_args[@]}"
      # score.sh expects metadata adjacent to its JSON result. Keep recorder metadata
      # separate from the score so actual bitrate is never replaced by the requested one.
      result="$browser_results/${content}-${bitrate}.json"
      cp "$recording.json" "$result.recording.json"
      score_args=(
        --source "$SOURCES/${content}.y4m"
        --encoded "$recording"
        --duration-s "$RECORD_SECONDS"
        --out "$result"
      )
      if [[ "$content" == slides ]]; then
        score_args+=(--ocr --truth-dir "$SOURCES/truth")
      fi
      "$SCRIPT_DIR/score.sh" "${score_args[@]}"
      RESULT_FILES+=("$result")
    done
  done
done

echo '[3/3] writing table'
node --input-type=module - "$OUT/table.md" "${RESULT_FILES[@]}" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises'
import { basename } from 'node:path'
const [outPath, ...files] = process.argv.slice(2)
const rows = []
for (const file of files) {
  const result = JSON.parse(await readFile(file, 'utf8'))
  const match = file.match(/\/results\/(chromium|chrome)\/([^-]+)-(\d+)\.json$/u)
  rows.push({ browser: match?.[1] ?? 'unknown', content: match?.[2] ?? basename(file), requested: Number(match?.[3]), ...result })
}
const number = (value) => value == null ? 'n/a' : Number(value).toFixed(2)
const table = (browser) => {
  const lines = [
    `## ${browser}`,
    '',
    '| content | requested kbps | actual kbps | actual mimeType | frames | avg fps | VFR | dropped frames | dropped ratio | pair PSNR dB | alignment | VMAF mean | VMAF 1% | OCR | OCR ceiling |',
    '| --- | ---: | ---: | --- | ---: | ---: | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |',
  ]
  for (const row of rows.filter((candidate) => candidate.browser === browser)) {
    lines.push(`| ${row.content} | ${row.requested} | ${number(row.actualKbps)} | ${row.actualMimeType ?? 'n/a'} | ${row.encoded?.frames ?? 'n/a'} | ${number(row.encoded?.averageFps)} | ${row.encoded?.vfr ? 'yes' : 'no'} | ${row.pairing?.droppedFrames ?? 'n/a'} | ${number(row.pairing?.droppedRatio)} | ${number(row.pairedPsnrMean)} | ${row.alignmentStatus ?? 'n/a'} | ${number(row.vmafMean)} | ${number(row.vmafP1)} | ${number(row.ocrAccuracy)} | ${number(row.ocrOriginalCeiling)} |`)
  }
  return lines
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
  '## Interpretation',
  '',
  '- VMAF is trained for natural-video fidelity; OCR is the slide readability check.',
  '- The camera score is aligned after converting the recording to 30fps CFR and selecting the source start frame with the highest preview PSNR.',
  '- Compare actual kbps and mimeType between Chromium and Chrome; requested kbps is not the measured bitrate.',
  '',
]
await writeFile(outPath, lines.join('\n'))
NODE
printf 'completed: %s\n' "$OUT"
