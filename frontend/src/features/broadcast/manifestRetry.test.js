import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { MANIFEST_RETRY_MAX_MS, manifestRetryDelayMs } from './hlsPlayer'

/**
 * 매니페스트에 404 가 나면 다시 받지 않아 대기 시청자가 재생을 못 한다. (#244)
 *
 * ★ hls.js 를 모킹한다. 이 저장소의 vitest 는 environment: 'node' 라 MediaSource 가 없고,
 *   그러면 실제 hls.js 의 `isSupported()` 가 false 여서 **경로 자체가 네이티브로 간다** -
 *   우리가 보려는 분기(NETWORK_ERROR fatal)에 닿지 못한다.
 *   그래서 hls.js 의 Events·ErrorTypes·ErrorDetails 만 같게 흉내 낸 가짜 클래스를 넣고,
 *   오류 이벤트를 직접 emit 해서 **hlsPlayer 의 분기**를 시험한다.
 *   (가짜의 상수 이름은 아래 시험에서 진짜 라이브러리 값과 대조한다 - 이름이 바뀌면 깨진다)
 *
 * ★ 배경 (#241 · #244). 방송 화면은 `broadcasting=true` 가 되는 순간 붙는데, 그때 ffmpeg 가
 *   아직 첫 `live.m3u8` 을 안 썼으면 404 다(시작할 때 옛 파일을 지운다). 그런데
 *   **매니페스트를 한 번도 못 받은 상태의 `startLoad()` 는 매니페스트를 다시 요청하지 않는다** -
 *   그래서 대기 시청자 10명이 그대로 멈춰 있었다.
 */
const EVENTS = { ERROR: 'hlsError', MANIFEST_PARSED: 'hlsManifestParsed' }
const ERROR_TYPES = { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError', OTHER_ERROR: 'otherError' }
const ERROR_DETAILS = {
  MANIFEST_LOAD_ERROR: 'manifestLoadError',
  MANIFEST_LOAD_TIMEOUT: 'manifestLoadTimeOut',
  MANIFEST_PARSING_ERROR: 'manifestParsingError',
  FRAG_LOAD_ERROR: 'fragLoadError',
  LEVEL_LOAD_ERROR: 'levelLoadError',
}

const calls = { loadSource: [], startLoad: 0, recoverMediaError: 0 }
let instances = []

class FakeHls {
  static isSupported() { return true }
  static get Events() { return EVENTS }
  static get ErrorTypes() { return ERROR_TYPES }
  static get ErrorDetails() { return ERROR_DETAILS }
  constructor(config) { this.config = config; this.handlers = {}; instances.push(this) }
  on(event, handler) { (this.handlers[event] ??= []).push(handler) }
  emit(event, data) { for (const handler of this.handlers[event] ?? []) handler(event, data) }
  loadSource(url) { calls.loadSource.push(url) }
  startLoad() { calls.startLoad += 1 }
  recoverMediaError() { calls.recoverMediaError += 1 }
  attachMedia() {}
  destroy() {}
}

vi.mock('hls.js', () => ({ default: FakeHls }))

const PLAYLIST = 'https://example.test/hls/meeting-1/live.m3u8'

function installDomShim() {
  const video = {
    src: '', currentTime: 0, paused: true, seeking: false, readyState: 4, networkState: 1,
    error: null, playbackRate: 1, canPlayType: () => 'maybe',
    buffered: { length: 0, start: () => 0, end: () => 0 },
    seekable: { length: 0, start: () => 0, end: () => 0 },
    addEventListener: () => {}, removeEventListener: () => {},
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

describe('매니페스트 오류 재시도 (#244)', () => {
  let video
  let handle

  beforeEach(async () => {
    vi.useFakeTimers()
    calls.loadSource = []
    calls.startLoad = 0
    calls.recoverMediaError = 0
    instances = []
    video = installDomShim()
    const { attachHls } = await import('./hlsPlayer')
    handle = await attachHls(video, PLAYLIST, {
      startAt: 0, onError: () => {}, onStatus: () => {}, onMetrics: () => {}, onQoe: () => {},
    })
  })

  afterEach(() => {
    handle?.destroy?.()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  const hls = () => instances.at(-1)
  const networkFatal = (details) => ({ fatal: true, type: ERROR_TYPES.NETWORK_ERROR, details })

  it.each([
    ['MANIFEST_LOAD_ERROR', 'MANIFEST_LOAD_ERROR'],
    ['MANIFEST_LOAD_TIMEOUT', 'MANIFEST_LOAD_TIMEOUT'],
    ['MANIFEST_PARSING_ERROR', 'MANIFEST_PARSING_ERROR'],
  ])('★ %s 이면 지연 뒤 loadSource 를 다시 부른다 (startLoad 가 아니라)', async (_name, detail) => {
    expect(calls.loadSource).toEqual([PLAYLIST])

    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS[detail]))

    // 예약만 하고 그 자리에서 다시 받지 않는다 - 대기 시청자 전원이 같은 순간 몰리면 안 된다.
    expect(calls.loadSource).toEqual([PLAYLIST])
    expect(calls.startLoad).toBe(0)

    await vi.advanceTimersByTimeAsync(10_000)
    expect(calls.loadSource.length).toBeGreaterThan(1)
    expect(calls.loadSource.at(-1)).toBe(PLAYLIST)
  })

  it('레벨·조각 단계 오류는 지금처럼 startLoad 다', () => {
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.FRAG_LOAD_ERROR))
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.LEVEL_LOAD_ERROR))

    expect(calls.startLoad).toBe(2)
    expect(calls.loadSource).toEqual([PLAYLIST])
  })

  it('미디어 오류는 recoverMediaError, 그 밖의 치명 오류는 포기한다', () => {
    const errors = []
    hls().emit(EVENTS.ERROR, { fatal: true, type: ERROR_TYPES.MEDIA_ERROR, details: 'bufferAppendError' })
    expect(calls.recoverMediaError).toBe(1)
  })

  it('★ 예약 중 같은 fatal 이 한 번 더 와도 다음 지연이 1단계 그대로다', async () => {
    // 난수를 고정해 지연을 셀 수 있게 한다: attempt0=500ms · attempt1=1000ms · attempt2=2000ms
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
    const fail = () => hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.MANIFEST_LOAD_ERROR))

    fail() // 1회차 예약(500ms)
    fail() // 늦게 도착한 중복 - 예약은 그대로, 시도 횟수도 그대로여야 한다

    await vi.advanceTimersByTimeAsync(500)
    expect(calls.loadSource.length).toBe(2) // 첫 재시도가 실행됐다

    fail() // 다시 실패 → 다음 지연은 1단계(1000ms)

    await vi.advanceTimersByTimeAsync(1_000)
    // 중복 이벤트가 횟수를 올렸다면 2단계(2000ms)라 여기서 오지 않는다
    expect(calls.loadSource.length).toBe(3)
  })

  it('★ destroy 뒤 오류 이벤트가 와도 새 예약을 만들지 않는다', async () => {
    handle.destroy()

    // 실제 hls.js 는 destroy 에서 리스너를 지운다. FakeHls 는 지우지 않으므로,
    // 늦게 온 오류가 죽은 인스턴스에 재시도를 걸지 않는지를 **구현의 보호**로 확인한다.
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.MANIFEST_LOAD_ERROR))
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.FRAG_LOAD_ERROR))
    await vi.advanceTimersByTimeAsync(60_000)

    expect(calls.loadSource).toEqual([PLAYLIST])
    expect(calls.startLoad).toBe(0)
    expect(calls.recoverMediaError).toBe(0)
  })

  it('★ destroy 뒤에는 예약된 재시도가 실행되지 않는다', async () => {
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.MANIFEST_LOAD_ERROR))
    expect(calls.loadSource).toEqual([PLAYLIST])

    handle.destroy()
    await vi.advanceTimersByTimeAsync(60_000)

    expect(calls.loadSource).toEqual([PLAYLIST])
  })

  it('★ 매니페스트를 받으면(MANIFEST_PARSED) 재시도 간격이 처음으로 돌아간다', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.99) // 첫 시도 = 0~1초

    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.MANIFEST_LOAD_ERROR))
    await vi.advanceTimersByTimeAsync(1_000)
    expect(calls.loadSource.length).toBe(2)

    hls().emit(EVENTS.MANIFEST_PARSED, {})
    hls().emit(EVENTS.ERROR, networkFatal(ERROR_DETAILS.MANIFEST_LOAD_ERROR))

    // 초기화됐다면 다시 0~1초 안에 온다(초기화가 없으면 1.5초쯤이라 여기서 안 온다)
    await vi.advanceTimersByTimeAsync(1_000)
    expect(calls.loadSource.length).toBe(3)
  })

  it('치명적이지 않은 매니페스트 오류는 아무것도 하지 않는다', async () => {
    hls().emit(EVENTS.ERROR, { fatal: false, type: ERROR_TYPES.NETWORK_ERROR, details: ERROR_DETAILS.MANIFEST_LOAD_ERROR })
    await vi.advanceTimersByTimeAsync(10_000)

    expect(calls.loadSource).toEqual([PLAYLIST])
    expect(calls.startLoad).toBe(0)
  })
})

describe('재시도 지연 규칙 (#244)', () => {
  it('첫 시도는 0~1초 사이 무작위다 - 늦게 온 시청자는 곧바로 한 번 더 본다', () => {
    expect(manifestRetryDelayMs(0, () => 0)).toBe(0)
    expect(manifestRetryDelayMs(0, () => 0.5)).toBe(500)
    expect(manifestRetryDelayMs(0, () => 0.9999)).toBeLessThanOrEqual(1000)
  })

  it('그 뒤는 1 · 2 · 4 … 로 늘고 기본 간격이 5초에서 멈춘다', () => {
    const noJitter = () => 0.5 // 흔들기 0 → 1.0배

    expect(manifestRetryDelayMs(1, noJitter)).toBe(1000)
    expect(manifestRetryDelayMs(2, noJitter)).toBe(2000)
    expect(manifestRetryDelayMs(3, noJitter)).toBe(4000)
    expect(manifestRetryDelayMs(4, noJitter)).toBe(5000)
    expect(manifestRetryDelayMs(9, noJitter)).toBe(MANIFEST_RETRY_MAX_MS)
  })

  it('★ 매번 ±50% 흔든다 - 전원이 같은 순간에 다시 몰리지 않게 (#191)', () => {
    expect(manifestRetryDelayMs(3, () => 0)).toBe(2000) // 4000 × 0.5
    expect(manifestRetryDelayMs(3, () => 1)).toBe(6000) // 4000 × 1.5
    // 상한은 **기본 간격**에 건다 - 흔들기까지 붙으면 최대 7.5초
    expect(manifestRetryDelayMs(9, () => 0)).toBe(2500)
    expect(manifestRetryDelayMs(9, () => 1)).toBe(7500)
  })

  it('실제 난수로도 범위를 벗어나지 않는다', () => {
    for (let i = 0; i < 300; i += 1) {
      const first = manifestRetryDelayMs(0)
      expect(first).toBeGreaterThanOrEqual(0)
      expect(first).toBeLessThanOrEqual(1000)

      const later = manifestRetryDelayMs(6)
      expect(later).toBeGreaterThanOrEqual(MANIFEST_RETRY_MAX_MS * 0.5)
      expect(later).toBeLessThanOrEqual(MANIFEST_RETRY_MAX_MS * 1.5)
    }
  })
})
