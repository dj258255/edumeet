/**
 * HLS 재생. (#123)
 *
 * ★ hls.js 를 먼저 쓴다. (#217)
 *   예전에는 {@code canPlayType('application/vnd.apple.mpegurl')} 가 참이면 네이티브를 먼저 썼다 -
 *   "네이티브 = Safari" 가정이었다. Chrome 도 이 값에 "maybe" 를 돌려주므로 Chrome 이 hls.js 를
 *   안 탔고, 자막 정렬(화면 시각, #185)과 지연 설정이 조용히 꺼져 있었다.
 *   이 앱은 hls.js 경로에만 그 둘이 있다. 순서는 playbackPath.js 가 정한다 -
 *   iOS Safari 는 MSE 가 없어 네이티브로 간다.
 *
 * ★ hls.js 는 동적으로 불러온다.
 *   방송 시청 화면에 들어온 사람에게만 필요하다. 정적 import 로 묶으면 시청 화면에 오지 않는
 *   사용자도 400KB 짜리 플레이어를 받는다.
 *   (경로를 정하려면 Hls.isSupported() 를 봐야 해서, 네이티브로 갈 브라우저도 이 파일은 받는다.
 *    그 대가로 Chrome 이 hls.js 를 타고 자막 정렬·지연 설정이 살아난다)
 */
import { snapshotHlsMetrics, snapshotNativeMetrics } from './hlsMetrics'
import { createQoeTracker } from './playbackQoe'
import { createRingLog, HLS_LOG_LIMIT } from './hlsDiagnostics'
import { forcedPlaybackPath, choosePlaybackPath } from './playbackPath'
import { createNativeRecovery } from './nativeRecovery'

export async function attachHls(
  videoEl,
  playlistUrl,
  { onError = () => {}, onMetrics = () => {}, onQoe, onStatus = () => {}, startAt = null } = {},
) {
  // ★ 어떤 경로로 갈지는 순수 함수가 정한다. (#217)
  //   네이티브 지원 여부와 hls.js 지원 여부를 **둘 다** 보고 고른다 -
  //   "네이티브가 되면 네이티브" 는 Chrome 에서 hls.js 를 영영 안 타게 만들었다.
  const nativeHlsSupported = Boolean(videoEl.canPlayType('application/vnd.apple.mpegurl'))
  const Hls = await loadHls()
  const path = choosePlaybackPath({
    hlsJsSupported: typeof Hls?.isSupported === 'function' && Hls.isSupported(),
    nativeHlsSupported,
    forcedPath: forcedPlaybackPath(),
  })

  // 하네스·운영 진단용. 값 하나다 - 이 값만 보면 어느 경로로 재생했는지 안다.
  window.__edumeetPlaybackPath = path

  if (path === 'native') {
    videoEl.src = playlistUrl
    const timer = setInterval(() => onMetrics(snapshotNativeMetrics(videoEl)), 1000)
    const qoe = wireQoe(videoEl, onQoe, { native: true, startAt })
    const recovery = createNativeRecovery({
      onRetry: () => reconnectNative(videoEl, playlistUrl),
      onStatus,
    })
    const onPlaying = () => recovery.playing()
    const onErrorEvent = () => recovery.failed()
    videoEl.addEventListener('playing', onPlaying)
    videoEl.addEventListener('error', onErrorEvent)
    return {
      destroy: () => {
        clearInterval(timer)
        videoEl.removeEventListener('playing', onPlaying)
        videoEl.removeEventListener('error', onErrorEvent)
        recovery.destroy()
        qoe.destroy()
        videoEl.removeAttribute('src')
        videoEl.load()
        delete window.__edumeetPlaybackPath
        // 네이티브 경로에는 hls.js 설정이 없다 - 앞선 경로가 남긴 값을 지운다.
        delete window.__edumeetHlsConfig
      },
      // ★ 기본 재생(Safari)에서는 화면 시각을 못 구한다. (#185)
      //   null 을 주면 자막을 붙잡지 않고 바로 띄운다 -
      //   어긋난 자막이 없는 자막보다 낫다.
      getPlayingDate: () => null,
      native: true,
    }
  }

  if (path === 'unsupported') {
    onError(new Error('이 브라우저는 HLS 재생을 지원하지 않습니다.'))
    return { destroy: () => {}, native: false }
  }

  // 진단용 따라잡기. 값이 없으면 항목 자체를 안 넣어 hls.js 기본(1 = 끔)을 쓴다.
  const catchup = catchupRate()
  const hls = new Hls({
    // 라이브에서 뒤로 밀리지 않게. 기본값은 버퍼를 크게 잡아 지연이 계속 늘어난다.
    lowLatencyMode: true,
    liveSyncDurationCount: liveSyncDurationCount(), // 진단용: 최신에서 N세그먼트 뒤를 따라간다
    backBufferLength: 30,
    // 첫 화면 중앙값 네이티브 1,625ms · hls.js 2,243ms. 미디어 소스가 붙기 전에 첫 조각을 미리 받는다.
    startFragPrefetch: true,
    ...(catchup === null ? {} : { maxLiveSyncPlaybackRate: catchup }),
  })

  // ★ **hls.js 가 받아들인 값**을 노출한다 - 우리가 넘긴 객체가 아니다. (#233)
  //   하네스가 요청한 설정이 실제로 적용됐는지 회차마다 확인할 수 있어야 한다.
  //   #233 그리드 11회차는 `--catchup-rate` 가 원격 경로에서 빠져 전부 "따라잡기 끔" 으로 돌았는데,
  //   산출물 어디에도 그 사실이 없어서 11회차를 다 돌고 나서야 알았다.
  //   경로 표시(`__edumeetPlaybackPath`)와 같은 방식이다 - destroy 에서 지운다.
  window.__edumeetHlsConfig = hlsConfigSnapshot(hls.config)
  hls.loadSource(playlistUrl)
  hls.attachMedia(videoEl)

  const qoe = wireQoe(videoEl, onQoe, { native: false, startAt })

  // ★ hls.js 가 왜 죽는지 보이게 한다. (#210)
  //   개수만 세면 종류·시점·앱이 한 조치를 알 수 없다. 최근 50건만 고리 버퍼에 남긴다.
  //   재생 동작은 건드리지 않는다 - 아래 분기의 결정은 그대로이고 기록만 늘었다.
  //   hls 인스턴스는 노출하지 않는다(순환 참조가 있고 크다). 로그만 노출한다.
  const hlsLog = createRingLog(HLS_LOG_LIMIT)
  const record = (entry) => {
    hlsLog.push(entry)
    window.__edumeetHlsLog = hlsLog.snapshot()
  }

  /**
   * 매니페스트 재시도 예약. 시도 횟수는 세지만 **상한을 두지 않는다** -
   * 언제 그만둘지는 #241 의 30초 종료 감시가 정한다. 화면을 떠나면(destroy) 취소한다.
   */
  let manifestRetryTimer = null
  let manifestRetryAttempt = 0
  let destroyed = false

  /**
   * 다음 재시도를 예약한다.
   *
   * ★ 시도 횟수는 **예약에 성공했을 때만** 올린다. 이미 예약이 있는데 늦게 도착한 중복 이벤트가
   *   한 번 더 오면, 예약은 무시되면서 횟수만 올라가 다음 지연이 1·2·4 규칙을 건너뛴다.
   *   (검토 #244 1번: 첫 오류로 500ms 예약 → 100ms 뒤 중복 오류 → 횟수만 2가 되어
   *    첫 재시도가 실패하면 1초가 아니라 2초를 기다린다)
   *
   * @returns {number|null} 예약한 지연(ms). 이미 예약돼 있거나 화면을 떠났으면 null
   */
  const scheduleManifestReload = () => {
    if (destroyed || manifestRetryTimer !== null) return null
    const delayMs = manifestRetryDelayMs(manifestRetryAttempt)
    manifestRetryAttempt += 1
    manifestRetryTimer = setTimeout(() => {
      manifestRetryTimer = null
      // startLoad 가 아니라 loadSource 다 - 위 ERROR 분기 주석 참조.
      hls.loadSource(playlistUrl)
    }, delayMs)
    return delayMs
  }

  // 받았다 - 재시도 간격을 처음으로 되돌린다. 다음 404 는 다시 0~1초부터.
  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    manifestRetryAttempt = 0
    if (manifestRetryTimer !== null) {
      clearTimeout(manifestRetryTimer)
      manifestRetryTimer = null
    }
  })

  hls.on(Hls.Events.MANIFEST_LOADED, (_event, data) => {
    record({
      t: Math.round(performance.now()),
      type: 'MANIFEST_LOADED',
      levels: data?.levels?.length ?? null,
    })
  })

  hls.on(Hls.Events.LEVEL_LOADED, (_event, data) => {
    const details = data?.details
    record({
      t: Math.round(performance.now()),
      type: 'LEVEL_LOADED',
      live: details?.live ?? null,
      startSN: details?.startSN ?? null,
      endSN: details?.endSN ?? null,
      fragments: details?.fragments?.length ?? null,
    })
  })

  const state = {
    fragLoadMs: null,
    fragBytes: null,
    errors: 0,
  }

  const emitMetrics = () => onMetrics(snapshotHlsMetrics(hls, videoEl, state))
  const timer = setInterval(emitMetrics, 1000)

  hls.on(Hls.Events.FRAG_LOADED, (_event, data) => {
    state.fragLoadMs = loadTimeMs(data?.stats)
    state.fragBytes = data?.frag?.stats?.loaded ?? data?.stats?.loaded ?? null
    emitMetrics()
  })

  hls.on(Hls.Events.ERROR, (_event, data) => {
    // ★ 화면을 떠난 뒤 들어온 오류는 무시한다. (검토 #244 3번)
    //   실제 hls.js 는 destroy 에서 리스너를 지우지만(그래서 운영 경로에서는 잘 안 온다),
    //   늦게 도착한 비동기 오류가 오면 여기서 죽은 인스턴스에 loadSource·startLoad 를 부른다.
    if (destroyed) return
    state.errors += 1
    emitMetrics()
    if (!data.fatal) {
      // 치명적이지 않은 오류도 종류를 남긴다. 앱이 한 일은 없다.
      record(errorEntry(data, 'none'))
      return
    }
    // ★ 매니페스트 단계 오류는 **지연 뒤 다시 받는다**. (#244)
    //   대기하던 시청자는 방송이 시작되는 순간 처음 붙는데, 그때 ffmpeg 가 아직 첫 live.m3u8 을
    //   쓰지 않았으면 404 다(시작할 때 옛 파일을 지운다).
    //
    //   여기서 startLoad 를 부르면 안 된다 - **매니페스트를 한 번도 못 받은 상태의 startLoad 는
    //   매니페스트를 다시 요청하지 않는다.** 그래서 #241 배포 뒤 시청자 10명 전원이
    //   manifestLoadError(fatal) → startLoad → 그 뒤 아무 일 없음 으로 멈춰 있었다.
    //   (예전에는 직전 방송의 옛 플레이리스트가 남아 있어 첫 요청이 404 가 아니었다.)
    //
    //   간격을 흩는 이유는 아래 manifestRetryDelayMs 주석에 있다 - 전원이 같은 순간 받는다.
    if (data.type === Hls.ErrorTypes.NETWORK_ERROR && isManifestStageError(data.details)) {
      // 예약에 성공했을 때만 지연·횟수가 올라간다. 중복 이벤트면 null 이 온다 -
      // 그때는 기록만 남기고 횟수를 건드리지 않는다(위 scheduleManifestReload 주석).
      const delayMs = scheduleManifestReload()
      record({ ...errorEntry(data, 'reloadManifest'), delayMs, attempt: manifestRetryAttempt })
      return
    }
    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
      record(errorEntry(data, 'startLoad'))
      hls.startLoad()
      return
    }
    if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
      record(errorEntry(data, 'recoverMediaError'))
      hls.recoverMediaError()
      return
    }
    // ★ 복구 못 한 오류만 시청 품질 오류로 센다(여기까지 왔다는 것이 그 뜻이다).
    //   NETWORK_ERROR·MEDIA_ERROR fatal 은 위에서 loadSource 재시도·startLoad·recoverMediaError 로
    //   복구를 시도하므로 여기 오지 않는다.
    //   (#244 이전 주석은 "방송 시작 전 플레이리스트 404 는 대기다 → startLoad" 라고 적었는데,
    //    매니페스트 단계에서는 틀렸다 - 한 번도 매니페스트를 못 받은 상태의 startLoad 는
    //    다시 요청하지 않는다. 그 주석대로 동작해서 10명이 멈췄다.)
    record(errorEntry(data, 'gaveUp'))
    qoe.tracker?.failed()
    onError(new Error(data.details || '재생 오류'))
  })

  return {
    destroy: () => {
      destroyed = true
      clearInterval(timer)
      // 화면을 떠났다 - 예약된 재시도가 뒤늦게 돌면 죽은 플레이어에 loadSource 를 부른다. (#244)
      if (manifestRetryTimer !== null) {
        clearTimeout(manifestRetryTimer)
        manifestRetryTimer = null
      }
      qoe.destroy()
      hls.destroy()
      delete window.__edumeetPlaybackPath
      delete window.__edumeetHlsLog
      delete window.__edumeetHlsConfig
    },
    /**
     * 지금 화면에 보이는 장면의 시각. (#185)
     *
     * <p>매니페스트에 EXT-X-PROGRAM-DATE-TIME 이 있어야 값이 나온다.
     * 없으면 null 이고, 그 경우 자막을 맞출 방법이 없으니 그냥 바로 띄운다.
     *
     * <p>이 값이 필요한 이유 - 자막은 1초 안에 오는데 영상은 몇 초 뒤에 온다.
     * "재생 위치 12.3초" 만으로는 그 화면이 몇 시 것인지 알 수 없어 맞출 수 없다.
     */
    getPlayingDate: () => hls.playingDate ?? null,
    native: false,
  }
}

/**
 * hls.js 가 받아들인 설정에서 노출할 값만 뽑는다. (#233)
 *
 * 순수 함수로 둔 이유 - 시험에서 **실제 hls.js 기본 설정**(`Hls.DefaultConfig`)과
 * 우리가 넘긴 값을 병합해 넘길 수 있다. 그러면 "안 넘기면 hls.js 기본(1 = 따라잡기 끔)" 이
 * 우리 가정이 아니라 라이브러리 값으로 확인된다.
 */
export function hlsConfigSnapshot(config) {
  return {
    liveSyncDurationCount: config?.liveSyncDurationCount ?? null,
    maxLiveSyncPlaybackRate: config?.maxLiveSyncPlaybackRate ?? null,
    lowLatencyMode: config?.lowLatencyMode ?? null,
    startFragPrefetch: config?.startFragPrefetch ?? null,
  }
}

/**
 * 매니페스트 단계 오류의 details. hls.js 의 `ErrorDetails` 값과 같아야 한다. (#244)
 * 이름이 바뀌면 시험이 잡는다(진짜 라이브러리 값과 대조한다).
 */
export const MANIFEST_ERROR_DETAILS = [
  'manifestLoadError',       // 404 등 - 매니페스트를 못 받았다
  'manifestLoadTimeOut',
  'manifestParsingError',
]

/** 매니페스트 단계 오류인가. 조각·레벨 단계는 여기 해당하지 않는다(그건 startLoad 로 충분하다). */
export function isManifestStageError(details) {
  return MANIFEST_ERROR_DETAILS.includes(details)
}

/** 재시도 기본 간격과 상한. (#244) */
export const MANIFEST_RETRY_BASE_MS = 1000
export const MANIFEST_RETRY_MAX_MS = 5000

/**
 * 매니페스트 재시도 지연(ms). **순수 함수다** - 난수를 인자로 받아 시험이 규칙을 고정할 수 있다. (#244)
 *
 * <pre>
 *   attempt 0 → 0 ~ 1초 (무작위)      이미 늦게 온 시청자는 곧바로 한 번 더 본다
 *   attempt 1 → 1초 × 흔들기
 *   attempt 2 → 2초 × 흔들기
 *   attempt 3+ → 5초(상한) × 흔들기
 * </pre>
 *
 * ★ 왜 흩는가. 대기 시청자 전원이 **같은 순간** 404 를 받는다(#191 · #244). 고정 간격이면
 *   전원이 같은 순간에 다시 몰리고, 그 몰림이 다시 404 를 만든다 - 방송이 막 시작해
 *   ffmpeg 가 첫 매니페스트를 쓰는 참이기 때문이다. 흔들면 도착 시각이 퍼진다.
 *
 * ★ 상한은 **기본 간격**에 건다. 흔들기(±50%)가 붙으므로 실제 지연은 최대 7.5초다.
 */
export function manifestRetryDelayMs(attempt, random = Math.random) {
  if (attempt <= 0) return Math.round(random() * MANIFEST_RETRY_BASE_MS)
  const base = Math.min(MANIFEST_RETRY_BASE_MS * 2 ** (attempt - 1), MANIFEST_RETRY_MAX_MS)
  return Math.round(base * (0.5 + random())) // ±50%
}

/** 진단용 liveSyncDurationCount. 사용자 설정이 아니며 허용한 값만 읽는다. */
export function liveSyncDurationCount(storage) {
  try {
    const value = (storage ?? globalThis.localStorage)?.getItem('edumeet.hls.liveSyncDurationCount')
    return value === '1' || value === '2' || value === '3' ? Number(value) : 2
  } catch {
    return 2
  }
}

/**
 * 진단용 따라잡기 재생 속도. (#233)
 *
 * hls.js 의 `maxLiveSyncPlaybackRate` 는 라이브 지연이 목표보다 커지면 재생 속도를 잠깐 올려
 * 따라잡게 한다. 화면 지연은 줄지만 **자막도 그만큼 빨리 지나간다** - 청각장애 학습자에게
 * 자막은 곧 내용이라, 읽기 속도 상한을 넘지 않는 범위에서만 켤 수 있다.
 * 그 범위가 얼마인지 재려고 여기서 값을 받는다(진단용 · 사용자 설정 아님).
 *
 * 허용한 값만 읽는다. 그 밖의 값은 null 을 주고, 그러면 호출자가 항목을 아예 넣지 않아
 * hls.js 기본(1 = 따라잡기 끔)이 된다.
 * `liveSyncDuration`·`liveMaxLatencyDuration` 은 건드리지 않는다 - 그건 다른 손잡이다.
 */
export function catchupRate(storage) {
  const allowed = ['1', '1.05', '1.1', '1.25', '1.5']
  try {
    const value = (storage ?? globalThis.localStorage)?.getItem('edumeet.hls.maxLiveSyncPlaybackRate')
    return allowed.includes(value) ? Number(value) : null
  } catch {
    return null
  }
}

function importHls() {
  return import('hls.js')
}

let hlsLoadPromise = null
let hlsLoadSource = null

/**
 * hls.js 를 동적으로 불러온다. (#217)
 *
 * <p>주소 조회와 겹쳐서 시작할 수 있게 약속을 캐시한다. 같은 로더로 여러 번
 * 불러도 한 번만 import 하고, import 자체가 실패하면 캐시를 비워 다음 시도는
 * 다시 import 한다. 로더 인자는 시험에서 import 를 주입하는 경계다.
 */
export function loadHls(load = importHls) {
  if (hlsLoadPromise && hlsLoadSource === load) return hlsLoadPromise

  let promise
  promise = Promise.resolve()
    .then(load)
    .then((module) => module?.default ?? null)
    .catch(() => {
      if (hlsLoadPromise === promise) {
        hlsLoadPromise = null
        hlsLoadSource = null
      }
      return null
    })
  hlsLoadSource = load
  hlsLoadPromise = promise
  return promise
}

/**
 * hls.js 를 동적으로 불러온다. (#217)
 *
 * ★ 실패해도 던지지 않는다.
 *   청크 로딩은 네트워크 사정으로 실패할 수 있다. 던지면 {@code attachHls} 가 reject 되어
 *   <b>네이티브로 재생할 수 있는 브라우저도 아무것도 못 튼다.</b>
 *   경로를 고르기 전에 import 를 하게 되면서 생긴 회귀다 -
 *   예전에는 Safari 가 import 전에 네이티브로 갔다.
 *   실패를 "hls.js 없음" 으로 보고 {@code choosePlaybackPath} 가 네이티브를 고르게 한다.
 */
export async function loadHlsModule(load = () => import('hls.js')) {
  try {
    const module = await load()
    return module?.default ?? null
  } catch {
    return null
  }
}

/**
 * hls.js 오류 한 건을 로그 항목으로 만든다. (#210)
 *
 * <p>{@code action} 은 앱이 그 오류에 한 일이다. 무엇을 할지 정하는 분기는 호출부에 그대로 있고,
 * 여기서는 이름만 붙인다 - 기록이 재생 동작을 바꾸지 않는다는 것을 이 모양으로 보인다.
 */
function errorEntry(data, action) {
  return {
    t: Math.round(performance.now()),
    type: data?.type ?? null,
    details: data?.details ?? null,
    fatal: Boolean(data?.fatal),
    responseCode: data?.response?.code ?? null,
    action,
  }
}

function loadTimeMs(stats) {
  const loading = stats?.loading
  if (Number.isFinite(loading?.start) && Number.isFinite(loading?.end)) {
    return Math.max(0, Math.round(loading.end - loading.start))
  }
  if (Number.isFinite(stats?.trequest) && Number.isFinite(stats?.tload)) {
    return Math.max(0, Math.round(stats.tload - stats.trequest))
  }
  return null
}

/**
 * 시청 품질 트래커를 <video> 이벤트에 연결한다. (#197)
 *
 * <p>{@code onQoe} 가 없으면 아무 일도 하지 않는다. 품질을 안 재는 화면은 그대로 동작한다.
 *
 * <p><b>오류는 경로마다 다르다.</b> Safari 네이티브는 <video> 의 error 이벤트가 전부이고,
 * hls.js 는 복구 가능한 오류를 스스로 처리하므로 치명적인 것만 호출자가 센다.
 * 그래서 여기서는 네이티브일 때만 error 이벤트를 듣는다 - 안 그러면 같은 오류를 두 번 센다.
 *
 * <p>끊김(waiting → playing)은 두 경로 모두 <video> 이벤트로 잡힌다.
 * hls.js 의 버퍼 이벤트에 기대지 않으므로 Safari 네이티브에서도 세진다.
 */
function wireQoe(videoEl, onQoe, { native, startAt = null }) {
  if (typeof onQoe !== 'function') return { tracker: null, destroy: () => {} }

  const tracker = createQoeTracker()
  const handlers = {
    playing: () => tracker.playing(),
    waiting: () => tracker.waiting(),
    pause: () => tracker.paused(),
    seeking: () => tracker.seeking(),
    seeked: () => tracker.resumedBySeekEnd(),
  }
  if (native) handlers.error = () => tracker.failed()

  Object.entries(handlers).forEach(([event, handler]) => videoEl.addEventListener(event, handler))
  tracker.attached(startAt)
  onQoe(tracker, { native })

  return {
    tracker,
    destroy: () => Object.entries(handlers)
        .forEach(([event, handler]) => videoEl.removeEventListener(event, handler)),
  }
}

/** 오류 난 네이티브 소스를 비우고 다시 붙인다. 순서는 Safari가 요구하는 순서를 따른다. */
function reconnectNative(videoEl, playlistUrl) {
  videoEl.removeAttribute('src')
  videoEl.load()
  videoEl.src = playlistUrl
  videoEl.muted = true
  const result = videoEl.play()
  if (result && typeof result.catch === 'function') result.catch(() => {})
}
