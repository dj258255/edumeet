/**
 * 백엔드 STOMP 연결. 채팅과 자막을 여기서 받는다. (#106)
 *
 * ── 왜 만들었나 ────────────────────────────────────────────────
 *
 *   전에는 채팅과 자막을 LiveKit 데이터채널로 주고받았다.
 *   참가자 브라우저끼리 직접 보내는 구조라 서버가 관여하지 않는다.
 *
 *   그래서 두 가지가 안 됐다.
 *
 *   1. AI 자막이 화면에 도달하지 않았다.
 *      파이썬 AI -> 백엔드(/api/v1/internal/meetings/{id}/captions)
 *      -> STOMP /topic/rooms/{id}/captions 까지는 있는데
 *      듣는 쪽이 없었다. 이 서비스의 존재 이유가 실시간 자막인데.
 *
 *   2. 다시보기 채팅이 불가능했다.
 *      서버를 안 거치니 저장할 수 없다.
 *
 * ── 백엔드 계약 ────────────────────────────────────────────────
 *
 *   연결      /ws                      CONNECT 헤더에 Authorization: Bearer <JWT>
 *   구독      /topic/rooms/{id}         채팅
 *             /topic/rooms/{id}/captions  자막
 *   발행      /app/rooms/{id}/send      { content }
 *
 *   구독 권한은 서버가 MeetingParticipant 로 검사한다. 방에 없으면 ERROR 프레임이 온다.
 */
import { Client } from '@stomp/stompjs'

/**
 * 하트비트 주기(ms).
 *
 * 서버는 25초로 설정돼 있고, STOMP 협상 규칙상 서버가 보낼 주기는
 * max(서버 설정, 클라이언트가 원하는 값) 이다. 여기서 10초를 원해도 25초가 된다.
 *
 * 왜 필요한가 - 채팅은 조용한 구간이 길다. 하트비트가 없으면 중간 프록시가
 * 유휴 연결로 보고 끊는다. 실측으로 nginx 기본값(60초)에서 60.9초에 끊겼다.
 */
const HEARTBEAT_MS = 10_000

/** 재연결 간격. 즉시 재시도하면 서버가 죽었을 때 폭주한다. */
const RECONNECT_MS = 3_000

/**
 * @param {object} options
 * @param {string} options.baseUrl   http(s) 주소. ws(s) 로 바꿔 쓴다
 * @param {string} options.token     JWT
 * @param {number|string} options.meetingId
 * @param {(msg: {sender: string, content: string, publishedAt: number}) => void} options.onChat
 * @param {(cap: {text: string, sequence: number, publishedAt: number}) => void} options.onCaption
 * @param {(state: 'connected'|'disconnected'|'error', detail?: string) => void} [options.onState]
 */
export function createRealtimeClient({
  baseUrl,
  token,
  meetingId,
  onChat,
  onCaption,
  onState = () => {},
}) {
  if (!token) throw new Error('STOMP 연결에 JWT 가 필요하다')
  if (!meetingId) throw new Error('STOMP 연결에 meetingId 가 필요하다')

  // https -> wss, http -> ws. https 페이지에서 ws:// 를 쓰면 브라우저가 막는다.
  const wsUrl = String(baseUrl).replace(/^http/, 'ws').replace(/\/$/, '') + '/ws'

  const client = new Client({
    brokerURL: wsUrl,
    // ★ 토큰은 CONNECT 프레임 헤더로 보낸다. 쿼리스트링에 넣지 않는다 -
    //   URL 은 프록시·브라우저 히스토리·접근 로그에 그대로 남는다.
    connectHeaders: { Authorization: `Bearer ${token}` },
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    reconnectDelay: RECONNECT_MS,
    // 운영에서는 프레임 로그를 끈다. 채팅 내용이 콘솔에 그대로 남는다.
    debug: import.meta.env.DEV ? (s) => console.debug('[stomp]', s) : () => {},
  })

  client.onConnect = () => {
    onState('connected')

    client.subscribe(`/topic/rooms/${meetingId}`, (frame) => {
      try {
        onChat(JSON.parse(frame.body))
      } catch (e) {
        // 프레임 하나가 깨졌다고 구독을 끊지 않는다. 다음 것은 정상일 수 있다.
        console.warn('채팅 프레임 해석 실패', e)
      }
    })

    client.subscribe(`/topic/rooms/${meetingId}/captions`, (frame) => {
      try {
        onCaption(JSON.parse(frame.body))
      } catch (e) {
        console.warn('자막 프레임 해석 실패', e)
      }
    })

    // 끊겼다 붙는 사이의 자막은 서버가 재전송하지 않는다. 실측으로 확인했다 -
    // 3초 끊기면 그 구간 63건이 전부 사라졌다. (#164)
    //
    // 이 목적지를 구독하면 서버가 최근 자막을 이 연결로만, 한 프레임에 담아 보낸다.
    // 나눠 보내면 순서가 안 지켜져서(실측 뒤집힘 80.71%) 전사가 뒤섞인다. (#165)
    //
    // 여기 오는 것에는 replay=true 가 박혀 있다. 실시간 자막 줄에 띄우면 안 된다 -
    // 사용자는 그동안 다음 말을 이미 놓쳤고, 이건 지나간 구간을 채우는 것이다.
    client.subscribe(`/topic/rooms/${meetingId}/captions/gap`, (frame) => {
      try {
        const gap = JSON.parse(frame.body)
        if (Array.isArray(gap)) gap.forEach(onCaption)
      } catch (e) {
        console.warn('자막 복구본 해석 실패', e)
      }
    })
  }

  // 서버가 보내는 ERROR 프레임. 구독 권한이 없을 때 여기로 온다.
  client.onStompError = (frame) => {
    onState('error', frame.headers?.message || 'STOMP 오류')
  }
  client.onWebSocketClose = () => onState('disconnected')

  return {
    connect: () => client.activate(),
    disconnect: () => client.deactivate(),
    /** 메시지를 보낸다. 연결이 끊겨 있으면 조용히 버린다 - 큐에 쌓아 두면 재연결 후 폭주한다. */
    send(content) {
      const text = String(content ?? '').trim()
      if (!text) return false
      if (!client.connected) return false
      client.publish({
        destination: `/app/rooms/${meetingId}/send`,
        body: JSON.stringify({ content: text }),
      })
      return true
    },
    get connected() {
      return client.connected
    },
  }
}
