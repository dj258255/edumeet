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

  return {
    viewer: v.viewer,
    endMode: v.endMode,
    error: v.error,
    sessionIds,
    truthEventSec: sec(sum((v.truth?.stallsEvent ?? []).map(duration))),
    truthEventCount: (v.truth?.stallsEvent ?? []).length,
    truthProgressSec: sec(sum((v.truth?.stallsProgress ?? []).map(duration))),
    discontinuities: (v.truth?.discontinuities ?? []).length,
    warmupTruthEventSec: warmupStallSec(v.truth, v.t0),
    sentStallSec: sec(sum(v.reports.map((r) => r.stallMs ?? 0))),
    sentStallCount: sum(v.reports.map((r) => r.stallCount ?? 0)),
    receivedStallSec: lokiSessions.length ? sec(sum(lokiSessions.map((s) => s.stallMs))) : null,
    receivedStallCount: lokiSessions.length ? sum(lokiSessions.map((s) => s.stallCount)) : null,
    sentReports: v.reports.length,
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
  }
})

const totals = {
  truthEventSec: sec(sum(rows.map((r) => r.truthEventSec * 1000))),
  truthProgressSec: sec(sum(rows.map((r) => r.truthProgressSec * 1000))),
  sentStallSec: sec(sum(rows.map((r) => r.sentStallSec * 1000))),
  receivedStallSec: sec(sum(rows.map((r) => (r.receivedStallSec ?? 0) * 1000))),
  sentReports: sum(rows.map((r) => r.sentReports)),
  receivedLines: sum(rows.map((r) => r.receivedLines)),
  missingReports: sum(rows.map((r) => r.missingReports)),
  discontinuities: sum(rows.map((r) => r.discontinuities)),
  rejectedPrometheus: Number(server?.prom?.rejected ?? 0),
  broadcastRejected429: broadcast?.chunksRejected ?? null,
  prometheusNote: server?.note ?? null,
}
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
  `- 보낸 값 끊김: ${totals.sentStallSec}초 (보고 ${totals.sentReports}건)`,
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
  '## 첫 화면 시간',
  '',
  '| 시청자 | 정답(ms) | 보낸 값(ms) |',
  '|---|---|---|',
  ...rows.map((r) => `| ${r.viewer} | ${r.startupTruthMs ?? '-'} | ${r.startupSentMs ?? '-'} |`),
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
