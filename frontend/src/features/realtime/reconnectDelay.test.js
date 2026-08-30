import { describe, it, expect } from 'vitest'
import { reconnectDelay } from './stompClient'

/**
 * 재연결 흩뿌림. (#191)
 *
 * 300명이 동시에 끊겼을 때 고정값이면 연결 지연 중앙값이 1,326ms,
 * 흩으면 13ms 였다. 접속은 양쪽 다 성공하므로 접속 수만 세면 안 보인다.
 */
describe('재연결 대기 시간을 흩는다', () => {
  it('★ 같은 값을 두 번 주지 않는다 - 전원이 같은 순간에 돌아오는 것을 막는다', () => {
    const seen = new Set()
    for (let i = 0; i < 200; i += 1) seen.add(reconnectDelay())
    expect(seen.size).toBeGreaterThan(150)
  })

  it('★ 기준값보다 짧아지지 않는다 - 즉시 재시도는 서버가 죽었을 때 폭주한다', () => {
    for (let i = 0; i < 200; i += 1) {
      expect(reconnectDelay(3000)).toBeGreaterThanOrEqual(3000)
    }
  })

  it('창의 폭이 기준값의 3배를 넘지 않는다 - 너무 넓으면 돌아오는 게 늦어진다', () => {
    expect(reconnectDelay(3000, () => 0)).toBe(3000)
    expect(reconnectDelay(3000, () => 0.999)).toBeLessThanOrEqual(9000)
  })

  it('무작위를 고정하면 값도 고정된다 - 시험이 흔들리지 않게', () => {
    expect(reconnectDelay(1000, () => 0.5)).toBe(2000)
  })
})
