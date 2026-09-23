/**
 * 브라우저에 심는 독립 계측. (#197 대조 하네스)
 *
 * ★ 앱 코드를 쓰지 않는다.
 *   playbackQoe.js 를 그대로 심어 검증하면 같은 코드로 같은 코드를 검증하는 순환이다.
 *   정의를 따로 적고 따로 센다.
 *
 *   ① 이벤트 기준 - 첫 playing 이후 waiting/error → 다음 playing. 앱과 같은 정의지만 코드는 따로다.
 *      오류 뒤의 pause 는 사용자 일시정지가 아니므로 열린 끊김을 닫지 않는다.
 *   ② 진행 기준   - 250ms 마다 currentTime 을 본다. 재생 중(!paused && !seeking)인데
 *      전진이 0 이상 0.1초 미만인 구간이 연속 500ms 이상이면 멈춤으로 센다.
 *      오류 뒤에는 paused 여도 멈춤 후보로 본다. 사용자는 그 멈춘 화면을 보고 있기 때문이다. (#219)
 *      뒤로 이동(음수 전진)은 멈춤이 아니라 불연속으로 따로 센다 -
 *      재버퍼링·스트림 교체로 탐색 이벤트를 놓친 경우가 그렇다.
 *
 * ★ page.addInitScript 로 심는다. 시각은 performance.now().
 *   결과는 window.__qoeTruth 에 누적되고, window.__qoeTruthFinalize() 가 열린 구간을 닫는다.
 *
 * ★ 비디오 요소가 바뀌면 이전 요소의 리스너를 떼고 새 요소 기준으로 다시 시작한다.
 *   안 그러면 옛 요소의 이벤트와 새 요소의 currentTime 을 섞어 센다.
 *   (부수적으로 wallAtAttach 를 남긴다 - 대조의 워밍업 경계를 스로틀 일정 기준으로 옮기려면
 *    performance.now() 와 벽시계의 대응을 알아야 한다)
 */
;(() => {
  if (window.__qoeTruth) return

  const SAMPLE_MS = 250
  const RATE_SAMPLE_MS = 1000
  const STALL_AFTER_MS = 500
  const FROZEN_ADVANCE_S = 0.1

  const now = () => performance.now()
  const round = (v) => Math.round(v)

  // 끊김 판정에는 쓰지 않고 events 에만 남기는 이벤트들.
  // "첫 playing 뒤 pause" 처럼 재생을 멈추게 한 사건을 나중에 볼 수 있어야 한다.
  const RECORD_ONLY_EVENTS = [
    'emptied', 'abort', 'stalled', 'suspend', 'ended', 'loadstart',
  ]
  function recordEvent(type) {
    truth.events.push({ t: round(now()), type })
  }

  const truth = {
    events: [],
    stallsEvent: [],
    stallsProgress: [],
    discontinuities: [],
    startupMs: null,
    videoAttachedAt: null,
    wallAtAttach: null,
    // 재생 속도 표본 (#233). 따라잡기(maxLiveSyncPlaybackRate)를 켰을 때 실제로 1을 넘었는지,
    // 얼마나 오래 넘었는지를 나중에 대조에서 센다 - 앱이 켰다고 믿는 것과 화면에서 일어난 것은 다르다.
    playbackRates: [],
  }
  window.__qoeTruth = truth

  let video = null
  let handlers = null
  let firstPlaying = false
  let paused = false
  let seeking = false
  let waitingSince = null // 이벤트 기준 끊김 시작
  let errorSince = null // 오류 뒤 다음 playing 까지의 끊김 시작

  let lastSampleT = null
  let lastCurrent = null
  let lastRateT = null
  let frozenSince = null // 진행 기준 멈춤이 시작된 시각
  let progressStallStart = null

  function endEventStall(t) {
    if (waitingSince == null) return
    truth.stallsEvent.push({ start: round(waitingSince), end: round(t) })
    waitingSince = null
    errorSince = null
  }

  function endProgressStall(t) {
    if (progressStallStart != null) {
      truth.stallsProgress.push({ start: round(progressStallStart), end: round(t) })
      progressStallStart = null
    }
    frozenSince = null
  }

  function onPlaying() {
    const t = now()
    truth.events.push({ t: round(t), type: 'playing' })
    if (!firstPlaying) {
      firstPlaying = true
      truth.startupMs =
        truth.videoAttachedAt == null ? null : round(t - truth.videoAttachedAt)
    }
    paused = false
    seeking = false
    endEventStall(t)
    endProgressStall(t)
  }

  function onWaiting() {
    truth.events.push({ t: round(now()), type: 'waiting' })
    if (!firstPlaying) return // 시작 대기
    if (paused || seeking) return // 사용자가 멈췄거나 탐색 중
    if (waitingSince != null) return
    waitingSince = now()
  }

  function onPause() {
    const t = now()
    truth.events.push({ t: round(t), type: 'pause' })
    paused = true
    if (errorSince != null) return // 오류 뒤 pause 는 브라우저의 부산물이다.
    endEventStall(t) // 사용자가 멈춘 것은 끊김이 아니다
  }

  function onSeeking() {
    const t = now()
    truth.events.push({ t: round(t), type: 'seeking' })
    seeking = true
    if (errorSince == null) endEventStall(t)
  }

  function onSeeked() {
    truth.events.push({ t: round(now()), type: 'seeked' })
    seeking = false
  }

  function onError() {
    const t = now()
    truth.events.push({ t: round(t), type: 'error' })
    if (!firstPlaying || errorSince != null) return
    errorSince = t
    if (waitingSince == null) waitingSince = t
    if (frozenSince == null) frozenSince = t
  }

  function detach(el, map) {
    if (!el || !map) return
    for (const [type, fn] of Object.entries(map)) el.removeEventListener(type, fn)
  }

  /** 새 요소 기준으로 진행 중 상태를 비운다. 이미 모은 구간은 그대로 둔다. */
  function resetInFlight() {
    firstPlaying = false
    paused = false
    seeking = false
    waitingSince = null
    errorSince = null
    lastSampleT = null
    lastCurrent = null
    lastRateT = null
    frozenSince = null
    progressStallStart = null
  }

  function attach(el) {
    if (video === el) return
    const changed = video !== null
    detach(video, handlers)
    resetInFlight()

    video = el
    truth.startupMs = null
    truth.videoAttachedAt = round(now())
    truth.wallAtAttach = Date.now()
    truth.events.push({ t: truth.videoAttachedAt, type: changed ? 'video-changed' : 'video-attached' })

    handlers = {
      playing: onPlaying,
      waiting: onWaiting,
      pause: onPause,
      seeking: onSeeking,
      seeked: onSeeked,
      error: onError,
    }
    // 기록만 하는 이벤트. 끊김 판정 로직은 건드리지 않는다.
    for (const type of RECORD_ONLY_EVENTS) handlers[type] = () => recordEvent(type)
    for (const [type, fn] of Object.entries(handlers)) el.addEventListener(type, fn)
  }

  function sample() {
    if (!video) return
    const t = now()
    const current = Number(video.currentTime)
    if (lastSampleT == null || !Number.isFinite(current)) {
      lastSampleT = t
      lastCurrent = current
      return
    }

    const playing = !video.paused && !video.seeking
    const advanced = current - lastCurrent
    const errorStopped = errorSince != null
    if (errorStopped) {
      // error 뒤 브라우저가 pause 해도 사용자는 멈춘 화면을 본다. (#219)
      if (frozenSince == null) frozenSince = errorSince
      if (progressStallStart == null && t - frozenSince >= STALL_AFTER_MS) {
        progressStallStart = frozenSince
      }
    } else if (playing && advanced >= 0 && advanced < FROZEN_ADVANCE_S) {
      // 판정 시작은 이번 샘플 시각이다. lastSampleT 로 잡으면 최대 한 샘플 앞당겨진다.
      if (frozenSince == null) frozenSince = t
      if (progressStallStart == null && t - frozenSince >= STALL_AFTER_MS) {
        progressStallStart = frozenSince
      }
    } else {
      if (playing && advanced < 0) {
        // 뒤로 이동. 멈춤이 아니라 불연속이다.
        truth.discontinuities.push({ t: round(t), deltaMs: round(advanced * 1000) })
      }
      endProgressStall(t)
    }

    // 재생 속도는 1초에 한 번만 남긴다(표본을 촘촘히 남겨도 판단이 달라지지 않는다).
    if (lastRateT == null || t - lastRateT >= RATE_SAMPLE_MS) {
      truth.playbackRates.push({ t: round(t), rate: Number(video.playbackRate) })
      lastRateT = t
    }

    lastSampleT = t
    lastCurrent = current
  }

  const timer = setInterval(sample, SAMPLE_MS)
  window.__qoeTruthFinalize = () => {
    const t = now()
    endEventStall(t)
    endProgressStall(t)
    clearInterval(timer)
  }

  function findVideo() {
    const el = document.querySelector('video')
    if (el) attach(el)
  }

  const observer = new MutationObserver(findVideo)
  function startWatching() {
    findVideo()
    observer.observe(document.documentElement, { childList: true, subtree: true })
  }
  if (document.documentElement) startWatching()
  else document.addEventListener('DOMContentLoaded', startWatching)
})()
