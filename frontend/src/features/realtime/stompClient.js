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

/**
 * 재연결 기준 간격. 실제 대기는 여기에 흩뿌림을 더한다.
 *
 * ★ 고정값을 그대로 쓰면 전원이 같은 순간에 돌아온다. (#191)
 *
 *   전에는 앱이 죽어야 전원이 끊겼다. 무중단 배포(#180)를 넣은 뒤로는
 *   **배포할 때마다** 옛 슬롯을 내리면서 전원이 동시에 끊긴다.
 *   그 전원이 정확히 3초 뒤에 한꺼번에 돌아온다.
 *
 *   300명(측정한 fan-out 상한)에서 재 봤다.
 *
 *     고정 3초   연결 지연 중앙값 1,326ms · p99 2,141ms · 그동안 REST p99 1,144ms
 *     흩뿌림     연결 지연 중앙값    13ms · p99    65ms · 그동안 REST p99   479ms
 *
 *   접속은 양쪽 다 300/300 성공한다. 다르게 나오는 것은 **얼마나 기다리는가**와
 *   **그동안 다른 요청이 얼마나 밀리는가**다. 재접속 자체는 성공하므로
 *   접속 수만 세면 이 차이가 안 보인다.
 *
 *   Slack 은 WebSocket 160만 개가 끊긴 뒤 재접속 폭주가 살아 있던 서버까지
 *   무너뜨려 복구에 135분이 걸렸고, Discord 도 같은 원인으로 두 번 장애를 냈다.
 *   우리 규모에서 그렇게까지 가지는 않지만, 원인의 모양은 같다.
 */
const RECONNECT_MS = 3_000

/**
 * 실제 대기 시간을 정한다. 기준값의 1~3배 사이에서 고른다.
 *
 * <p>★ 지수 백오프가 아니다. 그건 <b>반복 실패</b>에 대한 것이고,
 * 여기서 필요한 것은 <b>첫 시도를 겹치지 않게 하는 것</b>이다.
 * 폭주를 만드는 것은 재시도 횟수가 아니라 <b>동시에 오는 것</b>이다.
 *
 * <p>위쪽을 3배까지 연 이유 - 300명을 9초 창에 흩으면 초당 33명이다.
 * 측정에서 그 정도는 연결 지연이 두 자리 ms 로 끝났다.
 */
export function reconnectDelay(base = RECONNECT_MS, rand = Math.random) {
  return Math.round(base + rand() * base * 2)
}

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
    // ★ 매번 다시 계산돼야 흩어진다. 상수를 넣으면 전원이 같은 값을 쓴다.
    //   stompjs 는 이 값을 재연결 때마다 읽는다.
    get reconnectDelay() {
      return reconnectDelay()
    },
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
