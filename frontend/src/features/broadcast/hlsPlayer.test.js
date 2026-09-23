import { describe, it, expect } from 'vitest'
import { liveSyncDurationCount, loadHls, loadHlsModule } from './hlsPlayer'
import { choosePlaybackPath } from './playbackPath'

/** attachHls 가 경로를 고를 때 쓰는 것과 같은 계산. */
function pathWith(Hls, nativeHlsSupported) {
  return choosePlaybackPath({
    hlsJsSupported: typeof Hls?.isSupported === 'function' && Hls.isSupported(),
    nativeHlsSupported,
  })
}

describe('hls.js 동적 import', () => {
  it('liveSyncDurationCount 는 1·2·3만 읽고 나머지는 2를 쓴다', () => {
    expect(liveSyncDurationCount({ getItem: () => '1' })).toBe(1)
    expect(liveSyncDurationCount({ getItem: () => '2' })).toBe(2)
    expect(liveSyncDurationCount({ getItem: () => '3' })).toBe(3)
    expect(liveSyncDurationCount({ getItem: () => '0' })).toBe(2)
    expect(liveSyncDurationCount({ getItem: () => '4' })).toBe(2)
    expect(liveSyncDurationCount({ getItem: () => null })).toBe(2)
  })

  it('같은 로더를 여러 번 불러도 같은 약속을 돌려준다', async () => {
    let calls = 0
    const Hls = { isSupported: () => true }
    const load = () => {
      calls += 1
      return Promise.resolve({ default: Hls })
    }

    const first = loadHls(load)
    const second = loadHls(load)

    expect(second).toBe(first)
    await expect(first).resolves.toBe(Hls)
    expect(calls).toBe(1)
  })

  it('import 실패 뒤에는 새로 시도한다', async () => {
    let calls = 0
    const Hls = { isSupported: () => true }
    const load = () => {
      calls += 1
      return calls === 1
        ? Promise.reject(new Error('chunk load failed'))
        : Promise.resolve({ default: Hls })
    }

    await expect(loadHls(load)).resolves.toBeNull()
    await expect(loadHls(load)).resolves.toBe(Hls)
    expect(calls).toBe(2)
  })

  it('모듈을 못 받으면 null 을 준다 - 던지지 않는다', async () => {
    await expect(loadHlsModule(() => Promise.reject(new Error('chunk load failed')))).resolves.toBeNull()
  })

  it('모듈 모양이 이상해도 null 을 준다', async () => {
    await expect(loadHlsModule(() => Promise.resolve({}))).resolves.toBeNull()
  })

  it('동기로 던져도 null 을 준다', async () => {
    await expect(loadHlsModule(() => { throw new Error('sync failure') })).resolves.toBeNull()
  })

  it('받으면 default 를 그대로 준다', async () => {
    const Hls = { isSupported: () => true }

    await expect(loadHlsModule(() => Promise.resolve({ default: Hls }))).resolves.toBe(Hls)
  })

  it('★ import 가 실패해도 네이티브로 재생할 수 있는 브라우저는 살아난다', async () => {
    // 청크 로딩이 실패해도 attachHls 가 reject 되면 안 된다 -
    // 그러면 네이티브로 재생할 수 있는 브라우저가 아무것도 못 튼다. (#217 회귀)
    const Hls = await loadHlsModule(() => Promise.reject(new Error('chunk load failed')))

    expect(pathWith(Hls, true)).toBe('native')
  })

  it('import 가 실패하고 네이티브도 안 되면 unsupported 다', async () => {
    const Hls = await loadHlsModule(() => Promise.reject(new Error('chunk load failed')))

    expect(pathWith(Hls, false)).toBe('unsupported')
  })

  it('import 가 되고 지원하면 hls.js 를 고른다', async () => {
    const Hls = await loadHlsModule(() => Promise.resolve({ default: { isSupported: () => true } }))

    expect(pathWith(Hls, true)).toBe('hlsjs')
  })
})
