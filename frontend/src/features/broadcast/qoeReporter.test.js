import { describe, it, expect } from 'vitest'
import { classifyFailure, classifyOutcome, createQoeReporter, RETRY_LIMIT } from './qoeReporter'

const NON_EMPTY = { playingMs: 27_000, stallMs: 3_000, stallCount: 1, startupMs: null, errors: 0 }
const EMPTY = { playingMs: 0, stallMs: 0, stallCount: 0, startupMs: null, errors: 0 }

function harness(snapshot = () => NON_EMPTY) {
  const env = { t: 1000, tick: null, intervalMs: null, cleared: [] }
  const calls = { send: [], sendFinal: [] }
  const tracker = { snapshot }

  const reporter = createQoeReporter({
    meetingId: 7,
    tracker,
    send: (meetingId, body) => calls.send.push({ meetingId, body }),
    sendFinal: (meetingId, body) => calls.sendFinal.push({ meetingId, body }),
    intervalMs: 30_000,
    setInterval: (fn, ms) => { env.tick = fn; env.intervalMs = ms; return 1 },
    clearInterval: (id) => env.cleared.push(id),
    now: () => env.t,
    sessionId: 'sess-fixed',
    native: false,
  })
  return { env, calls, reporter }
}

/** axios 가 네트워크 실패에 주는 모양. */
const networkError = () =>
  Object.assign(new Error('network'), { code: 'ERR_NETWORK', request: {} })

function deferred() {
  let resolve
  const promise = new Promise((r) => { resolve = r })
  return { promise, resolve }
}

/**
 * 결과를 순서대로 정해 주는 하네스.
 *   'reject'      네트워크 실패(ERR_NETWORK)      'requestOnly'  응답 없이 request 만
 *   'refreshFail' 토큰 갱신 실패 모양(네트워크 아님)  숫자          그 코드로 거부(axios 모양)
 *   {status:N}    응답이 온 경우(fetch 모양)        그 외/생략     성공
 */
function scriptedHarness({ outcomes = [], fallback = undefined, snapshot = () => NON_EMPTY } = {}) {
  const env = { t: 1000, tick: null, cleared: [] }
  const calls = { send: [], sendFinal: [] }
  let attempt = 0

  const settle = (recorder) => (meetingId, body) => {
    recorder.push({ meetingId, body })
    const outcome = attempt < outcomes.length ? outcomes[attempt] : fallback
    attempt += 1
    if (outcome === 'reject') return Promise.reject(networkError())
    if (outcome === 'requestOnly') return Promise.reject({ request: {} })
    if (outcome === 'refreshFail') {
      return Promise.reject(Object.assign(new Error('refresh failed'), { code: 'ERR_BAD_REQUEST' }))
    }
    if (typeof outcome === 'number') return Promise.reject({ response: { status: outcome } })
    if (outcome && typeof outcome === 'object') return Promise.resolve(outcome)
    return undefined
  }

  const reporter = createQoeReporter({
    meetingId: 7,
    tracker: { snapshot },
    send: settle(calls.send),
    sendFinal: settle(calls.sendFinal),
    intervalMs: 30_000,
    setInterval: (fn) => { env.tick = fn; return 1 },
    clearInterval: () => {},
    now: () => env.t,
    sessionId: 'sess-fixed',
    native: false,
  })
  return { env, calls, reporter }
}

describe('판정', () => {
  it('응답이 있으면 그 코드로 판정한다 (axios 경로)', () => {
    expect(classifyFailure(networkError())).toBe('retry')
    expect(classifyFailure({ response: { status: 500 } })).toBe('retry')
    expect(classifyFailure({ response: { status: 400 } })).toBe('drop')
    expect(classifyFailure({ response: { status: 202 } })).toBe('sent')
  })

  it('응답이 없으면 전형적인 네트워크 실패만 재전송한다', () => {
    expect(classifyFailure({ code: 'ERR_NETWORK' })).toBe('retry')
    expect(classifyFailure({ code: 'ECONNABORTED' })).toBe('retry')
    expect(classifyFailure({ request: {}, code: 'ETIMEDOUT' })).toBe('retry')
    expect(classifyFailure({ request: {} })).toBe('retry')

    // ★ apiClient 의 401 인터셉터가 토큰 갱신에 실패하며 reject 하는 모양.
    //   응답이 없다고 무조건 네트워크 실패로 보면 다시 보내게 된다 - 그건 서버가 받은 요청이다.
    expect(classifyFailure(new Error('refresh failed'))).toBe('drop')
    expect(classifyFailure({ code: 'ERR_BAD_REQUEST' })).toBe('drop')

    // fetch(sendKeepalive)의 reject 는 그 자체로 네트워크 실패다
    expect(classifyFailure(new TypeError('Failed to fetch'), { transport: 'fetch' })).toBe('retry')
  })

  it('응답이 오면 상태 코드로 판정한다 (fetch 경로)', () => {
    expect(classifyOutcome({ status: 503 })).toBe('retry')
    expect(classifyOutcome({ status: 404 })).toBe('drop')
    expect(classifyOutcome({ status: 202 })).toBe('sent')
    // status 가 없는 값(axios resolve 는 2xx 뿐이다)은 성공으로 본다
    expect(classifyOutcome(undefined)).toBe('sent')
  })
})

describe('시청 품질 리포터', () => {
  it('주기 타이머가 울리면 이전 보고부터 흐른 시간을 실어 보낸다', () => {
    const { env, calls, reporter } = harness()
    reporter.start()
    env.t = 31_000
    env.tick()

    expect(env.intervalMs).toBe(30_000)
    expect(calls.send).toHaveLength(1)
    expect(calls.send[0].meetingId).toBe(7)
    expect(calls.send[0].body.intervalMs).toBe(30_000)
    expect(calls.send[0].body.seq).toBe(1)
    expect(calls.send[0].body.final).toBe(false)
  })

  it('모든 값이 0 인 주기 보고는 보내지 않는다', () => {
    const { env, calls, reporter } = harness(() => EMPTY)
    reporter.start()
    env.t = 31_000
    env.tick()

    expect(calls.send).toHaveLength(0)
  })

  it('★ 빈 주기를 건너뛰어도 다음 보고의 intervalMs 는 한 주기 길이다', () => {
    let snap = EMPTY
    const { env, calls, reporter } = harness(() => snap)
    reporter.start()
    env.t = 31_000
    env.tick()          // 빈 보고 → 전송은 안 하지만 시계는 당긴다
    snap = NON_EMPTY
    env.t = 61_000
    env.tick()

    expect(calls.send).toHaveLength(1)
    expect(calls.send[0].body.intervalMs).toBe(30_000)
  })

  it('보고마다 seq 가 늘어난다', () => {
    const { env, calls, reporter } = harness()
    reporter.start()
    env.t = 31_000
    env.tick()
    env.t = 61_000
    env.tick()

    expect(calls.send.map((c) => c.body.seq)).toEqual([1, 2])
  })

  it('마지막 전송은 sendFinal 로 가고, 두 번 불러도 한 번만 나간다', () => {
    const { calls, reporter } = harness()
    reporter.finalFlush()
    reporter.finalFlush()

    expect(calls.send).toHaveLength(0)
    expect(calls.sendFinal).toHaveLength(1)
    expect(calls.sendFinal[0].body.final).toBe(true)
  })

  it('값이 비어 있어도 마지막 전송은 나간다', () => {
    const { calls, reporter } = harness(() => EMPTY)
    reporter.finalFlush()

    expect(calls.sendFinal).toHaveLength(1)
    expect(calls.sendFinal[0].body.final).toBe(true)
  })

  it('전송이 실패해도 예외가 새지 않는다', () => {
    const throwing = createQoeReporter({
      meetingId: 7,
      tracker: { snapshot: () => NON_EMPTY },
      send: () => { throw new Error('network') },
      sendFinal: () => { throw new Error('network') },
      setInterval: () => 1,
      clearInterval: () => {},
      now: () => 1000,
      sessionId: 'sess-fixed',
    })
    const rejecting = createQoeReporter({
      meetingId: 7,
      tracker: { snapshot: () => NON_EMPTY },
      send: () => Promise.reject(new Error('network')),
      sendFinal: () => Promise.reject(new Error('network')),
      setInterval: () => 1,
      clearInterval: () => {},
      now: () => 1000,
      sessionId: 'sess-fixed',
    })

    expect(() => throwing.flush()).not.toThrow()
    expect(() => rejecting.finalFlush()).not.toThrow()
  })

  it('stop 하면 타이머를 정리한다', () => {
    const { env, reporter } = harness()
    reporter.start()
    reporter.stop()

    expect(env.cleared).toEqual([1])
  })

  it('본문에 사용자·이메일 같은 신원 정보를 싣지 않는다', () => {
    const { calls, reporter } = harness()
    reporter.start()
    calls.send.length = 0
    reporter.flush()

    expect(Object.keys(calls.send[0].body).sort()).toEqual([
      'errors', 'final', 'intervalMs', 'native', 'playingMs',
      'seq', 'sessionId', 'stallCount', 'stallMs', 'startupMs',
    ])
  })

  it('★ 실패한 보고는 다음 주기에 같은 본문(같은 seq)으로 먼저 나가고, 그다음 새 보고가 나간다', async () => {
    const { env, calls, reporter } = scriptedHarness({ outcomes: ['reject'] })
    reporter.start()

    env.t = 31_000
    await env.tick()
    expect(reporter.stats().seqs).toEqual([1])

    env.t = 61_000
    await env.tick()

    expect(calls.send.map((c) => c.body.seq)).toEqual([1, 1, 2])
    expect(calls.send[1].body).toEqual(calls.send[0].body) // 본문 그대로
    expect(reporter.stats().queued).toBe(0)
  })

  it('★ 5xx 는 재전송하고 4xx 는 버린다', async () => {
    const five = scriptedHarness({ outcomes: [500] })
    five.reporter.start()
    five.env.t = 31_000
    await five.env.tick()
    expect(five.reporter.stats().seqs).toEqual([1])

    const fetchFive = scriptedHarness({ outcomes: [{ status: 503 }] })
    fetchFive.reporter.start()
    fetchFive.env.t = 31_000
    await fetchFive.env.tick()
    expect(fetchFive.reporter.stats().seqs).toEqual([1])

    const four = scriptedHarness({ outcomes: [400] })
    four.reporter.start()
    four.env.t = 31_000
    await four.env.tick()
    expect(four.reporter.stats().queued).toBe(0)
  })

  it('★ 응답 없는 axios 실패도 전형적 네트워크 실패만 재전송한다 (갱신 실패는 버린다)', async () => {
    const net = scriptedHarness({ outcomes: ['reject'] })
    net.reporter.start()
    net.env.t = 31_000
    await net.env.tick()
    expect(net.reporter.stats().seqs).toEqual([1]) // ERR_NETWORK → 큐에 남는다

    const requestOnly = scriptedHarness({ outcomes: ['requestOnly'] })
    requestOnly.reporter.start()
    requestOnly.env.t = 31_000
    await requestOnly.env.tick()
    expect(requestOnly.reporter.stats().seqs).toEqual([1]) // 응답 없이 request 만 → 큐에 남는다

    const refreshFail = scriptedHarness({ outcomes: ['refreshFail'] })
    refreshFail.reporter.start()
    refreshFail.env.t = 31_000
    await refreshFail.env.tick()
    expect(refreshFail.reporter.stats().queued).toBe(0) // 토큰 갱신 실패 모양 → 버린다
  })

  it('★ 느린 전송 중에 주기가 두 번 돌아도 같은 seq 가 두 번 나가지 않는다', async () => {
    const env = { t: 1000, tick: null }
    const calls = { send: [] }
    const snapshots = []
    const pending = []
    const reporter = createQoeReporter({
      meetingId: 7,
      tracker: { snapshot: () => { snapshots.push(1); return { ...NON_EMPTY } } },
      send: (meetingId, body) => {
        calls.send.push({ meetingId, body })
        const wait = deferred()
        pending.push(wait)
        return wait.promise
      },
      sendFinal: () => undefined,
      intervalMs: 30_000,
      setInterval: (fn) => { env.tick = fn; return 1 },
      clearInterval: () => {},
      now: () => env.t,
      sessionId: 'sess-fixed',
    })
    reporter.start()

    env.t = 31_000
    const first = env.tick() // seq1 전송 시작 (아직 안 끝난다)
    expect(calls.send.map((c) => c.body.seq)).toEqual([1])
    expect(snapshots).toHaveLength(1)

    env.t = 61_000
    await env.tick() // 전송 중 → 이번 주기는 건너뛴다 (snapshot 도 하지 않는다)
    expect(calls.send.map((c) => c.body.seq)).toEqual([1])
    expect(snapshots).toHaveLength(1)
    expect(reporter.stats().queued).toBe(0)

    pending[0].resolve({ status: 202 })
    await first

    env.t = 91_000
    env.tick() // seq1 은 이미 나갔다. 같은 seq 가 다시 나가면 안 된다
    expect(calls.send.map((c) => c.body.seq)).toEqual([1, 2])
    // 건너뛴 주기만큼 간격이 늘어난다 - 그래도 서버 상한(120초) 안이다
    expect(calls.send[1].body.intervalMs).toBe(60_000)
  })

  it('★ 큐 상한 10 — 11번째 실패에 가장 오래된 것이 빠지고 버린 수가 1 이 된다', async () => {
    const { env, calls, reporter } = scriptedHarness({ fallback: 'reject' })
    reporter.start()

    for (let i = 0; i < 11; i += 1) {
      env.t += 30_000
      await env.tick()
    }

    expect(reporter.stats().queued).toBe(RETRY_LIMIT)
    expect(reporter.stats().seqs).toEqual([2, 3, 4, 5, 6, 7, 8, 9, 10, 11])
    expect(reporter.stats().dropped).toBe(1)
    // 매 주기 가장 오래된 것 하나만 시도하고 멈춘다
    expect(calls.send).toHaveLength(11)
  })

  it('★ 큐를 보내다 또 실패하면 거기서 멈추고 나머지는 큐에 남는다', async () => {
    const { env, calls, reporter } = scriptedHarness({ fallback: 'reject' })
    reporter.start()

    env.t = 31_000
    await env.tick() // seq1 실패 → 큐 [1]
    env.t = 61_000
    await env.tick() // seq1 재시도 실패 → 큐 [1, 2]

    const before = calls.send.length
    env.t = 91_000
    await env.tick() // seq1 재시도 실패 → 여기서 멈춘다

    expect(calls.send.length - before).toBe(1)
    expect(calls.send.at(-1).body.seq).toBe(1)
    expect(reporter.stats().seqs).toEqual([1, 2, 3])
  })

  it('★ finalFlush 는 큐 → final 순서로 sendFinal 을 부른다', async () => {
    const { env, calls, reporter } = scriptedHarness({ outcomes: ['reject'] })
    reporter.start()

    env.t = 31_000
    await env.tick() // seq1 실패 → 큐
    await reporter.finalFlush()

    expect(calls.send).toHaveLength(1) // 주기 전송은 실패한 한 건뿐
    expect(calls.sendFinal.map((c) => c.body.seq)).toEqual([1, 2])
    expect(calls.sendFinal[0].body.final).toBe(false) // 큐는 원래 본문 그대로
    expect(calls.sendFinal[1].body.final).toBe(true)
  })

  it('★ 같은 seq 가 큐에 두 번 들어가지 않는다', async () => {
    const { env, reporter } = scriptedHarness({ fallback: 'reject' })
    reporter.start()

    env.t = 31_000
    await env.tick()
    env.t = 61_000
    await env.tick()

    const { seqs } = reporter.stats()
    expect(seqs).toEqual([1, 2])
    expect(new Set(seqs).size).toBe(seqs.length)
  })

  it('멈춘 뒤 도착한 실패는 큐에 넣지 않는다', async () => {
    const { env, reporter } = scriptedHarness({ fallback: 'reject' })
    reporter.start()

    env.t = 31_000
    const pass = env.tick()
    reporter.stop()
    await pass

    expect(reporter.stats().queued).toBe(0)
  })
})
