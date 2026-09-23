#!/usr/bin/env node
import { createRequire } from 'node:module'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
let chromium
for (const candidate of ['playwright', '../browser/node_modules/playwright']) {
  try {
    ({ chromium } = require(candidate))
    break
  } catch {}
}
if (!chromium) {
  console.error('Playwright is required: install perf/browser/package.json dependencies first')
  process.exit(2)
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const codecFile = resolve(root, 'frontend/src/features/broadcast/codecChoice.js')

function arg(name, fallback = undefined) {
  const index = process.argv.indexOf(`--${name}`)
  return index < 0 ? fallback : process.argv[index + 1]
}

const source = arg('source')
const output = arg('out')
const bitrateKbps = Number(arg('bitrate-kbps'))
const durationSeconds = Number(arg('duration-s', process.env.RECORD_SECONDS || 40))
const channel = arg('channel')
if (!source || !output || !Number.isFinite(bitrateKbps) || !Number.isFinite(durationSeconds)) {
  console.error('usage: record.mjs --source FILE --bitrate-kbps N --duration-s N --out FILE [--channel chrome]')
  process.exit(2)
}

const codecSource = await readFile(codecFile, 'utf8')
const preferenceMatch = codecSource.match(/const VIDEO_PREFERENCES\s*=\s*\[([\s\S]*?)\]/)
const preferences = [...(preferenceMatch?.[1] ?? '').matchAll(/['"]([^'"]+)['"]/g)].map((match) => match[1])
if (!preferences.length) throw new Error(`could not read VIDEO_PREFERENCES from ${codecFile}`)

// The managed runner does not allow even a loopback listen. A file origin is
// sufficient for the fake camera and avoids turning this harness into a server.
const blankPage = resolve(dirname(fileURLToPath(import.meta.url)), 'blank.html')

const launchOptions = {
  headless: true,
  args: [
    '--use-fake-device-for-media-stream',
    '--use-fake-ui-for-media-stream',
    `--use-file-for-fake-video-capture=${resolve(source)}`,
    '--autoplay-policy=no-user-gesture-required',
    '--allow-file-access-from-files',
  ],
}
if (channel) launchOptions.channel = channel
const browser = await chromium.launch(launchOptions)

try {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`file://${blankPage}`)
  const result = await page.evaluate(async ({ preferences, bitrateKbps, durationSeconds }) => {
    const supported = preferences.filter((type) => MediaRecorder.isTypeSupported(type))
    const requestedMimeType = supported[0] || ''
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } },
    })
    const options = { videoBitsPerSecond: bitrateKbps * 1000 }
    if (requestedMimeType) options.mimeType = requestedMimeType
    const recorder = new MediaRecorder(stream, options)
    const chunks = []
    const asBase64 = (buffer) => {
      const bytes = new Uint8Array(buffer)
      let binary = ''
      for (let offset = 0; offset < bytes.length; offset += 0x8000) {
        binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000))
      }
      return btoa(binary)
    }
    const pendingChunks = []
    recorder.ondataavailable = (event) => {
      if (event.data.size) {
        pendingChunks.push(event.data.arrayBuffer().then((buffer) => chunks.push(asBase64(buffer))))
      }
    }
    const stopped = new Promise((resolveStopped, rejectStopped) => {
      recorder.onerror = () => rejectStopped(recorder.error?.message || 'MediaRecorder error')
      recorder.onstop = resolveStopped
    })
    const startedAt = performance.now()
    recorder.start(1000)
    await new Promise((resolveWait) => setTimeout(resolveWait, durationSeconds * 1000))
    recorder.stop()
    await stopped
    await Promise.all(pendingChunks)
    stream.getTracks().forEach((track) => track.stop())
    return {
      chunks,
      requestedMimeType,
      actualMimeType: recorder.mimeType,
      elapsedMs: performance.now() - startedAt,
      videoBitsPerSecond: recorder.videoBitsPerSecond,
    }
  }, { preferences, bitrateKbps, durationSeconds })

  const buffers = result.chunks.map((chunk) => Buffer.from(chunk, 'base64'))
  const data = Buffer.concat(buffers)
  await mkdir(dirname(resolve(output)), { recursive: true })
  await writeFile(output, data)
  await writeFile(`${output}.json`, JSON.stringify({
    source: resolve(source),
    channel: channel || 'chromium',
    requestedKbps: bitrateKbps,
    requestedMimeType: result.requestedMimeType,
    actualMimeType: result.actualMimeType,
    elapsedMs: result.elapsedMs,
    bytes: data.length,
    actualKbps: data.length * 8 / (result.elapsedMs / 1000) / 1000,
    videoBitsPerSecond: result.videoBitsPerSecond,
  }, null, 2) + '\n')
  const { chunks: _chunks, ...summary } = result
  console.log(JSON.stringify({
    output,
    channel: channel || 'chromium',
    ...summary,
    bytes: data.length,
  }))
} finally {
  await browser.close()
}
