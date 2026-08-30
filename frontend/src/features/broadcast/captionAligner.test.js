import { describe, it, expect } from 'vitest'
import { createCaptionAligner } from './captionAligner'

/** 2026-01-01 00:00:10.000 을 기준 시각으로 쓴다. */
const T0 = Date.UTC(2026, 0, 1, 0, 0, 10)

function caption(spokenAt, text = '안녕하세요') {
  return { spokenAt, text, finalSegment: true }
}

/** 시간과 화면 시각을 손으로 움직이는 테스트용 환경. */
function harness({ playerDate = null, maxHoldMs } = {}) {
  const env = { now: T0, playerDate }
  const aligner = createCaptionAligner(() => env.playerDate, {
    maxHoldMs,
    now: () => env.now,
  })
  return { env, aligner }
}

describe('자막을 화면 시각에 맞춘다', () => {
  it('★ 화면이 아직 그 시점에 안 왔으면 붙잡는다 - 자막이 화면보다 먼저 뜨는 것을 막는다', () => {
    const { env, aligner } = harness({ playerDate: new Date(T0 - 6000) })

    aligner.push(caption(T0))
    expect(aligner.tick()).toEqual([])
    expect(aligner.pendingCount).toBe(1)
    expect(aligner.leadMs).toBe(6000)
  })

  it('★ 화면이 그 시점을 지나가면 내보낸다', () => {
    const { env, aligner } = harness({ playerDate: new Date(T0 - 6000) })

    aligner.push(caption(T0))
    aligner.tick()

    env.playerDate = new Date(T0 + 100)
    expect(aligner.tick().map((c) => c.text)).toEqual(['안녕하세요'])
    expect(aligner.leadMs).toBe(0)
  })

  it('★ 화면 시각을 모르면 붙잡지 않는다 - 어긋난 자막보다 없는 자막이 훨씬 나쁘다', () => {
    const { aligner } = harness({ playerDate: null })

    aligner.push(caption(T0))
    expect(aligner.tick()).toHaveLength(1)
    expect(aligner.stats).toEqual({ aligned: 0, unaligned: 1 })
  })

  it('★ 상한을 넘기면 어긋난 채로라도 내보낸다 - 기기 시계가 틀어져 있을 수 있다', () => {
    const { env, aligner } = harness({ playerDate: new Date(T0 - 3_600_000), maxHoldMs: 12_000 })

    aligner.push(caption(T0))
    expect(aligner.tick()).toEqual([])

    env.now = T0 + 12_000
    const out = aligner.tick()
    expect(out).toHaveLength(1)
    expect(aligner.stats.unaligned).toBe(1)
  })

  it('★ 순서를 지킨다 - 앞의 것이 아직 때가 아니면 뒤의 것도 안 내보낸다', () => {
    // 전사는 순서가 곧 내용이다. 뒤집히면 뜻이 달라진다.
    const { env, aligner } = harness({ playerDate: new Date(T0 - 5000) })

    aligner.push(caption(T0 + 1000, '둘째'))
    aligner.push(caption(T0 - 1000, '첫째'))   // 늦게 왔지만 더 이른 시각

    // 화면이 첫째 시점을 지났다. 그런데 대기열 앞은 둘째다.
    env.playerDate = new Date(T0)
    expect(aligner.tick()).toEqual([])

    env.playerDate = new Date(T0 + 2000)
    expect(aligner.tick().map((c) => c.text)).toEqual(['둘째', '첫째'])
  })

  it('말한 시각이 없는 자막은 붙잡지 않는다', () => {
    const { aligner } = harness({ playerDate: new Date(T0 - 6000) })

    aligner.push({ text: '시각 없음' })
    expect(aligner.tick()).toHaveLength(1)
  })

  it('맞춘 것과 못 맞춘 것을 갈라 센다 - 합쳐 세면 "되고 있다" 를 알 수 없다', () => {
    const { env, aligner } = harness({ playerDate: new Date(T0 - 1000) })

    aligner.push(caption(T0 - 2000, '이미 지난 것'))
    aligner.tick()

    env.playerDate = null
    aligner.push(caption(T0, '시각 모름'))
    aligner.tick()

    expect(aligner.stats).toEqual({ aligned: 1, unaligned: 1 })
  })
})
