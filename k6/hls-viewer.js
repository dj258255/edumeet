import http from 'k6/http'
import { check, sleep } from 'k6'
import { Trend, Rate } from 'k6/metrics'

/**
 * HLS 시청자 부하. (#124)
 *
 * ffmpeg CPU 벤치마크와 분리한다.
 *
 *   ffmpeg 벤치마크  = 방송자 1명 입력을 HLS 로 만드는 비용
 *   이 스크립트      = 이미 만들어진 HLS 를 시청자 N명이 받아 갈 때의 비용
 *
 * HLS 는 live.m3u8 과 .ts 세그먼트의 캐시 정책이 다르므로 따로 본다.
 */

export const options = {
  scenarios: {
    viewers: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.RAMP || '30s', target: Number(__ENV.VUS || 50) },
        { duration: __ENV.HOLD || '2m', target: Number(__ENV.VUS || 50) },
        { duration: __ENV.RAMP || '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    hls_playlist_status: ['rate>0.99'],
    hls_segment_status: ['rate>0.99'],
    hls_playlist_wait: ['p(95)<500'],
    hls_segment_wait: ['p(95)<1000'],
  },
}

const playlistWait = new Trend('hls_playlist_wait', true)
const segmentWait = new Trend('hls_segment_wait', true)
const playlistStatus = new Rate('hls_playlist_status')
const segmentStatus = new Rate('hls_segment_status')

const playlistUrl = __ENV.HLS_PLAYLIST_URL
if (!playlistUrl) {
  throw new Error('HLS_PLAYLIST_URL is required')
}

export default function () {
  const playlist = http.get(cacheBust(playlistUrl), {
    tags: { kind: 'playlist' },
  })
  playlistWait.add(playlist.timings.waiting)
  playlistStatus.add(playlist.status === 200)

  check(playlist, {
    'playlist 200': (r) => r.status === 200,
    'playlist has extm3u': (r) => r.body.includes('#EXTM3U'),
  })

  const segmentUrls = parseSegments(playlist.body, playlistUrl)
  const latest = segmentUrls.slice(-3)
  for (const url of latest) {
    const segment = http.get(url, { tags: { kind: 'segment' } })
    segmentWait.add(segment.timings.waiting)
    segmentStatus.add(segment.status === 200)
    check(segment, {
      'segment 200': (r) => r.status === 200,
      'segment has bytes': (r) => r.body.length > 0,
    })
  }

  sleep(Number(__ENV.POLL_SECONDS || 2))
}

export function parseSegments(body, baseUrl) {
  return body
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => resolveUrl(line, baseUrl))
}

function cacheBust(url) {
  const [withoutHash, hash = ''] = url.split('#', 2)
  const separator = withoutHash.includes('?') ? '&' : '?'
  const busted = `${withoutHash}${separator}_=${Date.now()}-${__VU}-${__ITER}`
  return hash ? `${busted}#${hash}` : busted
}

function resolveUrl(path, baseUrl) {
  if (/^https?:\/\//.test(path)) {
    return path
  }

  const origin = baseUrl.match(/^https?:\/\/[^/]+/)?.[0]
  if (!origin) {
    throw new Error(`invalid HLS_PLAYLIST_URL: ${baseUrl}`)
  }

  if (path.startsWith('/')) {
    return `${origin}${path}`
  }

  const cleanBase = baseUrl.split('#', 1)[0].split('?', 1)[0]
  const baseDir = cleanBase.slice(0, cleanBase.lastIndexOf('/') + 1)
  return `${baseDir}${path}`
}
