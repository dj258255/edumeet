import { describe, it, expect } from 'vitest'
import {
  nextPublisherRecoveryDelay,
  transitionPublisherRecovery,
  PUBLISHER_RECOVERY_MAX_BACKOFF_MS,
} from './publisherRecovery'

describe('발표자 방송 재시작', () => {
  it('첫 대기는 0~1.5초이고 연속 실패는 1·2·4·8초로 늦어진다', () => {
    expect(nextPublisherRecoveryDelay(0, () => 0)).toBe(0)
    expect(nextPublisherRecoveryDelay(0, () => 1)).toBe(1_500)
    expect(nextPublisherRecoveryDelay(1, () => 0)).toBe(1_000)
    expect(nextPublisherRecoveryDelay(2, () => 0)).toBe(2_000)
    expect(nextPublisherRecoveryDelay(3, () => 0)).toBe(4_000)
    expect(nextPublisherRecoveryDelay(20, () => 1)).toBe(PUBLISHER_RECOVERY_MAX_BACKOFF_MS)
  })

  it('재시작 실패마다 시도 횟수를 늘리고 성공하면 초기화한다', () => {
    const waiting = transitionPublisherRecovery({}, 'failure', 10)
    const retrying = transitionPublisherRecovery(waiting, 'retry', 20)
    const failedAgain = transitionPublisherRecovery(retrying, 'failure', 30)

    expect(waiting).toMatchObject({ phase: 'waiting', attempt: 0, changedAt: 10 })
    expect(retrying).toMatchObject({ phase: 'retrying', attempt: 0, changedAt: 20 })
    expect(failedAgain).toMatchObject({ phase: 'waiting', attempt: 1, changedAt: 30 })
    const started = transitionPublisherRecovery(failedAgain, 'restarted', 40)
    expect(started).toMatchObject({ phase: 'awaiting-first-chunk', attempt: 1, changedAt: 40 })
    expect(transitionPublisherRecovery(started, 'accepted', 50))
      .toMatchObject({ phase: 'idle', attempt: 0, changedAt: 50 })
  })
})
