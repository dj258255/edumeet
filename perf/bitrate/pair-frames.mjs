#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises'

const [framesPath, sourceFramesArg, sourceRawPath, encodedRawPath, widthArg, heightArg, outPath] = process.argv.slice(2)
const sourceFrames = Number(sourceFramesArg)
const width = Number(widthArg)
const height = Number(heightArg)
if (!framesPath || !Number.isFinite(sourceFrames) || !sourceRawPath || !encodedRawPath || !width || !height || !outPath) {
  console.error('usage: pair-frames.mjs FRAMES_JSON SOURCE_FRAMES SOURCE_RAW ENCODED_RAW WIDTH HEIGHT OUT_JSON')
  process.exit(2)
}

const frameData = JSON.parse(await readFile(framesPath, 'utf8'))
const sourceRaw = await readFile(sourceRawPath)
const encodedRaw = await readFile(encodedRawPath)
const frames = frameData.frames ?? []
const frameBytes = width * height
const sourcePreviewFrames = Math.floor(sourceRaw.length / frameBytes)
const encodedPreviewFrames = Math.floor(encodedRaw.length / frameBytes)

function makePairs(offsetFrames) {
  const pairs = []
  const usedSourceFrames = new Set()
  let outOfRange = 0
  let duplicate = 0
  for (let encodedIndex = 0; encodedIndex < frames.length; encodedIndex += 1) {
    const frame = frames[encodedIndex]
    const pts = Number(frame.pts_time ?? frame.best_effort_timestamp_time)
    if (!Number.isFinite(pts)) continue
    const sourceIndex = Math.round(pts * 30) + offsetFrames
    if (sourceIndex < 0 || sourceIndex >= sourceFrames) {
      outOfRange += 1
      continue
    }
    if (usedSourceFrames.has(sourceIndex)) {
      duplicate += 1
      continue
    }
    usedSourceFrames.add(sourceIndex)
    pairs.push({ encodedIndex, sourceIndex, ptsSeconds: pts })
  }
  return { pairs, usedSourceFrames, outOfRange, duplicate }
}

function psnr(pairs, limit = pairs.length) {
  let squaredError = 0
  let samples = 0
  for (const { encodedIndex, sourceIndex } of pairs.slice(0, limit)) {
    const sourceStart = sourceIndex * frameBytes
    const encodedStart = encodedIndex * frameBytes
    if (sourceStart + frameBytes > sourceRaw.length || encodedStart + frameBytes > encodedRaw.length) continue
    for (let pixel = 0; pixel < frameBytes; pixel += 1) {
      const delta = sourceRaw[sourceStart + pixel] - encodedRaw[encodedStart + pixel]
      squaredError += delta * delta
    }
    samples += frameBytes
  }
  const meanSquaredError = squaredError / samples
  return meanSquaredError === 0 ? Infinity : 10 * Math.log10((255 * 255) / meanSquaredError)
}

let best
for (let offsetFrames = -3; offsetFrames <= 3; offsetFrames += 1) {
  const candidate = makePairs(offsetFrames)
  const candidatePsnr = psnr(candidate.pairs, 120)
  if (!best || candidatePsnr > best.previewPsnrDb) {
    best = { offsetFrames, previewPsnrDb: candidatePsnr, ...candidate }
  }
}

const { offsetFrames, pairs, usedSourceFrames, outOfRange, duplicate, previewPsnrDb } = best
const droppedFrames = sourceFrames - usedSourceFrames.size
const result = {
  sourceFrames,
  encodedFrames: frames.length,
  pairFrames: pairs.length,
  droppedFrames,
  droppedRatio: sourceFrames ? droppedFrames / sourceFrames : null,
  outOfRange,
  duplicateMappings: duplicate,
  offsetFrames,
  previewPsnrDb,
  sourcePreviewFrames,
  encodedPreviewFrames,
  // Used by ffmpeg's select filter. The escaped commas are intentional.
  sourceSelect: `select='${pairs.map(({ sourceIndex }) => `eq(n\\,${sourceIndex})`).join('+')}'`,
  pairs,
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({
  sourceFrames,
  encodedFrames: frames.length,
  pairFrames: pairs.length,
  droppedFrames,
  droppedRatio: result.droppedRatio,
  outOfRange,
  duplicateMappings: duplicate,
  offsetFrames,
  previewPsnrDb,
}))
