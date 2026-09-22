import { describe, it, expect } from 'vitest'
import { createQoeReporter } from './qoeReporter'

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
})
