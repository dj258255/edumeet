import { describe, it, expect } from 'vitest'
import {
  getBroadcastProfile,
  mediaConstraintsFor,
  profileSummary,
  recorderOptionsFor,
} from './broadcastProfiles'

describe('broadcast profiles', () => {
  it('없는 프로파일은 표준 720p 로 떨어진다', () => {
    expect(getBroadcastProfile('missing').id).toBe('standard')
  })

  it('데이터 절약 모드는 360p15 로 캡처한다', () => {
    const constraints = mediaConstraintsFor({ audioOnly: false, profileId: 'dataSaver' })

    expect(constraints.video.width.ideal).toBe(640)
    expect(constraints.video.height.ideal).toBe(360)
    expect(constraints.video.frameRate.max).toBe(15)
  })

  it('오디오 방송은 비디오 제약을 아예 보내지 않는다', () => {
    expect(mediaConstraintsFor({ audioOnly: true, profileId: 'standard' })).toEqual({ audio: true })
  })

  it('MediaRecorder 에 실제 비트레이트 힌트를 준다', () => {
    const choice = { mimeType: 'video/webm;codecs=vp8,opus' }
    const options = recorderOptionsFor(choice, { audioOnly: false, profileId: 'dataSaver' })

    expect(options.mimeType).toBe(choice.mimeType)
    expect(options.videoBitsPerSecond).toBe(700_000)
    expect(options.audioBitsPerSecond).toBe(64_000)
  })

  it('프로파일 요약은 발표자 화면에 그대로 보여 줄 수 있다', () => {
    expect(profileSummary('standard', false)).toBe('720p30')
    expect(profileSummary('dataSaver', false)).toBe('360p15')
    expect(profileSummary('dataSaver', true)).toBe('오디오 전용')
  })
})
