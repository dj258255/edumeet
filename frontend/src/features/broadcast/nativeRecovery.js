/**
 * 네이티브 HLS 재연결 상태 기계. (#210)
 *
 * ★ 대기 시간 계산과 상태 전이는 브라우저 API와 분리한다.
 *   시계·난수·타이머를 주입할 수 있어 실제 시간을 기다리지 않고 시험한다.
 */

export const RECOVERY_BASE_MS = 1_000
export const RECOVERY_MAX_MS = 15_000
export const RECOVERY_JITTER = 0.3

/**
 * 재시도 횟수에 따른 대기 시간.
 *
 * @param {number} attempt 첫 오류는 0. 1, 2, 4…초로 늘어난다.
 * @param {() => number} random [0, 1) 난수 공급자
 */
export function nextRecoveryDelay(attempt = 0, random = Math.random) {
  const n = Number.isFinite(attempt) ? Math.max(0, Math.floor(attempt)) : 0
  const base = Math.min(RECOVERY_MAX_MS, RECOVERY_BASE_MS * 2 ** n)
  const sample = Math.min(1, Math.max(0, Number(random()) || 0))
  const multiplier = 1 - RECOVERY_JITTER + sample * RECOVERY_JITTER * 2
  // #191: 기준값만 15초에서 멈춘다. 지터 뒤에 자르면 상한 구간의 재시도가 같은 순간 몰린다.
  return Math.round(base * multiplier)
}

/**
 * 복구 상태를 다음 상태로 옮긴다.
 *
 * phase 는 idle → waiting → retrying → idle 순으로 흐른다.
 * retrying 중 오류가 다시 나면 attempt 를 유지해 다음 대기 시간이 늘어난다.
 */
export function transitionRecovery(state = {}, event, at = null) {
  const current = {
    phase: state.phase ?? 'idle',
    attempt: Number.isFinite(state.attempt) ? Math.max(0, state.attempt) : 0,
    changedAt: state.changedAt ?? null,
  }

  if (event === 'error') {
    return { ...current, phase: 'waiting', changedAt: at }
  }
  if (event === 'retry') {
    return { ...current, phase: 'retrying', attempt: current.attempt + 1, changedAt: at }
  }
  if (event === 'playing') {
    return { phase: 'idle', attempt: 0, changedAt: at }
  }
  return current
}

// 이름을 풀어 쓴 별칭도 공개해 순수 전이 함수를 읽는 쪽의 의도를 분명히 한다.
export const nextRecoveryState = transitionRecovery

/**
 * 네이티브 재연결 타이머.
 *
 * onRetry 는 실제 video 소스를 다시 붙이는 일만 담당한다. 이 모듈은 DOM을 직접 만지지 않는다.
 */
export function createNativeRecovery({
  now = () => performance.now(),
  random = Math.random,
  setTimeout: setTimeoutFn = (fn, ms) => globalThis.setTimeout(fn, ms),
  clearTimeout: clearTimeoutFn = (id) => globalThis.clearTimeout(id),
  onRetry = () => {},
  onStatus = () => {},
} = {}) {
  let state = transitionRecovery({}, 'playing', now())
  let timer = null
  let destroyed = false

  function failed() {
    if (destroyed || timer !== null) return

    state = transitionRecovery(state, 'error', now())
    const delayMs = nextRecoveryDelay(state.attempt, random)
    onStatus({ state: 'reconnecting', attempt: state.attempt, delayMs })
    if (destroyed) return
    timer = setTimeoutFn(() => {
      if (destroyed) return
      timer = null
      state = transitionRecovery(state, 'retry', now())
      onRetry({ attempt: state.attempt, at: state.changedAt })
    }, delayMs)
  }

  function playing() {
    const wasRecovering = state.phase !== 'idle' || timer !== null
    if (timer !== null) {
      clearTimeoutFn(timer)
      timer = null
    }
    state = transitionRecovery(state, 'playing', now())
    if (wasRecovering) onStatus({ state: 'playing' })
  }

  function destroy() {
    if (destroyed) return
    destroyed = true
    if (timer !== null) {
      clearTimeoutFn(timer)
      timer = null
    }
  }

  return {
    failed,
    // 이전 호출부가 error라는 이름을 사용해도 같은 상태 기계를 쓴다.
    error: failed,
    playing,
    destroy,
    snapshot: () => ({ ...state, retryPending: timer !== null }),
  }
}
