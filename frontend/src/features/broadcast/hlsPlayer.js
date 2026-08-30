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

export async function attachHls(videoEl, playlistUrl, { onError = () => {}, onMetrics = () => {} } = {}) {
  if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.src = playlistUrl
    const timer = setInterval(() => onMetrics(snapshotNativeMetrics(videoEl)), 1000)
    return {
      destroy: () => {
        clearInterval(timer)
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
    if (!data.fatal) return
    // 방송 시작 직전에는 플레이리스트가 아직 없어 404 가 난다. 그건 오류가 아니라 대기다.
    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
      hls.startLoad()
      return
    }
    if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
      hls.recoverMediaError()
      return
    }
    onError(new Error(data.details || '재생 오류'))
  })

  return {
    destroy: () => {
      clearInterval(timer)
      hls.destroy()
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
