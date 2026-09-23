/**
 * 시청 품질 상태 기계. (#197)
 *
 * ★ 왜 이벤트가 아니라 시간인가.
 *   끊김 이벤트만 세면 분모(끊김 없이 본 시간)가 없어 리버퍼율을 못 낸다.
 *   그러면 "끊김 3회" 가 많은 것인지 적은 것인지 판단할 수 없다.
 *   그래서 재생 시간과 끊김 시간을 초 단위로 누적한다.
 *
 * ★ 왜 순수 함수인가.
 *   시계를 주입받는다. 테스트가 실제 시간을 기다리지 않아도 되고,
 *   브라우저 없이 vitest(node 환경)에서 돈다.
 *
 * ★ 끊김이란.
 *   첫 playing 이후 waiting → 다음 playing 사이의 시간이다.
 *   첫 playing 이전의 waiting 은 시작 대기이고(부착~첫 화면),
 *   일시정지·탐색 중의 waiting 은 버퍼가 아니라 사용자가 멈춘 것이다.
 */

const IDLE = 'idle'
const PLAYING = 'playing'
const STALLED = 'stalled'
const FAILED = 'failed'
const PAUSED = 'paused'

export function createQoeTracker({ now = () => performance.now() } = {}) {
  let attachedAt = null
  let startupAt = null
  let startupMs = null
  let startupEmitted = false

  let mode = IDLE
  let inSeek = false
  let activeBeforeSeek = false
  let accountedAt = null

  let playingMs = 0
  let stallMs = 0
  let stallCount = 0
  let errors = 0

  // 지금 모드가 시작된 시점부터 지금까지를 해당 누적값에 더하고 시계를 당긴다.
  // 모드를 바꾸기 전에 반드시 부른다. 그래야 경계가 겹치거나 비지 않는다.
  function accrue() {
    const t = now()
    if (accountedAt === null) {
      accountedAt = t
      return
    }
    const elapsed = t - accountedAt
    if (elapsed > 0) {
      if (mode === PLAYING) playingMs += elapsed
      else if (mode === STALLED || mode === FAILED) stallMs += elapsed
    }
    accountedAt = t
  }

  function attached(startAt = null) {
    if (attachedAt !== null) return
    attachedAt = now()
    startupAt = Number.isFinite(startAt) ? startAt : attachedAt
    accountedAt = attachedAt
    mode = IDLE
  }

  function playing() {
    if (startupMs === null) {
      const base = startupAt === null ? (attachedAt === null ? now() : attachedAt) : startupAt
      startupMs = Math.max(0, now() - base)
    }
    accrue()
    mode = PLAYING
    inSeek = false
    activeBeforeSeek = true
  }

  function waiting() {
    if (startupMs === null) return       // 첫 화면 전. 그건 시작 대기다.
    if (inSeek) return                   // 탐색 중 대기. 끊김이 아니다.
    if (mode !== PLAYING) return         // 이미 끊김 중이거나 일시정지. 새로 세지 않는다.
    accrue()
    mode = STALLED
    stallCount += 1
  }

  function paused() {
    if (mode === IDLE) return
    if (mode === FAILED) return          // 오류 뒤 pause 는 브라우저의 부산물이다.
    accrue()
    mode = PAUSED
    inSeek = false
    activeBeforeSeek = false
  }

  function seeking() {
    if (mode === IDLE) {
      inSeek = true
      return
    }
    if (mode === PLAYING || mode === STALLED || mode === FAILED) activeBeforeSeek = true
    accrue()
    if (mode === FAILED) return          // 오류 복구가 끝날 때까지 실패 상태를 유지한다.
    mode = PAUSED
    inSeek = true
  }

  function resumedBySeekEnd() {
    inSeek = false
    if (mode === FAILED) return
    if (!activeBeforeSeek) return
    // 버퍼가 이미 있어서 seeked 뒤에 playing 이 안 올 수 있다. 그 시간을 잃지 않는다.
    accrue()
    mode = PLAYING
  }

  /**
   * 복구 못 한 오류 수.
   *
   * NETWORK_ERROR·MEDIA_ERROR fatal 은 hls.js 경로에서 startLoad·recoverMediaError 로
   * 복구를 시도하므로 세지 않는다 - 방송 시작 전 플레이리스트 404 도 NETWORK fatal 로
   * 오는데 그건 오류가 아니라 대기다. 복구되는 동안의 영향은 끊김 시간으로 잡힌다.
   * 그래서 이 함수는 hlsPlayer 의 네이티브 error·hls.js fatal 분기에서 불린다.
   */
  function failed() {
    errors += 1
    if (startupMs === null || mode === PAUSED || mode === IDLE || mode === FAILED) return
    if (mode === STALLED) {
      // waiting 이 이미 끊김 하나를 세었다. 상태만 FAILED 로 이어 pause 를 무시한다.
      accrue()
      mode = FAILED
      return
    }
    accrue()
    mode = FAILED
    stallCount += 1
  }

  function snapshot() {
    accrue()
    const out = {
      playingMs: Math.round(playingMs),
      stallMs: Math.round(stallMs),
      stallCount,
      startupMs: startupEmitted || startupMs === null ? null : Math.round(startupMs),
      errors,
    }
    playingMs = 0
    stallMs = 0
    stallCount = 0
    errors = 0
    if (startupMs !== null) startupEmitted = true
    return out
  }

  return {
    attached,
    playing,
    waiting,
    paused,
    seeking,
    resumedBySeekEnd,
    failed,
    // 호환용 이름. 새 호출부는 failed()를 사용한다.
    error: failed,
    snapshot,
  }
}
