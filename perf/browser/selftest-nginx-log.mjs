#!/usr/bin/env node
/**
 * nginx 로그 읽기·집계 자기 시험. (#235)
 *
 *   node perf/browser/selftest-nginx-log.mjs
 *
 * ★ 운영에 붙지 않는다. `collectOrigin` 에 **가짜 원격 실행기**를 넣어 같은 코드를 돌린다.
 *   확인하는 것:
 *     - 회전 파일 이름이 `access.log-YYYYMMDD(.gz)` 인가 (운영 사실)
 *     - 자정을 걸친 창에서 두 파일을 다 읽는가
 *     - 못 읽은 파일이 조용한 0 이 아니라 error 로 남는가
 *     - grep 0건(exit 1)을 성공으로 보는가
 *     - fMP4 로 바뀌는 조각 이름(seg_*_NNNNN.mp4)을 segment 로 가르는가
 */
import { aggregateHls, collectOrigin, hlsKind, pickRotatedFiles } from './lib/nginx-log.mjs'

let failures = 0
function check(name, condition, detail = '') {
  if (condition) {
    console.log(`  ✓ ${name}`)
  } else {
    failures += 1
    console.log(`  ✗ ${name} ${detail}`)
  }
}

console.log('hlsKind — fMP4 로 바뀌는 이름까지')
check('init_<sid>.mp4 → init', hlsKind('/hls/meeting-3/init_ab12cd.mp4').kind === 'init')
check('seg_<sid>_00042.mp4 → segment', hlsKind('/hls/meeting-3/seg_ab12cd_00042.mp4').kind === 'segment')
check('seg_<sid>_00042.m4s → segment', hlsKind('/hls/meeting-3/seg_ab12cd_00042.m4s').kind === 'segment')
check('seg_<sid>_00042.ts → segment', hlsKind('/hls/meeting-3/seg_ab12cd_00042.ts').kind === 'segment')
check('live.m3u8 → playlist', hlsKind('/hls/meeting-3/live.m3u8').kind === 'playlist')
check('init_ab12cd.mp4 이름을 그대로 돌려준다', hlsKind('/hls/meeting-3/init_ab12cd.mp4').file === 'init_ab12cd.mp4')

console.log('pickRotatedFiles — 같은 날짜에 비압축과 gz 가 있으면 비압축')
{
  const listing = 'access.log\naccess.log-20260923.gz\naccess.log-20260924\nerror.log\n'
  const picked = pickRotatedFiles(listing, new Set(['20260923', '20260924', '20260925']))
  check('gz 를 고른다', picked.selected.some((f) => f.path.endsWith('access.log-20260923.gz') && f.compressed))
  check('비압축을 고른다', picked.selected.some((f) => f.path.endsWith('access.log-20260924') && !f.compressed))
  check('없는 날짜는 missing 으로 남는다', picked.missing.length === 1 && picked.missing[0].endsWith('access.log-20260925'))
  check('access.log.1.gz 는 후보가 아니다', !picked.selected.some((f) => f.path.includes('.1.gz')))
}

console.log('collectOrigin — 자정을 걸친 창 (23:59:50 → 00:00:20)')
{
  // 운영 형식 그대로. 시간대는 +0000 이다.
  const line = (time, path, status = 200, bytes = 1024) =>
    `203.0.113.5 - - [${time} +0000] "GET ${path} HTTP/2.0" ${status} ${bytes} "-" "curl/8" "203.0.113.5"`

  const previousDay = [
    line('23/Sep/2026:23:59:50', '/hls/meeting-3/seg_ab_00001.mp4', 200, 100_000),
    line('23/Sep/2026:23:59:52', '/hls/meeting-3/seg_ab_00002.mp4', 200, 200_000),
    line('23/Sep/2026:23:59:59', '/hls/meeting-3/live.m3u8', 200, 800),
    // 창 밖(하루 전) - 세면 안 된다
    line('23/Sep/2026:23:00:00', '/hls/meeting-3/seg_ab_00000.mp4', 200, 999_999),
  ].join('\n')
  const today = [
    line('24/Sep/2026:00:00:05', '/hls/meeting-3/seg_ab_00003.mp4', 200, 300_000),
    line('24/Sep/2026:00:00:10', '/hls/meeting-3/init_ab.mp4', 200, 700),
  ].join('\n')

  const runRemote = (command) => {
    if (command.startsWith('sudo -n true')) return { stdout: '' }
    if (command.includes('sudo -n ls ')) return { stdout: 'access.log\naccess.log-20260923.gz\n' }
    if (command.includes('access.log-20260923.gz')) return { stdout: previousDay }
    if (command.includes('/var/log/nginx/access.log')) return { stdout: today }
    return { error: `예상 못 한 명령: ${command}` }
  }

  const from = Math.floor(Date.parse('2026-09-23T23:59:50Z') / 1000)
  const to = Math.floor(Date.parse('2026-09-24T00:00:20Z') / 1000)
  const origin = collectOrigin({ runRemote, from, to })

  check('두 파일을 읽었다', (origin.filesRead ?? []).length === 2, JSON.stringify(origin.filesRead))
  check('창 안 5건', origin.hlsRequestsInWindow === 5, `실제 ${origin.hlsRequestsInWindow}`)
  check('조각 3건', origin.byKind.segment?.total === 3, `실제 ${origin.byKind.segment?.total}`)
  check('매니페스트 1건', origin.byKind.playlist?.total === 1)
  check('init 1건', origin.byKind.init?.total === 1)
  check('창 밖(23:00)은 안 셌다', origin.byKind.segment?.statuses['200'] === 3)
  check('완전히 읽었다', origin.complete === true)
  check('바이트 합계', origin.bytesSentInWindow === 100_000 + 200_000 + 800 + 300_000 + 700,
    `실제 ${origin.bytesSentInWindow}`)
  check('파일별로도 센다', origin.byFile['seg_ab_00001.mp4']?.requests === 1)
  check('경계 규칙을 남긴다', typeof origin.window?.rule === 'string')
}

console.log('collectOrigin — 못 읽으면 error (조용한 0 금지)')
{
  const runRemote = (command) => {
    if (command.startsWith('sudo -n true')) return { stdout: '' }
    if (command.includes('sudo -n ls ')) return { stdout: 'access.log\n' }
    return { error: 'sudo: a password is required' }
  }
  const origin = collectOrigin({ runRemote, from: 1000, to: 2000 })
  check('error 가 있다', Boolean(origin.error), JSON.stringify(origin))
  check('0건으로 위장하지 않는다', origin.hlsRequestsInWindow === undefined)
  check('수를 만들지 않는다', origin.bytesSentInWindow === undefined)
}

console.log('collectOrigin — grep 0건은 성공(0건)이다')
{
  const runRemote = (command) => {
    if (command.startsWith('sudo -n true')) return { stdout: '' }
    if (command.includes('sudo -n ls ')) return { stdout: 'access.log\n' }
    return { stdout: '' } // grep 이 exit 1 이어도 readHlsLines 는 성공으로 본다
  }
  const origin = collectOrigin({ runRemote, from: 1000, to: 2000 })
  check('error 가 없다', !origin.error, JSON.stringify(origin.error))
  check('0건이다', origin.hlsRequestsInWindow === 0)
  check('완전히 읽었다', origin.complete === true)
}

console.log('collectOrigin — sudo 가 안 되면 그렇게 적는다')
{
  const runRemote = () => ({ error: 'sudo: a password is required' })
  const origin = collectOrigin({ runRemote, from: 1000, to: 2000 })
  check('sudo 실패를 구분한다', String(origin.error ?? '').includes('sudo'), JSON.stringify(origin.error))
}

console.log('aggregateHls — 창 밖 줄은 세지 않는다')
{
  const lines = [
    '203.0.113.5 - - [24/Sep/2026:00:00:01 +0000] "GET /hls/meeting-3/seg_ab_00001.mp4 HTTP/2.0" 200 10 "-" "x" "-"',
    '203.0.113.5 - - [24/Sep/2026:00:00:30 +0000] "GET /hls/meeting-3/seg_ab_00002.mp4 HTTP/2.0" 200 10 "-" "x" "-"',
  ]
  const result = aggregateHls(lines, Math.floor(Date.parse('2026-09-24T00:00:00Z') / 1000),
    Math.floor(Date.parse('2026-09-24T00:00:20Z') / 1000))
  check('창 안 1건만', result.hlsRequestsInWindow === 1, `실제 ${result.hlsRequestsInWindow}`)
}

console.log('')
if (failures > 0) {
  console.log(`실패 ${failures}건`)
  process.exit(1)
}
console.log('모두 통과')
