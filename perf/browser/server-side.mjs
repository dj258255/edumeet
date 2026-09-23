#!/usr/bin/env node
/**
 * ③ 받은 값 수집. (#197 대조 하네스)
 *
 * ★ ssh 로 운영 서버에서 조회한다.
 *   명령은 로컬에서 문자열로 만들어 인자로 넘긴다. heredoc 을 쓰지 않는다 -
 *   원격에 heredoc 을 보내면 앞 명령이 stdin 을 먹는다.
 *
 * ★ Prometheus 는 sum 으로 합친다.
 *   슬롯(앱 인스턴스)이 여럿이면 그대로는 슬롯별 값이 나온다. (#184)
 *
 * ★ Loki 라벨은 Alloy 설정을 보고 맞춘다.
 *   observability/alloy/config.alloy 는 컨테이너 이름을 container 라벨로 올린다
 *   (edumeet-app-blue / edumeet-app-green). 회의 번호·세션은 라벨이 아니라 JSON 필드다.
 *   그래서 {container=~"edumeet-app.*"} 로 스트림을 고르고 JSON 으로 푼다.
 *
 * 사용:
 *   node server-side.mjs --run <이름> [--from <epoch초>] [--to <epoch초>]
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { loadEnv, outDir, parseArgs } from './lib/env.mjs'
import { collectOrigin } from './lib/nginx-log.mjs'

const METRICS = [
  ['stallSeconds', 'playback_stall_seconds_total'],
  ['playingSeconds', 'playback_playing_seconds_total'],
  ['stallCount', 'playback_stall_count_total'],
  ['reports', 'playback_qoe_reports_total'],
  ['rejected', 'playback_qoe_rejected_total'],
]

const LOG_MESSAGE = '시청 품질 보고'
const LOKI_LABEL = '{container=~"edumeet-app.*"}'

// ★ 운영 nginx 접근 로그는 lib/nginx-log.mjs 가 읽는다 (#235).
//   경로(/var/log/nginx/access.log) · 회전 파일 형식(access.log-YYYYMMDD(.gz)) ·
//   실패 처리(못 읽으면 error, 0 으로 위장하지 않음)는 그쪽 주석에 있다.
//   여기서는 ssh 실행기만 주입한다 - 그래야 그 규칙을 운영 없이 시험할 수 있다.

/** HTTP 서버 지연. Spring Boot 의 http_server_requests_seconds 히스토그램을 창으로 자른다. */
const HTTP_BUCKET = 'http_server_requests_seconds_bucket'
/** 방송 전 대기 화면이 3초마다 때리는 조회. 템플릿 uri 로 좁힌다. */
const MEETING_URI = '/api/v1/meeting/{meetingId}'

const args = parseArgs(process.argv.slice(2))
const env = loadEnv()
const run = args.run
const dir = outDir(run)
mkdirSync(dir, { recursive: true })

function broadcastWindow() {
  try {
    const data = JSON.parse(readFileSync(join(dir, 'broadcast.json'), 'utf8'))
    return {
      from: Math.floor(Date.parse(data.startedAt) / 1000),
      to: Math.floor(Date.parse(data.endedAt) / 1000),
    }
  } catch {
    return null
  }
}

const VIEWER_FROM_MARGIN_MS = 60_000
const DRAIN_MS = 30_000

function wallMs(value) {
  if (Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const parsed = Date.parse(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

/** 새 원격·로컬 회차는 시청자 산출물의 벽시계로 조회 창을 잡는다. */
function viewerWindow() {
  let files
  try {
    files = readdirSync(dir).filter((file) => /^viewer-\d+\.json$/.test(file))
  } catch {
    return null
  }
  const records = files.map((file) => {
    try {
      return JSON.parse(readFileSync(join(dir, file), 'utf8'))
    } catch {
      return null
    }
  }).filter(Boolean)
  const starts = records.map((record) => wallMs(record.t0)).filter((value) => value !== null)
  const ends = records.map((record) => wallMs(record.endedAt)).filter((value) => value !== null)
  if (starts.length === 0 || ends.length === 0) return null

  return {
    from: Math.floor((Math.min(...starts) - VIEWER_FROM_MARGIN_MS) / 1000),
    to: Math.ceil((Math.max(...ends) + DRAIN_MS) / 1000),
  }
}

const fallback = broadcastWindow()
const byViewer = viewerWindow()
const from = Number(args.from ?? byViewer?.from ?? fallback?.from)
const to = Number(args.to ?? byViewer?.to ?? fallback?.to)
const windowSource = args.from !== undefined || args.to !== undefined
  ? 'argument'
  : byViewer
    ? 'viewer-wall-clock'
    : 'broadcast'
if (!Number.isFinite(from) || !Number.isFinite(to)) {
  throw new Error('측정 창을 모른다. --from/--to 를 주거나 broadcast.json 이 있어야 한다')
}

// ★ 측정 창 [from, to] 만 본다. to 를 실제로 쓴다 -
//   지금(now)까지 보면 drain 구간과 다른 실행의 트래픽이 섞인다.
// ★ 창 규칙: 초 단위 내림, 양끝 포함 [from, to].
//   로그는 초 해상도이고 Prometheus 는 increase(m[to-from+1]) 를 time=to 에서 평가해
//   같은 구간 (to-window, to] = [from, to] 가 된다 - 두 수를 바로 맞출 수 있다.
//   창의 양끝은 시청자 벽시계에서 floor/ceil 하므로 최대 1초의 여유가 있다(결과에 적는다).
const windowSeconds = Math.max(1, to - from + 1)
const meetingId = String(env.EDUMEET_MEETING_ID)

// Prometheus 지표에는 meetingId·세션 라벨이 없다(#197 설계 - 카디널리티).
// 그래서 합계는 같은 창의 다른 방송·시청자까지 섞일 수 있다. 산출물에 그대로 적는다.
const PROMETHEUS_NOTE =
  'Prometheus 지표에는 meetingId·세션 라벨이 없다. 같은 창에 다른 방송·시청자의 보고가 있으면 ' +
  '합계에 섞인다. 세션 단위 분해는 Loki 의 meetingId 필터로 한다.'

function ssh(remoteCommand) {
  return execFileSync('ssh', [env.SSH_HOST, remoteCommand], {
    encoding: 'utf8',
    timeout: 60_000,
    maxBuffer: 64 * 1024 * 1024,
  })
}

/**
 * Prometheus 스칼라. **빈 결과는 null 이다 - 0 이 아니다.**
 *
 *   "지표가 없다"(스크레이프 실패·창에 표본 없음)와 "값이 0 이다"(아무 일도 없었다)는 다른 사실이다.
 *   0 으로 위장하면 대조가 "유실 0" 이라고 말한다.
 */
function promQuery(metric) {
  // increase(m[to-from+1]) 를 time=to 에서 평가한다 →
  // (to-window, to] = [from, to] 로, 로그의 양끝 포함 규칙과 같은 구간이 된다.
  return promScalar(`sum(increase(${metric}[${windowSeconds}s]))`)
}

function promScalar(expression) {
  const query = encodeURIComponent(expression)
  const remote =
    `docker exec edumeet-prometheus wget -qO- 'http://localhost:9090/api/v1/query` +
    `?query=${query}&time=${to}'`
  let parsed
  try {
    parsed = JSON.parse(ssh(remote))
  } catch (error) {
    console.warn(`[prom] 질의 실패(다른 질의는 계속한다): ${expression} — ${error.message}`)
    return null
  }
  if (parsed.status !== 'success') return null
  const result = parsed.data.result
  if (result.length === 0) return null
  const value = Number(result[0].value[1])
  return Number.isFinite(value) ? value : null
}

/** 측정 창의 REST 지연. 창을 초 단위로 잘라 increase 로 센다. */
function restLatency() {
  const quantile = (q, selector = '') =>
    promScalar(
      `histogram_quantile(${q}, sum by (le) (increase(${HTTP_BUCKET}${selector}[${windowSeconds}s])))`,
    )
  const meeting = (q) => quantile(q, `{uri="${MEETING_URI}"}`)
  // ★ histogram_quantile 은 **초**를 준다. 이름이 ms 이므로 여기서 환산한다 -
  //   안 하면 120ms 가 0.12ms 로 보고된다.
  const toMs = (seconds) => (seconds === null || !Number.isFinite(seconds)
    ? null
    : Math.round(seconds * 1000))
  return {
    windowSeconds,
    p50Ms: toMs(quantile(0.5)),
    p95Ms: toMs(quantile(0.95)),
    p99Ms: toMs(quantile(0.99)),
    // 대기 화면의 폴링이 REST 지연에 남긴 자국. uri 템플릿이 다르면 null 이 된다.
    meetingP99Ms: toMs(meeting(0.99)),
    meetingCount: promScalar(
      `sum(increase(${HTTP_BUCKET.replace('_bucket', '_count')}{uri="${MEETING_URI}"}[${windowSeconds}s]))`,
    ),
    metric: HTTP_BUCKET,
    unit: 'ms',
    note: 'Prometheus 지표에는 meetingId 라벨이 없다. 같은 창의 다른 요청이 섞인다. 값이 없으면 null 이다.',
  }
}

function lokiQuery() {
  // meetingId 는 라벨이 아니라 JSON 필드다(Alloy 는 level 만 라벨로 올린다).
  // 그래서 | json 뒤에 필터로 건다 - 같은 시간대의 다른 방송 보고를 뺀다.
  const query = encodeURIComponent(
    `${LOKI_LABEL} | json | meetingId="${meetingId}" | message="${LOG_MESSAGE}"`,
  )
  const startNs = from * 1_000_000_000
  const endNs = to * 1_000_000_000
  const remote =
    `docker exec edumeet-loki wget -qO- 'http://localhost:3100/loki/api/v1/query_range` +
    `?query=${query}&start=${startNs}&end=${endNs}&limit=5000&direction=forward'`
  return JSON.parse(ssh(remote))
}

const prom = {}
for (const [key, metric] of METRICS) {
  prom[key] = promQuery(metric)
}

const lokiData = lokiQuery()
const loki = { label: LOKI_LABEL, message: LOG_MESSAGE, lines: 0, bySession: {} }
for (const stream of lokiData.data?.result ?? []) {
  for (const [ns, line] of stream.values ?? []) {
    let parsed
    try {
      parsed = JSON.parse(line)
    } catch {
      continue
    }
    if (parsed.message !== LOG_MESSAGE) continue
    const sessionId = parsed.sessionId ?? '(none)'
    if (!loki.bySession[sessionId]) {
      loki.bySession[sessionId] = {
        lines: 0,
        playingMs: 0,
        stallMs: 0,
        stallCount: 0,
        seqs: [],
        finals: 0,
        meetingIds: [],
      }
    }
    const entry = loki.bySession[sessionId]
    entry.lines += 1
    entry.playingMs += Number(parsed.playingMs ?? 0)
    entry.stallMs += Number(parsed.stallMs ?? 0)
    entry.stallCount += Number(parsed.stallCount ?? 0)
    if (parsed.seq != null) entry.seqs.push(parsed.seq)
    if (parsed.final === true) entry.finals += 1
    if (parsed.meetingId != null && !entry.meetingIds.includes(String(parsed.meetingId))) {
      entry.meetingIds.push(String(parsed.meetingId))
    }
    loki.lines += 1
  }
}

const rest = restLatency()
// 원격 실행기를 주입한다 - 회전 파일·실패 처리는 lib/nginx-log.mjs 가 한다.
const origin = collectOrigin({
  from,
  to,
  runRemote: (command) => {
    try {
      return { stdout: ssh(command) }
    } catch (error) {
      return { error: error.stderr ? String(error.stderr).trim() : error.message }
    }
  },
})

const result = {
  run,
  window: {
    from,
    to,
    windowSeconds,
    source: windowSource,
    rule: '초 단위 내림, 양끝 포함 [from, to] · 로그와 Prometheus 같은 구간 · 양끝 최대 1초 여유',
    collectedAt: new Date().toISOString(),
  },
  lokiLabel: LOKI_LABEL,
  meetingId,
  note: PROMETHEUS_NOTE,
  prom,
  rest,
  origin,
  loki,
}
writeFileSync(join(dir, 'server.json'), `${JSON.stringify(result, null, 2)}\n`)
console.log(
  `server.json: prom=${JSON.stringify(prom)} loki줄=${loki.lines} 세션=${Object.keys(loki.bySession).length}`,
)
console.log(
  `  REST p50/p95/p99=${rest.p50Ms}/${rest.p95Ms}/${rest.p99Ms}ms · ` +
    `원본 /hls/ 요청=${origin.error ? origin.error : origin.hlsRequestsInWindow}건 ` +
    `(${origin.error ? '-' : origin.bytesSentInWindow} 바이트)`,
)
