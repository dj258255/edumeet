import { describe, it, expect } from 'vitest'
import {
  CATCHUP_DISABLE_AHEAD_SEC,
  CATCHUP_DISABLE_BANDWIDTH_RATIO,
  CATCHUP_ENABLE_AHEAD_SEC,
  CATCHUP_ENABLE_BANDWIDTH_RATIO,
  CATCHUP_MIN_OFF_MS,
  CATCHUP_MIN_QUIET_MS,
  decideCatchup,
} from './catchupPolicy'

/**
 * 조건부 따라잡기 정책. (#233 · #233c)
 *
 * ★ 이 함수가 있는 이유 - 재생 속도를 올리면 정상망에서는 지연이 절반이 되지만
 *   제한망에서는 끊김이 30~65% 늘었다(그리드 16회차). 그래서 조건을 다 만족할 때만 켠다.
 *
 * ★ v2 에서 켜는 문턱과 끄는 문턱이 달라졌다(히스테리시스).
 *   켜면 버퍼를 당겨 곧 문턱 아래로 내려가고, 회복하면 다시 켜지는 깜빡임이 있었다
 *   (정상망 시청자당 3분에 약 21회 전환). 아래 시험들이 그 두 문턱을 따로 고정한다.
 */
const NOW = 1_000_000
const LEVEL = 1_000_000

const sample = (over = {}) => ({
  now: NOW,
  aheadSec: 3,
  bandwidthBps: 4_000_000,
  streamBitrateBps: LEVEL,
  ...over,
})

const off = () => ({ enabled: false, lastStallAt: null, disabledAt: null })
const on = () => ({ enabled: true, lastStallAt: null, disabledAt: null })

describe('따라잡기 정책 (#233c)', () => {
  it('★ 세 조건이 다 맞으면 켠다', () => {
    expect(decideCatchup(off(), sample())).toBe(true)
  })

  it('★ 최근 30초 안에 끊겼으면 켜지 않는다 - 제한망 창의 여파', () => {
    expect(decideCatchup({ ...off(), lastStallAt: NOW - CATCHUP_MIN_QUIET_MS + 1 }, sample())).toBe(false)
    expect(decideCatchup({ ...off(), lastStallAt: NOW - CATCHUP_MIN_QUIET_MS - 1 }, sample())).toBe(true)
  })

  it('★ 버퍼 히스테리시스 - 켤 때는 2.5초, 유지할 때는 1.0초', () => {
    expect(decideCatchup(off(), sample({ aheadSec: CATCHUP_ENABLE_AHEAD_SEC - 0.1 }))).toBe(false)
    expect(decideCatchup(off(), sample({ aheadSec: CATCHUP_ENABLE_AHEAD_SEC }))).toBe(true)
    expect(decideCatchup(off(), sample({ aheadSec: null }))).toBe(false)

    // 그 사이 구간은 **켜져 있으면 유지**된다 - 여기가 깜빡임을 막는 자리다
    const between = (CATCHUP_DISABLE_AHEAD_SEC + CATCHUP_ENABLE_AHEAD_SEC) / 2
    expect(decideCatchup(on(), sample({ aheadSec: between }))).toBe(true)
    expect(decideCatchup(off(), sample({ aheadSec: between }))).toBe(false)

    // 끄는 문턱 - hls.js 가 어차피 안 당기는 지점
    expect(decideCatchup(on(), sample({ aheadSec: CATCHUP_DISABLE_AHEAD_SEC - 0.1 }))).toBe(false)
    expect(decideCatchup(on(), sample({ aheadSec: CATCHUP_DISABLE_AHEAD_SEC }))).toBe(true)
  })

  it('★ 대역 히스테리시스 - 켤 때는 1.5배, 유지할 때는 1.2배', () => {
    const bw = (ratio) => sample({ streamBitrateBps: LEVEL, bandwidthBps: LEVEL * ratio })

    expect(decideCatchup(off(), bw(CATCHUP_ENABLE_BANDWIDTH_RATIO - 0.01))).toBe(false)
    expect(decideCatchup(off(), bw(CATCHUP_ENABLE_BANDWIDTH_RATIO))).toBe(true)

    const between = (CATCHUP_DISABLE_BANDWIDTH_RATIO + CATCHUP_ENABLE_BANDWIDTH_RATIO) / 2
    expect(decideCatchup(on(), bw(between))).toBe(true)   // 유지된다 - 대역 추정이 흔들려도 안 꺼진다
    expect(decideCatchup(off(), bw(between))).toBe(false)

    expect(decideCatchup(on(), bw(CATCHUP_DISABLE_BANDWIDTH_RATIO - 0.01))).toBe(false)
    expect(decideCatchup(on(), bw(CATCHUP_DISABLE_BANDWIDTH_RATIO))).toBe(true)
  })

  it('★ bitrate 를 모르면 켜지 않는다 - 건너뛰지 않는다 (#233c)', () => {
    // 버퍼도 대역도 완벽하지만 스트림 bitrate 를 모른다 (레벨 없음 + 조각 부족)
    expect(decideCatchup(off(), sample({ streamBitrateBps: null, bandwidthBps: 100_000_000 }))).toBe(false)
    expect(decideCatchup(off(), sample({ streamBitrateBps: 0 }))).toBe(false)
    expect(decideCatchup(off(), sample({ streamBitrateBps: undefined }))).toBe(false)
  })

  it('★ 켜져 있는 중에 bitrate 를 모르게 되면 끈다 - 모르면 보수적으로', () => {
    expect(decideCatchup(on(), sample({ streamBitrateBps: null }))).toBe(false)
  })

  it('★ 켜져 있으면 유지 문턱 아래에서 끈다', () => {
    expect(decideCatchup(on(), sample())).toBe(true)
    expect(decideCatchup({ ...on(), lastStallAt: NOW - 1000 }, sample())).toBe(false)
    expect(decideCatchup(on(), sample({ bandwidthBps: 10 }))).toBe(false)
  })

  it('★ 막 껐으면 10초는 다시 켜지 않는다 - 화면 속도가 계속 바뀌면 더 나빠 보인다', () => {
    const state = { ...off(), disabledAt: NOW - (CATCHUP_MIN_OFF_MS - 1) }

    expect(decideCatchup(state, sample())).toBe(false)
    expect(decideCatchup({ ...state, disabledAt: NOW - CATCHUP_MIN_OFF_MS }, sample())).toBe(true)
  })

  it('한 번도 안 껐으면(disabledAt null) 기다리지 않는다', () => {
    expect(decideCatchup(off(), sample())).toBe(true)
  })

  it('조각에서 추정한 bitrate 도 같은 문턱으로 본다 - 출처는 판단에 영향이 없다', () => {
    const estimated = 900_000
    expect(decideCatchup(off(), sample({ streamBitrateBps: estimated, bandwidthBps: estimated * 1.6 }))).toBe(true)
    expect(decideCatchup(off(), sample({ streamBitrateBps: estimated, bandwidthBps: estimated * 1.4 }))).toBe(false)
  })
})
