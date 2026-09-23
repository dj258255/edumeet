#!/usr/bin/env node
import { readFile, writeFile } from 'node:fs/promises'

const [outPath, reason, sourceProbePath, encodedProbePath, sourceDecodePath] = process.argv.slice(2)
if (!outPath || !reason || !sourceProbePath || !encodedProbePath) {
  console.error('usage: failure-result.mjs OUT REASON SOURCE_PROBE ENCODED_PROBE [SOURCE_DECODE]')
  process.exit(2)
}

const readJson = async (path, fallback = {}) => JSON.parse(await readFile(path, 'utf8').catch(() => JSON.stringify(fallback)))
const sourceProbe = (await readJson(sourceProbePath)).streams?.[0] ?? {}
const encodedProbe = (await readJson(encodedProbePath)).streams?.[0] ?? {}
const sourceDecode = sourceDecodePath ? await readJson(sourceDecodePath) : {}
const meta = await readJson(`${outPath}.recording.json`)
const duration = Number(encodedProbe.duration)
const number = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}
const rate = (value) => {
  if (typeof value !== 'string' || !value.includes('/')) return number(value)
  const [numerator, denominator] = value.split('/').map(Number)
  return denominator ? numerator / denominator : null
}
const probeInfo = (probe) => ({
  frames: number(probe.nb_read_frames ?? probe.nb_frames),
  width: number(probe.width),
  height: number(probe.height),
  durationSeconds: number(probe.duration),
  nominalFps: rate(probe.r_frame_rate),
  averageFps: rate(probe.avg_frame_rate),
  vfr: rate(probe.r_frame_rate) != null && rate(probe.avg_frame_rate) != null
    ? Math.abs(rate(probe.r_frame_rate) - rate(probe.avg_frame_rate)) > 0.01
    : false,
})
const result = {
  requestedKbps: meta.requestedKbps ?? null,
  actualKbps: Number.isFinite(meta.bytes) && duration > 0 ? meta.bytes * 8 / duration / 1000 : null,
  wallClockKbps: Number.isFinite(meta.bytes) && Number.isFinite(meta.elapsedMs) && meta.elapsedMs > 0
    ? meta.bytes * 8 / (meta.elapsedMs / 1000) / 1000
    : null,
  actualMimeType: meta.actualMimeType ?? null,
  vmafMean: null,
  vmafP1: null,
  ocrAccuracy: null,
  ocrOriginalCeiling: null,
  ocrScenes: [],
  source: probeInfo(sourceProbe),
  encoded: probeInfo(encodedProbe),
  frameBand: {
    includedInVmaf: true,
    width: 384,
    height: 24,
    sourceDecodeFailures: sourceDecode.decodeFailures ?? null,
    decodeFailures: null,
    failureCounts: sourceDecode.failureCounts ?? null,
  },
  pairing: {
    frames: 0,
    droppedFrames: null,
    droppedRatio: null,
    scoringSourceFrames: null,
    outOfRange: null,
    duplicateMappings: null,
  },
  pairedPsnrMean: null,
  vmafLowDiagnostics: [],
  alignmentStatus: reason,
  failureReasons: [reason],
  status: reason,
}
await writeFile(outPath, JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, failureReasons: result.failureReasons }))
