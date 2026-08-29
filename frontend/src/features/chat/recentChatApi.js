/**
 * 입장 시 보여줄 지난 대화. (#170)
 *
 * 백엔드 계약
 *   GET /api/v1/meeting/{meetingId}/chat/recent  →  최근 50건, 오래된 것부터
 *
 * ★ 다시보기(/replay)와 다른 질문이다.
 *   그쪽은 "그때 무슨 얘기 했나" 라 재생 위치를 받고,
 *   이쪽은 "지금 들어왔는데 방금 무슨 얘기 했나" 라 인자가 없다.
 *
 * ★ 끊긴 동안 놓친 것을 메우는 용도가 아니다.
 *   자막은 재접속 시 놓친 구간을 서버가 밀어 준다. 채팅은 안 한다 -
 *   자막을 놓치면 강의 내용을 놓치지만, 채팅을 놓치는 건 라이브에서 원래 그렇다.
 *   여기서 하는 것은 화면이 통째로 비어 보이지 않게 하는 것까지다.
 */
import apiClient from '@/utils/apiClient'

/**
 * 최근 대화를 가져온다.
 *
 * 실패해도 던지지 않는다. 지난 대화는 있으면 좋은 것이지 없으면 못 쓰는 것이 아니고,
 * 여기서 던지면 그것 때문에 실시간 채팅 연결까지 못 하게 된다.
 *
 * @param {number|string} meetingId
 * @returns {Promise<Array>} 오래된 것부터. 실패하면 빈 배열
 */
export async function fetchRecentChat(meetingId) {
  try {
    const { data } = await apiClient.get(`/meeting/${meetingId}/chat/recent`)
    return Array.isArray(data) ? data : []
  } catch (e) {
    console.warn('지난 대화를 못 가져왔다. 실시간 채팅은 계속한다', e)
    return []
  }
}
