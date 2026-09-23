import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPublisher } from './broadcastPublisher'

const api = vi.hoisted(() => ({ post: vi.fn(), delete: vi.fn() }))
vi.mock('@/utils/apiClient', () => ({ default: api }))

const flush = async () => {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

describe('발표자 방송 세션 복구', () => {
  const events = []
  const stream = { getTracks: () => [] }

  class FakeRecorder {
    static instances = []

    static isTypeSupported() {
      return true
    }

    constructor() {
      this.mimeType = 'video/webm;codecs=h264,opus'
      this.state = 'inactive'
      this.ondataavailable = null
      FakeRecorder.instances.push(this)
      events.push('recorder.create')
    }

    start(ms) {
      this.state = 'recording'
      events.push(`recorder.start:${ms}`)
    }

    stop() {
      this.state = 'inactive'
      events.push('recorder.stop')
    }

    emit(size = 1) {
      this.ondataavailable?.({ data: { size } })
    }
  }

  afterEach(() => {
    api.post.mockReset()
    api.delete.mockReset()
    events.length = 0
    FakeRecorder.instances = []
    vi.unstubAllGlobals()
  })

  function setup() {
    vi.stubGlobal('MediaRecorder', FakeRecorder)
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: vi.fn(async () => stream) } })
  }

  it('409 뒤 recorder.stop → 방송 시작 → 새 recorder.start 순서로 복구한다', async () => {
    setup()
    let chunkAttempt = 0
    api.post.mockImplementation(async (url) => {
      events.push(url.includes('/chunk') ? 'api.chunk' : 'api.start')
      if (url.includes('/chunk') && chunkAttempt++ === 0) {
        throw { response: { status: 409, data: { code: 'BROADCAST_NOT_ACTIVE' } } }
      }
      return { data: { playlistUrl: url.includes('/chunk') ? undefined : '/hls/new.m3u8' } }
    })

    const publisher = createPublisher(7, { random: () => 0, sleep: async () => {} })
    await publisher.start()
    FakeRecorder.instances[0].emit()
    await flush()

    expect(events).toEqual([
      'recorder.create', 'api.start', 'recorder.start:2000',
      'api.chunk', 'recorder.stop', 'recorder.create', 'api.start', 'recorder.start:2000',
    ])
  })

  it('재시작 중 들어온 조각은 버리고, 연속 실패는 백오프한다', async () => {
    setup()
    let release
    const waiting = new Promise((resolve) => { release = resolve })
    let starts = 0
    api.post.mockImplementation(async (url) => {
      events.push(url.includes('/chunk') ? 'api.chunk' : 'api.start')
      if (url.includes('/chunk')) {
        throw { response: { status: 409, data: { code: 'BROADCAST_NOT_ACTIVE' } } }
      }
      starts += 1
      if (starts === 2) throw new Error('새 세션도 아직 없다')
      return { data: { playlistUrl: '/hls/ready.m3u8' } }
    })

    const sleeps = []
    const publisher = createPublisher(7, {
      random: () => 0,
      sleep: async (ms) => {
        sleeps.push(ms)
        await waiting
      },
    })
    await publisher.start()
    FakeRecorder.instances[0].emit()
    await vi.waitFor(() => expect(sleeps).toEqual([0]))

    // 기존 recorder의 늦은 dataavailable은 새 세션에 섞여서는 안 된다.
    FakeRecorder.instances[0].emit()
    expect(api.post).toHaveBeenCalledTimes(2)

    release()
    await flush()
    expect(sleeps).toEqual([0, 1_000])
  })

  it('이전 recorder 세대의 늦은 409는 새 세션을 다시 시작하지 않는다', async () => {
    setup()
    const pending = []
    api.post.mockImplementation(async (url) => {
      events.push(url.includes('/chunk') ? 'api.chunk' : 'api.start')
      if (url.includes('/chunk')) {
        return new Promise((resolve, reject) => pending.push({ resolve, reject }))
      }
      return { data: { playlistUrl: '/hls/ready.m3u8' } }
    })

    const statuses = []
    const publisher = createPublisher(7, {
      onStatus: (value) => statuses.push(value),
      sleep: async () => {},
    })
    await publisher.start()
    FakeRecorder.instances[0].emit()
    FakeRecorder.instances[0].emit()
    await vi.waitFor(() => expect(pending).toHaveLength(2))

    pending[0].reject({ response: { status: 409, data: { code: 'BROADCAST_NOT_ACTIVE' } } })
    await vi.waitFor(() => expect(FakeRecorder.instances).toHaveLength(2))
    pending[1].reject({ response: { status: 409, data: { code: 'BROADCAST_NOT_ACTIVE' } } })
    await flush()

    expect(api.post.mock.calls.filter(([url]) => !url.includes('/chunk'))).toHaveLength(2)
    expect(statuses.filter((value) => value.state === 'reconnecting')).toHaveLength(1)
    expect(publisher.stats().failed).toBe(0)
  })

  it('첫 조각이 202가 되기 전에는 시작 성공만으로 백오프를 초기화하지 않는다', async () => {
    setup()
    let acceptNext = false
    api.post.mockImplementation(async (url) => {
      if (url.includes('/chunk')) {
        if (acceptNext) {
          acceptNext = false
          return { status: 202, data: {} }
        }
        throw { response: { status: 409, data: { code: 'BROADCAST_NOT_ACTIVE' } } }
      }
      return { data: { playlistUrl: '/hls/ready.m3u8' } }
    })

    const sleeps = []
    const publisher = createPublisher(7, {
      random: () => 0,
      sleep: async (ms) => { sleeps.push(ms) },
    })
    await publisher.start()

    const trigger = async (recorderIndex, expectedDelay) => {
      FakeRecorder.instances[recorderIndex].emit()
      await vi.waitFor(() => expect(sleeps.at(-1)).toBe(expectedDelay))
      await vi.waitFor(() => expect(FakeRecorder.instances.length).toBeGreaterThan(recorderIndex + 1))
    }

    await trigger(0, 0)
    await trigger(1, 1_000)
    await trigger(2, 2_000)
    await trigger(3, 4_000)
    await trigger(4, 8_000)

    // 이제서야 첫 조각을 202로 수락시키면 다음 장애는 다시 첫 대기다.
    acceptNext = true
    FakeRecorder.instances[5].emit()
    await flush()
    FakeRecorder.instances[5].emit()
    await vi.waitFor(() => expect(sleeps.at(-1)).toBe(0))
  })
})
