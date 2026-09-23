/**
 * HLS 재생. (#123)
 *
 * ★ Safari 와 나머지가 다르다.
 *   Safari 는 <video src="...m3u8"> 을 그대로 재생한다. 그 경우 hls.js 를 붙이면
 *   두 개가 같은 스트림을 물어 오히려 깨진다. 네이티브가 되면 네이티브를 쓴다.
 *
 * ★ hls.js 는 동적으로 불러온다.
 *   Safari 는 필요 없고, 방송 시청 화면에 들어온 사람에게만 필요하다.
 *   정적 import 로 묶으면 모바일 사용자가 처음부터 400KB 짜리 플레이어를 받는다.
 */
import { snapshotHlsMetrics, snapshotNativeMetrics } from './hlsMetrics'
import { createQoeTracker } from './playbackQoe'
import { createRingLog, HLS_LOG_LIMIT } from './hlsDiagnostics'

export async function attachHls(videoEl, playlistUrl, { onError = () => {}, onMetrics = () => {}, onQoe } = {}) {
  if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.src = playlistUrl
    const timer = setInterval(() => onMetrics(snapshotNativeMetrics(videoEl)), 1000)
    const qoe = wireQoe(videoEl, onQoe, { native: true })
    return {
      destroy: () => {
        clearInterval(timer)
        qoe.destroy()
        videoEl.removeAttribute('src')
        videoEl.load()
      },
      // ★ 기본 재생(Safari)에서는 화면 시각을 못 구한다. (#185)
      //   null 을 주면 자막을 붙잡지 않고 바로 띄운다 -
      //   어긋난 자막이 없는 자막보다 낫다.
      getPlayingDate: () => null,
      native: true,
    }
  }

  const { default: Hls } = await import('hls.js')

  if (!Hls.isSupported()) {
    onError(new Error('이 브라우저는 HLS 재생을 지원하지 않습니다.'))
    return { destroy: () => {}, native: false }
  }

  const hls = new Hls({
    // 라이브에서 뒤로 밀리지 않게. 기본값은 버퍼를 크게 잡아 지연이 계속 늘어난다.
    lowLatencyMode: true,
    liveSyncDurationCount: 2,     // 최신에서 2세그먼트 뒤를 따라간다
    backBufferLength: 30,
  })
  hls.loadSource(playlistUrl)
  hls.attachMedia(videoEl)

  const qoe = wireQoe(videoEl, onQoe, { native: false })

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
    qoe.tracker?.error()
    onError(new Error(data.details || '재생 오류'))
  })

  return {
    destroy: () => {
      clearInterval(timer)
      qoe.destroy()
      hls.destroy()
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
function wireQoe(videoEl, onQoe, { native }) {
  if (typeof onQoe !== 'function') return { tracker: null, destroy: () => {} }

  const tracker = createQoeTracker()
  const handlers = {
    playing: () => tracker.playing(),
    waiting: () => tracker.waiting(),
    pause: () => tracker.paused(),
    seeking: () => tracker.seeking(),
    seeked: () => tracker.resumedBySeekEnd(),
  }
  if (native) handlers.error = () => tracker.error()

  Object.entries(handlers).forEach(([event, handler]) => videoEl.addEventListener(event, handler))
  tracker.attached()
  onQoe(tracker, { native })

  return {
    tracker,
    destroy: () => Object.entries(handlers)
        .forEach(([event, handler]) => videoEl.removeEventListener(event, handler)),
  }
}

