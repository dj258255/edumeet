/**
 * 브라우저가 실제로 낼 수 있는 코덱을 고른다. (#123)
 *
 * ★ 이 선택이 서버 CPU 를 결정한다.
 *
 *   H264 를 고르면   서버는 컨테이너만 바꾼다(-c:v copy). 거의 공짜다
 *   VP8 밖에 없으면  서버가 전부 다시 인코딩한다(libx264). 코어 하나를 먹는다
 *
 * 그래서 H264 를 먼저 시도한다. 다만 **원하는 값이 아니라 받아 낸 값을 서버에 보내야 한다** -
 * MediaRecorder 는 요청한 mimeType 을 못 쓰면 조용히 다른 것으로 바꾼다.
 */

/** 위에서부터 시도한다. 앞쪽일수록 서버가 싸다. */
const VIDEO_PREFERENCES = [
  'video/mp4;codecs=avc1.42E01E,mp4a.40.2',   // H264 + AAC — 서버가 그대로 옮긴다
  'video/mp4;codecs=avc1',
  'video/webm;codecs=h264,opus',              // 컨테이너는 webm 인데 안은 H264
  'video/webm;codecs=vp8,opus',               // 여기부터는 서버가 재인코딩한다
  'video/webm',
]

const AUDIO_PREFERENCES = [
  'audio/mp4;codecs=mp4a.40.2',               // AAC — 그대로 옮긴다
  'audio/webm;codecs=opus',                   // Opus — AAC 로 바꾼다(싸다)
  'audio/webm',
]

/**
 * @param {boolean} audioOnly 오디오 방송인가
 * @returns {{mimeType: string|null, remuxable: boolean, tried: string[]}}
 */
export function chooseMimeType(audioOnly, isSupported = defaultIsSupported) {
  const candidates = audioOnly ? AUDIO_PREFERENCES : VIDEO_PREFERENCES
  const tried = []

  for (const candidate of candidates) {
    tried.push(candidate)
    if (isSupported(candidate)) {
      return { mimeType: candidate, remuxable: isRemuxable(candidate, audioOnly), tried }
    }
  }
  // 하나도 안 되면 브라우저 기본값에 맡긴다. 서버가 받은 값으로 판단한다.
  return { mimeType: null, remuxable: false, tried }
}

/** 서버가 재인코딩 없이 넘길 수 있는 조합인가. 화면에 알려 주려고 계산한다. */
export function isRemuxable(mimeType, audioOnly) {
  if (!mimeType) return false
  const m = mimeType.toLowerCase()
  if (audioOnly) return m.includes('mp4a') || m.includes('aac')
  return m.includes('avc1') || m.includes('avc3') || m.includes('h264')
}

function defaultIsSupported(type) {
  return typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(type)
}
