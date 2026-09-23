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
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { loadEnv, outDir, parseArgs } from './lib/env.mjs'

const METRICS = [
  ['stallSeconds', 'playback_stall_seconds_total'],
  ['playingSeconds', 'playback_playing_seconds_total'],
  ['stallCount', 'playback_stall_count_total'],
  ['reports', 'playback_qoe_reports_total'],
  ['rejected', 'playback_qoe_rejected_total'],
]

const LOG_MESSAGE = '시청 품질 보고'
const LOKI_LABEL = '{container=~"edumeet-app.*"}'

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

const fallback = broadcastWindow()
const from = Number(args.from ?? fallback?.from)
const to = Number(args.to ?? fallback?.to)
if (!Number.isFinite(from) || !Number.isFinite(to)) {
  throw new Error('측정 창을 모른다. --from/--to 를 주거나 broadcast.json 이 있어야 한다')
}

// ★ 측정 창 [from, to] 만 본다. to 를 실제로 쓴다 -
//   지금(now)까지 보면 drain 구간과 다른 실행의 트래픽이 섞인다.
const windowSeconds = Math.max(1, to - from)
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

function promQuery(metric) {
  const query = encodeURIComponent(`sum(increase(${metric}[${windowSeconds}s]))`)
  // time=to 로 평가한다. increase(...[windowSeconds]) 는 [to-window, to] = [from, to] 를 덮는다.
  const remote =
    `docker exec edumeet-prometheus wget -qO- 'http://localhost:9090/api/v1/query` +
    `?query=${query}&time=${to}'`
  const parsed = JSON.parse(ssh(remote))
  if (parsed.status !== 'success') throw new Error(`Prometheus 질의 실패: ${metric}`)
  const result = parsed.data.result
  if (result.length === 0) return 0
  return Number(result[0].value[1])
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

const result = {
  run,
  window: { from, to, windowSeconds, collectedAt: new Date().toISOString() },
  lokiLabel: LOKI_LABEL,
  meetingId,
  note: PROMETHEUS_NOTE,
  prom,
  loki,
}
writeFileSync(join(dir, 'server.json'), `${JSON.stringify(result, null, 2)}\n`)
console.log(`server.json: prom=${JSON.stringify(prom)} loki줄=${loki.lines} 세션=${Object.keys(loki.bySession).length}`)
