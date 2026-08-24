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
