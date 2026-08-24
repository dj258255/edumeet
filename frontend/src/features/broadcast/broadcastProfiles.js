/**
 * 송출 품질 프로파일. (#124)
 *
 * ABR 은 delivery 구간에서 여러 화질을 동시에 만드는 일이다.
 * 지금 서버는 2 OCPU 이므로 계단을 늘리는 대신, ingest 부터 덜 보내는 선택지를 둔다.
 */

export const BROADCAST_PROFILES = {
  standard: {
    id: 'standard',
    label: '표준 720p',
    description: '문서·얼굴이 보이는 기본값',
    video: { width: 1280, height: 720, frameRate: 30 },
    videoBitsPerSecond: 2_500_000,
    audioBitsPerSecond: 96_000,
  },
  dataSaver: {
    id: 'dataSaver',
    label: '데이터 절약 360p',
    description: '모바일·약한 네트워크용',
    video: { width: 640, height: 360, frameRate: 15 },
    videoBitsPerSecond: 700_000,
    audioBitsPerSecond: 64_000,
  },
}

export function getBroadcastProfile(id) {
  return BROADCAST_PROFILES[id] || BROADCAST_PROFILES.standard
}

export function mediaConstraintsFor({ audioOnly, profileId }) {
  if (audioOnly) return { audio: true }

  const profile = getBroadcastProfile(profileId)
  return {
    audio: true,
    video: {
      width: { ideal: profile.video.width },
      height: { ideal: profile.video.height },
      frameRate: { ideal: profile.video.frameRate, max: profile.video.frameRate },
    },
  }
}

export function recorderOptionsFor(choice, { audioOnly, profileId }) {
  const profile = getBroadcastProfile(profileId)
  const options = {}
  if (choice.mimeType) options.mimeType = choice.mimeType
  options.audioBitsPerSecond = profile.audioBitsPerSecond
  if (!audioOnly) options.videoBitsPerSecond = profile.videoBitsPerSecond
  return options
}

export function profileSummary(profileId, audioOnly) {
  if (audioOnly) return '오디오 전용'
  const profile = getBroadcastProfile(profileId)
  return `${profile.video.height}p${profile.video.frameRate}`
}
