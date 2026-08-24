/**
 * HLS 시청 품질 지표. (#124)
 *
 * hls.js 가 주는 값과 <video> 가 주는 값이 섞여 있다.
 * 한 곳에서 정리해야 "지연" 이 버퍼인지 live edge 거리인지 헷갈리지 않는다.
 */

export function finiteNumber(value) {
  return Number.isFinite(value) ? value : null
}

export function bufferAhead(videoEl) {
  if (!videoEl || !videoEl.buffered || videoEl.buffered.length === 0) return null

  const currentTime = Number(videoEl.currentTime)
  if (!Number.isFinite(currentTime)) return null

  for (let i = 0; i < videoEl.buffered.length; i += 1) {
    const start = videoEl.buffered.start(i)
    const end = videoEl.buffered.end(i)
    if (start <= currentTime && currentTime <= end) {
      return round1(end - currentTime)
    }
  }

  const lastEnd = videoEl.buffered.end(videoEl.buffered.length - 1)
  return round1(Math.max(0, lastEnd - currentTime))
}

export function droppedFrames(videoEl) {
  const quality = videoEl?.getVideoPlaybackQuality?.()
  if (!quality || !Number.isFinite(quality.droppedVideoFrames)) return null
  return quality.droppedVideoFrames
}

export function bandwidthKbps(bitsPerSecond) {
  if (!Number.isFinite(bitsPerSecond) || bitsPerSecond <= 0) return null
  return Math.round(bitsPerSecond / 1000)
}

export function hlsLevelLabel(level) {
  if (level === -1) return 'auto'
  if (!Number.isInteger(level) || level < 0) return '-'
  return `L${level}`
}

export function snapshotNativeMetrics(videoEl, extra = {}) {
  return {
    native: true,
    latency: null,
    targetLatency: null,
    buffer: bufferAhead(videoEl),
    bandwidthKbps: null,
    level: '-',
    droppedFrames: droppedFrames(videoEl),
    fragLoadMs: null,
    errors: 0,
    ...extra,
  }
}

export function snapshotHlsMetrics(hls, videoEl, state = {}) {
  return {
    native: false,
    latency: round1(finiteNumber(hls?.latency)),
    targetLatency: round1(finiteNumber(hls?.targetLatency)),
    buffer: bufferAhead(videoEl),
    bandwidthKbps: bandwidthKbps(hls?.bandwidthEstimate),
    level: hlsLevelLabel(hls?.currentLevel),
    droppedFrames: droppedFrames(videoEl),
    fragLoadMs: state.fragLoadMs ?? null,
    fragBytes: state.fragBytes ?? null,
    errors: state.errors ?? 0,
  }
}

export function metricText(value, suffix = '') {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}${suffix}`
}

function round1(value) {
  if (!Number.isFinite(value)) return null
  return Math.round(value * 10) / 10
}
