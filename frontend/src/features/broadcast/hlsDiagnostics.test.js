import { describe, it, expect } from 'vitest'
import { createRingLog, HLS_LOG_LIMIT } from './hlsDiagnostics'

describe('hls.js 진단 로그', () => {
  it('비어 있으면 빈 배열이다', () => {
    expect(createRingLog(3).snapshot()).toEqual([])
  })

  it('상한까지만 남기고 넘치면 가장 오래된 것부터 버린다', () => {
    const log = createRingLog(3)

    log.push({ id: 'a' })
    log.push({ id: 'b' })
    log.push({ id: 'c' })
    log.push({ id: 'd' })

    expect(log.snapshot().map((e) => e.id)).toEqual(['b', 'c', 'd'])
  })

  it('오래된 것이 앞에 온다', () => {
    const log = createRingLog(5)

    log.push({ id: 1 })
    log.push({ id: 2 })
    log.push({ id: 3 })

    expect(log.snapshot().map((e) => e.id)).toEqual([1, 2, 3])
  })

  it('snapshot 은 사본이다 - 밖에서 밀어 넣어도 로그가 안 변한다', () => {
    const log = createRingLog(3)
    log.push({ id: 1 })

    log.snapshot().push({ id: 999 })

    expect(log.snapshot()).toEqual([{ id: 1 }])
  })

  it('넘긴 항목을 그대로 돌려준다', () => {
    const log = createRingLog(2)
    const entry = { type: 'ERROR' }

    expect(log.push(entry)).toBe(entry)
  })

  it('기본 상한은 50건이다', () => {
    const log = createRingLog()

    for (let i = 0; i < HLS_LOG_LIMIT + 5; i += 1) log.push({ i })

    expect(log.snapshot()).toHaveLength(HLS_LOG_LIMIT)
    expect(log.snapshot()[0].i).toBe(5)
  })
})
