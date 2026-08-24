// 대칭 채팅 부하 — 모두가 보내고 모두가 받는다. (#85)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 -e USERS=200 k6/chat-symmetric.js
//
// ── chat-fanout.js 와 무엇이 다른가 ────────────────────────────────
//
//   fanout    소수 발행 : 다수 구독.  라이브·오디오 방송 채팅을 닮았다.
//             인바운드는 N 과 무관하게 고정이고 아웃바운드만 N 배로 는다.
//
//   symmetric 모두 발행 : 모두 구독.  화상 수업·단톡방을 닮았다.
//             인바운드도 N 에 비례해 늘고, 아웃바운드는 N^2 에 비례한다.
//             그래서 같은 인원이라도 이쪽이 훨씬 빨리 무너진다.
//
// 두 모양을 다 재는 이유는 EduMeet 의 방송 모드가 셋이고
// 그중 화상강의만 대칭에 가깝기 때문이다. 하나만 재면 나머지 모드를 잘못 말하게 된다.
//
// 환경변수
//   BASE_URL        기본 ws://localhost:8081
//   TOKEN           STOMP CONNECT 에 쓸 JWT (필수)
//   MEETING_ID      방 번호 (필수)
//   USERS           참가자 수(전원이 보내고 받는다), 기본 100
//   MSGS_PER_MIN    참가자 1명당 분당 메시지 수, 기본 6 (10초에 한 번)
//   DURATION        유지 시간, 기본 60s

import { WebSocket } from 'k6/websockets';
import { Trend, Counter, Gauge } from 'k6/metrics';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const USERS = parseInt(__ENV.USERS || '100');
const MSGS_PER_MIN = parseFloat(__ENV.MSGS_PER_MIN || '6');
const DURATION = __ENV.DURATION || '60s';

if (!TOKEN || !MEETING_ID) {
  throw new Error('TOKEN 과 MEETING_ID 가 필요하다');
}

const e2eLatency = new Trend('chat_e2e_latency_ms', true);
// ★ 연결 수립 시간. WebSocket 표준 부하 항목인데 fanout 스크립트에는 없었다.
//   "붙는 데 얼마나 걸리나" 와 "붙은 뒤 얼마나 빠른가" 는 다른 질문이고,
//   사용자가 방에 들어갈 때 체감하는 것은 앞쪽이다.
const connectTime = new Trend('chat_connect_ms', true);
const received = new Counter('chat_received');
const sent = new Counter('chat_sent');
const connectErrors = new Counter('chat_connect_errors');
const openSockets = new Gauge('chat_open_sockets');

export const options = {
  scenarios: {
    users: {
      executor: 'per-vu-iterations',
      exec: 'chatter',
      vus: USERS,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  // 무너지는 지점을 보는 시험이라 임계값으로 중단시키지 않는다.
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

function bodyOf(text) {
  const split = text.indexOf('\n\n');
  if (split < 0) return null;
  let body = text.slice(split + 2);
  if (body.charCodeAt(body.length - 1) === 0) body = body.slice(0, -1);
  return body;
}

function durationMs() {
  const m = /^(\d+)([sm])$/.exec(DURATION);
  if (!m) return 60000;
  return parseInt(m[1]) * (m[2] === 'm' ? 60000 : 1000);
}

export function chatter() {
  const openedAt = Date.now();
  const ws = new WebSocket(BASE_URL + '/ws');
  setTimeout(function () { ws.close(); }, durationMs());

  ws.onopen = function () {
    openSockets.add(1);
    ws.send(CONNECT);
  };

  ws.onmessage = function (e) {
    const text = typeof e.data === 'string' ? e.data : '';

    if (text.indexOf('CONNECTED') === 0) {
      connectTime.add(Date.now() - openedAt);
      ws.send(subscribeFrame('sym-' + __VU));

      // ★ 전원이 보낸다. 시작 시점을 흩뿌려서 같은 순간에 몰리지 않게 한다.
      //   흩뿌리지 않으면 실제 대화가 아니라 동시 폭주를 재게 된다.
      const intervalMs = Math.max(1, Math.floor(60000 / MSGS_PER_MIN));
      const jitter = Math.floor((__VU / USERS) * intervalMs);
      setTimeout(function () {
        const timer = setInterval(function () {
          // 닫힌 소켓에 보내면 InvalidStateError 로 VU 가 죽는다.
          // 시나리오 종료와 타이머가 정확히 같은 순간에 끝나지 않기 때문에 반드시 확인해야 한다.
          if (ws.readyState !== 1) { clearInterval(timer); return; }
          ws.send(sendFrame({ content: 'user' + __VU + '-' + Date.now() }));
          sent.add(1);
        }, intervalMs);
        setTimeout(function () { clearInterval(timer); }, durationMs() - jitter - 1000);
      }, jitter);

    } else if (text.indexOf('MESSAGE') === 0) {
      received.add(1);
      const body = bodyOf(text);
      if (!body) return;
      try {
        const msg = JSON.parse(body);
        if (msg.publishedAt) e2eLatency.add(Date.now() - msg.publishedAt);
      } catch (err) { /* 잘린 프레임은 버린다 */ }
    } else if (text.indexOf('ERROR') === 0) {
      connectErrors.add(1);
      ws.close();
    }
  };

  ws.onerror = function () { connectErrors.add(1); };
}

export function handleSummary(data) {
  const get = (name, stat) =>
    data.metrics[name] && data.metrics[name].values
      ? data.metrics[name].values[stat] : undefined;
  const n = (v, d) => (v === undefined ? '-' : v.toFixed(d === undefined ? 0 : d));

  const out = {};
  out[__ENV.SUMMARY_PATH || 'chat-symmetric-summary.json'] = JSON.stringify(data, null, 2);
  out.stdout = [
    '',
    `  참가자      ${USERS} 명 (전원 발행+구독), ${MSGS_PER_MIN} msg/분`,
    `  발행        ${n(get('chat_sent', 'count'))}`,
    `  수신        ${n(get('chat_received', 'count'))}`,
    `  증폭        ${n(get('chat_received', 'count') / (get('chat_sent', 'count') || 1), 1)}배`,
    `  연결 p95    ${n(get('chat_connect_ms', 'p(95)'))} ms`,
    `  연결 max    ${n(get('chat_connect_ms', 'max'))} ms`,
    `  e2e p95     ${n(get('chat_e2e_latency_ms', 'p(95)'))} ms`,
    `  e2e max     ${n(get('chat_e2e_latency_ms', 'max'))} ms`,
    `  연결오류    ${n(get('chat_connect_errors', 'count')) || 0}`,
    // Gauge 는 마지막 값만 남는다. 최대치를 봐야 실제로 몇 개가 붙었는지 알 수 있고,
    // 그마저도 서버의 chat_sessions_active 가 최종 근거다.
    `  ws 세션     ${n(get('chat_open_sockets', 'max'))} (서버 지표로 교차 확인할 것)`,
    '',
  ].join('\n');
  return out;
}
