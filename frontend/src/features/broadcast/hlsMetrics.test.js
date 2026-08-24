import { describe, it, expect } from 'vitest'
import {
  bandwidthKbps,
  bufferAhead,
  hlsLevelLabel,
  metricText,
  snapshotHlsMetrics,
} from './hlsMetrics'

function bufferedRange(ranges) {
  return {
    length: ranges.length,
    start: (i) => ranges[i][0],
    end: (i) => ranges[i][1],
  }
}

describe('HLS metrics', () => {
  it('현재 시간이 들어 있는 버퍼 구간의 남은 길이를 잰다', () => {
    const video = { currentTime: 12.4, buffered: bufferedRange([[0, 8], [10, 18.28]]) }

    expect(bufferAhead(video)).toBe(5.9)
  })

  it('버퍼가 비었거나 현재 시간이 숫자가 아니면 null 로 둔다', () => {
    expect(bufferAhead({ currentTime: 1, buffered: bufferedRange([]) })).toBeNull()
    expect(bufferAhead({ currentTime: Number.NaN, buffered: bufferedRange([[0, 2]]) })).toBeNull()
  })

  it('bits/s 를 kbps 로 바꾼다', () => {
    expect(bandwidthKbps(2_345_678)).toBe(2346)
    expect(bandwidthKbps(0)).toBeNull()
  })

  it('hls.js 자동 레벨(-1)을 auto 로 표시한다', () => {
    expect(hlsLevelLabel(-1)).toBe('auto')
    expect(hlsLevelLabel(2)).toBe('L2')
    expect(hlsLevelLabel(null)).toBe('-')
  })

  it('hls.js live latency 와 video buffer 를 구분해서 스냅샷에 담는다', () => {
    const hls = {
      latency: 5.64,
      targetLatency: 4.2,
      bandwidthEstimate: 1_520_000,
      currentLevel: -1,
    }
    const video = {
      currentTime: 30,
      buffered: bufferedRange([[20, 36]]),
      getVideoPlaybackQuality: () => ({ droppedVideoFrames: 3 }),
    }

    expect(snapshotHlsMetrics(hls, video, { fragLoadMs: 120, errors: 1 })).toEqual({
      native: false,
      latency: 5.6,
      targetLatency: 4.2,
      buffer: 6,
      bandwidthKbps: 1520,
      level: 'auto',
      droppedFrames: 3,
      fragLoadMs: 120,
      fragBytes: null,
      errors: 1,
    })
  })

  it('값이 없으면 대시로 표시한다', () => {
    expect(metricText(null, '초')).toBe('-')
    expect(metricText(4.1, '초')).toBe('4.1초')
  })
})
