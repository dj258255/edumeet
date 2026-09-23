#!/usr/bin/env node
/**
 * 시청자 N대 + 스로틀 일정 + ①② 수집. (#197 대조 하네스)
 *
 * ★ 시청자마다 별도 context.
 *   저장소·세션·localStorage 를 나눠야 sessionId 가 갈리고 보고가 섞이지 않는다.
 *
 * ★ 정답(①)은 lib/truth.js 를 addInitScript 로 심어 센다. 앱 코드를 import 하지 않는다.
 *   보낸 값(②)은 요청 본문을 가로채 모은다.
 *
 * ★ 부하 생성기가 병목이면 정답이 틀어진다. (#160)
 *   1초마다 이 프로세스와 자식의 CPU 사용률을 out/<run>/load.json 에 남긴다.
 *
 * 사용:
 *   node qoe-crosscheck.mjs --run <이름> [--viewers 5] [--schedule '<json>'] [--force-path native]
 *                             [--catchup-rate 1.25]   # 따라잡기 진단 (#233)
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'
import { BROWSER_DIR, loadEnv, outDir, parseArgs } from './lib/env.mjs'

/** 기본 스로틀 일정. 첫 60초는 워밍업이라 대조에서 뺀다. */
const DEFAULT_SCHEDULE = [
  { from: 0, to: 60, limit: null }, // 워밍업
  { from: 60, to: 120, limit: null }, // 기준 구간
  { from: 120, to: 150, limit: 800 }, // 800 kbps - 송출 2.5Mbps 보다 낮춰 끊김을 만든다
  { from: 150, to: 210, limit: null }, // 회복
  { from: 210, to: 225, limit: 'offline' }, // 완전 끊김
  { from: 225, to: 285, limit: null },
]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const args = parseArgs(process.argv.slice(2))
const env = loadEnv()
const run = args.run
const viewerCount = Number(args.viewers ?? 5)
const schedule = args.schedule ? JSON.parse(args.schedule) : DEFAULT_SCHEDULE
const forcePath = args['force-path'] === 'native' ? 'native' : null
const liveSync = args['live-sync'] ?? null
// ★ 진단용 따라잡기 재생 속도 (#233). 앱이 읽는 값과 같은 목록만 받는다.
//   이걸 켜면 화면 지연은 줄지만 자막도 그만큼 빨리 지나간다 - 자막 읽기와 함께 봐야 한다.
const CATCHUP_RATES = ['1', '1.05', '1.1', '1.25', '1.5']
const catchupRate = CATCHUP_RATES.includes(String(args['catchup-rate'] ?? ''))
  ? String(args['catchup-rate'])
  : null
if (args['catchup-rate'] && catchupRate === null) {
  throw new Error(`--catchup-rate 는 ${CATCHUP_RATES.join(' · ')} 중 하나여야 한다`)
}
const allowBroadcastRestart = process.env.ALLOW_BROADCAST_RESTART === '1'
const dir = outDir(run)
mkdirSync(dir, { recursive: true })

// ★ 실제로 쓴 스로틀 일정을 남긴다. 기본값을 썼어도 남긴다.
//   넘긴 일정이 기록되지 않으면 "이 회차가 어떤 조건이었나" 를 사후에 알 수 없다.
const scheduleSource = args.schedule ? 'argument' : 'default'
writeFileSync(
  join(dir, 'schedule.json'),
  `${JSON.stringify({ source: scheduleSource, schedule, allowBroadcastRestart, catchupRate }, null, 2)}\n`,
)

const totalMs = Math.max(...schedule.map((s) => s.to)) * 1000

/**
 * 앱의 로그인 가드는 토큰만으로 부족하다.
 *
 * frontend/src/stores/auth.js 의 isLoggedIn() 이
 *   isTokenValid() && getUser() !== null
 * 이라, localStorage.user 가 없으면 라우터가 시청 화면 대신 /login 으로 보낸다.
 * (실제로 그렇게 5대가 전부 빈 정답을 냈다.)
 *
 * login() 이 저장하는 모양과 같게 { email, nickname } 을 만든다.
 * 토큰은 어디에도 찍지 않는다.
 */
async function fetchCurrentUser() {
  const res = await fetch(`${env.API}/members/me`, {
    headers: { Authorization: `Bearer ${env.EDUMEET_TOKEN}` },
  })
  if (!res.ok) {
    throw new Error(
      `GET /members/me 가 실패했다 (HTTP ${res.status}). 토큰이 만료됐을 수 있다`,
    )
  }
  const body = await res.json()
  if (!body?.email) throw new Error('GET /members/me 응답에 email 이 없다')
  return { email: body.email, nickname: body.nickname ?? body.email.split('@')[0] }
}

function sampleTreeCpuPct() {
  const out = execFileSync('ps', ['-axo', 'pid=,ppid=,%cpu='], { encoding: 'utf8' })
  const cpu = new Map()
  const children = new Map()
  for (const line of out.split('\n')) {
    const m = line.trim().match(/^(\d+)\s+(\d+)\s+([\d.]+)$/)
    if (!m) continue
    const pid = Number(m[1])
    const ppid = Number(m[2])
    cpu.set(pid, Number(m[3]))
    if (!children.has(ppid)) children.set(ppid, [])
    children.get(ppid).push(pid)
  }
  let total = 0
  const stack = [process.pid]
  const seen = new Set()
  while (stack.length) {
    const pid = stack.pop()
    if (seen.has(pid)) continue
    seen.add(pid)
    total += cpu.get(pid) ?? 0
    for (const child of children.get(pid) ?? []) stack.push(child)
  }
  return Math.round(total * 10) / 10
}

async function applyLimit(cdp, limit) {
  const params =
    limit === 'offline'
      ? { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 }
      : limit == null
        ? { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 }
        : {
            offline: false,
            latency: 0,
            // CDP 는 bytes/s 다. kbps 는 bits/s 다.
            downloadThroughput: Math.round((limit * 1000) / 8),
            uploadThroughput: Math.round((limit * 1000) / 8),
          }
  await cdp.send('Network.emulateNetworkConditions', params)
}

/**
 * 콘솔·예외 문자열에서 Bearer 토큰을 가린다.
 * 토큰이 요청 헤더로만 가서 콘솔에 남을 일은 없어야 하지만, 남더라도 산출물에 남기지 않는다.
 */
function redactBearer(text) {
  return String(text).replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer <redacted>')
}

function collectReports(context) {
  const reports = []
  const failures = []
  const byRequest = new Map()

  context.on('request', (req) => {
    if (req.method() !== 'POST' || !req.url().includes('/broadcast/qoe')) return
    let body = null
    try {
      body = req.postDataJSON()
    } catch {
      body = null
    }
    const entry = {
      at: Date.now(),
      sessionId: body?.sessionId ?? null,
      seq: body?.seq ?? null,
      intervalMs: body?.intervalMs ?? null,
      playingMs: body?.playingMs ?? null,
      stallMs: body?.stallMs ?? null,
      stallCount: body?.stallCount ?? null,
      startupMs: body?.startupMs ?? null,
      errors: body?.errors ?? null,
      final: body?.final === true,
      status: null,
      failure: null,
    }
    reports.push(entry)
    byRequest.set(req, entry)
  })

  context.on('response', (res) => {
    // ★ request 객체 동일성으로만 짝짓는다.
    //   "아직 응답이 안 붙은 첫 보고" 같은 순서 짝짓기를 하면 영상 조각(206)·404 같은
    //   다른 요청의 응답이 보고에 붙는다. 실제로 그렇게 206 이 붙었다.
    const entry = byRequest.get(res.request())
    if (entry) entry.status = res.status()
  })

  context.on('requestfailed', (req) => {
    if (req.method() !== 'POST' || !req.url().includes('/broadcast/qoe')) return
    const error = req.failure()?.errorText ?? 'request failed'
    failures.push({ at: Date.now(), error })
    // 실패한 보고는 status 를 null 로 두고 오류 문자열만 남긴다.
    const entry = byRequest.get(req)
    if (entry) {
      entry.status = null
      entry.failure = error
    }
  })

  return { reports, failures }
}

async function runViewer(browser, k, user) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 } })
  await context.addInitScript({ path: join(BROWSER_DIR, 'lib', 'truth.js') })
  await context.addInitScript(({ path }) => {
    if (path === 'native') localStorage.setItem('edumeet.playbackPath', path)
  }, { path: forcePath })
  await context.addInitScript(({ value }) => {
    if (value !== null) localStorage.setItem('edumeet.hls.maxLiveSyncPlaybackRate', String(value))
  }, { value: catchupRate })

  await context.addInitScript(({ value }) => {
    if (value !== null) localStorage.setItem('edumeet.hls.liveSyncDurationCount', String(value))
  }, { value: liveSync })
  await context.addInitScript(({ token, user }) => {
    try {
      localStorage.setItem('token', token)
      // 로그인 가드(isTokenValid() && getUser() !== null)를 통과해야
      // 라우터가 시청 화면을 띄운다. auth.js 의 setUser 와 같은 모양이다.
      localStorage.setItem('user', JSON.stringify(user))
    } catch {
      // about:blank 등 저장소가 없는 문서에서는 실패한다. 대상 출처에서는 성공한다.
    }
  }, { token: env.EDUMEET_TOKEN, user })

  const { reports, failures } = collectReports(context)
  const page = await context.newPage()

  // 앱이 alert 로 막으면 Playwright 가 조용히 닫아 버린다. 무엇을 말했는지 남긴다.
  // (이 조용함 때문에 "빈 정답" 의 원인을 못 봤다)
  const dialogs = []
  page.on('dialog', (dialog) => {
    dialogs.push({ at: Date.now(), type: dialog.type(), message: dialog.message() })
    void dialog.dismiss().catch(() => {})
  })

  // ★ 콘솔 warning·error 와 페이지 예외를 남긴다.
  //   "첫 playing 뒤 pause" 처럼 화면이 스스로 멈추는 일은 앱이 콘솔에 흔적을 남긴다.
  const consoleLines = []
  page.on('console', (msg) => {
    const type = msg.type()
    if (type !== 'warning' && type !== 'error') return
    consoleLines.push({ at: Date.now(), type, text: redactBearer(msg.text()) })
  })
  page.on('pageerror', (error) => {
    consoleLines.push({
      at: Date.now(),
      type: 'pageerror',
      text: redactBearer(error.message),
      stack: redactBearer(error.stack ?? ''),
    })
  })

  const endMode = k % 2 === 0 ? 'close' : 'spa'
  const record = {
    viewer: k,
    endMode,
    url: `${env.SITE}/meeting/${env.EDUMEET_MEETING_ID}/live`,
    error: null,
    t0: null,
    endedAt: null,
    truth: null,
    reports,
    requestFailures: failures,
    scheduleApplied: [],
    snapshots: [],
    latencySamples: [],
    finalSent: false,
    finalStatus: null,
    dialogs,
    console: consoleLines,
    finalState: null,
    lastUrl: null,
  }
  const captureUrl = () => {
    try {
      record.lastUrl = page.url()
    } catch {
      // 이미 닫힌 페이지에서는 못 읽는다. 마지막으로 읽은 값을 남긴다.
    }
  }

  // ★ 끝나기 직전 화면 상태를 남긴다.
  //   "첫 playing 뒤 pause" 처럼 재생이 멈춘 채 끝나면 그 사실이 값으로 남아야 한다.
  //
  //   버퍼·시크 가능 구간은 **전체 구간**을 남긴다. 끝만 보면 "어디까지 받아 뒀나" 를 못 본다 -
  //   35초 offline 뒤 "멈춘 위치 124초 · 버퍼 끝 158초" 같은 상태를 보려면 구간이 필요하다. (#210)
  const readState = async () => {
    try {
      return await page.evaluate(() => {
        const overlay = document.querySelector('.watch__overlay')
        const v = document.querySelector('video')
        const ranges = (timeRanges) => {
          const out = []
          if (!timeRanges) return out
          for (let i = 0; i < timeRanges.length; i += 1) {
            out.push([
              Math.round(timeRanges.start(i) * 100) / 100,
              Math.round(timeRanges.end(i) * 100) / 100,
            ])
          }
          return out
        }
        const buffered = v ? ranges(v.buffered) : []
        return {
          hadVideo: Boolean(v),
          overlayText: overlay ? overlay.textContent.trim() : null,
          paused: v ? v.paused : null,
          ended: v ? v.ended : null,
          readyState: v ? v.readyState : null,
          networkState: v ? v.networkState : null,
          errorCode: v && v.error ? v.error.code : null,
          currentTime: v ? v.currentTime : null,
          buffered,
          bufferedEnd: buffered.length > 0 ? buffered[buffered.length - 1][1] : null,
          seekable: v ? ranges(v.seekable) : [],
          // 앱이 남긴 hls.js 진단 로그. 없으면 빈 배열이다(네이티브 재생 등).
          hlsLog: Array.isArray(window.__edumeetHlsLog) ? window.__edumeetHlsLog : [],
          // 어느 경로로 재생했나 - hlsjs | native | unsupported. (#217)
          playbackPath: window.__edumeetPlaybackPath ?? null,
        }
      })
    } catch (error) {
      return { error: error.message }
    }
  }

  const captureFinalState = async () => {
    record.finalState = await readState()
  }

  const timers = []
  let latencyTimer = null

  /** offline 구간이 끝난 순간과 그 10초 뒤의 상태를 남긴다. (#210) */
  const snapshotLater = (atMs, label) => {
    timers.push(setTimeout(() => {
      void readState().then((state) => {
        record.snapshots.push({ at: Date.now(), label, state })
      })
    }, atMs))
  }
  try {
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')

    await page.goto(record.url, { waitUntil: 'domcontentloaded', timeout: 60_000 })
    record.t0 = Date.now()
    latencyTimer = setInterval(() => {
      void page.evaluate(() => window.__edumeetPlayingDate?.() ?? null)
        .then((playingAt) => {
          if (!Number.isFinite(playingAt)) return
          const at = Date.now()
          record.latencySamples.push({ at, latencyMs: at - playingAt })
        })
        .catch(() => {})
    }, 1000)

    // 스로틀 일정을 시청자마다 적용한다. 경계 시각을 남긴다.
    for (const seg of schedule) {
      const apply = async () => {
        await applyLimit(cdp, seg.limit)
        record.scheduleApplied.push({ ...seg, at: Date.now() })
      }
      if (seg.from <= 0) await apply()
      else timers.push(setTimeout(() => { void apply() }, seg.from * 1000))
    }

    // ★ offline 이 끝나는 순간(복구 직후)과 그 10초 뒤. (#210)
    //   플레이어가 복구되는지, 아니면 죽은 채 남는지가 이 두 스냅샷의 차이로 보인다.
    //   apply 타이머 뒤에 등록하므로 같은 시각에 걸려도 복구 적용 다음에 읽는다.
    for (const seg of schedule) {
      if (seg.limit !== 'offline') continue
      snapshotLater(seg.to * 1000, `offline-end@${seg.to}s`)
      snapshotLater((seg.to + 10) * 1000, `offline-end+10s@${seg.to + 10}s`)
    }

    await sleep(totalMs)

    await page.evaluate(() => window.__qoeTruthFinalize && window.__qoeTruthFinalize())
    record.truth = await page.evaluate(() => window.__qoeTruth ?? null)

    if (endMode === 'spa') {
      // SPA 안에서 화면을 떠난다 - onBeforeUnmount 가 finalFlush 를 부른다.
      // (page.goto 는 문서를 새로 열어 pagehide 경로가 되어 버린다)
      await page.evaluate(() => {
        window.history.pushState({}, '', '/')
        window.dispatchEvent(new PopStateEvent('popstate'))
      })
      await sleep(2500)
    }
    // 화면이 어떤 상태로 끝나는지 먼저 찍고 닫는다.
    await captureFinalState()
    captureUrl()
    await page.close()
    // 탭 종료 중에 나간 마지막 보고가 관측될 시간을 준다.
    await sleep(2500)
  } catch (error) {
    record.error = error.message
    await captureFinalState()
    captureUrl()
  } finally {
    if (latencyTimer) clearInterval(latencyTimer)
    for (const timer of timers) clearTimeout(timer)
  }

  const finalReport = reports.find((r) => r.final)
  record.finalSent = Boolean(finalReport)
  record.finalStatus = finalReport ? finalReport.status : null

  await context.close().catch(() => {})
  record.endedAt = Date.now()
  writeFileSync(join(dir, `viewer-${k}.json`), `${JSON.stringify(record, null, 2)}\n`)
  return record
}

const load = []
const loadTimer = setInterval(() => {
  try {
    load.push({ t: Date.now(), cpuPct: sampleTreeCpuPct() })
  } catch {
    // ps 가 잠깐 실패할 수 있다. 표본 하나를 건너뛰는 것은 문제가 아니다.
  }
}, 1000)

// 시청 화면이 뜨려면 앱의 로그인 가드를 통과해야 한다. 사용자 정보를 먼저 받아 둔다.
const user = await fetchCurrentUser()
console.log('로그인 사용자 확인됨 (email·nickname·토큰은 찍지 않는다)')

const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] })
try {
  await Promise.all(Array.from({ length: viewerCount }, (_v, k) => runViewer(browser, k, user)))
} finally {
  clearInterval(loadTimer)
  await browser.close()
  writeFileSync(join(dir, 'load.json'), `${JSON.stringify({ samples: load }, null, 2)}\n`)
}
