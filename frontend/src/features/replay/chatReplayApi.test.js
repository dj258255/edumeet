import { describe, it, expect } from 'vitest'
import { windowOf, visibleAt, WINDOW_MS } from './chatReplayApi'

/**
 * 다시보기 채팅의 순수 로직. (#115)
 *
 * 컴포넌트를 띄우지 않고 이것만 시험하는 이유 -
 * 여기에 두 가지 판단이 들어 있고, 둘 다 틀리면 화면에서 조용히 잘못 보인다.
 *
 *   windowOf   구간 캐싱. 없으면 재생 중 초당 여러 번 요청한다
 *   visibleAt  스포일러 방지. 없으면 아직 안 나온 대화가 미리 보인다
 */
describe('windowOf — 재생 위치를 구간으로 끊는다', () => {
  it('같은 구간 안의 위치는 같은 구간 번호를 준다', () => {
    // 재생 중 위치는 초당 여러 번 바뀐다. 매번 요청하면 서버가 견디지 못한다.
    const a = windowOf(0)
    const b = windowOf(30_000)
    const c = windowOf(WINDOW_MS - 1)

    expect(a.index).toBe(b.index)
    expect(b.index).toBe(c.index)
    expect(a.from).toBe(0)
    expect(a.to).toBe(WINDOW_MS)
  })

  it('구간을 넘어가면 번호가 바뀐다', () => {
    expect(windowOf(WINDOW_MS).index).toBe(1)
    expect(windowOf(WINDOW_MS).from).toBe(WINDOW_MS)
    expect(windowOf(WINDOW_MS).to).toBe(WINDOW_MS * 2)
  })

  it('구간이 겹치지 않는다 — 겹치면 같은 대화가 두 번 보인다', () => {
    const first = windowOf(0)
    const second = windowOf(WINDOW_MS)
    expect(first.to).toBe(second.from)
  })

  it('음수 위치는 0으로 본다 — 서버가 400을 내지 않게', () => {
    expect(windowOf(-5_000).from).toBe(0)
    expect(windowOf(-5_000).index).toBe(0)
  })
})

describe('visibleAt — 아직 안 나온 대화는 감춘다', () => {
  const messages = [
    { offsetMillis: 1_000, sender: 'a', content: '처음' },
    { offsetMillis: 5_000, sender: 'b', content: '중간' },
    { offsetMillis: 9_000, sender: 'c', content: '나중' },
  ]

  it('★ 재생 위치보다 뒤의 대화는 안 보인다 — 다시보기의 스포일러', () => {
    const shown = visibleAt(messages, 5_000)
    expect(shown.map((m) => m.content)).toEqual(['처음', '중간'])
  })

  it('정확히 그 시점의 대화는 보인다', () => {
    expect(visibleAt(messages, 1_000).map((m) => m.content)).toEqual(['처음'])
  })

  it('시작 시점에는 아무것도 없다', () => {
    expect(visibleAt(messages, 0)).toEqual([])
  })

  it('끝까지 가면 전부 보인다', () => {
    expect(visibleAt(messages, 60_000)).toHaveLength(3)
  })
})
