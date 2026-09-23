import { describe, it, expect } from 'vitest'
import {
  CATCHUP_MIN_AHEAD_SEC,
  CATCHUP_MIN_OFF_MS,
  CATCHUP_MIN_QUIET_MS,
  decideCatchup,
} from './catchupPolicy'

/**
 * 조건부 따라잡기 정책. (#233)
 *
 * ★ 이 함수가 있는 이유 - 재생 속도를 올리면 정상망에서는 지연이 절반이 되지만
 *   제한망에서는 끊김이 30~65% 늘었다(그리드 16회차). 그래서 조건을 다 만족할 때만 켠다.
 */
const NOW = 1_000_000

const sample = (over = {}) => ({
  now: NOW,
  aheadSec: 3,
  bandwidthBps: 4_000_000,
  levelBitrateBps: 1_000_000,
  ...over,
})

describe('따라잡기 정책 (#233)', () => {
  it('★ 세 조건이 다 맞으면 켠다', () => {
    const state = { enabled: false, lastStallAt: null, disabledAt: null }

    expect(decideCatchup(state, sample())).toBe(true)
  })

  it('★ 최근 30초 안에 끊겼으면 켜지 않는다 - 제한망 창의 여파', () => {
    const state = { enabled: false, lastStallAt: NOW - CATCHUP_MIN_QUIET_MS + 1, disabledAt: null }
    expect(decideCatchup(state, sample())).toBe(false)

    // 30초를 넘기면 켠다
    expect(decideCatchup({ ...state, lastStallAt: NOW - CATCHUP_MIN_QUIET_MS - 1 }, sample())).toBe(true)
  })

  it('★ 앞쪽 버퍼가 2초 미만이면 켜지 않는다 - 당길 여유가 없다', () => {
    const state = { enabled: false, lastStallAt: null, disabledAt: null }

    expect(decideCatchup(state, sample({ aheadSec: CATCHUP_MIN_AHEAD_SEC - 0.1 }))).toBe(false)
    expect(decideCatchup(state, sample({ aheadSec: CATCHUP_MIN_AHEAD_SEC }))).toBe(true)
    expect(decideCatchup(state, sample({ aheadSec: null }))).toBe(false)
  })

  it('★ 대역이 레벨 bitrate 의 1.5배 미만이면 켜지 않는다', () => {
    const state = { enabled: false, lastStallAt: null, disabledAt: null }
    const level = 1_000_000

    expect(decideCatchup(state, sample({ levelBitrateBps: level, bandwidthBps: level * 1.5 - 1 }))).toBe(false)
    expect(decideCatchup(state, sample({ levelBitrateBps: level, bandwidthBps: level * 1.5 }))).toBe(true)
  })

  it('★ 레벨 bitrate 를 모르면 대역 조건은 건너뛴다 - 영영 못 켜지는 것을 막는다', () => {
    const state = { enabled: false, lastStallAt: null, disabledAt: null }

    expect(decideCatchup(state, sample({ levelBitrateBps: null, bandwidthBps: 0 }))).toBe(true)
    expect(decideCatchup(state, sample({ levelBitrateBps: 0, bandwidthBps: undefined }))).toBe(true)
  })

  it('★ 켜져 있으면 조건이 깨질 때 끈다', () => {
    const enabled = { enabled: true, lastStallAt: null, disabledAt: null }

    expect(decideCatchup(enabled, sample())).toBe(true)
    expect(decideCatchup(enabled, sample({ aheadSec: 0.5 }))).toBe(false)
    expect(decideCatchup({ ...enabled, lastStallAt: NOW - 1000 }, sample())).toBe(false)
    expect(decideCatchup(enabled, sample({ bandwidthBps: 10 }))).toBe(false)
  })

  it('★ 막 껐으면 10초는 다시 켜지 않는다 - 화면 속도가 계속 바뀌면 더 나빠 보인다', () => {
    const state = { enabled: false, lastStallAt: null, disabledAt: NOW - (CATCHUP_MIN_OFF_MS - 1) }

    expect(decideCatchup(state, sample())).toBe(false)
    // 10초를 넘기면 켠다
    expect(decideCatchup({ ...state, disabledAt: NOW - CATCHUP_MIN_OFF_MS }, sample())).toBe(true)
  })

  it('한 번도 안 껐으면(disabledAt null) 기다리지 않는다', () => {
    expect(decideCatchup({ enabled: false, lastStallAt: null, disabledAt: null }, sample())).toBe(true)
  })
})
