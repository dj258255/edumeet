/**
 * 발표자 방송 재시작 상태 기계. (#229)
 *
 * 서버가 배포로 메모리의 ffmpeg 세션을 잃으면 늦게 도착한 조각은 409가 된다.
 * 대기 시간과 상태 전이는 브라우저·axios와 분리해 시험한다.
 */

export const PUBLISHER_RECOVERY_JITTER_MS = 1_500
export const PUBLISHER_RECOVERY_MAX_BACKOFF_MS = 8_000

/**
 * 첫 재시작은 0~1.5초로 흩고, 재시작 자체가 연속 실패하면 1·2·4·8초로 늦춘다.
 * @param {number} attempt 첫 시도는 0, 그 다음 실패부터 1·2·4·8초
 * @param {() => number} random [0, 1] 난수 공급자
 */
export function nextPublisherRecoveryDelay(attempt = 0, random = Math.random) {
  const n = Number.isFinite(attempt) ? Math.max(0, Math.floor(attempt)) : 0
  const backoff = n === 0
    ? 0
    : Math.min(PUBLISHER_RECOVERY_MAX_BACKOFF_MS, 1_000 * 2 ** (n - 1))
  const value = Number(random())
  const sample = Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0
  return Math.min(
    PUBLISHER_RECOVERY_MAX_BACKOFF_MS,
    backoff + Math.round(sample * PUBLISHER_RECOVERY_JITTER_MS),
  )
}

/**
 * idle → waiting → retrying → awaiting-first-chunk → idle.
 * 재시작 요청이 실패하면 waiting 상태의 시도 횟수를 하나 늘린다.
 */
export function transitionPublisherRecovery(state = {}, event, at = null) {
  const current = {
    phase: state.phase ?? 'idle',
    attempt: Number.isFinite(state.attempt) ? Math.max(0, Math.floor(state.attempt)) : 0,
    changedAt: state.changedAt ?? null,
  }

  if (event === 'failure') {
    const attempt = current.phase === 'idle' ? current.attempt : current.attempt + 1
    return { phase: 'waiting', attempt, changedAt: at }
  }
  if (event === 'retry') return { ...current, phase: 'retrying', changedAt: at }
  if (event === 'restarted') {
    return { phase: 'awaiting-first-chunk', attempt: current.attempt, changedAt: at }
  }
  if (event === 'accepted') return { phase: 'idle', attempt: 0, changedAt: at }
  return current
}

export function isBroadcastNotActiveError(error) {
  return error?.response?.status === 409 &&
    error?.response?.data?.code === 'BROADCAST_NOT_ACTIVE'
}
