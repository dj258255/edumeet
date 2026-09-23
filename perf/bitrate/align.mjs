#!/usr/bin/env node
import { readFile } from 'node:fs/promises'

const [sourceRawPath, encodedRawPath, widthArg, heightArg, maxOffsetArg] = process.argv.slice(2)
const width = Number(widthArg)
const height = Number(heightArg)
const maxOffsetFrames = Number(maxOffsetArg)
if (!sourceRawPath || !encodedRawPath || !width || !height || !Number.isFinite(maxOffsetFrames)) {
  console.error('usage: align.mjs SOURCE_RAW ENCODED_RAW WIDTH HEIGHT MAX_OFFSET_FRAMES')
  process.exit(2)
}

const source = await readFile(sourceRawPath)
const encoded = await readFile(encodedRawPath)
const frameBytes = width * height
const sourceFrames = Math.floor(source.length / frameBytes)
const encodedFrames = Math.floor(encoded.length / frameBytes)
const comparedFrames = Math.min(encodedFrames, 60)
if (!sourceFrames || !encodedFrames || !comparedFrames) {
  throw new Error(`not enough preview frames: source=${sourceFrames}, encoded=${encodedFrames}`)
}

function psnr(offset) {
  if (offset + comparedFrames > sourceFrames) return -Infinity
  let squaredError = 0
  let samples = 0
  for (let frame = 0; frame < comparedFrames; frame += 1) {
    const sourceStart = (offset + frame) * frameBytes
    const encodedStart = frame * frameBytes
    for (let pixel = 0; pixel < frameBytes; pixel += 1) {
      const delta = source[sourceStart + pixel] - encoded[encodedStart + pixel]
      squaredError += delta * delta
    }
    samples += frameBytes
  }
  const meanSquaredError = squaredError / samples
  return meanSquaredError === 0 ? Infinity : 10 * Math.log10((255 * 255) / meanSquaredError)
}

let best = { offsetFrames: 0, psnrDb: psnr(0) }
for (let offset = 1; offset <= Math.min(maxOffsetFrames, sourceFrames - comparedFrames); offset += 1) {
  const candidate = psnr(offset)
  if (candidate > best.psnrDb) best = { offsetFrames: offset, psnrDb: candidate }
}

console.log(JSON.stringify({
  ...best,
  offsetSeconds: best.offsetFrames / 30,
  comparedFrames,
  sourcePreviewFrames: sourceFrames,
  encodedPreviewFrames: encodedFrames,
}))
