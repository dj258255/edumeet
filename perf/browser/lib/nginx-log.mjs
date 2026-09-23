/**
 * 운영 nginx 접근 로그 읽기·집계. (#235)
 *
 * ★ 왜 모듈로 뺐나.
 *   `server-side.mjs` 는 import 하는 순간 ssh 로 운영에 붙는다 - 파싱 규칙을 시험할 수 없다.
 *   여기서는 원격 실행을 `runRemote(command)` 로 주입받는다. 시험은 가짜 실행기를 넣어
 *   **같은 코드**로 회전 파일·자정 경계를 검증한다.
 *
 * ★ 운영에서 확인된 사실 (이 세션이 봤다)
 *   - 시간대는 **+0000** 이다(로그의 $time_local). 회전 파일은
 *     `access.log-YYYYMMDD`(가장 최근 것은 비압축) 와 `access.log-YYYYMMDD.gz` 다.
 *     `access.log.1.gz` 는 **없다** - 있다고 가정하면 자정을 넘긴 회차에서 0건이 된다.
 *   - `/hls/` 응답의 cf-cache-status 는 매니페스트·조각이 DYNAMIC, init 만 MISS→HIT 다
 *     (Cloudflare 는 확장자로 캐시 대상을 정한다).
 *   - 곧 fMP4 조각 이름이 `seg_<sid>_NNNNN.mp4` 로 바뀐다 - 그래서 분류는 확장자로 한다.
 *
 * ★ grep 은 일치가 없으면 exit 1 이다. 그걸 실패로 보면 "0건" 과 "못 읽음" 이 섞인다.
 *   그래서 `|| [ $? -eq 1 ]` 로 감싸고, 읽을 수 없는 파일은 exit 3 으로 따로 알린다.
 */

export const NGINX_DIR = '/var/log/nginx'
export const NGINX_LOG = `${NGINX_DIR}/access.log`
export const HLS_PATH_MARK = ' /hls/'

const MONTHS = {
  Jan: 1, Feb: 2, Mar: 3, Apr: 4, May: 5, Jun: 6,
  Jul: 7, Aug: 8, Sep: 9, Oct: 10, Nov: 11, Dec: 12,
}
// nginx main 의 $time_local: 23/Sep/2026:21:03:12 +0000
const LOCAL_TIME = /\[(\d{2})\/([A-Za-z]{3})\/(\d{4}):(\d{2}):(\d{2}):(\d{2}) ([+-]\d{4})\]/
// "GET /hls/meeting-3/seg_ab12cd_00042.mp4 HTTP/2.0" 200 84321
const REQUEST_LINE = /"(\S+) ([^"]*?) HTTP\/[\d.]+" (\d{3}) (\d+|-)/

/** 로그 한 줄 → { at(초), method, path, status, bytes } | null */
export function parseLogLine(line) {
  const time = LOCAL_TIME.exec(line)
  if (!time) return null
  const [, dd, mon, yyyy, hh, mm, ss, tz] = time
  const month = MONTHS[mon] ?? MONTHS[mon.slice(0, 1).toUpperCase() + mon.slice(1, 3).toLowerCase()]
  if (!month) return null
  const iso = `${yyyy}-${String(month).padStart(2, '0')}-${dd}T${hh}:${mm}:${ss}${tz.slice(0, 3)}:${tz.slice(3)}`
  const parsed = Date.parse(iso)
  if (!Number.isFinite(parsed)) return null

  const request = REQUEST_LINE.exec(line)
  if (!request) return { at: Math.floor(parsed / 1000) }
  const size = Number(request[4])
  return {
    at: Math.floor(parsed / 1000),
    method: request[1],
    path: request[2],
    status: request[3],
    bytes: Number.isFinite(size) ? size : null,
  }
}

/**
 * 경로로 파일 종류를 가른다. compare 의 브라우저 쪽 분류와 **같은 규칙**이어야 두 수를 맞출 수 있다.
 *
 *   init_*.mp4            → init      (fMP4 초기화 조각)
 *   seg_*.(ts|m4s|mp4)    → segment   (.mp4 로 바뀌는 중이다)
 *   *.m3u8                → playlist  (캐시하지 않는다 - #11)
 */
export function hlsKind(path) {
  const file = path.split('?')[0].split('/').pop() ?? ''
  if (file.endsWith('.m3u8')) return { kind: 'playlist', file }
  if (file.startsWith('init') && file.endsWith('.mp4')) return { kind: 'init', file }
  if (/^seg_.*\.(ts|m4s|mp4)$/.test(file)) return { kind: 'segment', file }
  if (file.startsWith('init')) return { kind: 'init', file }
  if (/\.(ts|m4s)$/.test(file)) return { kind: 'segment', file }
  return { kind: 'other', file }
}

/** 창이 걸친 UTC 날짜들 (YYYYMMDD). 로그가 +0000 이라 UTC 날짜로 고른다. */
export function datesInWindow(fromSec, toSec) {
  const dates = new Set()
  const end = toSec * 1000
  for (let at = fromSec * 1000; at <= end; at += 86_400_000) {
    dates.add(new Date(at).toISOString().slice(0, 10).replace(/-/g, ''))
  }
  return dates
}

/**
 * `ls /var/log/nginx` 결과에서 창에 필요한 회전 파일을 고른다.
 *
 * 같은 날짜에 비압축과 .gz 가 있으면 **비압축**을 쓴다(운영에서 가장 최근 것은 비압축이다).
 *
 * @returns {{ selected: {path, date, compressed}[], missing: string[] }} missing = 그 날짜의 파일이 아예 없는 경우
 */
export function pickRotatedFiles(listing, dates) {
  const candidates = []
  for (const name of String(listing).split('\n').map((line) => line.trim()).filter(Boolean)) {
    const match = /^access\.log-(\d{8})(\.gz)?$/.exec(name)
    if (match) candidates.push({ name, date: match[1], compressed: Boolean(match[2]) })
  }

  const selected = []
  const missing = []
  for (const date of dates) {
    const forDate = candidates.filter((candidate) => candidate.date === date)
    if (forDate.length === 0) {
      missing.push(`${NGINX_DIR}/access.log-${date}`)
      continue
    }
    const plain = forDate.find((candidate) => !candidate.compressed)
    const chosen = plain ?? forDate[0]
    selected.push({ path: `${NGINX_DIR}/${chosen.name}`, date, compressed: chosen.compressed })
  }
  return { selected, missing }
}

/**
 * 원격에서 `/hls/` 줄만 받아 온다. grep 의 exit 1(일치 0건)은 **성공**이다.
 *
 * @returns {{ lines?: string[], error?: string }}
 */
export function readHlsLines(runRemote, file) {
  const grep = file.compressed
    ? `gzip -dc ${file.path} | grep -F "${HLS_PATH_MARK}"`
    : `grep -F "${HLS_PATH_MARK}" ${file.path}`
  const script =
    `test -r ${file.path} || { echo UNREADABLE 1>&2; exit 3; }; ${grep} || [ $? -eq 1 ]`
  // 파일마다 상태를 본다 - 못 읽은 것을 빈 줄로 바꾸면 조용한 0 이 된다.
  const result = runRemote(`sudo -n sh -c ${shellQuote(script)}`)
  if (result.error) return { error: `${file.path}: ${result.error}` }
  return { lines: result.stdout.split('\n').filter((line) => line.length > 0) }
}

/** 창 안의 /hls/ 요청을 종류별·파일별로 센다. */
export function aggregateHls(lines, fromSec, toSec) {
  const byKind = {}
  const byFile = {}
  let scanned = 0
  let inWindow = 0
  let bytes = 0

  for (const line of lines) {
    scanned += 1
    const parsed = parseLogLine(line)
    if (!parsed || parsed.at < fromSec || parsed.at > toSec) continue
    // HLS 경로만 센다. grep 으로 이미 걸렀지만 파서를 신뢰한다 - 같은 규칙을 두 번 확인한다.
    if (!parsed.path || !parsed.path.includes('/hls/')) continue

    inWindow += 1
    const { kind, file } = hlsKind(parsed.path)
    const kindStats = (byKind[kind] ??= { total: 0, bytes: 0, statuses: {} })
    kindStats.total += 1
    if (parsed.bytes !== null) {
      kindStats.bytes += parsed.bytes
      bytes += parsed.bytes
    }
    kindStats.statuses[parsed.status] = (kindStats.statuses[parsed.status] ?? 0) + 1

    const fileStats = (byFile[file] ??= { requests: 0, bytes: 0, statuses: {} })
    fileStats.requests += 1
    if (parsed.bytes !== null) fileStats.bytes += parsed.bytes
    fileStats.statuses[parsed.status] = (fileStats.statuses[parsed.status] ?? 0) + 1
  }

  return { byKind, byFile, scannedLines: scanned, hlsRequestsInWindow: inWindow, bytesSentInWindow: bytes }
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, "'\\''")}'`
}

/**
 * 원본(운영 nginx)이 받은 `/hls/` 요청을 측정 창으로 걸러 센다.
 *
 * @param {object} options
 * @param {(command: string) => {stdout?: string, error?: string}} options.runRemote 원격 실행기
 * @param {number} options.from 창 시작(초)
 * @param {number} options.to 창 끝(초)
 * @returns 집계 + 읽은 파일 목록(실패는 error 로 남는다 - 빈 줄로 대체하지 않는다)
 */
export function collectOrigin({ runRemote, from, to }) {
  const preflight = runRemote('sudo -n true')
  if (preflight.error) {
    return {
      error: 'sudo -n 이 안 된다 - 접근 로그를 읽을 수 없다',
      detail: preflight.error,
      hint: '운영 설정은 바꾸지 않는다. 읽을 수 있는 방법을 찾거나 못 읽었다고 적는다.',
    }
  }

  const listing = runRemote(`sudo -n ls ${NGINX_DIR}`)
  if (listing.error) {
    return { error: `로그 디렉터리를 못 읽었다: ${NGINX_DIR}`, detail: listing.error }
  }

  const { selected, missing } = pickRotatedFiles(listing.stdout, datesInWindow(from, to))

  // ★ 창의 **끝 날짜**는 현재 로그(access.log)가 덮는다. 회전 파일이 없다고 실패로 보면 안 된다.
  //   회전 파일이 필요한 것은 창이 자정을 넘어간 앞 날짜뿐이다.
  const currentDate = new Date(to * 1000).toISOString().slice(0, 10).replace(/-/g, '')
  const rotated = selected.filter((file) => file.date !== currentDate)
  const missingRotated = missing.filter((path) => !path.endsWith(`access.log-${currentDate}`))

  const toRead = [
    { path: NGINX_LOG, date: currentDate, compressed: false },
    ...rotated,
  ].filter((file, index, list) => list.findIndex((other) => other.path === file.path) === index)

  const files = []
  const errors = []
  const allLines = []
  for (const file of toRead) {
    const read = readHlsLines(runRemote, file)
    if (read.error) {
      errors.push(read.error)
      continue
    }
    files.push(file.path)
    allLines.push(...read.lines)
  }
  for (const path of missingRotated) errors.push(`${path}: 파일이 없다`)

  const base = {
    window: { from, to, rule: '초 단위 내림, 양끝 포함 [from, to] - 로그·Prometheus 같은 구간' },
    filesRead: files,
    filesTried: toRead.map((file) => file.path),
    note: 'nginx 접근 로그의 /hls/ 요청만 셌다. Cloudflare 가 병합하면 이 수가 시청자 수보다 훨씬 작다.',
  }

  // ★ 하나도 못 읽었으면 **수를 만들지 않는다.** 0 을 실어 보내면 "0건" 과 구분되지 않는다.
  if (files.length === 0) {
    return { ...base, error: '접근 로그를 하나도 읽지 못했다', errors }
  }

  const aggregate = aggregateHls(allLines, from, to)
  return {
    ...base,
    ...aggregate,
    // 일부만 읽었으면 그렇다고 밝힌다 - 이 수는 하한이다.
    complete: errors.length === 0,
    ...(errors.length > 0 ? { error: '일부 로그만 읽었다 - 수치는 하한이다', errors } : {}),
  }
}
