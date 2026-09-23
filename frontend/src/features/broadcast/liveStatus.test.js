import { beforeEach, describe, it, expect } from 'vitest'
import { createEndWatcher, livePlaylistUrl } from './liveStatus'

describe('라이브 플레이리스트 상태', () => {
  const url = '/hls/meeting-1/live.m3u8'

  it('방송 중이고 주소가 있으면 붙을 주소를 돌려준다', () => {
    expect(livePlaylistUrl({ broadcasting: true, hlsPlaylistUrl: url })).toBe(url)
  })

  it('방송이 끝났으면 남아 있는 주소를 재생하지 않는다', () => {
    expect(livePlaylistUrl({ broadcasting: false, hlsPlaylistUrl: url })).toBeNull()
  })

  it('방송 중이어도 주소가 없으면 붙지 않는다', () => {
    expect(livePlaylistUrl({ broadcasting: true, hlsPlaylistUrl: null })).toBeNull()
  })

  it('broadcasting 필드가 없는 옛 서버 응답에는 붙지 않는다', () => {
    expect(livePlaylistUrl({ hlsPlaylistUrl: url })).toBeNull()
  })
})

describe('방송 종료 감시', () => {
  let time
  let watcher

  beforeEach(() => {
    time = 0
    watcher = createEndWatcher({ now: () => time })
  })

  it('30초 미만 false 뒤 true가 돌아오면 종료하지 않는다', () => {
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 20_000
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 25_000
    expect(watcher.observe({ broadcasting: true })).toBe(false)
    time = 50_000
    expect(watcher.observe({ broadcasting: true })).toBe(false)
  })

  it('30초 연속 false면 종료한다', () => {
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 29_999
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 30_000
    expect(watcher.observe({ broadcasting: false })).toBe(true)
  })

  it('조회 실패는 false로 세지 않아 종료 유예를 다시 시작한다', () => {
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 10_000
    expect(watcher.observe(null)).toBe(false)
    time = 30_000
    expect(watcher.observe({ broadcasting: false })).toBe(false)
    time = 59_999
    expect(watcher.observe({ broadcasting: false })).toBe(false)
  })
})
