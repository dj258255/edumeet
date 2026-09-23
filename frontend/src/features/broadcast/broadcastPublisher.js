/**
 * 발표자 송출. MediaRecorder 조각을 HTTP 로 올린다. (#123)
 *
 * ★ WebSocket 을 쓰지 않는다.
 *   이 서비스에서 WebSocket 은 채팅 전용이다. 미디어까지 그 위에 얹으면
 *   방송이 밀릴 때 채팅도 같이 밀린다. 경로를 나누면 한쪽이 막혀도 다른 쪽은 산다.
 *
 * ★ 429 를 성공으로 세지 않는다.
 *   서버 큐가 차면 429 가 온다. 그때 계속 보내면 조각이 버려지고 영상이 깨지는데
 *   발표자는 잘 나가는 줄 안다. 세어서 화면에 보여 준다.
 */
import apiClient from '@/utils/apiClient'
import { chooseMimeType, isRemuxable } from './codecChoice'
import {
  getBroadcastProfile,
  mediaConstraintsFor,
  profileSummary,
  recorderOptionsFor,
} from './broadcastProfiles'
import {
  isBroadcastNotActiveError,
  nextPublisherRecoveryDelay,
  transitionPublisherRecovery,
} from './publisherRecovery'

/** 조각 하나의 길이. 서버의 세그먼트 길이와 맞춘다. */
export const CHUNK_MS = 2000

export function createPublisher(
  meetingId,
  {
    audioOnly = false,
    profileId = 'standard',
    onStatus = () => {},
    random = Math.random,
    sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  } = {},
) {
  let recorder = null
  let stream = null
  let choice = null
  let profile = null
  let recorderGeneration = 0
  let seq = 0
  let sent = 0
  let rejected = 0
  let failed = 0
  let stopped = false
  let restarting = false
  let recoveryState = {}

  function installRecorder(nextRecorder, generation) {
    recorder = nextRecorder
    recorder.ondataavailable = (event) => {
      // 새 서버 세션이 준비될 때까지 들어온 조각은 이전 WebM 헤더와 섞이면 안 된다.
      if (stopped || restarting || generation !== recorderGeneration) return
      if (event.data && event.data.size > 0) {
        const mySeq = seq++
        void upload(event.data, mySeq, generation)
      }
    }
  }

  function stopRecorder() {
    if (recorder && recorder.state !== 'inactive') recorder.stop()
    recorder = null
  }

  async function openBroadcast(generation = recorderGeneration) {
    const nextRecorder = new MediaRecorder(stream, recorderOptionsFor(choice, { audioOnly, profileId }))
    installRecorder(nextRecorder, generation)
    const actual = nextRecorder.mimeType

    try {
      if (stopped) {
        stopRecorder()
        return null
      }
      const { data } = await apiClient.post(`/meeting/${meetingId}/broadcast`, { mimeType: actual })
      if (stopped) {
        stopRecorder()
        return null
      }

      // 서버 세션은 새 WebM 헤더부터 받아야 한다.
      seq = 0
      nextRecorder.start(CHUNK_MS)
      onStatus({
        state: 'live',
        mimeType: actual,
        remuxable: isRemuxable(actual, audioOnly),
        profileId: profile.id,
        profileLabel: profile.label,
        profileSummary: profileSummary(profile.id, audioOnly),
        playlistUrl: data.playlistUrl,
        sent,
        rejected,
        failed,
      })
      return data.playlistUrl
    } catch (error) {
      stopRecorder()
      throw error
    }
  }

  async function start() {
    choice = chooseMimeType(audioOnly)
    profile = getBroadcastProfile(profileId)

    stream = await navigator.mediaDevices.getUserMedia(mediaConstraintsFor({ audioOnly, profileId }))
    return openBroadcast()
  }

  async function upload(blob, mySeq, generation) {
    if (stopped || restarting || generation !== recorderGeneration) return
    try {
      const response = await apiClient.post(`/meeting/${meetingId}/broadcast/chunk`, blob, {
        params: { seq: mySeq },
        headers: { 'Content-Type': 'application/octet-stream' },
        transformRequest: [(d) => d],   // axios 가 Blob 을 건드리지 않게 한다
      })
      sent++
      if (response?.status === 202 && generation === recorderGeneration &&
          recoveryState.phase === 'awaiting-first-chunk') {
        recoveryState = transitionPublisherRecovery(recoveryState, 'accepted', performance.now())
      }
    } catch (e) {
      if (e.response && e.response.status === 429) {
        // 서버가 못 따라가고 있다. 조각이 버려졌다는 뜻이다.
        rejected++
      } else if (isBroadcastNotActiveError(e)) {
        if (generation === recorderGeneration) await recover(generation)
        // 이전 recorder의 늦은 409는 현재 세션의 장애가 아니다.
      } else {
        failed++
      }
    }
    if (!stopped && !restarting) onStatus({ state: 'live', sent, rejected, failed })
  }

  async function recover(failedGeneration) {
    if (stopped || restarting || failedGeneration !== recorderGeneration) return
    restarting = true
    recorderGeneration += 1
    const nextGeneration = recorderGeneration
    stopRecorder()
    recoveryState = transitionPublisherRecovery(recoveryState, 'failure', performance.now())

    while (!stopped) {
      const delayMs = nextPublisherRecoveryDelay(recoveryState.attempt, random)
      onStatus({ state: 'reconnecting', attempt: recoveryState.attempt, delayMs })
      await sleep(delayMs)
      if (stopped) break

      recoveryState = transitionPublisherRecovery(recoveryState, 'retry', performance.now())
      try {
        await openBroadcast(nextGeneration)
        recoveryState = transitionPublisherRecovery(recoveryState, 'restarted', performance.now())
        restarting = false
        return
      } catch {
        // 재시작 요청도 실패하면 다음 대기에서 1·2·4·8초 백오프를 적용한다.
        recoveryState = transitionPublisherRecovery(recoveryState, 'failure', performance.now())
      }
    }
    restarting = false
  }

  async function stop() {
    if (stopped) return
    stopped = true
    if (recorder && recorder.state !== 'inactive') recorder.stop()
    if (stream) stream.getTracks().forEach((t) => t.stop())
    try {
      await apiClient.delete(`/meeting/${meetingId}/broadcast`)
    } finally {
      onStatus({ state: 'stopped', sent, rejected, failed })
    }
  }

  return {
    start,
    stop,
    stats: () => ({ sent, rejected, failed }),
    mediaStream: () => stream,
  }
}
