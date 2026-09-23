#!/usr/bin/env node
/**
 * 합성 방송. (#197 대조 하네스)
 *
 * ★ 브라우저 MediaRecorder 대신 ffmpeg 출력을 같은 업로드 API 로 넣는다.
 *   서버가 보는 것은 브라우저가 보내는 것과 같은 모양이어야 한다 - 조각을 2초마다
 *   octet-stream 으로 올리고, 첫 조각에 컨테이너 헤더가 들어간다.
 *   (docs/performance/25 의 합성 방송 방식)
 *
 * ★ 키프레임 간격을 조각 길이에 맞춘다.
 *   서버는 H264 를 받으면 -c:v copy 로 리먹싱한다. copy 는 키프레임을 새로 못 만든다.
 *   그래서 HLS 세그먼트 경계는 우리가 만든 키프레임을 따라간다.
 *
 * ★ 어떤 경로로 끝나도 DELETE 를 부른다.
 *   안 부르면 유령 방송이 남고 서버가 기동 때 걷어내야 한다. (#168)
 *   그래서 stop() 은 한 번만 도는 약속(promise)이고, 모든 신호·예외·ffmpeg 사망이
 *   같은 약속을 기다린 뒤 프로세스가 끝난다. 업로드 체인이 reject 돼도 finally 에서 DELETE 한다.
 *
 * 사용:
 *   node broadcast-synthetic.mjs --run <이름> [--chunk-ms 2000] [--duration-s 300] [--bitrate-k 2500]
 *     [--segment-type mpegts|fmp4] [--hls-time 1|2]
 *   node broadcast-synthetic.mjs --stop-only     # 진행 중인 방송을 내리기만 한다
 */
import { spawn } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { loadEnv, outDir, parseArgs } from './lib/env.mjs'

const MIME_TYPE = 'video/webm;codecs=h264,opus'

const args = parseArgs(process.argv.slice(2))
const env = loadEnv()

const broadcastUrl = `${env.API}/meeting/${env.EDUMEET_MEETING_ID}/broadcast`
const auth = { Authorization: `Bearer ${env.EDUMEET_TOKEN}` }

// ★ --stop-only: 유령 방송을 내리는 최소 모드.
//   셸이 토큰을 들고 curl 을 부르지 않게 한다 - 그러면 토큰이 ps 명령줄에 보인다.
if (args['stop-only']) {
  let ok = false
  try {
    const res = await fetch(broadcastUrl, { method: 'DELETE', headers: auth })
    console.log(`방송 중지 요청: HTTP ${res.status}`)
    ok = res.ok
  } catch (error) {
    console.error(`방송 중지 요청이 실패했다: ${error.message}`)
  }
  process.exit(ok ? 0 : 1)
}

const chunkMs = Number(args['chunk-ms'] ?? 2000)
const durationS = Number(args['duration-s'] ?? 300)
const bitrateK = Number(args['bitrate-k'] ?? 2500)
const segmentType = args['segment-type'] ?? 'mpegts'
const hlsTimeSec = Number(args['hls-time'] ?? 2)
const run = args.run
const dir = outDir(run)
mkdirSync(dir, { recursive: true })

const chunkS = chunkMs / 1000
const result = {
  run,
  chunkMs,
  bitrateK,
  durationS,
  mimeType: MIME_TYPE,
  segmentType,
  hlsTimeSec,
  playlistUrl: null,
  startedAt: null,
  endedAt: null,
  elapsedS: null,
  seqTotal: 0,
  chunksSent: 0,
  chunksRejected: 0,
  chunksFailed: 0,
}

function writeResult() {
  writeFileSync(join(dir, 'broadcast.json'), `${JSON.stringify(result, null, 2)}\n`)
}

const chunkUrl = (n) => `${broadcastUrl}/chunk?seq=${n}`

let child = null
let seq = 0
let sent = 0
let rejected = 0
let failed = 0
let pending = []
let chain = Promise.resolve()
let tick = null
let durationTimer = null

let stopping = false
let stopPromise = null
let resolveDone
const donePromise = new Promise((resolve) => {
  resolveDone = resolve
})

function ffmpegArgs() {
  return [
    '-hide_banner',
    '-loglevel', 'error',
    '-re', '-f', 'lavfi', '-i', 'testsrc2=size=1280x720:rate=30',
    '-re', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
    '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency',
    '-pix_fmt', 'yuv420p', '-b:v', `${bitrateK}k`,
    // 조각 길이마다 키프레임을 강제한다. 서버가 copy 로 리먹싱해도 세그먼트가 맞는다.
    '-force_key_frames', `expr:gte(t,n_forced*${chunkS})`,
    '-c:a', 'libopus', '-b:a', '96k',
    // ★ webm 먹서는 H.264 를 거부한다(스펙상 VP8/VP9/AV1 만). 그래서 matroska 로 쓴다.
    //   같은 EBML 계열이고 ffprobe 도 matroska,webm 으로 읽는다 - 서버가 보는 모양은
    //   Chrome 의 video/webm;codecs=h264 와 같다. 서버 ffmpeg 는 컨테이너를 스스로 판별한다.
    '-f', 'matroska', 'pipe:1',
  ]
}

async function upload(mySeq, buf) {
  try {
    const res = await fetch(chunkUrl(mySeq), {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'application/octet-stream' },
      body: buf,
    })
    if (res.status === 429) {
      // 서버 큐가 찼다. 조각이 버려졌다는 뜻이므로 성공으로 세지 않는다.
      rejected += 1
    } else if (res.ok) {
      sent += 1
    } else {
      failed += 1
    }
  } catch {
    failed += 1
  }
  result.chunksSent = sent
  result.chunksRejected = rejected
  result.chunksFailed = failed
  result.seqTotal = seq
  writeResult()
}

function flushPending() {
  if (pending.length === 0) return
  const buf = Buffer.concat(pending)
  pending = []
  const mySeq = seq
  seq += 1
  chain = chain.then(() => upload(mySeq, buf))
}

/** 한 번만 도는 정지. 어디서 불러도 같은 약속을 돌려준다. */
function stop() {
  stopping = true
  if (!stopPromise) {
    stopPromise = doStop().finally(() => resolveDone())
  }
  return stopPromise
}

async function doStop() {
  clearInterval(tick)
  clearTimeout(durationTimer)
  if (child && child.exitCode == null) child.kill('SIGTERM')

  try {
    flushPending()
    await chain
  } catch (error) {
    // 업로드 체인이 reject 돼도 DELETE 를 건너뛰지 않는다.
    console.error(`업로드가 실패했다: ${error.message}`)
  } finally {
    try {
      await fetch(broadcastUrl, { method: 'DELETE', headers: auth })
    } catch {
      // 내리지 못해도 산출물은 남긴다.
    }
  }

  result.endedAt = new Date().toISOString()
  result.elapsedS = result.startedAt
    ? Math.round((Date.now() - Date.parse(result.startedAt)) / 1000)
    : null
  result.seqTotal = seq
  result.chunksSent = sent
  result.chunksRejected = rejected
  result.chunksFailed = failed
  try {
    // 산출물을 못 써도 정지는 이미 끝났다. 여기서 던지면 stop() 약속이 reject 되어
    // 부르는 쪽에 unhandled rejection 이 된다.
    writeResult()
  } catch (error) {
    console.error(`산출물을 쓰지 못했다: ${error.message}`)
  }
}

async function start() {
  const res = await fetch(broadcastUrl, {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'application/json' },
    body: JSON.stringify({ mimeType: MIME_TYPE, segmentType, hlsTimeSec }),
  })
  if (!res.ok) throw new Error(`방송 시작 실패 (HTTP ${res.status})`)
  const data = await res.json()
  result.playlistUrl = data.playlistUrl ?? null
  result.startedAt = new Date().toISOString()
  writeResult()

  child = spawn('ffmpeg', ffmpegArgs(), { stdio: ['ignore', 'pipe', 'inherit'] })
  child.stdout.on('data', (d) => {
    if (!stopping) pending.push(d)
  })
  // ★ ffmpeg 가 스스로 죽어도 방송을 내린다. 안 그러면 Node 가 살아 있는 동안
  //   유령 방송이 유지되고, 그 사이 Node 가 죽으면 DELETE 가 아예 안 나간다.
  child.on('exit', (code, signal) => {
    if (stopping) return
    console.error(`ffmpeg 가 끝났다 (code=${code}, signal=${signal}). 방송을 내린다`)
    void stop()
  })
  child.on('error', (error) => {
    console.error(`ffmpeg 를 시작하지 못했다: ${error.message}`)
    void stop()
  })

  tick = setInterval(flushPending, chunkMs)
  durationTimer = setTimeout(() => { void stop() }, durationS * 1000)
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    // stop() 이 같은 약속을 돌려주므로 두 번째 신호도 DELETE 가 끝날 때까지 기다린다.
    void stop()
  })
}

let exitCode = 0
try {
  await start()
} catch (error) {
  console.error(error.message)
  exitCode = 1
  void stop()
}

await donePromise
process.exit(exitCode)
