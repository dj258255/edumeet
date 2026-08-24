import { describe, it, expect } from 'vitest'
import { chooseMimeType, isRemuxable } from './codecChoice'

/**
 * 코덱 선택. (#123)
 *
 * 이 선택이 서버 CPU 를 정한다. 틀려도 에러가 안 나고 CPU 만 조용히 붙으므로
 * 시험으로 고정한다.
 */
const supports = (...allowed) => (type) => allowed.some((a) => type === a)

describe('chooseMimeType — 서버가 싼 것을 먼저 고른다', () => {
  it('★ H264 가 되면 H264 를 고른다 — 서버가 재인코딩을 안 한다', () => {
    const only = 'video/mp4;codecs=avc1.42E01E,mp4a.40.2'
    const result = chooseMimeType(false, supports(only, 'video/webm;codecs=vp8,opus'))

    expect(result.mimeType).toBe(only)
    expect(result.remuxable).toBe(true)
  })

  it('★ H264 가 안 되면 VP8 로 내려가되 재인코딩이라고 알린다', () => {
    const result = chooseMimeType(false, supports('video/webm;codecs=vp8,opus'))

    expect(result.mimeType).toBe('video/webm;codecs=vp8,opus')
    expect(result.remuxable).toBe(false)
  })

  it('아무것도 안 되면 브라우저 기본값에 맡긴다 — 던지지 않는다', () => {
    const result = chooseMimeType(false, () => false)

    expect(result.mimeType).toBeNull()
    expect(result.remuxable).toBe(false)
    expect(result.tried.length).toBeGreaterThan(0)
  })

  it('★ 오디오 방송은 오디오 후보만 본다 — 비디오 코덱을 고르면 카메라가 켜진다', () => {
    const result = chooseMimeType(true, supports('audio/webm;codecs=opus'))

    expect(result.mimeType).toBe('audio/webm;codecs=opus')
    expect(result.tried.every((t) => t.startsWith('audio/'))).toBe(true)
  })

  it('선호 순서를 지킨다 — 앞쪽이 서버에 싸다', () => {
    const both = supports('video/mp4;codecs=avc1', 'video/webm;codecs=vp8,opus')
    expect(chooseMimeType(false, both).mimeType).toBe('video/mp4;codecs=avc1')
  })
})

describe('isRemuxable — 서버가 그대로 넘길 수 있는가', () => {
  it('H264 계열은 참', () => {
    expect(isRemuxable('video/mp4;codecs=avc1.42E01E', false)).toBe(true)
    expect(isRemuxable('video/webm;codecs=h264,opus', false)).toBe(true)
  })

  it('VP8·VP9 는 거짓', () => {
    expect(isRemuxable('video/webm;codecs=vp8,opus', false)).toBe(false)
    expect(isRemuxable('video/webm;codecs=vp9', false)).toBe(false)
  })

  it('★ 오디오는 AAC 만 참 — Opus 는 MPEG-TS 에 못 담는다', () => {
    expect(isRemuxable('audio/mp4;codecs=mp4a.40.2', true)).toBe(true)
    expect(isRemuxable('audio/webm;codecs=opus', true)).toBe(false)
  })

  it('빈 값에도 안 터진다', () => {
    expect(isRemuxable(null, false)).toBe(false)
    expect(isRemuxable('', true)).toBe(false)
  })
})
