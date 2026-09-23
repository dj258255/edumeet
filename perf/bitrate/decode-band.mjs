#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises'

const [rawPath, frameCountArg, maxSourceFrameArg, outPath, ptsPath, mode] = process.argv.slice(2)
const frameCount = Number(frameCountArg)
const maxSourceFrame = Number(maxSourceFrameArg)
const width = 16 * 24
const height = 24
const cellSize = 24
const cellSampleStart = 4
const cellSampleSize = 16

if (!rawPath || !Number.isInteger(frameCount) || frameCount < 0 || !Number.isInteger(maxSourceFrame) || maxSourceFrame < 0 || !outPath) {
  console.error('usage: decode-band.mjs RAW_PATH FRAME_COUNT MAX_SOURCE_FRAME OUT_JSON [PTS_JSON|-] [source]')
  process.exit(2)
}

const raw = await readFile(rawPath)
const ptsFrames = ptsPath && ptsPath !== '-' ? (JSON.parse(await readFile(ptsPath, 'utf8')).frames ?? []) : []
const frameBytes = width * height
const availableFrames = Math.floor(raw.length / frameBytes)
const failureCounts = {
  parity: 0,
  uncertain: 0,
  range: 0,
  order: 0,
  ptsMismatch: 0,
}
let previousSourceIndex = null
let lastValidSourceIndex = null
const frames = []
const candidates = []

function meanCell(frameStart, cell) {
  let sum = 0
  for (let y = cellSampleStart; y < cellSampleStart + cellSampleSize; y += 1) {
    for (let x = cell * cellSize + cellSampleStart; x < cell * cellSize + cellSampleStart + cellSampleSize; x += 1) {
      sum += raw[frameStart + y * width + x]
    }
  }
  return sum / (cellSampleSize * cellSampleSize)
}

function parity(value) {
  let ones = 0
  for (let bit = 0; bit < 15; bit += 1) ones += (value >> bit) & 1
  return ones % 2
}

function fail(frameIndex, reason, type) {
  failureCounts[type] += 1
  frames[frameIndex] = { encodedIndex: frameIndex, valid: false, reason }
}

for (let frameIndex = 0; frameIndex < frameCount; frameIndex += 1) {
  const frameStart = frameIndex * frameBytes
  if (frameStart + frameBytes > raw.length || frameIndex >= availableFrames) {
    fail(frameIndex, 'frame missing', 'range')
    continue
  }
  const means = Array.from({ length: 16 }, (_, cell) => meanCell(frameStart, cell))
  if (means.some((mean) => Math.abs(mean - 128) <= 40)) {
    fail(frameIndex, 'uncertain cell', 'uncertain')
    continue
  }
  let value = 0
  for (let bit = 0; bit < 15; bit += 1) {
    if (means[bit] >= 128) value |= 1 << bit
  }
  const parityBit = means[15] >= 128 ? 1 : 0
  if (parityBit !== parity(value)) {
    fail(frameIndex, 'parity mismatch', 'parity')
    continue
  }
  if (value < 0 || value >= maxSourceFrame) {
    fail(frameIndex, 'out of range', 'range')
    continue
  }
  const pts = Number(ptsFrames[frameIndex]?.pts_time ?? ptsFrames[frameIndex]?.best_effort_timestamp_time)
  candidates.push({ frameIndex, sourceIndex: value, pts, c: Number.isFinite(pts) ? value - Math.round(pts * 30) : null })
}

const cValues = candidates.map((candidate) => candidate.c).filter(Number.isFinite).sort((left, right) => left - right)
const c0 = cValues.length ? cValues[Math.floor(cValues.length / 2)] : null
const checkPts = ptsFrames.length > 0
for (const candidate of candidates) {
  if (checkPts && (c0 == null || candidate.c == null || Math.abs(candidate.c - c0) > 3)) {
    fail(candidate.frameIndex, 'PTS residual mismatch', 'ptsMismatch')
    continue
  }
  if (previousSourceIndex != null && candidate.sourceIndex < previousSourceIndex) {
    fail(candidate.frameIndex, 'decreased frame number', 'order')
    continue
  }
  previousSourceIndex = candidate.sourceIndex
  frames[candidate.frameIndex] = {
    encodedIndex: candidate.frameIndex,
    sourceIndex: candidate.sourceIndex,
    pts: candidate.pts,
    residual: candidate.c,
    valid: true,
  }
  lastValidSourceIndex = candidate.sourceIndex
}

const decodeFailures = Object.values(failureCounts).reduce((sum, value) => sum + value, 0)
const result = {
  frameCount,
  availableFrames,
  width,
  height,
  decodeFailures,
  failureCounts,
  ptsResidualMedian: c0,
  lastValidSourceIndex,
  frames,
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')

if (mode === 'source') {
  const sourceIsExact = decodeFailures === 0
    && frames.length === frameCount
    && frames.every((frame, index) => frame.valid && frame.sourceIndex === index)
  if (!sourceIsExact) {
    console.error(`source frame-band self-check failed: ${decodeFailures} decode failures`)
    process.exitCode = 1
  }
}

console.log(JSON.stringify({
  frameCount,
  availableFrames,
  decodeFailures,
  failureCounts,
  ptsResidualMedian: c0,
  lastValidSourceIndex,
  sourceSelfCheck: mode === 'source' ? decodeFailures === 0 && frames.every((frame, index) => frame.valid && frame.sourceIndex === index) : undefined,
}))
