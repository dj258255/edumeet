import { describe, it, expect } from 'vitest'
import {
  createNativeRecovery,
  nextRecoveryDelay,
  transitionRecovery,
  RECOVERY_MAX_MS,
} from './nativeRecovery'

describe('네이티브 HLS 복구', () => {
  it('대기 기준값은 15초에서 멈추고, 그 뒤에도 ±30% 지터를 유지한다', () => {
    expect(nextRecoveryDelay(0, () => 0)).toBe(700)
    expect(nextRecoveryDelay(0, () => 1)).toBe(1300)
    expect(nextRecoveryDelay(1, () => 0.5)).toBe(2000)
    expect(nextRecoveryDelay(20, () => 0)).toBe(10_500)
    expect(nextRecoveryDelay(20, () => 0.5)).toBe(RECOVERY_MAX_MS)
    expect(nextRecoveryDelay(20, () => 1)).toBe(19_500)
  })

  it('상태 전이는 오류·재시도에서 횟수를 늘리고 playing 에서 초기화한다', () => {
    const waiting = transitionRecovery({}, 'error', 10)
    const retrying = transitionRecovery(waiting, 'retry', 20)

    expect(waiting).toMatchObject({ phase: 'waiting', attempt: 0, changedAt: 10 })
    expect(retrying).toMatchObject({ phase: 'retrying', attempt: 1, changedAt: 20 })
    expect(transitionRecovery(retrying, 'playing', 30))
      .toMatchObject({ phase: 'idle', attempt: 0, changedAt: 30 })
  })

  it('오류마다 재시도하고 playing 이 오면 예약을 취소한다', () => {
    const env = { now: 100, timer: null, cleared: [], retried: [], statuses: [] }
    const recovery = createNativeRecovery({
      now: () => env.now,
      random: () => 0.5,
      setTimeout: (fn, ms) => { env.timer = { fn, ms }; return env.timer },
      clearTimeout: (timer) => env.cleared.push(timer),
      onRetry: (value) => env.retried.push(value),
      onStatus: (value) => env.statuses.push(value),
    })

    recovery.failed()
    recovery.failed()
    expect(env.timer.ms).toBe(1000)
    expect(env.statuses).toHaveLength(1)

    env.now = 1100
    env.timer.fn()
    expect(env.retried[0].attempt).toBe(1)

    recovery.failed()
    expect(env.timer.ms).toBe(2000)
    recovery.playing()
    expect(env.cleared).toHaveLength(1)
    expect(env.statuses.at(-1)).toEqual({ state: 'playing' })
  })

  it('onStatus 안에서 destroy 해도 타이머와 onRetry 를 만들지 않는다', () => {
    const env = { timerCalls: 0, retried: 0 }
    let recovery
    recovery = createNativeRecovery({
      setTimeout: () => { env.timerCalls += 1 },
      onRetry: () => { env.retried += 1 },
      onStatus: () => recovery.destroy(),
    })

    recovery.failed()

    expect(env.timerCalls).toBe(0)
    expect(env.retried).toBe(0)
  })
})
