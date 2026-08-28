// 세션 안에서 메시지 순서가 실제로 뒤집히는지 센다. (#158)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 k6/chat-order.js
//
// 왜 이 시험이 필요한가
//   setPreservePublishOrder 를 켜면 e2e p95 가 2.7배가 된다(15 문서).
//   그래서 껐는데, 끈 상태에서 순서가 실제로 얼마나 뒤집히는지는 안 쟀다.
//   한 번도 안 뒤집히면 끄는 게 공짜고, 자주 뒤집히면
//   "2.7배가 아까워서 대화가 뒤섞이는 것을 방치했다" 가 된다.
//
// 어떻게 세나
//   발행자마다 자기 번호를 1씩 올려 보낸다. 구독자는 발행자별로 마지막 번호를 들고 있다가
//   더 작은 번호가 오면 뒤집힌 것으로 센다.
//
//   발행자를 섞어서 보는 것이 아니라 발행자별로 본다.
//   서로 다른 발행자 사이의 순서는 애초에 보장 대상이 아니다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter } from 'k6/metrics';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const SUBSCRIBERS = parseInt(__ENV.SUBSCRIBERS || '100');
const PUBLISHERS = parseInt(__ENV.PUBLISHERS || '4');
const RATE = parseInt(__ENV.RATE || '20');
const DURATION = __ENV.DURATION || '60s';

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const outOfOrder = new Counter('chat_out_of_order');
const inOrder = new Counter('chat_in_order');
const e2e = new Trend('chat_e2e_latency_ms', true);
const connectErrors = new Counter('chat_connect_errors');

export const options = {
  scenarios: {
    subscribers: { executor: 'per-vu-iterations', exec: 'subscriber',
                   vus: SUBSCRIBERS, iterations: 1, maxDuration: DURATION },
    publishers:  { executor: 'per-vu-iterations', exec: 'publisher',
                   vus: PUBLISHERS, iterations: 1, maxDuration: DURATION, startTime: '10s' },
  },
  thresholds: {},
};

const CONNECT = `CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${TOKEN}\n\n\0`;
const durationMs = () => (parseInt(DURATION) || 60) * 1000;

function bodyOf(text) {
  const split = text.indexOf('\n\n');
  if (split < 0) return null;
  let body = text.slice(split + 2);
  if (body.charCodeAt(body.length - 1) === 0) body = body.slice(0, -1);
  return body;
}

function open(onConnected, onMessage) {
  const ws = new WebSocket(BASE_URL + '/ws');
  setTimeout(() => ws.close(), durationMs());
  ws.onopen = () => ws.send(CONNECT);
  ws.onmessage = (e) => {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') === 0) onConnected(ws);
    else if (text.indexOf('MESSAGE') === 0) onMessage(text);
    else if (text.indexOf('ERROR') === 0) { connectErrors.add(1); ws.close(); }
  };
  ws.onerror = () => connectErrors.add(1);
  return ws;
}

export function subscriber() {
  // 발행자별 마지막 번호. 이 세션이 본 순서다.
  const last = {};
  open(
    (socket) => socket.send(
      `SUBSCRIBE\nid:sub-${__VU}\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`),
    (text) => {
      const body = bodyOf(text);
      if (!body) return;
      try {
        const msg = JSON.parse(body);
        if (msg.publishedAt) e2e.add(Date.now() - msg.publishedAt);
        const m = /^p(\d+)-(\d+)$/.exec(msg.content || '');
        if (!m) return;
        const pub = m[1], n = parseInt(m[2]);
        if (last[pub] !== undefined && n < last[pub]) outOfOrder.add(1);
        else inOrder.add(1);
        if (last[pub] === undefined || n > last[pub]) last[pub] = n;
      } catch (err) { /* 잘린 프레임은 버린다 */ }
    });
}

export function publisher() {
  let n = 0;
  open(
    (socket) => {
      const timer = setInterval(() => {
        n += 1;
        socket.send(`SEND\ndestination:/app/rooms/${MEETING_ID}/send\n` +
          `content-type:application/json\n\n${JSON.stringify({ content: `p${__VU}-${n}` })}\0`);
      }, Math.max(1, Math.floor(1000 / RATE)));
      setTimeout(() => clearInterval(timer), durationMs() - 1000);
    },
    () => {});
}

export function handleSummary(data) {
  const c = (k) => (data.metrics[k] && data.metrics[k].values.count) || 0;
  const p95 = (data.metrics.chat_e2e_latency_ms &&
               data.metrics.chat_e2e_latency_ms.values['p(95)']) || 0;
  const ooo = c('chat_out_of_order'), ok = c('chat_in_order');
  return { stdout: [
    '',
    `  순서대로    ${ok}`,
    `  뒤집힘      ${ooo}`,
    `  뒤집힘 비율 ${ok + ooo ? (ooo / (ok + ooo) * 100).toFixed(4) : 0}%`,
    `  e2e p95     ${p95.toFixed(0)} ms`,
    `  연결오류    ${c('chat_connect_errors')}`,
    '', ''].join('\n') };
}
