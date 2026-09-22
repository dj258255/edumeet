/**
 * 시청 품질 보고 전송. (#197)
 *
 * ★ 30초마다 누적 요약(델타)을 보내고, 떠날 때 마지막 요약을 보낸다.
 *   매초 스냅샷을 보내면 300명에 초당 300건이다. 30초면 10건이다.
 *
 * ★ 왜 sendBeacon 이 아닌가.
 *   이 서비스의 토큰은 Authorization 헤더로 간다. sendBeacon 은 헤더를 못 붙인다.
 *   그래서 pagehide 에서 fetch(url, { keepalive: true }) 를 쓴다.
 *   keepalive 본문은 64KB 로 제한된다. 우리 본문은 수백 바이트라 여유가 크다.
 *
 * ★ api 서브도메인이라 교차 출처(preflight)가 붙는다.
 *   탭이 닫히는 순간 preflight 뒤의 본 요청이 도착하는지는 브라우저에서 확인해야 한다.
 *   여기서는 보내기만 한다 (B3 에서 대조한다).
 *
 * ★ 실패는 삼킨다.
 *   품질 보고 때문에 재생이 흔들리면 안 된다. 실패한 보고는 다음 보고에 합치지도 않는다 -
 *   재전송·중복 방지보다 단순함을 택한다. 그래서 서버 합계는 약간 과소할 수 있다.
 */
import apiClient, { API_BASE_URL } from '@/utils/apiClient'

export function createQoeReporter({
  meetingId,
  tracker,
  send,
  sendFinal,
  intervalMs = 30_000,
  setInterval: setIntervalFn = (fn, ms) => globalThis.setInterval(fn, ms),
  clearInterval: clearIntervalFn = (id) => globalThis.clearInterval(id),
  now = () => Date.now(),
  sessionId = newSessionId(),
  native = false,
} = {}) {
  let seq = 0
  let lastReportAt = null
  let timer = null
  let stopped = false
  let finalSent = false

  function flush(final) {
    const snap = tracker.snapshot()
    const elapsed = lastReportAt === null ? intervalMs : Math.max(0, now() - lastReportAt)

    const empty = snap.playingMs === 0
      && snap.stallMs === 0
      && snap.stallCount === 0
      && snap.startupMs === null
      && snap.errors === 0
    if (empty && !final) {
      // 아무도 안 보는 탭의 빈 보고. 보내지는 않지만 시계는 당긴다 -
      // 보고의 intervalMs 는 그 보고가 담은 창의 길이여야 한다.
      // 안 당기면 30초 일시정지 뒤의 다음 보고가 60초가 되어 서버에서 거부된다.
      lastReportAt = now()
      return
    }

    seq += 1
    lastReportAt = now()
    const body = {
      sessionId,
      seq,
      intervalMs: Math.round(elapsed),
      playingMs: snap.playingMs,
      stallMs: snap.stallMs,
      stallCount: snap.stallCount,
      startupMs: snap.startupMs,
      errors: snap.errors,
      final,
      native,
    }

    try {
      const result = final ? sendFinal(meetingId, body) : send(meetingId, body)
      if (result && typeof result.catch === 'function') result.catch(() => {})
    } catch (_) {
      // 삼킨다. 보고 하나 때문에 재생이 멈추면 안 된다.
    }
  }

  function start() {
    if (stopped || timer !== null) return
    lastReportAt = now()
    timer = setIntervalFn(() => flush(false), intervalMs)
  }

  function finalFlush() {
    if (finalSent) return
    finalSent = true
    flush(true)
  }

  function stop() {
    stopped = true
    if (timer !== null) {
      clearIntervalFn(timer)
      timer = null
    }
  }

  return { start, flush: () => flush(false), finalFlush, stop }
}

export function sendViaApi(meetingId, body) {
  return apiClient.post(`/meeting/${meetingId}/broadcast/qoe`, body)
}

export function sendKeepalive(meetingId, body) {
  const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
  return fetch(`${API_BASE_URL}/meeting/${meetingId}/broadcast/qoe`, {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })
}

function newSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `s-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
