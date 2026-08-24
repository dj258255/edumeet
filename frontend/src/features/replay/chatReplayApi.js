/**
 * 다시보기 채팅 조회. (#115)
 *
 * 백엔드 계약 (#108)
 *   GET /api/v1/meeting/{meetingId}/chat/replay?from=0&to=60000
 *
 *   from/to 는 회의 시작 기준 밀리초다. 절대 시각이 아니다 -
 *   재생 위치와 맞춰야 하기 때문이다.
 *
 *   응답의 hasMore 는 "상한에 걸려 잘렸다" 는 뜻이다.
 *   false 를 "이게 전부" 로 읽으면 안 된다 - 정확히 상한만큼 있을 때도 false 다.
 */
import apiClient from '@/utils/apiClient'

/** 한 번에 물어보는 구간 길이. 재생 위치를 따라가며 조금씩 가져온다. */
export const WINDOW_MS = 60_000

/**
 * 재생 위치 주변의 대화를 가져온다.
 *
 * @param {number|string} meetingId
 * @param {number} fromMillis 구간 시작(포함)
 * @param {number} toMillis   구간 끝(제외)
 */
export async function fetchChatWindow(meetingId, fromMillis, toMillis) {
  const { data } = await apiClient.get(`/meeting/${meetingId}/chat/replay`, {
    params: { from: Math.max(0, Math.floor(fromMillis)), to: Math.floor(toMillis) },
  })
  return data
}

/**
 * 재생 위치가 속한 구간을 계산한다.
 *
 * 위치마다 요청하지 않고 구간 단위로 끊는 이유 -
 * 재생 중에는 위치가 초당 여러 번 바뀐다. 그때마다 요청하면 서버가 견디지 못한다.
 * 같은 구간 안에서는 이미 받아 둔 것을 쓴다.
 */
export function windowOf(positionMs, windowMs = WINDOW_MS) {
  const index = Math.floor(Math.max(0, positionMs) / windowMs)
  return { from: index * windowMs, to: (index + 1) * windowMs, index }
}

/**
 * 구간 안에서 "지금까지 나온" 대화만 고른다.
 *
 * 구간을 통째로 보여주면 아직 오지 않은 대화가 미리 보인다 -
 * 다시보기에서 스포일러가 된다.
 */
export function visibleAt(messages, positionMs) {
  return messages.filter((m) => m.offsetMillis <= positionMs)
}
