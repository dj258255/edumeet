import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * 조건부 따라잡기 (#233) - hlsPlayer 쪽.
 *
 * ★ 이 시험이 지키려는 것 하나: **끌 때 화면이 실제로 1배로 돌아오는가.**
 *   hls.js 의 latency-controller 는 `maxLiveSyncPlaybackRate` 가 1 이면 일찍 return 해서
 *   이미 올려 둔 재생 속도를 되돌리지 않는다(1.5.20 dist/hls.mjs 4926행).
 *   그래서 hlsPlayer 가 `videoEl.playbackRate = 1` 을 직접 한다. 그 줄이 없으면
 *   정책은 껐는데 화면은 1.1배로 계속 돈다 - 제한망에서 끊김만 늘어난다.
 *
 * ★ hls.js 를 모킹한다. 이 저장소의 vitest 는 environment: 'node' 라 MediaSource 가 없고
 *   실제 hls.js 는 isSupported()=false 로 네이티브 경로를 타서 이 분기에 닿지 못한다.
 *   가짜는 config·on/emit·levels·bandwidthEstimate 만 흉내 낸다.
 */
const EVENTS = { ERROR: 'hlsError', MANIFEST_PARSED: 'hlsManifestParsed' }
const ERROR_TYPES = { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError', OTHER_ERROR: 'otherError' }
const ERROR_DETAILS = { MANIFEST_LOAD_ERROR: 'manifestLoadError' }

let instances = []

class FakeHls {
  static isSupported() { return true }
  static get Events() { return EVENTS }
  static get ErrorTypes() { return ERROR_TYPES }
  static get ErrorDetails() { return ERROR_DETAILS }
  constructor(config) {
    this.config = config
    this.handlers = {}
    this.bandwidthEstimate = 4_000_000 // 대역 추정 (hls.js 가 채운다)
    this.levels = [{ bitrate: 1_000_000 }]
    this.currentLevel = 0
    instances.push(this)
  }
  on(event, handler) { (this.handlers[event] ??= []).push(handler) }
  emit(event, data) { for (const handler of this.handlers[event] ?? []) handler(event, data) }
  loadSource() {}
  startLoad() {}
  recoverMediaError() {}
  attachMedia() {}
  destroy() {}
}

vi.mock('hls.js', () => ({ default: FakeHls }))

const PLAYLIST = 'https://example.test/hls/meeting-1/live.m3u8'

function installDomShim() {
  const listeners = {}
  const video = {
    src: '', currentTime: 10, paused: false, seeking: false, readyState: 4, networkState: 1,
    error: null, playbackRate: 1, canPlayType: () => 'maybe',
    bufferedEnd: 13, // 앞쪽 버퍼 3초
    buffered: { length: 1, start: () => 0, end: () => video.bufferedEnd },
    seekable: { length: 0, start: () => 0, end: () => 0 },
    addEventListener: (event, handler) => { (listeners[event] ??= []).push(handler) },
    removeEventListener: (event, handler) => {
      listeners[event] = (listeners[event] ?? []).filter((h) => h !== handler)
    },
    dispatch: (event) => { for (const handler of listeners[event] ?? []) handler(event) },
    removeAttribute: () => {}, load: () => {}, pause: () => {}, play: () => Promise.resolve(),
  }
  const store = new Map()
  vi.stubGlobal('localStorage', {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  })
  vi.stubGlobal('document', {
    createElement: () => video, querySelector: () => video, documentElement: {}, addEventListener: () => {},
  })
  vi.stubGlobal('window', globalThis)
  vi.stubGlobal('MutationObserver', class { observe() {} disconnect() {} })
  return video
}

async function attach(mode) {
  const video = installDomShim()
  if (mode) localStorage.setItem('edumeet.hls.catchupMode', mode)
  const { attachHls } = await import('./hlsPlayer')
  const handle = await attachHls(video, PLAYLIST, {
    startAt: 0, onError: () => {}, onStatus: () => {}, onMetrics: () => {}, onQoe: () => {},
  })
  await vi.advanceTimersByTimeAsync(1200) // 1초 metrics 타이머 한 번
  return { video, handle }
}

describe('조건부 따라잡기 - hlsPlayer (#233)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    instances = []
    delete globalThis.__edumeetHlsConfig
    delete globalThis.__edumeetCatchup
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.useRealTimers()
    delete globalThis.__edumeetHlsConfig
    delete globalThis.__edumeetCatchup
  })

  it('조건이 맞으면 adaptive 가 1.1배로 올린다', async () => {
    const { handle } = await attach('adaptive')

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)
    expect(globalThis.__edumeetHlsConfig.catchupMode).toBe('adaptive')
    handle.destroy()
  })

  it('★ 조건이 깨지면 설정을 1로 내리고, video.playbackRate 도 직접 1로 되돌린다', async () => {
    const { video, handle } = await attach('adaptive')
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)

    // hls.js latency-controller 가 올려 둔 상태를 흉내 낸다.
    //   (진짜 hls.js 는 설정이 1이 돼도 이 값을 스스로 되돌리지 않는다 - 그래서 이 줄이 필요하다)
    video.playbackRate = 1.1
    video.bufferedEnd = video.currentTime + 0.5 // 앞쪽 버퍼 0.5초 → 조건 깨짐

    await vi.advanceTimersByTimeAsync(1200)

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1)
    expect(video.playbackRate).toBe(1)
    handle.destroy()
  })

  it('★ 재생 중 끊김(waiting) 뒤에는 다시 켜지 않는다', async () => {
    const { video, handle } = await attach('adaptive')
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)

    video.playbackRate = 1.1
    video.dispatch('playing') // 첫 화면이 떠야 waiting 이 끊김으로 잡힌다
    video.dispatch('waiting') // 재생 중 끊김
    await vi.advanceTimersByTimeAsync(1200)

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1)
    expect(video.playbackRate).toBe(1)

    // 30초를 조용히 지나고 버퍼도 회복되면 다시 켠다 - 하지만 껐다 켠 사이 10초는 기다린다.
    video.bufferedEnd = video.currentTime + 3
    await vi.advanceTimersByTimeAsync(31_000)
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)
    handle.destroy()
  })

  it('★ 첫 playing 전의 waiting 은 끊김이 아니다 - 시작 대기다 (검토 #233a 1)', async () => {
    const { video, handle } = await attach('adaptive')
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)

    // play() 직후 붙기 전. 브라우저가 paused=false 로 waiting 을 낸다.
    video.dispatch('waiting')
    await vi.advanceTimersByTimeAsync(1200)

    // 끊김으로 세면 여기서 30초 동안 꺼진다.
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)
    handle.destroy()
  })

  it('★ 탐색 중의 waiting 은 끊김이 아니다 - 사용자가 움직인 것이다 (검토 #233a 1)', async () => {
    const { video, handle } = await attach('adaptive')
    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)

    video.dispatch('playing')
    video.dispatch('seeking')
    video.dispatch('waiting')
    await vi.advanceTimersByTimeAsync(1200)

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.1)
    handle.destroy()
  })

  it('★ destroy 하면 마지막 tick 이후 구간도 마감된다 (검토 #233a 3)', async () => {
    // attach 가 fake 시계 1200ms 를 쓰고 tick 하나(1000ms)가 지나 있다.
    const { handle } = await attach('adaptive')
    await vi.advanceTimersByTimeAsync(400) // 다음 tick(2000ms) 전에서 멈춘다

    const before = globalThis.__edumeetCatchup
    expect(before.enabledMs + before.disabledMs).toBe(1000) // 마지막 tick 까지만

    handle.destroy()

    const counters = globalThis.__edumeetCatchup
    // 마감하지 않으면 1000 에서 멈춘다 - 플레이어가 살아 있던 1600ms 중 600ms 가 사라진다.
    expect(counters.enabledMs + counters.disabledMs).toBe(1600)
  })

  it('destroy 뒤에도 누적은 남는다 - 하네스가 화면을 닫기 직전에 읽는다', async () => {
    const { handle } = await attach('adaptive')
    handle.destroy()

    expect(globalThis.__edumeetCatchup).not.toBeUndefined()
  })

  it('모드를 안 정하면(기본) 아무것도 안 한다 - 제품 기본을 바꾸지 않는다', async () => {
    const { video, handle } = await attach(null)

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBeUndefined()
    expect(video.playbackRate).toBe(1)
    expect(globalThis.__edumeetCatchup).toBeUndefined()
    handle.destroy()
  })

  it('always 는 조건을 안 보고 maxLiveSyncPlaybackRate 키를 배율로 쓴다', async () => {
    // 배율 키는 인스턴스 생성 시점에 읽으므로 attach 전에 넣는다.
    const video = installDomShim()
    localStorage.setItem('edumeet.hls.catchupMode', 'always')
    localStorage.setItem('edumeet.hls.maxLiveSyncPlaybackRate', '1.25')
    const { attachHls } = await import('./hlsPlayer')
    const handle = await attachHls(video, PLAYLIST, {
      startAt: 0, onError: () => {}, onStatus: () => {}, onMetrics: () => {}, onQoe: () => {},
    })
    video.bufferedEnd = video.currentTime // 버퍼가 없어도 always 는 그대로
    await vi.advanceTimersByTimeAsync(1200)

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBe(1.25)
    expect(globalThis.__edumeetCatchup).toBeUndefined()
    handle.destroy()
  })

  it('허용한 값이 아니면 끔으로 떨어진다 - 제품 기본을 안 바꾼다', async () => {
    const { video, handle } = await attach('1.1') // 모드가 아니라 배율을 넣은 경우

    expect(instances.at(-1).config.maxLiveSyncPlaybackRate).toBeUndefined()
    expect(video.playbackRate).toBe(1)
    handle.destroy()
  })

  it('켜진 시간과 전환 횟수를 남긴다', async () => {
    const { video, handle } = await attach('adaptive')
    expect(globalThis.__edumeetCatchup.toggles).toBe(1)

    video.bufferedEnd = video.currentTime + 0.5
    await vi.advanceTimersByTimeAsync(1200) // 끈다 - 이 시점까지의 시간은 '켜져 있었다' 에 더해진다
    await vi.advanceTimersByTimeAsync(2000) // 꺼진 채로 조금 더

    expect(globalThis.__edumeetCatchup.toggles).toBe(2)
    expect(globalThis.__edumeetCatchup.enabledMs).toBeGreaterThan(0)
    expect(globalThis.__edumeetCatchup.disabledMs).toBeGreaterThan(0)
    handle.destroy()
  })
})
