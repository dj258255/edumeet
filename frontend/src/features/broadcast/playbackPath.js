/**
 * 재생 경로 선택. (#217)
 *
 * ★ 왜 hls.js 가 먼저인가.
 *   hls.js 공식 권장 순서다. 그리고 이 앱은 <b>hls.js 경로에만</b> 자막 정렬(화면 시각, #185)과
 *   지연 설정({@code liveSyncDurationCount})이 있다 - 네이티브로 가면 그 둘이 조용히 꺼진다.
 *
 * ★ 왜 "네이티브 = Safari" 가정이 깨졌나.
 *   예전에는 {@code canPlayType('application/vnd.apple.mpegurl')} 가 참이면 네이티브를 먼저 썼다.
 *   그런데 Chrome 도 이 값에 {@code "maybe"} 를 돌려준다(Playwright Chromium 153 에서 확인).
 *   그래서 Chrome 이 hls.js 를 타지 않았다. (#217)
 *
 * ★ iOS Safari 는 그대로 네이티브로 간다.
 *   MSE 가 없어 hls.js 를 못 쓴다({@code Hls.isSupported()} 가 false).
 *   {@code ManagedMediaSource} 지원 여부는 hls.js 버전에 따라 다르므로, 지원하지 않으면
 *   네이티브가 유일한 길이다.
 *
 * @param {{ hlsJsSupported?: boolean, nativeHlsSupported?: boolean, forcedPath?: string }} capabilities
 * @returns {'hlsjs' | 'native' | 'unsupported'}
 */
export function choosePlaybackPath({
  hlsJsSupported = false,
  nativeHlsSupported = false,
  forcedPath = null,
} = {}) {
  // 진단용 강제 경로다. 지원하지 않는 브라우저까지 우회시키면 재생 자체가 깨지므로
  // 네이티브가 실제로 가능한 경우에만 적용한다. 사용자 기능으로 노출하지 않는다.
  if (forcedPath === 'native' && nativeHlsSupported) return 'native'
  if (hlsJsSupported) return 'hlsjs'
  if (nativeHlsSupported) return 'native'
  return 'unsupported'
}

/** 진단용 localStorage override. 저장소가 없는 환경에서는 조용히 기본 경로를 쓴다. */
export function forcedPlaybackPath(storage) {
  try {
    const source = storage ?? globalThis.localStorage
    return source?.getItem('edumeet.playbackPath') === 'native' ? 'native' : null
  } catch {
    return null
  }
}
