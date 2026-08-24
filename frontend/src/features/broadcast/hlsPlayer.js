/**
 * HLS 재생. (#123)
 *
 * ★ Safari 와 나머지가 다르다.
 *   Safari 는 <video src="...m3u8"> 을 그대로 재생한다. 그 경우 hls.js 를 붙이면
 *   두 개가 같은 스트림을 물어 오히려 깨진다. 네이티브가 되면 네이티브를 쓴다.
 */
import Hls from 'hls.js'

export function attachHls(videoEl, playlistUrl, { onError = () => {} } = {}) {
  if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.src = playlistUrl
    return { destroy: () => { videoEl.removeAttribute('src'); videoEl.load() }, native: true }
  }

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

  hls.on(Hls.Events.ERROR, (_event, data) => {
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

  return { destroy: () => hls.destroy(), native: false }
}
