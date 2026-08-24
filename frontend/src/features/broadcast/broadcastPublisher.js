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

/** 조각 하나의 길이. 서버의 세그먼트 길이와 맞춘다. */
export const CHUNK_MS = 2000

export function createPublisher(meetingId, { audioOnly = false, onStatus = () => {} } = {}) {
  let recorder = null
  let stream = null
  let seq = 0
  let sent = 0
  let rejected = 0
  let failed = 0
  let stopped = false

  async function start() {
    const choice = chooseMimeType(audioOnly)

    stream = await navigator.mediaDevices.getUserMedia(
      audioOnly ? { audio: true } : { audio: true, video: { width: 1280, height: 720 } },
    )

    recorder = choice.mimeType
      ? new MediaRecorder(stream, { mimeType: choice.mimeType })
      : new MediaRecorder(stream)

    // ★ 요청한 값이 아니라 실제로 정해진 값을 보낸다.
    //   MediaRecorder 는 못 쓰는 mimeType 을 조용히 바꾼다.
    const actual = recorder.mimeType
    const { data } = await apiClient.post(`/meeting/${meetingId}/broadcast`, { mimeType: actual })

    onStatus({
      state: 'live',
      mimeType: actual,
      remuxable: isRemuxable(actual, audioOnly),
      playlistUrl: data.playlistUrl,
    })

    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        upload(event.data, seq++)
      }
    }
    recorder.start(CHUNK_MS)
    return data.playlistUrl
  }

  async function upload(blob, mySeq) {
    if (stopped) return
    try {
      await apiClient.post(`/meeting/${meetingId}/broadcast/chunk`, blob, {
        params: { seq: mySeq },
        headers: { 'Content-Type': 'application/octet-stream' },
        transformRequest: [(d) => d],   // axios 가 Blob 을 건드리지 않게 한다
      })
      sent++
    } catch (e) {
      if (e.response && e.response.status === 429) {
        // 서버가 못 따라가고 있다. 조각이 버려졌다는 뜻이다.
        rejected++
      } else {
        failed++
      }
    }
    onStatus({ state: 'live', sent, rejected, failed })
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

  return { start, stop, stats: () => ({ sent, rejected, failed }) }
}
