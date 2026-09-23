import { describe, it, expect } from 'vitest'
import { createQoeTracker } from './playbackQoe'

function harness() {
  const env = { t: 0 }
  const tracker = createQoeTracker({ now: () => env.t })
  return { env, tracker }
}

describe('시청 품질 트래커', () => {
  it('부착에서 첫 재생까지가 첫 화면 시간이다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1200
    tracker.playing()

    expect(tracker.snapshot().startupMs).toBe(1200)
  })

  it('첫 재생 이후의 waiting → playing 이 끊김이다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 5000
    tracker.waiting()
    env.t = 6500
    tracker.playing()

    const snap = tracker.snapshot()
    expect(snap.stallMs).toBe(1500)
    expect(snap.stallCount).toBe(1)
  })

  it('첫 재생 전의 waiting 은 끊김이 아니다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 500
    tracker.waiting()
    env.t = 1200
    tracker.playing()

    const snap = tracker.snapshot()
    expect(snap.stallMs).toBe(0)
    expect(snap.stallCount).toBe(0)
    expect(snap.startupMs).toBe(1200)
  })

  it('일시정지 중에는 재생 시간이 늘지 않고 waiting 도 끊김이 아니다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 3000
    tracker.paused()
    env.t = 4000
    tracker.waiting()
    env.t = 10000

    const snap = tracker.snapshot()
    expect(snap.playingMs).toBe(2000)
    expect(snap.stallMs).toBe(0)
    expect(snap.stallCount).toBe(0)
  })

  it('탐색 중 waiting 은 끊김이 아니고, seeked 뒤 재생이 이어진다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 2000
    tracker.seeking()
    env.t = 2500
    tracker.waiting()
    env.t = 3000
    tracker.resumedBySeekEnd()
    env.t = 8000

    const snap = tracker.snapshot()
    expect(snap.stallMs).toBe(0)
    expect(snap.stallCount).toBe(0)
    expect(snap.playingMs).toBe(6000)
  })

  it('★ 끊김 도중 snapshot 을 떠도 두 보고의 합이 실제 끊김 길이와 같다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 5000
    tracker.waiting()
    env.t = 6500

    const first = tracker.snapshot()
    env.t = 8000
    tracker.playing()
    const second = tracker.snapshot()

    expect(first.stallMs).toBe(1500)
    expect(second.stallMs).toBe(1500)
    expect(first.stallMs + second.stallMs).toBe(3000)
    expect(first.stallCount).toBe(1)
    expect(second.stallCount).toBe(0)
    expect(first.stallCount + second.stallCount).toBe(1)
  })

  it('startupMs 는 확정된 뒤 첫 snapshot 에만 실린다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1200
    tracker.playing()

    expect(tracker.snapshot().startupMs).toBe(1200)
    expect(tracker.snapshot().startupMs).toBeNull()
  })

  it('오류 → pause → playing 은 사용자 일시정지가 아닌 15초 끊김 한 번이다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 5000
    tracker.failed()
    env.t = 6000
    tracker.paused()
    env.t = 20_000
    tracker.playing()

    const snap = tracker.snapshot()
    expect(snap.stallMs).toBe(15_000)
    expect(snap.stallCount).toBe(1)
    expect(snap.errors).toBe(1)
  })

  it('waiting 중의 error 는 같은 끊김을 두 번 세지 않는다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 5000
    tracker.waiting()
    tracker.failed()
    env.t = 6500
    tracker.playing()

    expect(tracker.snapshot()).toMatchObject({ stallMs: 1500, stallCount: 1, errors: 1 })
  })

  it('waiting → failed → pause 뒤에도 playing 까지 전 구간을 한 번의 끊김으로 센다', () => {
    const { env, tracker } = harness()
    tracker.attached()
    env.t = 1000
    tracker.playing()
    env.t = 5000
    tracker.waiting()
    tracker.failed()
    tracker.paused()
    env.t = 15_000
    tracker.playing()

    expect(tracker.snapshot()).toMatchObject({ stallMs: 10_000, stallCount: 1, errors: 1 })
  })
})
