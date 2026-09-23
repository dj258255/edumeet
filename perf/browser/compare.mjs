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
 * 따라잡기(#233) 요약.
 *
 *   - 비율: 재생 속도 표본 중 1을 넘은 비율. 표본은 1초 간격이라 시간 비율로 봐도 된다.
 *   - 연쇄 끊김: 앞 끊김이 **끝난 뒤 10초 안에** 다시 시작한 끊김의 수.
 *     Twitch 가 보고한 모양(재생 속도를 올리면 끊김이 연달아 온다)을 이 하네스에서 세는 값이다.
 */
function catchupStats(truth) {
  const samples = truth?.playbackRates ?? []
  const faster = samples.filter((s) => Number(s.rate) > 1.0001)
  const rates = samples.map((s) => Number(s.rate)).filter(Number.isFinite)

  const stalls = [...(truth?.stallsEvent ?? [])].sort((a, b) => a.start - b.start)
  let chained = 0
  for (let i = 1; i < stalls.length; i += 1) {
    if (stalls[i].start - stalls[i - 1].end <= 10_000) chained += 1
  }

  return {
    samples: samples.length,
    fasterShare: samples.length > 0 ? faster.length / samples.length : null,
    maxRate: rates.length > 0 ? Math.max(...rates) : null,
    chained,
    stalls: stalls.length,
  }
}

/**
 * CDN 적중 요약. (#235)
 *
 * ★ HIT 만이 CDN 이 준 것이다. 나머지(MISS·EXPIRED·BYPASS·DYNAMIC·REVALIDATED)와
 *   **헤더 없음**은 원본까지 갔다고 센다 - 헤더가 없으면 CDN 을 안 거친 경로라는 뜻이다.
 *
 * ★ 조각 하나에 대해 "받은 시청자 수" 와 "원본까지 간 시청자 수" 를 같이 낸다.
 *   그 비율이 요청 병합의 증거다 - 5명이 같은 조각을 받았는데 원본 요청이 1건이면 병합된 것이다.
 */
/**
 * 요청한 설정과 실제 적용된 설정을 맞춘다. (#233)
 *
 * ★ 왜 필요한가. #233 그리드 11회차가 원격 경로에서 `--catchup-rate` 가 빠진 채 돌았는데,
 *   요청(준비 로그)과 적용(앱)을 회차마다 맞춰 보지 않아서 11회차를 다 돌고 나서야 알았다.
 *
 *   applied === null 이면 그 시청자에게는 그 설정이 없는 경로다(네이티브 재생 등) - 불일치가 아니다.
 */
/** hls.js 가 안 넘기면 쓰는 값. 프론트 시험에서 `Hls.DefaultConfig` 로 확인했다. (#233) */
const HLS_DEFAULT_MAX_LIVE_SYNC_PLAYBACK_RATE = 1

function configCheck(row) {
  const requested = row.requestedConfig ?? null
  const applied = row.effectiveConfig ?? null
  const path = row.effectivePlaybackPath ?? row.playbackPath ?? null
  const native = path === 'native'
  const mismatches = []

  // ★ 적용값이 없으면 **불일치가 아니다.** 그 경로에는 hls.js 설정이 없다(네이티브) 이거나
  //   앱이 노출하지 않은 것이다. `Number(null) === 0` 같은 비교를 하면 요청 1 ↔ 적용 null 이
  //   불일치로 둔갑한다 - 양방향이 비대칭이 된다.
  if (!applied) {
    return {
      native, exposed: false, mismatches, applied, requested,
      verdict: native ? '적용 불가(네이티브 재생)' : '노출 없음',
    }
  }

  // ★ 요청 null 은 "아무것도 안 넘겼다" 가 아니라 **hls.js 기본값을 기대한다** 는 뜻이다.
  //   적용값이 그 기본값이면 일치로 보되 '기본값 일치' 로 따로 적는다 - 형식이 다른 것을 숨기지 않는다.
  let normalizedDefault = false
  const requestedRate = requested?.maxLiveSyncPlaybackRate
  const appliedRate = Number(applied.maxLiveSyncPlaybackRate)
  if (requestedRate === null || requestedRate === undefined) {
    if (appliedRate === HLS_DEFAULT_MAX_LIVE_SYNC_PLAYBACK_RATE) {
      normalizedDefault = true
    } else {
      mismatches.push(
        `따라잡기 미요청(기본값 ${HLS_DEFAULT_MAX_LIVE_SYNC_PLAYBACK_RATE} 기대) → 적용 ${applied.maxLiveSyncPlaybackRate}`,
      )
    }
  } else if (appliedRate !== Number(requestedRate)) {
    mismatches.push(`따라잡기 요청 ${requestedRate} → 적용 ${applied.maxLiveSyncPlaybackRate}`)
  }

  // ★ 조건부 따라잡기 모드 (#233). 요청하지 않았으면 앱 기본(off)을 기대한다.
  //   adaptive 회차에서 '요청은 adaptive 인데 앱은 off' 이면 그 회차는 조건부를 안 돌린 것이다 -
  //   #233 그리드 11회차가 그렇게 돌았는데(원격에서 --catchup-rate 누락) 로그로는 알 수 없었다.
  const requestedMode = requested?.catchupMode ?? null
  const appliedMode = applied.catchupMode ?? null
  if (appliedMode !== null && appliedMode !== (requestedMode ?? 'off')) {
    mismatches.push(`따라잡기 모드 요청 ${requestedMode ?? '없음(기본 끔)'} → 적용 ${appliedMode}`)
  }

  const requestedCount = requested?.liveSyncDurationCount
  if (requestedCount !== null && requestedCount !== undefined &&
      Number(applied.liveSyncDurationCount) !== Number(requestedCount)) {
    mismatches.push(`liveSyncDurationCount 요청 ${requestedCount} → 적용 ${applied.liveSyncDurationCount}`)
  }

  return {
    native,
    exposed: true,
    mismatches,
    applied,
    requested,
    verdict: mismatches.length > 0
      ? `**불일치 — ${mismatches.join(' / ')}**`
      : normalizedDefault
        ? '기본값 일치'
        : '일치',
  }
}

function cdnStats(rows) {
  const byKind = {}
  const byFile = {}
  let withHeader = 0
  let withoutHeader = 0

  for (const row of rows) {
    for (const entry of row.hls ?? []) {
      const kind = entry.kind ?? 'other'
      const stats = (byKind[kind] ??= { total: 0, statuses: {}, origin: 0, bytes: 0 })
      stats.total += 1
      stats.bytes += entry.contentLength ?? 0
      const cache = entry.cfCacheStatus ?? '(헤더 없음)'
      stats.statuses[cache] = (stats.statuses[cache] ?? 0) + 1
      if (entry.cfCacheStatus === null) withoutHeader += 1
      else withHeader += 1
      const origin = entry.cfCacheStatus !== 'HIT'
      if (origin) stats.origin += 1

      if (kind === 'segment' || kind === 'init') {
        const file = (byFile[entry.file] ??= {
          viewers: new Set(), originViewers: new Set(), requests: 0, bytes: entry.contentLength ?? 0,
          statuses: {},
        })
        file.viewers.add(row.viewer)
        file.requests += 1
        // ★ 첫 값에 고정하지 않는다 (#199 검토 4). 첫 응답에 content-length 가 없거나(0)
        //   일부만 왔다면 뒤에 온 온전한 값이 이겨야 한다 - 아니면 그 조각이 표본에서 빠진다.
        file.bytes = Math.max(file.bytes ?? 0, entry.contentLength ?? 0)
        file.statuses[entry.cfCacheStatus ?? '(헤더 없음)'] =
          (file.statuses[entry.cfCacheStatus ?? '(헤더 없음)'] ?? 0) + 1
        if (origin) file.originViewers.add(row.viewer)
      }
    }
  }
  return { byKind, byFile, withHeader, withoutHeader }
}

/**
 * 방송 시작 몰림 요약. (#235)
 *
 *   firstPlayMs : 방송 시작 → 그 시청자의 첫 재생 (truth 의 첫 playing 벽시계 기준)
 *   lookups     : 시작 전후 창에서 초마다 몇 건의 `GET /meeting/{id}` 가 나갔나
 *   firstMedia  : 시작 → 첫 매니페스트 · 첫 조각 요청
 */
function startStats(rows, startedAtMs, marginS = 10) {
  // 방송 시작 시각은 방송 호스트의 시계, 첫 재생은 시청자 브라우저의 시계다.
  // 각각의 보정량을 빼서 **로컬 시계**로 옮긴 뒤 뺀다.
  const startLocal = Number.isFinite(startedAtMs) ? startedAtMs - broadcastOffsetMs : NaN
  const toLocal = (at) => (Number.isFinite(at) ? at - viewerOffsetMs : NaN)

  const perViewer = rows.map((row) => {
    const firstPlayAt = toLocal(row.firstPlayingAtMs)
    const firstPlaylist = (row.hls ?? []).find((e) => e.kind === 'playlist')
    const firstSegment = (row.hls ?? []).find((e) => e.kind === 'segment' || e.kind === 'init')
    return {
      viewer: row.viewer,
      firstPlayMs: Number.isFinite(startLocal) && Number.isFinite(firstPlayAt) ? firstPlayAt - startLocal : null,
      firstPlaylistMs: Number.isFinite(startLocal) && firstPlaylist ? firstPlaylist.at - startLocal : null,
      firstSegmentMs: Number.isFinite(startLocal) && firstSegment ? firstSegment.at - startLocal : null,
      lookups: (row.lookups ?? []).length,
    }
  })

  const histogram = {}
  for (const row of rows) {
    for (const lookup of row.lookups ?? []) {
      if (!Number.isFinite(startLocal)) continue
      const offset = Math.round((toLocal(lookup.at) - startLocal) / 1000)
      if (Math.abs(offset) > marginS) continue
      histogram[offset] = (histogram[offset] ?? 0) + 1
    }
  }
  return { perViewer, histogram, marginS }
}

function round3(value) {
  return Math.round(value * 1000) / 1000
}

function parseManifestSegments(text) {
  const lines = text.split(/\r?\n/)
  const segments = []
  for (let i = 0; i < lines.length; i += 1) {
    const extinf = lines[i].match(/^#EXTINF:([0-9]+(?:\.[0-9]+)?),/)
    if (!extinf) continue
    let uri = null
    for (let j = i + 1; j < lines.length; j += 1) {
      const candidate = lines[j].trim()
      if (!candidate || candidate.startsWith('#')) continue
      uri = candidate
      break
    }
    if (uri) segments.push({ uri, duration: Number(extinf[1]) })
  }
  return segments
}

// 시험용 실제 표본: EXTINF 바로 다음이 태그여도 그 뒤 URI를 잡아야 한다.
const manifestParserSample = `#EXTINF:2.000,\n#EXT-X-PROGRAM-DATE-TIME:2026-09-23T00:00:00Z\nseg_00001.ts\n`
const sampleSegments = parseManifestSegments(manifestParserSample)
if (sampleSegments[0]?.uri !== 'seg_00001.ts') {
  throw new Error('매니페스트 표본에서 태그 뒤 URI를 찾지 못했다')
}

/** 설정값이 아니라 실제로 받은 HLS 결과물을 잰다. #193: hls_time 을 줄여도 리먹싱은 키프레임에서 자른다. */
function manifestStats(dir) {
  let files
  try {
    files = readdirSync(join(dir, 'manifests'))
      .filter((file) => /^\d+\.m3u8$/.test(file))
      .sort()
  } catch {
    files = []
  }

  const targetDurations = new Map()
  const chunks = new Map()
  const formats = new Map()
  let hasProgramDateTime = false

  for (const file of files) {
    let lines
    try {
      lines = readFileSync(join(dir, 'manifests', file), 'utf8').split(/\r?\n/)
    } catch {
      continue
    }
    for (const line of lines) {
      const target = line.match(/^#EXT-X-TARGETDURATION:(\d+(?:\.\d+)?)$/)
      if (target) targetDurations.set(target[1], (targetDurations.get(target[1]) ?? 0) + 1)
      if (line.startsWith('#EXT-X-PROGRAM-DATE-TIME:')) hasProgramDateTime = true
    }

    for (const { uri, duration } of parseManifestSegments(lines.join('\n'))) {
      if (!Number.isFinite(duration) || chunks.has(uri)) continue
      chunks.set(uri, duration)
      const extension = uri.split(/[?#]/, 1)[0].split('.').pop()?.toLowerCase() ?? 'unknown'
      formats.set(extension, (formats.get(extension) ?? 0) + 1)
    }
  }

  const durations = [...chunks.values()]
  const targetDurationDistribution = Object.fromEntries(targetDurations)
  const segmentFormats = Object.fromEntries(formats)
  return {
    sampleCount: files.length,
    targetDurationDistribution,
    extinfCount: durations.length,
    extinfAverage: durations.length ? round3(sum(durations) / durations.length) : null,
    extinfMinimum: durations.length ? round3(Math.min(...durations)) : null,
    extinfMaximum: durations.length ? round3(Math.max(...durations)) : null,
    segmentFormats,
    hasProgramDateTime,
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
// ★ 시계 보정 (#235). 시청자·방송 호스트가 다르면 VM 간 시계 차이가 "시작 → 첫 재생" 에 그대로 들어간다.
//   run-qoe-crosscheck.sh 가 ssh 왕복 중간값으로 재서 남긴다. 없으면 0 으로 보고 그 사실을 적는다.
const clock = readJson(join(dir, 'clock.json'), null)
// ★ Number(null) 은 0 이다 - null 을 "0ms 로 쟀다" 로 읽으면 못 잰 것을 감춘다. 타입을 본다.
const isMeasured = (value) => typeof value === 'number' && Number.isFinite(value)
const viewerOffsetMs = isMeasured(clock?.viewerOffsetMs) ? clock.viewerOffsetMs : 0
const broadcastOffsetMs = isMeasured(clock?.broadcastOffsetMs) ? clock.broadcastOffsetMs : 0
const clockKnown = clock !== null && isMeasured(clock?.viewerOffsetMs) && isMeasured(clock?.broadcastOffsetMs)
const broadcast = readJson(join(dir, 'broadcast.json'), null)
const scheduleRecord = readJson(join(dir, 'schedule.json'), {})
const manifest = manifestStats(dir)
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
  const catchup = catchupStats(v.truth)

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
    catchupRateSamples: catchup.samples,
    catchupFasterShare: catchup.fasterShare,
    catchupMaxRate: catchup.maxRate,
    catchupChained: catchup.chained,
    catchupStalls: catchup.stalls,
    hls: v.hls ?? [],
    lookups: v.lookups ?? [],
    requestedConfig: v.requestedConfig ?? null,
    effectiveConfig: v.effectiveConfig ?? v.finalState?.hlsConfig ?? null,
    // 조건부 따라잡기 누적 (#233) - 앱이 노출한 그대로. adaptive 회차에만 있다.
    catchup: v.catchup ?? null,
    // 재생 시작 때 잡은 경로를 우선한다 - SPA 종료에서는 destroy 가 지운 뒤다. (#233)
    effectivePlaybackPath: v.effectivePlaybackPath ?? null,
    playbackPath: v.effectivePlaybackPath ?? v.finalState?.playbackPath ?? null,
    firstPlayingAtMs: Number.isFinite(v.truth?.firstPlayingAt) ? v.truth.firstPlayingAt : null,
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
    // playbackPath 는 위에서 이미 정했다(재생 시작 때 잡은 값 우선). 여기서 다시 쓰면
    // 객체 리터럴의 뒤 키가 이겨서 SPA 종료 뒤 값(null)으로 덮인다. (#233)
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

const broadcastElapsedS = Number(broadcast?.elapsedS)
const broadcastChunkMs = Number(broadcast?.chunkMs)
const expectedBroadcastChunks = Number.isFinite(broadcastElapsedS) && broadcastElapsedS > 0 &&
  Number.isFinite(broadcastChunkMs) && broadcastChunkMs > 0
  ? (broadcastElapsedS * 1000) / broadcastChunkMs
  : null
const broadcastFailedChunks = Number(broadcast?.chunksFailed ?? 0) +
  Number(broadcast?.chunksRejected ?? 0)
const broadcastSentChunks = Number(broadcast?.chunksSent ?? 0)
const broadcastFailureRatio = expectedBroadcastChunks
  ? broadcastFailedChunks / expectedBroadcastChunks
  : null
const broadcastSentRatio = expectedBroadcastChunks
  ? broadcastSentChunks / expectedBroadcastChunks
  : null
const broadcastQuality = {
  expectedChunks: expectedBroadcastChunks,
  sentChunks: broadcastSentChunks,
  failedChunks: broadcastFailedChunks,
  failureRatio: broadcastFailureRatio,
  sentRatio: broadcastSentRatio,
}
const allowBroadcastRestart = scheduleRecord.allowBroadcastRestart === true
const broadcastRestartFailures = Number(broadcast?.restartFailures ?? 0)
const broadcastRestartStallMs = Number(broadcast?.restartStallMs ?? 0)
const viewerStarts = rows.map((row) => Number(row.t0)).filter(Number.isFinite)
const viewerEnds = rows.map((row) => Date.parse(row.endedAt ?? '')).filter(Number.isFinite)
const viewerWindowMs = viewerStarts.length > 0 && viewerEnds.length > 0
  ? Math.max(...viewerEnds) - Math.min(...viewerStarts)
  : null
// 재시작 중단은 시청자들이 실제로 본 측정 창에 대조한다. 표본이 없으면 방송 벽시계로 대체한다.
const broadcastWindowMs = viewerWindowMs ?? (Number.isFinite(Date.parse(broadcast?.startedAt ?? '')) &&
  Number.isFinite(Date.parse(broadcast?.endedAt ?? ''))
  ? Date.parse(broadcast.endedAt) - Date.parse(broadcast.startedAt)
  : Number.isFinite(Number(broadcast?.elapsedS)) ? Number(broadcast.elapsedS) * 1000 : null)
const broadcastRestartStallRatio = broadcastWindowMs > 0
  ? broadcastRestartStallMs / broadcastWindowMs
  : null

/**
 * 따라잡기 합계 (#233). 시청자 평균과 전체 연쇄 끊김을 한 줄로 낸다.
 * 표본이 없는 회차(따라잡기 끔)에서는 값이 null/0 이고, 보고서가 그렇게 적는다.
 *
 * ★ 마지막 두 값은 **조건부(adaptive)** 회차용이다. 앱이 노출한 누적(`__edumeetCatchup`)에서
 *   계산한다 - 켜져 있던 시간 비율이 낮으면 정책이 거의 안 켠 것이고, 전환 횟수가 많으면
 *   조건이 경계에서 흔들린 것이다. 둘 다 "정책이 의도대로 돌았나" 를 보는 값이다.
 */
const catchupTotals = (() => {
  const sampled = rows.filter((r) => r.catchupRateSamples > 0)
  const shares = sampled.map((r) => r.catchupFasterShare).filter((v) => v !== null)
  const exposed = rows.map((r) => r.catchup).filter((c) => c && Number.isFinite(c.enabledMs))
  const enabledRatios = exposed
    .map((c) => (c.enabledMs + c.disabledMs > 0 ? c.enabledMs / (c.enabledMs + c.disabledMs) : null))
    .filter((v) => v !== null)
  return {
    viewersWithSamples: sampled.length,
    fasterShare: shares.length > 0 ? shares.reduce((a, b) => a + b, 0) / shares.length : null,
    maxRate: sampled.length > 0 ? Math.max(...sampled.map((r) => r.catchupMaxRate ?? 1)) : null,
    chained: sum(sampled.map((r) => r.catchupChained)),
    stalls: sum(sampled.map((r) => r.catchupStalls)),
    exposedCount: exposed.length,
    enabledShare: enabledRatios.length > 0
      ? enabledRatios.reduce((a, b) => a + b, 0) / enabledRatios.length
      : null,
    toggles: exposed.length > 0 ? sum(exposed.map((c) => c.toggles ?? 0)) : null,
  }
})()

const cdn = cdnStats(rows)
const originByFile = server?.origin?.byFile ?? {}

/**
 * 실제 조각 크기로 본 비트레이트. (#199)
 *
 * ★ **조각 전체(영상 + 오디오 + 컨테이너)** 다. 합성 방송 ffmpeg 는 `-b:v <BITRATE_K>k` 와
 *   **별도로** `-b:a 96k` 를 넣는다(`broadcast-synthetic.mjs`) - 그래서 실측을 영상 요청값과
 *   그대로 비교하면 오디오만큼 크게 나오는 것이 정상인데 경고가 뜬다(검토 #199s 3번).
 *   기준을 **영상 + 오디오 96k** 로 잡고, 컨테이너 오버헤드는 실측 쪽에만 있는 차이임을 적는다.
 *
 * ★ 판정은 **반올림 전** 값으로 한다(#199 검토 5). 49.6% 를 50% 로 반올림해 경고를 삼키면 안 된다.
 *   정확히 50%·150% 는 경고하지 않는다(요구: 미만/초과).
 */
const BROADCAST_AUDIO_KBPS = 96 // broadcast-synthetic.mjs 의 -b:a 96k 와 같아야 한다
const requestedBitrateK = Number.isFinite(Number(broadcast?.bitrateK)) ? Number(broadcast.bitrateK) : null
const expectedTotalKbps = requestedBitrateK === null ? null : requestedBitrateK + BROADCAST_AUDIO_KBPS
const segmentBytesByFile = Object.entries(cdn.byFile)
  .filter(([file]) => /^seg_.*\.(ts|m4s|mp4)$/.test(file))
  .map(([, stats]) => stats.bytes)
  .filter((bytes) => Number.isFinite(bytes) && bytes > 0)
const bitrateSegmentSeconds = Number(manifest?.extinfAverage)
const actualBitrateRawK = segmentBytesByFile.length > 0 &&
    Number.isFinite(bitrateSegmentSeconds) && bitrateSegmentSeconds > 0
  ? sum(segmentBytesByFile) / segmentBytesByFile.length * 8 / bitrateSegmentSeconds / 1000
  : null
const actualBitrateK = actualBitrateRawK === null ? null : Math.round(actualBitrateRawK)
const bitrateRatio = actualBitrateRawK !== null && expectedTotalKbps !== null && expectedTotalKbps > 0
  ? actualBitrateRawK / expectedTotalKbps
  : null
const bitrateWarning = bitrateRatio !== null && (bitrateRatio < 0.5 || bitrateRatio > 1.5)
  ? `실제 조각 전체 비트레이트 ${actualBitrateK} kbps 가 기준 ${expectedTotalKbps} kbps` +
    `(영상 ${requestedBitrateK} + 오디오 ${BROADCAST_AUDIO_KBPS}) 의 ${(bitrateRatio * 100).toFixed(1)}% 다 - ` +
    '인코더가 조건을 안 따랐을 수 있다 (요청은 broadcast.json, 실측은 조각 크기 ÷ EXTINF 평균)'
  : null

const originAvailable = Boolean(server?.origin) && !server?.origin?.error
const broadcastStartedAtMs = broadcast?.startedAt ? Date.parse(broadcast.startedAt) : NaN
const start = startStats(rows, broadcastStartedAtMs)
const broadcastRest = server?.rest ?? null

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
  // ★ 빈 결과는 null 이다(0 이 아니다). 0 으로 적으면 "아무 일도 없었다" 로 읽힌다.
  rejectedPrometheus: server?.prom?.rejected ?? null,
  broadcastRejected429: broadcast?.chunksRejected ?? null,
  broadcastRestarts: broadcast?.restarts ?? 0,
  broadcastRestartStallMs,
  broadcastRestartFailures,
  broadcastRestartStallRatio,
  allowBroadcastRestart,
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
if (expectedBroadcastChunks !== null &&
    (broadcastFailureRatio > 0.02 || broadcastSentRatio < 0.95)) {
  reasons.push(
    '측정 조건 불성립 - 송출이 흔들렸다 ' +
      `(기대 ${expectedBroadcastChunks.toFixed(1)}조각, 보냄 ${broadcastSentChunks}조각, ` +
      `실패·거절 ${broadcastFailedChunks}조각)`,
  )
}
if (!allowBroadcastRestart &&
    (broadcastRestartFailures > 0 ||
      (broadcastRestartStallRatio !== null && broadcastRestartStallRatio > 0.1))) {
  reasons.push(
    '측정 조건 불성립 - 방송 재시작이 측정 창을 흔들었다 ' +
      `(재시작 실패 ${broadcastRestartFailures}회, 중단 ${broadcastRestartStallMs}ms, ` +
      `측정 창 ${broadcastWindowMs ?? '-'}ms)`,
  )
}
// ★ 요청 ≠ 적용이면 그 회차는 조건 불성립이다. (#233)
const configRows = rows.map((row) => ({ viewer: row.viewer, path: row.playbackPath, ...configCheck(row) }))
const mismatched = configRows.filter((row) => row.mismatches.length > 0)
const configUnavailable = configRows.filter((row) => !row.exposed)
if (mismatched.length > 0) {
  reasons.push(
    `요청한 설정이 적용되지 않은 시청자가 ${mismatched.length}/${rows.length}명이다 ` +
      `(예: 시청자 ${mismatched[0].viewer} — ${mismatched[0].mismatches[0]})`,
  )
}

const conditionFailed = reasons.length > 0

// ★ 따라잡기를 켰는데 아무도 1을 넘지 않았고 지연이 목표보다 크면 - 켠 것이 일을 안 한 것이다. (#233)
const requestedCatchup = rows
  .map((r) => r.requestedConfig?.maxLiveSyncPlaybackRate)
  .find((value) => value !== null && value !== undefined && Number(value) > 1) ?? null
const maxObservedRate = rows.length > 0 ? Math.max(...rows.map((r) => r.catchupMaxRate ?? 1)) : null
const segmentSeconds = Number(manifest?.extinfAverage)
const effectiveLiveSync = rows
  .map((r) => Number(r.effectiveConfig?.liveSyncDurationCount))
  .find((value) => Number.isFinite(value)) ?? null
// 조각 길이를 모르면 목표를 만들 수 없다 - 0ms 라고 적으면 거짓말이 된다.
const targetLatencyMs = Number.isFinite(segmentSeconds) && segmentSeconds > 0 && effectiveLiveSync !== null
  ? segmentSeconds * effectiveLiveSync * 1000
  : null
const catchupWarning = requestedCatchup !== null && maxObservedRate !== null && maxObservedRate <= 1 &&
    targetLatencyMs !== null && totals.screenLatencyP50Ms !== null &&
    totals.screenLatencyP50Ms > targetLatencyMs
  ? `따라잡기를 ${requestedCatchup} 로 요청했는데 전원의 최대 재생 속도가 ${maxObservedRate} 이고 ` +
    `화면 지연 p50 이 ${totals.screenLatencyP50Ms}ms 로 목표(${targetLatencyMs}ms = 조각 ${segmentSeconds}초 × ` +
    `liveSyncDurationCount ${effectiveLiveSync})보다 크다 - 따라잡기가 실제로 일어나지 않았다.`
  : null

const warning = conditionFailed
  ? [
      '# ⚠⚠⚠ 측정 조건 불성립 ⚠⚠⚠',
      '',
      '**이 회차의 값으로는 아무것도 판단할 수 없다. 조건을 먼저 고친다.**',
      '',
      ...reasons.map((reason) => `- ${reason}`),
      '',
      ...(mismatched.length > 0
        ? [
            '| 시청자 | 경로 | 요청 | 적용 | 어긋난 것 |',
            '|---|---|---|---|---|',
            ...mismatched.map((row) =>
              `| ${row.viewer} | ${row.path ?? '-'} | ` +
              `따라잡기 ${row.requested?.maxLiveSyncPlaybackRate ?? '없음'} · ` +
              `liveSync ${row.requested?.liveSyncDurationCount ?? '-'} | ` +
              `따라잡기 ${row.applied?.maxLiveSyncPlaybackRate ?? '없음'} · ` +
              `liveSync ${row.applied?.liveSyncDurationCount ?? '-'} | ` +
              `${row.mismatches.join(' / ')} |`,
            ),
            '',
          ]
        : []),
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

/** 바이트를 사람이 읽는 단위로. 원본 송신량(#235)에 쓴다. */
const bytesText = (value) => {
  if (!Number.isFinite(value) || value <= 0) return '-'
  if (value >= 1024 * 1024) return `${Math.round((value / (1024 * 1024)) * 10) / 10} MB`
  if (value >= 1024) return `${Math.round(value / 1024)} KB`
  return `${value} B`
}

/** 전체 중 몇 % 인가. (pct() 는 "받은 것 vs 보낸 것" 용이라 부호가 반대다) */
const shareText = (part, whole) => (whole > 0 ? `${Math.round((part / whole) * 100)}%` : '-')

/** 방송 시작 기준 오프셋. 음수면 시작 전이다. (#235) */
const startMsText = (value) => (Number.isFinite(value) ? `${value >= 0 ? '+' : ''}${value}ms` : '-')
const tri = (value) => (value === null || value === undefined ? '-' : value ? 'O' : 'X')

/** 경로 분포 한 줄. 이 회차가 hls.js 를 탔는지 네이티브로 갔는지. (#217) */
const pathCounts = rows.reduce((acc, r) => {
  const key = r.playbackPath ?? '없음'
  acc[key] = (acc[key] ?? 0) + 1
  return acc
}, {})
const pathSummary = Object.entries(pathCounts).map(([key, n]) => `${key} ${n}`).join(' · ')
const targetDurationText = Object.entries(manifest.targetDurationDistribution)
  .map(([value, count]) => `${value}초 ${count}개`)
  .join(' · ') || '-'
const segmentFormatText = Object.entries(manifest.segmentFormats)
  .map(([format, count]) => `${format} ${count}개`)
  .join(' · ') || '-'

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
  `- rejected: Prometheus ${totals.rejectedPrometheus ?? '없음'} · 합성 방송 429 ${totals.broadcastRejected429 ?? '-'}`,
  `- 합성 방송 재시작: ${totals.broadcastRestarts}회 · 재시작 실패 ${totals.broadcastRestartFailures}회 · ` +
    `재시작 중단 ${totals.broadcastRestartStallMs}ms · ALLOW_BROADCAST_RESTART=${totals.allowBroadcastRestart ? '1' : '0'}`,
  ...(expectedBroadcastChunks !== null
    ? [
        `- 송출: 기대 ${expectedBroadcastChunks.toFixed(1)}조각 · 보냄 ${broadcastSentChunks}조각 · ` +
          `실패·거절 ${broadcastFailedChunks}조각`,
      ]
    : []),
  `- 받지 못한 보고((sessionId, seq) 쌍 중 Loki 에 없는 것): ${totals.missingReports}건`,
  ...(totals.prometheusNote ? ['', `> ${totals.prometheusNote}`] : []),
  '',
  '## 결과물',
  '',
  `- manifest 표본: ${manifest.sampleCount}개`,
  `- TARGETDURATION 분포: ${targetDurationText}`,
  `- EXTINF: ${manifest.extinfCount}개 조각 · 평균 ${manifest.extinfAverage ?? '-'}초 · 최소 ${manifest.extinfMinimum ?? '-'}초 · 최대 ${manifest.extinfMaximum ?? '-'}초 (URI 중복 제거)`,
  `- 조각 형식: ${segmentFormatText}`,
  `- EXT-X-PROGRAM-DATE-TIME: ${manifest.hasProgramDateTime ? '있음' : '없음'}`,
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
  '## 따라잡기 (#233)',
  '',
  '| 시청자 | 속도 표본 | 재생 속도>1 비율 | 최대 속도 | 끊김 | 연쇄 끊김 | 화면 지연 p50(ms) | p95(ms) |',
  '|---|---:|---:|---:|---:|---:|---:|---:|',
  ...rows.map((r) =>
    `| ${r.viewer} | ${r.catchupRateSamples} | ${
      r.catchupFasterShare === null ? '-' : `${Math.round(r.catchupFasterShare * 100)}%`
    } | ${r.catchupMaxRate ?? '-'} | ${r.catchupStalls} | ${r.catchupChained} | ` +
      `${r.screenLatencyP50Ms ?? '-'} | ${r.screenLatencyP95Ms ?? '-'} |`,
  ),
  '',
  `- 전체: 재생 속도>1 비율 ${catchupTotals.fasterShare === null ? '-' : `${Math.round(catchupTotals.fasterShare * 100)}%`} · ` +
    `최대 ${catchupTotals.maxRate ?? '-'} · 끊김 ${catchupTotals.stalls}회 · **연쇄 끊김 ${catchupTotals.chained}회**`,
  catchupTotals.viewersWithSamples > 0
    ? `- 표본을 남긴 시청자 ${catchupTotals.viewersWithSamples}/${rows.length}명 (나머지는 재생 속도 표본이 없다)`
    : '- 따라잡기를 켜지 않았거나 표본이 없다 (재생 속도 표본 0개)',
  // 조건부 회차에서만 값이 있다 - always/off 회차는 앱이 누적을 노출하지 않는다.
  catchupTotals.exposedCount > 0
    ? `- 조건부(adaptive) 누적: 노출 ${catchupTotals.exposedCount}/${rows.length}명 · ` +
      `**켜진 시간 비율 ${Math.round(catchupTotals.enabledShare * 100)}%** · ` +
      `전환 ${catchupTotals.toggles}회 (시청자 평균 ${(catchupTotals.toggles / catchupTotals.exposedCount).toFixed(1)}회)`
    : '- 조건부 누적 없음 (adaptive 로 돌리지 않았다)',
  '',
  '> 판단 기준(#233): 자막 읽기 상한을 넘는 자막이 5% 이하이고, 연쇄 끊김이 V4 대비 늘지 않아야 한다.',
  '> 연쇄 끊김은 앞 끊김이 끝난 뒤 10초 안에 다시 시작한 끊김이다.',
  '',
  '## 설정 적용 (#233)',
  '',
  ...(configRows.length === 0
    ? ['- viewer 산출물이 없다.']
    : [
        '| 시청자 | 경로 | 요청(모드/배율/liveSync) | 적용(모드/배율/liveSync) | 판정 |',
        '|---|---|---|---|---|',
        ...configRows.map((row) => {
          const verdict = row.verdict
          return `| ${row.viewer} | ${row.path ?? '-'} | ` +
            `${row.requested?.catchupMode ?? '없음'} / ${row.requested?.maxLiveSyncPlaybackRate ?? '없음'} / ` +
            `${row.requested?.liveSyncDurationCount ?? '-'} | ` +
            `${row.applied ? `${row.applied.catchupMode ?? '없음'} / ` +
              `${row.applied.maxLiveSyncPlaybackRate ?? '없음'} / ${row.applied.liveSyncDurationCount ?? '-'}` : '-'} | ` +
            `${verdict} |`
        }),
        '',
        `- 일치 ${configRows.filter((r) => r.verdict === '일치').length} · ` +
          `기본값 일치 ${configRows.filter((r) => r.verdict === '기본값 일치').length} · ` +
          `불일치 ${mismatched.length} · 적용 불가 ${configUnavailable.filter((r) => r.native).length} · ` +
          `노출 없음 ${configUnavailable.filter((r) => !r.native).length}`,
        ...(catchupWarning ? ['', `- ⚠ ${catchupWarning}`] : []),
      ]),
  '',
  '## 비트레이트 (#199)',
  '',
  ...(requestedBitrateK === null && actualBitrateK === null
    ? ['- 요청값도 실측값도 없다. `BITRATE_K` 를 주고 돌렸는지, 매니페스트 표본이 있는지 본다.']
    : [
        `- 요청(합성 방송): ${requestedBitrateK === null ? '기록 없음' : `영상 ${requestedBitrateK} kbps + 오디오 ${BROADCAST_AUDIO_KBPS} kbps = 기준 ${expectedTotalKbps} kbps`} · ` +
          `실측(**조각 전체** = 영상+오디오+컨테이너, 조각 크기 ÷ EXTINF 평균): ` +
          `${actualBitrateK === null ? '못 쟀다' : `${actualBitrateK} kbps`}` +
          `${bitrateRatio === null ? '' : ` (기준의 ${(bitrateRatio * 100).toFixed(1)}%)`}`,
        ...(actualBitrateK !== null
          ? [`- 표본: 조각 ${segmentBytesByFile.length}개 · 평균 ${bytesText(sum(segmentBytesByFile) / segmentBytesByFile.length)} · ` +
              `EXTINF 평균 ${bitrateSegmentSeconds}초`]
          : []),
        `- 실측이 기준보다 조금 큰 것은 정상이다 - 조각에는 컨테이너 오버헤드가 더 들어간다. ` +
          `경고는 기준의 50% 미만·150% 초과일 때만 낸다(반올림 전 값으로 판정).`,
        ...(bitrateWarning ? ['', `- ⚠ ${bitrateWarning}`] : []),
      ]),
  '',
  '## CDN (#235)',
  '',
  ...(cdn.withHeader === 0 && cdn.withoutHeader === 0
    ? ['- `/hls/` 응답을 하나도 못 봤다 — 매니페스트를 받지 못했거나 URL 이 다르다.']
    : [
        '| 파일 종류 | 응답 수 | HIT | MISS | EXPIRED | BYPASS·DYNAMIC | 헤더 없음 | 원본까지 간 비율 | 받은 바이트 |',
        '|---|---:|---:|---:|---:|---:|---:|---:|---:|',
        ...['playlist', 'init', 'segment', 'other']
          .filter((kind) => cdn.byKind[kind])
          .map((kind) => {
            const stats = cdn.byKind[kind]
            const status = (name) => stats.statuses[name] ?? 0
            const bypass = Object.entries(stats.statuses)
              .filter(([name]) => !['HIT', 'MISS', 'EXPIRED', '(헤더 없음)'].includes(name))
              .reduce((acc, [, count]) => acc + count, 0)
            return `| ${kind} | ${stats.total} | ${status('HIT')} | ${status('MISS')} | ${status('EXPIRED')} | ` +
              `${bypass} | ${status('(헤더 없음)')} | ${shareText(stats.origin, stats.total)} | ${bytesText(stats.bytes)} |`
          }),
        '',
        `- CDN 헤더가 있는 응답 ${cdn.withHeader}건 · 없는 응답 ${cdn.withoutHeader}건`,
        cdn.withHeader === 0
          ? '  - **헤더가 하나도 없다** — 로컬 하네스이거나 CDN 을 안 거친 회차다. 적중률을 말할 수 없다.'
          : '  - HIT 가 아닌 것(MISS·EXPIRED·BYPASS·DYNAMIC)과 헤더 없음은 **원본까지 갔다**고 셌다.',
      ]),
  ...(Object.keys(cdn.byFile).length === 0
    ? []
    : [
        '',
        '조각·init 파일별 — **원본 요청 병합의 증거**',
        '',
        '| 파일 | 받은 시청자 | 원본 요청(nginx) | 시청자÷원본 | 시청자 쪽 DYNAMIC | 시청자 쪽 HIT |',
        '|---|---:|---:|---:|---:|---:|',
        ...Object.entries(cdn.byFile)
          .sort((a, b) => b[1].viewers.size - a[1].viewers.size)
          .slice(0, 12)
          .map(([file, stats]) => {
            const nginx = Number(originByFile[file]?.requests ?? 0)
            const ratio = nginx > 0 ? stats.viewers.size / nginx : null
            const dynamic = stats.statuses.DYNAMIC ?? 0
            const hit = stats.statuses.HIT ?? 0
            return `| ${file} | ${stats.viewers.size} | ${originAvailable ? nginx : '없음'} | ` +
              `${ratio === null ? '-' : `${Math.round(ratio * 10) / 10}배`} | ${dynamic} | ${hit} |`
          }),
        '',
        '- **시청자÷원본** 이 병합의 크기다. 1배면 아무도 병합되지 않았고(시청자마다 원본 요청), ' +
          '5배면 원본이 한 번만 받아 5명이 나눠 썼다는 뜻이다.',
        '- 원본 요청 수는 **운영 nginx 접근 로그**에서 읽는다(server.json 의 origin.byFile). ' +
          `${originAvailable ? '' : '이 회차에는 그 수치가 없다(로컬 회차이거나 로그를 못 읽었다) - 빈칸으로 두지 않고 "없음" 으로 적는다.'}`,
        `- 원본 로그: ${originAvailable
          ? `읽은 파일 ${(server.origin.filesRead ?? []).length}개${server.origin.complete === false ? ' **(일부만 읽었다 - 아래 수치는 하한)**' : ''}`
          : `읽지 못했다${server?.origin?.error ? ` — ${server.origin.error}` : ''}`}`,
        `- 창 규칙: ${server?.window?.rule ?? '알 수 없음'} (${server?.window?.from ?? '-'} ~ ${server?.window?.to ?? '-'}초)`,
        '- 시청자 쪽 DYNAMIC/HIT 는 **브라우저가 본 헤더**라 보조 정보다. Cloudflare 는 매니페스트·조각을 ' +
          'DYNAMIC 으로 표시하면서도 원본 요청을 합칠 수 있다 - 그래서 병합 판단은 nginx 수치로 한다.',

      ]),
  '',
  '## 방송 시작 (#235)',
  '',
  ...(Number.isFinite(broadcastStartedAtMs)
    ? [
        '| 시청자 | 시작 → 첫 재생 | 시작 → 첫 매니페스트 | 시작 → 첫 조각 | 시작 전후 대기 요청 |',
        '|---|---:|---:|---:|---:|',
        ...start.perViewer.map((r) =>
          `| ${r.viewer} | ${startMsText(r.firstPlayMs)} | ${startMsText(r.firstPlaylistMs)} | ` +
          `${startMsText(r.firstSegmentMs)} | ${r.lookups} |`,
        ),
        '',
        ...(Object.keys(start.histogram).length === 0
          ? [`- 시작 ±${start.marginS}초에 \`GET /meeting/{id}\` 요청이 없다 — 모드가 \`waiting\` 이 아니었거나 폴링 URL 이 다르다.`]
          : [
              `초마다 나간 \`GET /meeting/{id}\` (시작 = 0초, ±${start.marginS}초)`,
              '',
              '| 시작 기준 초 | 요청 수 |',
              '|---:|---:|',
              ...Object.keys(start.histogram).map(Number).sort((a, b) => a - b)
                .map((offset) => `| ${offset} | ${start.histogram[offset]} |`),
              '',
              `- 합계 ${sum(Object.values(start.histogram))}건 · 최대 ${Math.max(...Object.values(start.histogram))}건/초` +
                ` (${Object.keys(start.histogram).find((k) => start.histogram[k] === Math.max(...Object.values(start.histogram)))}초)`,
              '- 방송이 시작된 순간 대기하던 시청자가 한꺼번에 이 조회를 한다. 그 초의 수가 몰림의 크기다.',
            ]),
        '',
        clockKnown
          ? `- 시계 보정: 시청자 \`${clock.viewerHost}\` ${viewerOffsetMs}ms · 방송 \`${clock.broadcastHost}\` ` +
            `${broadcastOffsetMs}ms (원격 − 로컬). 위 "시작 → 첫 재생" 은 두 값을 뺀 로컬 시계 기준이다.`
          : clock
            ? '- **못 쟀다 - 보정 없이 계산했다.** `clock.json` 은 있는데 오프셋이 null 이다' +
              `(시청자 \`${clock.viewerHost ?? '?'}\` · 방송 \`${clock.broadcastHost ?? '?'}\`). ` +
              '다른 호스트의 시계 차이가 "시작 → 첫 재생" 에 그대로 들어갔을 수 있다.'
            : '- **시계 보정 기록이 없다**(clock.json 없음). 다른 호스트의 시계 차이가 그대로 들어갔을 수 있다.',
        ...(broadcastRest
          ? ['', `- 서버 REST 지연(그 창, Prometheus): p50 ${broadcastRest.p50Ms ?? '-'}ms · ` +
              `p95 ${broadcastRest.p95Ms ?? '-'}ms · p99 ${broadcastRest.p99Ms ?? '-'}ms` +
              `${broadcastRest.meetingP99Ms !== undefined ? ` · \`GET /meeting/{id}\` p99 ${broadcastRest.meetingP99Ms ?? '-'}ms` : ''}`]
          : []),
      ]
    : ['- `broadcast.json` 이 없어 방송 시작 시각을 모른다 — 시작 기준 값을 낼 수 없다.']),
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
      allowBroadcastRestart,
      broadcastQuality,
      manifest,
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
