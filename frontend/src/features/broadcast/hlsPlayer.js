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

  const hls = new Hls({
    // 라이브에서 뒤로 밀리지 않게. 기본값은 버퍼를 크게 잡아 지연이 계속 늘어난다.
    lowLatencyMode: true,
    liveSyncDurationCount: liveSyncDurationCount(), // 진단용: 최신에서 N세그먼트 뒤를 따라간다
    backBufferLength: 30,
    // 첫 화면 중앙값 네이티브 1,625ms · hls.js 2,243ms. 미디어 소스가 붙기 전에 첫 조각을 미리 받는다.
    startFragPrefetch: true,
  })
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
    state.errors += 1
    emitMetrics()
    if (!data.fatal) {
      // 치명적이지 않은 오류도 종류를 남긴다. 앱이 한 일은 없다.
      record(errorEntry(data, 'none'))
      return
    }
    // 방송 시작 직전에는 플레이리스트가 아직 없어 404 가 난다. 그건 오류가 아니라 대기다.
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
    //   NETWORK_ERROR·MEDIA_ERROR fatal 은 위에서 startLoad·recoverMediaError 로 복구를 시도하므로
    //   여기 오지 않는다 - 방송 시작 전 플레이리스트 404 도 NETWORK fatal 로 오지만 그건 대기다.
    //   복구되는 동안의 영향은 끊김 시간으로 잡힌다.
    record(errorEntry(data, 'gaveUp'))
    qoe.tracker?.failed()
    onError(new Error(data.details || '재생 오류'))
  })

  return {
    destroy: () => {
      clearInterval(timer)
      qoe.destroy()
      hls.destroy()
      delete window.__edumeetPlaybackPath
      delete window.__edumeetHlsLog
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

/** 진단용 liveSyncDurationCount. 사용자 설정이 아니며 허용한 값만 읽는다. */
export function liveSyncDurationCount(storage) {
  try {
    const value = (storage ?? globalThis.localStorage)?.getItem('edumeet.hls.liveSyncDurationCount')
    return value === '1' || value === '2' || value === '3' ? Number(value) : 2
  } catch {
    return 2
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
