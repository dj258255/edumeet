#!/usr/bin/env node
/**
 * ①②③ 대조. (#197 대조 하네스)
 *
 * ★ 워밍업(첫 60초)은 대조에서 뺀다.
 *   보고 경계가 30초라 구간을 정확히 못 자른다. 그래서 대조는 세션 전체 합으로 하고,
 *   워밍업 구간의 정답 끊김 초를 따로 적는다.
 *
 * ★ ①≠② 면 앱의 계측이 틀렸고, ②≠③ 이면 전송·검증에서 빠졌다.
 *
 * 사용:
 *   node compare.mjs --run <이름>
 *   node compare.mjs --dir <경로>      # out/ 밖 임시 폴더로 자기 시험할 때
 */
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { outDir, parseArgs } from './lib/env.mjs'

const WARMUP_MS = 60_000

const args = parseArgs(process.argv.slice(2))
const run = args.run
if (!run && !args.dir) {
  throw new Error('--run <이름> 또는 --dir <경로> 가 필요하다')
}
// --dir 는 자기 시험이 out/ 밖 임시 폴더를 가리킬 때만 쓴다.
// 실제 회차 폴더(perf/browser/out/<run>)는 어떤 경우에도 지우지 않는다.
const dir = args.dir ? resolve(args.dir) : outDir(run)
mkdirSync(dir, { recursive: true })

function readJson(path, fallback = null) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch {
    return fallback
  }
}

const sum = (arr) => arr.reduce((a, b) => a + b, 0)
const sec = (ms) => Math.round((ms / 1000) * 100) / 100
const duration = (range) => Math.max(0, range.end - range.start)

function percentile(values, p) {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const index = (sorted.length - 1) * p
  const lower = Math.floor(index)
  const upper = Math.ceil(index)
  if (lower === upper) return sorted[lower]
  return Math.round(sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower))
}

/** 화면 지연 표본은 시청자 벽시계 기준으로 워밍업 60초를 뺀다. */
function screenLatencyStats(samples, t0) {
  const boundary = Number.isFinite(t0) ? t0 + WARMUP_MS : Infinity
  const values = (samples ?? [])
    .filter((sample) => Number.isFinite(sample?.at) && sample.at >= boundary)
    .map((sample) => Number(sample.latencyMs))
    .filter(Number.isFinite)
  return {
    values,
    count: values.length,
    p50: percentile(values, 0.5),
    p95: percentile(values, 0.95),
  }
}

/**
 * #212 재전송은 서버가 (sessionId, seq) 하나로 한 번만 받는다.
 * 전송 시도 수는 원본 그대로 보존하고, 계측 합계에는 첫 보고만 쓴다.
 */
function dedupeReports(reports) {
  const seen = new Set()
  const unique = []
  let retransmissions = 0
  for (const report of reports) {
    const key = report?.sessionId && report.seq != null
      ? `${report.sessionId}|${report.seq}`
      : null
    if (key !== null && seen.has(key)) {
      retransmissions += 1
      continue
    }
    if (key !== null) seen.add(key)
    unique.push(report)
  }
  return { unique, retransmissions }
}

/**
 * 워밍업(첫 60초) 정답 끊김.
 *
 * 경계는 시청자의 스로틀 일정 기준 시각(t0Wall)이다 - 비디오가 붙은 시각이 아니다.
 * 페이지 로딩 중 비디오가 먼저 붙으면 실제 워밍업 경계가 일정보다 앞당겨진다.
 * truth 의 구간은 performance.now() 라서, 벽시계 대응(wallAtAttach)으로 경계를 옮긴다.
 */
function warmupStallSec(truth, t0Wall) {
  if (!truth?.videoAttachedAt || !truth?.wallAtAttach || t0Wall == null) return 0
  const perfToWall = truth.wallAtAttach - truth.videoAttachedAt
  const boundaryPerf = t0Wall + WARMUP_MS - perfToWall
  const overlap = (truth.stallsEvent ?? []).reduce(
    (acc, range) => acc + Math.max(0, Math.min(range.end, boundaryPerf) - range.start),
    0,
  )
  return sec(overlap)
}

const server = readJson(join(dir, 'server.json'), null)
const broadcast = readJson(join(dir, 'broadcast.json'), null)
let viewerFiles = []
try {
  viewerFiles = readdirSync(dir).filter((f) => /^viewer-\d+\.json$/.test(f)).sort()
} catch {
  viewerFiles = []
}

const rows = viewerFiles.map((file) => {
  const v = readJson(join(dir, file))
  const { unique: uniqueReports, retransmissions } = dedupeReports(v.reports ?? [])
  const scheduleEnds = (v.scheduleApplied ?? [])
    .map((segment) => Number(segment.to))
    .filter(Number.isFinite)
  const scheduleEndS = scheduleEnds.length > 0 ? Math.max(...scheduleEnds) : null
  const scheduledEndAt = Number.isFinite(v.t0) && scheduleEndS !== null
    ? v.t0 + scheduleEndS * 1000
    : null

  // 한 시청자의 보고에 세션이 여럿일 수 있다. 전부 모아서 합친다.
  const sessionIds = [...new Set(v.reports.map((r) => r.sessionId).filter(Boolean))]
  const lokiSessions = sessionIds
    .map((id) => server?.loki?.bySession?.[id])
    .filter(Boolean)

  // (sessionId, seq) 로 중복을 제거한다. seq 만 보면 세션이 섞였을 때 잘못 센다.
  const sentKeys = new Set(
    v.reports
      .filter((r) => r.sessionId && r.seq != null)
      .map((r) => `${r.sessionId}|${r.seq}`),
  )
  const receivedKeys = new Set()
  for (const id of sessionIds) {
    for (const q of server?.loki?.bySession?.[id]?.seqs ?? []) receivedKeys.add(`${id}|${q}`)
  }
  const missing = [...sentKeys].filter((k) => !receivedKeys.has(k))

  const firstStartup = v.reports.find((r) => r.startupMs != null)?.startupMs ?? null
  const finalReport = v.reports.find((r) => r.final)
  const screenLatency = screenLatencyStats(v.latencySamples, v.t0)

  // ★ 플레이어가 살아 있는가. (#210) 앱이 남긴 hls.js 진단 로그와 복구 뒤 스냅샷에서 읽는다.
  const hlsLog = v.finalState?.hlsLog ?? []
  const fatals = hlsLog.filter((entry) => entry && entry.fatal === true)
  const lastFatal = fatals.length > 0 ? fatals[fatals.length - 1] : null

  const offlineSnapshots = (v.snapshots ?? []).filter((s) =>
    String(s.label ?? '').startsWith('offline-end'))
  const lastOf = (list, matches) => {
    const hits = list.filter(matches)
    return hits.length > 0 ? hits[hits.length - 1] : null
  }
  const afterRecovery = lastOf(offlineSnapshots, (s) => String(s.label).includes('+10s'))
  const recoveryState = afterRecovery?.state ?? null

  return {
    viewer: v.viewer,
    endMode: v.endMode,
    error: v.error,
    t0: v.t0 ?? null,
    endedAt: v.endedAt ?? null,
    scheduleEndS,
    scheduledEndAt,
    screenLatencyValues: screenLatency.values,
    screenLatencySampleCount: screenLatency.count,
    screenLatencyP50Ms: screenLatency.p50,
    screenLatencyP95Ms: screenLatency.p95,
    sessionIds,
    truthEventSec: sec(sum((v.truth?.stallsEvent ?? []).map(duration))),
    truthEventCount: (v.truth?.stallsEvent ?? []).length,
    truthProgressSec: sec(sum((v.truth?.stallsProgress ?? []).map(duration))),
    discontinuities: (v.truth?.discontinuities ?? []).length,
    warmupTruthEventSec: warmupStallSec(v.truth, v.t0),
    sentStallSec: sec(sum(uniqueReports.map((r) => r.stallMs ?? 0))),
    sentStallCount: sum(uniqueReports.map((r) => r.stallCount ?? 0)),
    receivedStallSec: lokiSessions.length ? sec(sum(lokiSessions.map((s) => s.stallMs))) : null,
    receivedStallCount: lokiSessions.length ? sum(lokiSessions.map((s) => s.stallCount)) : null,
    sentReports: uniqueReports.length,
    transmissionAttempts: (v.reports ?? []).length,
    retransmissions,
    receivedLines: sum(lokiSessions.map((s) => s.lines)),
    missingReports: missing.length,
    startupTruthMs: v.truth?.startupMs ?? null,
    startupSentMs: firstStartup,
    finalSent: v.finalSent,
    finalStatus: v.finalStatus,
    finalReceived: lokiSessions.some((s) => s.finals > 0),
    finalReceivedVia: finalReport ? '보고 본문' : null,
    lastUrl: v.lastUrl ?? null,
    dialogs: v.dialogs ?? [],
    // 플레이어 생존 (#210)
    errorCode: v.finalState?.errorCode ?? null,
    playbackPath: v.finalState?.playbackPath ?? null,
    playerGaveUp: hlsLog.some((entry) => entry && entry.action === 'gaveUp'),
    lastFatalDetails: lastFatal?.details ?? null,
    lastFatalType: lastFatal?.type ?? null,
    lastFatalAction: lastFatal?.action ?? null,
    lastFatalResponseCode: lastFatal?.responseCode ?? null,
    hlsLogLines: hlsLog.length,
    stoppedAtSec: v.finalState?.currentTime ?? null,
    bufferedEndSec: v.finalState?.bufferedEnd ?? null,
    afterRecoveryPaused: recoveryState?.paused ?? null,
    afterRecoveryCurrentTimeSec: recoveryState?.currentTime ?? null,
    resumedAfterRecovery: recoveryState
      ? recoveryState.paused === false && recoveryState.ended === false
      : null,
  }
})

const broadcastEndedAt = broadcast?.endedAt ? Date.parse(broadcast.endedAt) : NaN
const earlyBroadcastRows = Number.isFinite(broadcastEndedAt)
  ? rows.filter((row) => Number.isFinite(row.scheduledEndAt) && broadcastEndedAt < row.scheduledEndAt)
  : []

const totals = {
  truthEventSec: sec(sum(rows.map((r) => r.truthEventSec * 1000))),
  truthProgressSec: sec(sum(rows.map((r) => r.truthProgressSec * 1000))),
  sentStallSec: sec(sum(rows.map((r) => r.sentStallSec * 1000))),
  receivedStallSec: sec(sum(rows.map((r) => (r.receivedStallSec ?? 0) * 1000))),
  sentReports: sum(rows.map((r) => r.sentReports)),
  transmissionAttempts: sum(rows.map((r) => r.transmissionAttempts)),
  retransmissions: sum(rows.map((r) => r.retransmissions)),
  receivedLines: sum(rows.map((r) => r.receivedLines)),
  missingReports: sum(rows.map((r) => r.missingReports)),
  discontinuities: sum(rows.map((r) => r.discontinuities)),
  rejectedPrometheus: Number(server?.prom?.rejected ?? 0),
  broadcastRejected429: broadcast?.chunksRejected ?? null,
  prometheusNote: server?.note ?? null,
}
const allScreenLatencyValues = rows.flatMap((row) => row.screenLatencyValues)
totals.screenLatencySampleCount = allScreenLatencyValues.length
totals.screenLatencyP50Ms = percentile(allScreenLatencyValues, 0.5)
totals.screenLatencyP95Ms = percentile(allScreenLatencyValues, 0.95)
const pct = (received, sent) =>
  sent > 0 ? `${Math.round(((received - sent) / sent) * 1000) / 10}%` : '-'
totals.sentVsReceivedDiffSec = sec((totals.receivedStallSec - totals.sentStallSec) * 1000)
totals.sentVsReceivedDiffPct = pct(totals.receivedStallSec, totals.sentStallSec)
totals.truthVsSentDiffSec = sec((totals.sentStallSec - totals.truthEventSec) * 1000)

// ★ 조용한 0 을 막는다.
//   시청 화면이 아예 안 떴으면(로그인 가드에 걸려 /login 으로 튕기는 등) 정답도 보고도 0 이다.
//   그 0 을 "끊김이 없었다" 로 읽으면 안 된다. 조건이 성립하지 않은 회차다.
//
//   산출물이 아예 없어도 마찬가지다. 실제로 out/<run> 에 compare.json/md 만 남은 상태에서
//   viewer 파일이 0개인데 표가 0행으로 나오고 exit 0 을 냈다.
const failedViewers = rows.filter((r) => r.startupTruthMs == null)

const reasons = []
if (viewerFiles.length === 0) {
  reasons.push(`viewer-*.json 이 0개다 (${dir})`)
}
if (!broadcast) {
  reasons.push('broadcast.json 이 없다 - 합성 방송의 조각·시각을 못 읽었다')
}
if (!server) {
  reasons.push('server.json 이 없다 - 서버 쪽(Loki·Prometheus) 수집을 못 읽었다')
}
if (failedViewers.length > 0) {
  reasons.push(
    `영상이 한 번도 재생되지 않은 시청자가 ${failedViewers.length}/${rows.length}명이다 (startupMs 없음)`,
  )
}
if (earlyBroadcastRows.length > 0) {
  reasons.push(
    '측정 조건 불성립 - 시청 도중 방송이 끝났다: ' +
      earlyBroadcastRows.map((row) => `시청자 ${row.viewer}`).join(', '),
  )
}
const conditionFailed = reasons.length > 0

const warning = conditionFailed
  ? [
      '# ⚠⚠⚠ 측정 조건 불성립 ⚠⚠⚠',
      '',
      '**이 회차의 값으로는 아무것도 판단할 수 없다. 조건을 먼저 고친다.**',
      '',
      ...reasons.map((reason) => `- ${reason}`),
      '',
      ...(failedViewers.length > 0
        ? [
            '| 시청자 | 마지막 URL | dialog | 보고 | 오류 |',
            '|---|---|---|---|---|',
            ...failedViewers.map(
              (r) =>
                `| ${r.viewer} | ${r.lastUrl ?? '-'} | ${(r.dialogs ?? []).length}건 | ` +
                `${r.sentReports}건 | ${r.error ?? '-'} |`,
            ),
            '',
            ...failedViewers.flatMap((r) =>
              (r.dialogs ?? []).map((d) => `- 시청자 ${r.viewer} ${d.type}: ${d.message}`),
            ),
            '',
          ]
        : []),
    ]
  : []

const header =
  '| 시청자 | 정답(이벤트) 끊김 초·횟수 | 정답(진행) 끊김 초 | 보낸 값 끊김 초·횟수 | ' +
  '받은 값(Loki) 끊김 초·횟수 | final 보냄 | final 받음 | 종료 방식 |'
const divider = '|---|---|---|---|---|---|---|---|'
const lines = rows.map(
  (r) =>
    `| ${r.viewer} | ${r.truthEventSec}초·${r.truthEventCount} | ${r.truthProgressSec}초 | ` +
    `${r.sentStallSec}초·${r.sentStallCount} | ` +
    `${r.receivedStallSec ?? '-'}초·${r.receivedStallCount ?? '-'} | ` +
    `${r.finalSent ? 'O' : 'X'} | ${r.finalReceived ? 'O' : 'X'} | ${r.endMode} |`,
)

/** 플레이어 생존 절에서 쓴다. (#210) */
const died = rows.filter((r) => r.errorCode !== null || r.playerGaveUp)
const resumedCount = rows.filter((r) => r.resumedAfterRecovery === true)
const secText = (value) => (typeof value === 'number' ? `${Math.round(value * 10) / 10}초` : '-')
const tri = (value) => (value === null || value === undefined ? '-' : value ? 'O' : 'X')

/** 경로 분포 한 줄. 이 회차가 hls.js 를 탔는지 네이티브로 갔는지. (#217) */
const pathCounts = rows.reduce((acc, r) => {
  const key = r.playbackPath ?? '없음'
  acc[key] = (acc[key] ?? 0) + 1
  return acc
}, {})
const pathSummary = Object.entries(pathCounts).map(([key, n]) => `${key} ${n}`).join(' · ')

const md = [
  // 조건 불성립이면 표보다 먼저, 크게 보여 준다.
  ...warning,
  `# 시청 품질 대조 — ${run ?? dir}`,
  '',
  header,
  divider,
  ...lines,
  '',
  '## 합계',
  '',
  `- 정답(이벤트) 끊김: ${totals.truthEventSec}초 · 정답(진행) 끊김: ${totals.truthProgressSec}초`,
  `- 보낸 값 끊김: ${totals.sentStallSec}초 (중복 제거 보고 ${totals.sentReports}건)`,
  `- 전송 시도 ${totals.transmissionAttempts}건(재전송 ${totals.retransmissions}건)`,
  `- 받은 값 끊김: ${totals.receivedStallSec}초 (Loki ${totals.receivedLines}줄)`,
  `- 보낸 값 ↔ 받은 값 차이: ${totals.sentVsReceivedDiffSec}초 (${totals.sentVsReceivedDiffPct})`,
  `- 정답 ↔ 보낸 값 차이: ${totals.truthVsSentDiffSec}초`,
  `- 진행 기준 불연속(뒤로 이동) 횟수: ${totals.discontinuities} (멈춤과 따로 셈)`,
  `- rejected: Prometheus ${totals.rejectedPrometheus} · 합성 방송 429 ${totals.broadcastRejected429 ?? '-'}`,
  `- 받지 못한 보고((sessionId, seq) 쌍 중 Loki 에 없는 것): ${totals.missingReports}건`,
  ...(totals.prometheusNote ? ['', `> ${totals.prometheusNote}`] : []),
  '',
  '## 워밍업(첫 60초) 정답 끊김',
  '',
  ...rows.map((r) => `- 시청자 ${r.viewer}: ${r.warmupTruthEventSec}초`),
  '',
  '## 화면 지연 (워밍업 60초 제외)',
  '',
  '| 시청자 | 표본 수 | p50(ms) | p95(ms) |',
  '|---|---:|---:|---:|',
  ...rows.map((r) =>
    `| ${r.viewer} | ${r.screenLatencySampleCount} | ${r.screenLatencyP50Ms ?? '-'} | ${r.screenLatencyP95Ms ?? '-'} |`,
  ),
  '',
  `- 전체: 표본 ${totals.screenLatencySampleCount}개 · p50 ${totals.screenLatencyP50Ms ?? '-'}ms · p95 ${totals.screenLatencyP95Ms ?? '-'}ms`,
  '',
  '## 첫 화면 시간',
  '',
  '| 시청자 | 정답(ms) | 보낸 값(ms) |',
  '|---|---|---|',
  ...rows.map((r) => `| ${r.viewer} | ${r.startupTruthMs ?? '-'} | ${r.startupSentMs ?? '-'} |`),
  '',
  '## 플레이어 생존',
  '',
  `- error(MediaError) 로 끝난 시청자: ${died.length}/${rows.length}${
    died.length > 0
      ? ' · ' + died.map((r) => `${r.viewer}(code ${r.errorCode ?? '-'}${r.playerGaveUp ? ', gaveUp' : ''})`).join(' · ')
      : ''
  }`,
  `- 복구 10초 뒤 재생이 재개된 시청자: ${resumedCount.length}/${rows.length}`,
  `- 재생 경로: ${pathSummary}`,
  '',
  '| 시청자 | 경로 | errorCode | 마지막 fatal | 조치 | hls 로그 | 멈춘 위치 | 버퍼 끝 | 복구 10초 뒤 | 재생 재개 |',
  '|---|---|---|---|---|---|---|---|---|---|',
  ...rows.map(
    (r) =>
      `| ${r.viewer} | ${r.playbackPath ?? '-'} | ${r.errorCode ?? '-'} | ${r.lastFatalDetails ?? '-'} | ${r.lastFatalAction ?? '-'} | ` +
      `${r.hlsLogLines}줄 | ${secText(r.stoppedAtSec)} | ${secText(r.bufferedEndSec)} | ` +
      `${r.afterRecoveryPaused === null ? '-' : `paused=${r.afterRecoveryPaused}`} | ${tri(r.resumedAfterRecovery)} |`,
  ),
  '',
].join('\n')

writeFileSync(
  join(dir, 'compare.json'),
  `${JSON.stringify(
    {
      run,
      conditionFailed,
      reasons,
      failedViewers: failedViewers.map((r) => r.viewer),
      rows,
      totals,
    },
    null,
    2,
  )}\n`,
)
writeFileSync(join(dir, 'compare.md'), md)
console.log(md)

// 종료 코드로도 알린다. 진입점이 이 값을 그대로 돌려준다.
// 조건이 성립하지 않은 회차를 "성공" 으로 넘기면 다음 단계가 그 0 을 근거로 삼는다.
if (conditionFailed) {
  console.error(`측정 조건 불성립 (exit 2): ${reasons.join(' / ')}`)
  process.exit(2)
}
