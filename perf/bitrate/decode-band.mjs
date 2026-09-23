#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises'

const [rawPath, frameCountArg, maxSourceFrameArg, outPath, mode] = process.argv.slice(2)
const frameCount = Number(frameCountArg)
const maxSourceFrame = Number(maxSourceFrameArg)
const width = 16 * 24
const height = 24
const cellSize = 24
const cellSampleStart = 4
const cellSampleSize = 16

if (!rawPath || !Number.isInteger(frameCount) || frameCount < 0 || !Number.isInteger(maxSourceFrame) || maxSourceFrame < 0 || !outPath) {
  console.error('usage: decode-band.mjs RAW_PATH FRAME_COUNT MAX_SOURCE_FRAME OUT_JSON [source]')
  process.exit(2)
}

const raw = await readFile(rawPath)
const frameBytes = width * height
const availableFrames = Math.floor(raw.length / frameBytes)
const frames = []
let decodeFailures = 0
let previousSourceIndex = null
let lastValidSourceIndex = null

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

for (let frameIndex = 0; frameIndex < frameCount; frameIndex += 1) {
  const frameStart = frameIndex * frameBytes
  let value = 0
  for (let bit = 0; bit < 15; bit += 1) {
    if (meanCell(frameStart, bit) >= 128) value |= 1 << bit
  }
  const parityBit = meanCell(frameStart, 15) >= 128 ? 1 : 0
  let reason = null
  if (frameStart + frameBytes > raw.length || frameIndex >= availableFrames) {
    reason = 'frame missing'
  } else if (parityBit !== parity(value)) {
    reason = 'parity mismatch'
  } else if (value < 0 || value >= maxSourceFrame) {
    reason = 'out of range'
  } else if (previousSourceIndex != null && value < previousSourceIndex) {
    reason = 'decreased frame number'
  }
  if (reason) {
    decodeFailures += 1
    frames.push({ encodedIndex: frameIndex, valid: false, reason })
    continue
  }
  previousSourceIndex = value
  lastValidSourceIndex = value
  frames.push({ encodedIndex: frameIndex, sourceIndex: value, valid: true })
}

const result = {
  frameCount,
  availableFrames,
  width,
  height,
  decodeFailures,
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
  lastValidSourceIndex,
  sourceSelfCheck: mode === 'source' ? decodeFailures === 0 && frames.every((frame, index) => frame.valid && frame.sourceIndex === index) : undefined,
}))
