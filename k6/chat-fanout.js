// 채팅 브로드캐스트 부하. 무한 큐로 OOM 이 나는지 본다. (#43)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 -e SUBSCRIBERS=200 k6/chat-fanout.js
//
// 환경변수
//   BASE_URL     기본 ws://localhost:8081
//   TOKEN        STOMP CONNECT 에 쓸 JWT (필수)
//   MEETING_ID   방 번호 (필수)
//   SUBSCRIBERS  구독만 하는 연결 수, 기본 200
//   PUBLISHERS   발행하는 연결 수, 기본 4
//   RATE         발행자 1명당 초당 메시지 수, 기본 50
//   DURATION     유지 시간, 기본 5m
//
// * k6 는 STOMP 를 모른다. 프레임을 문자열로 조립하고 널 바이트로 끝내야 한다.
// * k6/websockets 를 쓴다. k6/ws 는 레거시, k6/experimental/websockets 는 deprecated.
//   글로벌 이벤트 루프라 VU 1개가 여러 연결을 유지할 수 있다 - 연결 수를 늘리는 데 결정적이다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter, Gauge } from 'k6/metrics';
// k6 의 타이머는 소켓 메서드가 아니라 전역이다.
// k6/experimental/websockets 시절에는 socket.setInterval 이 있었지만
// k6/websockets 로 오면서 없어졌다. 이걸 모르면 TypeError 만 잔뜩 나온다.
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const SUBSCRIBERS = parseInt(__ENV.SUBSCRIBERS || '200');
const PUBLISHERS = parseInt(__ENV.PUBLISHERS || '4');
const RATE = parseInt(__ENV.RATE || '50');
const DURATION = __ENV.DURATION || '5m';

if (!TOKEN || !MEETING_ID) {
  throw new Error('TOKEN 과 MEETING_ID 가 필요하다');
}

// end-to-end 지연. 서버가 페이로드에 넣어준 publishedAt 과 수신 시각의 차이다.
// 기본 메트릭에는 이런 게 없다 - "서버가 처리한 시간" 만 보이고 "사용자가 받기까지" 는 안 보인다.
const e2eLatency = new Trend('chat_e2e_latency_ms', true);
const received = new Counter('chat_received');
const sent = new Counter('chat_sent');
const connectErrors = new Counter('chat_connect_errors');
const openSockets = new Gauge('chat_open_sockets');

export const options = {
  scenarios: {
    // 구독자. 받기만 한다. fan-out 의 증폭 배수를 만든다.
    subscribers: {
      executor: 'per-vu-iterations',
      exec: 'subscriber',
      vus: SUBSCRIBERS,
      iterations: 1,
      maxDuration: DURATION,
    },
    // 발행자. 처리량보다 빠르게 밀어 넣는 것이 목적이다.
    publishers: {
      executor: 'per-vu-iterations',
      exec: 'publisher',
      vus: PUBLISHERS,
      iterations: 1,
      maxDuration: DURATION,
      startTime: '10s',   // 구독이 먼저 붙어야 fan-out 이 생긴다
    },
  },
  // 죽는 것을 보는 시험이라 임계값으로 중단시키지 않는다.
  thresholds: {},
};

const NUL = String.fromCharCode(0);

function frame(command, headers, body) {
  let out = command + '\n';
  for (const k of Object.keys(headers)) {
    out += k + ':' + headers[k] + '\n';
  }
  out += '\n' + (body || '') + NUL;
  return out;
}

const CONNECT = frame('CONNECT', {
  'accept-version': '1.2',
  'heart-beat': '0,0',
  host: 'localhost',
  Authorization: 'Bearer ' + TOKEN,
});

function subscribeFrame(id) {
  return frame('SUBSCRIBE', { id: id, destination: '/topic/rooms/' + MEETING_ID });
}

function sendFrame(payload) {
  return frame('SEND', {
    destination: '/app/rooms/' + MEETING_ID + '/send',
    'content-type': 'application/json',
  }, JSON.stringify(payload));
}

/** MESSAGE 프레임에서 JSON 본문만 꺼낸다. 헤더와 본문은 빈 줄로 갈린다. */
function bodyOf(text) {
  const split = text.indexOf('\n\n');
  if (split < 0) return null;
  let body = text.slice(split + 2);
  if (body.charCodeAt(body.length - 1) === 0) {
    body = body.slice(0, -1);
  }
  return body;
}

function open(onConnected, onMessage) {
  const ws = new WebSocket(BASE_URL + '/ws');
  // 시나리오 시간이 끝나면 닫는다. 닫지 않으면 VU 가 끝나지 않는다.
  setTimeout(function () { ws.close(); }, durationMs());
  ws.onopen = function () {
    openSockets.add(1);
    ws.send(CONNECT);
  };
  ws.onmessage = function (e) {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') === 0) {
      onConnected(ws);
    } else if (text.indexOf('MESSAGE') === 0) {
      onMessage(text);
    } else if (text.indexOf('ERROR') === 0) {
      connectErrors.add(1);
      ws.close();
    }
  };
  ws.onerror = function () { connectErrors.add(1); };
  return ws;
}

export function subscriber() {
  open(
    function (socket) { socket.send(subscribeFrame('sub-' + __VU)); },
    function (text) {
      received.add(1);
      const body = bodyOf(text);
      if (!body) return;
      try {
        const msg = JSON.parse(body);
        if (msg.publishedAt) {
          e2eLatency.add(Date.now() - msg.publishedAt);
        }
      } catch (err) { /* 잘린 프레임은 버린다 */ }
    });
}

export function publisher() {
  open(
    function (socket) {
      // 처리량보다 빠르게 밀어 넣는다. 무한 큐면 여기서 힙이 쌓인다.
      const intervalMs = Math.max(1, Math.floor(1000 / RATE));
      const timer = setInterval(function () {
        socket.send(sendFrame({ content: 'load-' + __VU + '-' + Date.now() }));
        sent.add(1);
      }, intervalMs);
      setTimeout(function () { clearInterval(timer); }, durationMs() - 1000);
    },
    function () { received.add(1); });
}

// --summary-export 는 k6 v1 에서 deprecated 다. 스크립트가 직접 쓴다.
export function handleSummary(data) {
  const out = {};
  out[__ENV.SUMMARY_PATH || 'chat-summary.json'] = JSON.stringify(data, null, 2);
  out.stdout = textSummary(data);
  return out;
}

function textSummary(data) {
  const m = data.metrics || {};
  const get = (name, field) => (m[name] && m[name].values ? m[name].values[field] : undefined);
  const sent = get('chat_sent', 'count') || 0;
  const received = get('chat_received', 'count') || 0;
  return [
    '',
    `  발행       ${sent}`,
    `  수신       ${received}`,
    `  fan-out    ${sent ? (received / sent).toFixed(1) : '-'}배`,
    `  e2e p95    ${(get('chat_e2e_latency_ms', 'p(95)') || 0).toFixed(1)} ms`,
    `  e2e max    ${(get('chat_e2e_latency_ms', 'max') || 0).toFixed(1)} ms`,
    `  연결오류    ${get('chat_connect_errors', 'count') || 0}`,
    `  ws 세션    ${get('ws_sessions', 'count') || 0}`,
    '',
  ].join('\n');
}

function durationMs() {
  const m = /^(\d+)(s|m)$/.exec(DURATION);
  if (!m) return 300000;
  return parseInt(m[1]) * (m[2] === 'm' ? 60000 : 1000);
}
