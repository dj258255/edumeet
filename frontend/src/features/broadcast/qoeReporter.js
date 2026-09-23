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
 *
 * ★ 실패한 보고를 버리지 않는다. (#197 후속)
 *
 *   운영에서 재 보니 **보고가 실패하는 때가 곧 끊김이 일어나는 때**였다.
 *   35초 offline 에서 서버가 받은 끊김이 보낸 값의 20% 였다 - 유실이 측정 대상과
 *   상관관계를 가진다. 그래서 실패한 보고는 **본문 그대로** 큐에 넣고 다음 주기에
 *   오래된 것부터 다시 보낸다.
 *
 *   - 합치지 않는다. 긴 장애에서 합친 간격이 서버 상한(120초)을 넘어 다시 거부된다.
 *     원래 모양이면 보고마다 서버 검증이 그대로 성립하고 (sessionId, seq) 가 보존된다.
 *   - 재전송 대상은 응답이 없는 실패(네트워크 오류)와 5xx 다. 4xx 는 버린다 -
 *     서버가 거부한 보고는 다시 보내도 거부된다.
 *   - 큐 상한은 10건(약 5분). 넘치면 가장 오래된 것을 버리고 센다.
 *   - 큐를 보내다 또 실패하면 거기서 멈춘다. 네트워크가 아직 나쁜데 전부 쏘지 않는다.
 *
 * ★ 잃는 것 - 중복.
 *   요청이 서버에 도착했는데 응답만 잃으면 재전송이 중복을 만든다.
 *   (sessionId, seq) 로 서버가 거를 수 있게 seq 를 보존했지만 **지금 서버는 거르지 않는다.**
 *   중복보다 유실이 흔하고 편향이 크다는 것을 쟀기 때문이다(docs/performance/29).
 *   서버 중복 제거는 필요해지면 그때 넣는다.
 *
 * ★ 전송 실패로 재생이 흔들리면 안 된다.
 *   판정과 큐 적재는 전부 삼킨다. 보고 하나 때문에 재생이 멈추면 안 된다.
 */
import apiClient, { API_BASE_URL } from '@/utils/apiClient'

/** 재전송 큐 상한. 30초 주기로 약 5분이다. */
export const RETRY_LIMIT = 10

/**
 * 전송이 응답한 경우의 판정. 'sent' | 'retry' | 'drop'
 *
 * - status 가 없으면 성공으로 본다(axios 는 2xx 만 resolve 하고, fetch 는 Response 를 준다).
 * - 5xx 는 재전송, 4xx 는 버린다.
 */
export function classifyOutcome(value) {
  const status = value && typeof value.status === 'number' ? value.status : null
  if (status === null) return 'sent'
  if (status >= 500) return 'retry'
  if (status >= 400) return 'drop'
  return 'sent'
}

/** 전형적인 네트워크 실패로 보는 코드들. (axios) */
const NETWORK_ERROR_CODES = new Set(['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'])

/**
 * 전송이 거부된 경우의 판정. 'sent' | 'retry' | 'drop'
 *
 * - axios 는 `err.response.status` 로 코드를 준다. 5xx 재전송, 4xx 버림.
 * - fetch(`sendKeepalive`)의 reject 는 그 자체로 네트워크 실패다 - 재전송한다.
 * - ★ 응답이 없다고 무조건 네트워크 실패로 보지 않는다.
 *   `apiClient` 의 401 인터셉터가 토큰 갱신에 실패하면 원래 401 대신 다른 오류를 reject 한다.
 *   그 요청은 서버가 받은 것이므로 다시 보내도 같다 - 버린다.
 *   전형적인 네트워크 실패(`ERR_NETWORK`·`ECONNABORTED`·`ETIMEDOUT`, 또는 응답 없이
 *   `request` 만 있는 경우)만 재전송한다.
 */
export function classifyFailure(error, { transport = 'axios' } = {}) {
  const status = error?.response?.status
  if (typeof status === 'number') {
    if (status >= 500) return 'retry'
    if (status >= 400) return 'drop'
    return 'sent'
  }
  if (transport === 'fetch') return 'retry'
  if (NETWORK_ERROR_CODES.has(error?.code)) return 'retry'
  if (error?.request && !error?.response) return 'retry'
  return 'drop'
}

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
  /** 주기 전송이 진행 중이면 그 약속. 진행 중에는 다음 주기를 건너뛴다. */
  let inFlight = null

  /** 재전송 큐. 오래된 것이 앞이다. 본문은 보낼 때 그대로 둔다. */
  const queue = []
  let droppedCount = 0

  /** 큐가 상한을 넘으면 가장 오래된 것부터 버리고 센다. */
  function trimQueue() {
    while (queue.length > RETRY_LIMIT) {
      queue.shift()
      droppedCount += 1
    }
  }

  /**
   * 이번 주기가 잡은 항목을 큐에서 떼어 낸다.
   * ★ 잡은 것만 다룬다 - 그 사이 다른 경로가 뒤에 쌓은 것을 지우지 않는다.
   */
  function takeQueue() {
    return queue.splice(0, queue.length)
  }

  /** 한 건을 보내고 판정한다. 동기 전송이면 결과가 바로 온다. */
  function trySend(entry) {
    let result
    try {
      result = entry.useFinal ? sendFinal(meetingId, entry.body) : send(meetingId, entry.body)
    } catch (_) {
      // 동기 예외 = 네트워크 실패. 서버가 못 받았을 수 있으니 재전송 대상이다.
      return { sync: true, retry: true }
    }
    if (!result || typeof result.then !== 'function') {
      // 동기 전송(시험이 쓰는 형태)은 성공으로 본다.
      return { sync: true, retry: false }
    }
    return {
      sync: false,
      promise: Promise.resolve(result).then(
        (value) => ({ retry: classifyOutcome(value) === 'retry' }),
        (error) => ({
          retry:
            classifyFailure(error, { transport: entry.useFinal ? 'fetch' : 'axios' }) === 'retry',
        }),
      ),
    }
  }

  /** 실패한 지점부터 끝까지를 큐 앞으로 되돌린다. 잡은 뒤 새로 쌓인 것보다 오래됐다. */
  function restoreFrom(entries, index) {
    if (stopped) return // 멈춘 뒤 도착한 실패는 큐에 넣지 않는다
    const existing = new Set(queue.map((body) => body.seq))
    const restore = entries
      .slice(index)
      .map((entry) => entry.body)
      // 같은 (sessionId, seq) 를 두 번 넣지 않는다
      .filter((body) => !existing.has(body.seq))
    queue.unshift(...restore)
    trimQueue()
  }

  /** entries 를 순서대로 보낸다. 재전송 대상 실패를 만나면 거기서 멈춘다. */
  function sendPass(entries, start = 0) {
    for (let i = start; i < entries.length; i += 1) {
      const attempt = trySend(entries[i])
      if (!attempt.sync) {
        return attempt.promise.then((outcome) => {
          if (outcome.retry) {
            restoreFrom(entries, i)
            return undefined
          }
          return sendPass(entries, i + 1)
        })
      }
      if (attempt.retry) {
        restoreFrom(entries, i)
        return undefined
      }
    }
    // 전부 나갔다. 떼어 둔 큐는 그대로 비어 있고, 그 사이 쌓인 것은 남아 있다.
    return undefined
  }

  function flush(final) {
    // ★ 앞 주기의 전송이 아직 안 끝났으면 이번 주기는 건너뛴다.
    //   여기서 snapshot 을 하지 않으므로 트래커가 계속 누적하고, 그 값이 다음 보고에 함께 실린다.
    //   기준 시각도 당기지 않아 간격이 60초가 될 수 있지만 서버 상한(120초) 안이다.
    if (!final && inFlight) return undefined

    const snap = tracker.snapshot()
    const elapsed = lastReportAt === null ? intervalMs : Math.max(0, now() - lastReportAt)

    const empty = snap.playingMs === 0
      && snap.stallMs === 0
      && snap.stallCount === 0
      && snap.startupMs === null
      && snap.errors === 0
    if (empty && !final) {
      // 아무도 안 보는 탭의 빈 보고. 새 보고는 만들지 않지만 시계는 당긴다 -
      // 보고의 intervalMs 는 그 보고가 담은 창의 길이여야 한다.
      // 다만 큐에 남은 것은 보낸다. 안 그러면 조용한 구간에서 큐가 영영 안 빠진다.
      lastReportAt = now()
      return startPass((taken) => taken.map((body) => ({ body, useFinal: false })), false)
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

    // 큐를 먼저(오래된 것부터) 보내고 이번 보고를 보낸다.
    return startPass(
      (taken) => [
        ...taken.map((queued) => ({ body: queued, useFinal: final })),
        { body, useFinal: final },
      ],
      final,
    )
  }

  /** 이번 주기가 잡은 항목만으로 한 번 보낸다. */
  function startPass(buildEntries, final) {
    const taken = takeQueue() // 잡은 것만 다룬다 - 뒤에 쌓인 것은 건드리지 않는다
    const pass = sendPass(buildEntries(taken))
    if (final) return pass // final 은 기다리지 않는다(아래 finalFlush 주석)
    if (pass && typeof pass.then === 'function') {
      const clear = () => { inFlight = null }
      inFlight = pass.then(clear, clear)
    }
    return pass
  }

  function start() {
    if (stopped || timer !== null) return
    lastReportAt = now()
    timer = setIntervalFn(() => flush(false), intervalMs)
  }

  function finalFlush() {
    if (finalSent) return undefined
    finalSent = true
    // ★ 진행 중인 주기 전송을 기다리지 않는다. 탭을 닫는 순간이라 기다릴 수 없다.
    //   대신 그 보고가 이 뒤에 실패하면 **잃는다** - 큐로 돌아와도 다시 보낼 주기가 없다.
    //   지금 큐에 있는 것과 final 을 보낸다.
    return flush(true)
  }

  function stop() {
    stopped = true
    if (timer !== null) {
      clearIntervalFn(timer)
      timer = null
    }
  }

  return {
    start,
    flush: () => flush(false),
    finalFlush,
    stop,
    stats: () => ({
      queued: queue.length,
      seqs: queue.map((queued) => queued.seq),
      dropped: droppedCount,
    }),
  }
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
