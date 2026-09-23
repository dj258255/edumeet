import { describe, it, expect } from 'vitest'
import { choosePlaybackPath } from './playbackPath'

describe('재생 경로 선택', () => {
  it('★ 둘 다 되면 hls.js 를 고른다 - Chrome 이 네이티브도 지원한다고 답해도', () => {
    expect(choosePlaybackPath({ hlsJsSupported: true, nativeHlsSupported: true })).toBe('hlsjs')
  })

  it('hls.js 만 되면 hls.js 다', () => {
    expect(choosePlaybackPath({ hlsJsSupported: true, nativeHlsSupported: false })).toBe('hlsjs')
  })

  it('hls.js 를 못 쓰면 네이티브로 간다 (iOS Safari - MSE 없음)', () => {
    expect(choosePlaybackPath({ hlsJsSupported: false, nativeHlsSupported: true })).toBe('native')
  })

  it('둘 다 안 되면 unsupported 다', () => {
    expect(choosePlaybackPath({ hlsJsSupported: false, nativeHlsSupported: false })).toBe('unsupported')
  })

  it('아무것도 안 주면 unsupported 다', () => {
    expect(choosePlaybackPath()).toBe('unsupported')
  })
})
