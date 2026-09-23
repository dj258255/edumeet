// 다시보기 채팅 저장의 유실을 재기 위한 발행 부하. (#201 B8)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 -e RATE=50 -e PUBLISH_MS=20000 k6/chat-archive-publish.js
//
// ★ "수락" 을 무엇으로 세나.
//   구독자가 받은 수다. STOMP receipt 는 브로커·핸들러 구현에 따라 오고 안 온다.
//   이 앱에서 발행이 처리되면 곧바로 방 토픽으로 브로드캐스트되고(발행 경로에서 저장하지 않는다),
//   그 브로드캐스트를 받은 수가 "서버가 처리한 수" 다 = 저장 큐에 들어간 수.
//   수락되지 않은 발행(앱이 죽었거나 연결이 끊긴 뒤)은 여기 안 들어온다 - 그게 우리가 재는 유실의 일부다.
//
// ★ e2e p99 는 본문에 실은 발행 시각으로 잰다.
//   서버가 붙이는 publishedAt 은 저장을 뺀 뒤라 큐 적재 자체의 영향을 못 본다.
//
// ★ 발행자는 구독자가 붙은 뒤(SUBTLE settle)에 시작한다.
//   안 그러면 앞쪽 발행이 구독 전에 나가 "수락" 이 과소 집계된다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter } from 'k6/metrics';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const RATE = parseFloat(__ENV.RATE || '50');          // 초당 발행(전체)
// ★ 발행자는 여러 명일 수 있다. (#201 B9r)
//   setInterval 의 최소 간격이 1ms 라 한 연결로는 1,000건/초를 못 넘는다.
//   그 위 구간(800·1600)을 재려면 발행자를 나눠 붙여야 한다.
const PUBLISHERS = parseInt(__ENV.PUBLISHERS || '1');
const PUBLISH_MS = parseInt(__ENV.PUBLISH_MS || '20000');
const SETTLE_MS = parseInt(__ENV.SETTLE_MS || '4000'); // 구독자가 붙을 시간
const TAIL_MS = parseInt(__ENV.TAIL_MS || '5000');     // 마지막 메시지를 받을 여유

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const published = new Counter('archive_published');
const accepted = new Counter('archive_accepted');
const dup = new Counter('archive_duplicate');
const connectErr = new Counter('archive_connect_errors');
const e2e = new Trend('archive_e2e_ms', true);

export const options = {
  scenarios: {
    subscriber: {
      executor: 'per-vu-iterations', exec: 'subscriber', vus: 1, iterations: 1,
      maxDuration: `${SETTLE_MS + PUBLISH_MS + TAIL_MS + 20000}ms`,
    },
    publisher: {
      executor: 'per-vu-iterations', exec: 'publisher', vus: PUBLISHERS, iterations: PUBLISHERS,
      startTime: `${SETTLE_MS}ms`,
      maxDuration: `${PUBLISH_MS + 20000}ms`,
    },
  },
  thresholds: {},
};

const CONNECT = `CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${TOKEN}\n\n\0`;

function bodyOf(text) {
  const split = text.indexOf('\n\n');
  if (split < 0) return null;
  let body = text.slice(split + 2);
  if (body.charCodeAt(body.length - 1) === 0) body = body.slice(0, -1);
  return body;
}

export function subscriber() {
  const seen = {};
  const ws = new WebSocket(BASE_URL + '/ws');

  setTimeout(() => { try { ws.close(); } catch (e) {} }, SETTLE_MS + PUBLISH_MS + TAIL_MS);

  ws.onopen = () => ws.send(CONNECT);
  ws.onmessage = (e) => {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') === 0) {
      ws.send(`SUBSCRIBE\nid:archive-sub\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`);
      return;
    }
    if (text.indexOf('ERROR') === 0) { connectErr.add(1); return; }
    if (text.indexOf('MESSAGE') !== 0) return;

    const body = bodyOf(text);
    if (!body) return;
    let message;
    try { message = JSON.parse(body); } catch (err) { return; }

    const parsed = /^a(\d+)-(\d+)-(\d+)$/.exec(message.content || '');
    if (!parsed) return;
    const key = `${parsed[1]}-${parsed[2]}`;
    if (seen[key]) { dup.add(1); return; }
    seen[key] = true;

    accepted.add(1);
    // e2e 는 **클라이언트가 보낸 시각**부터 잰다. 서버가 붙이는 publishedAt 은 같은 머신이라
    // 0ms 로 뭉개져 "발행이 느려졌나" 를 못 본다. 여기서 재는 값에는 WS 왕복과 앱 처리가 들어간다.
    e2e.add(Date.now() - parseInt(parsed[3]));
  };
  ws.onerror = () => connectErr.add(1);
}

export function publisher() {
  let n = 0;
  const ws = new WebSocket(BASE_URL + '/ws');
  ws.onopen = () => ws.send(CONNECT);
  ws.onerror = () => connectErr.add(1);
  ws.onclose = () => {};

  ws.onmessage = (e) => {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') !== 0) return;

    const timer = setInterval(() => {
      n += 1;
      const payload = JSON.stringify({ content: `a${__VU}-${n}-${Date.now()}` });
      try {
        ws.send(`SEND\ndestination:/app/rooms/${MEETING_ID}/send\n` +
                `content-type:application/json\n\n${payload}\0`);
        published.add(1);
      } catch (err) {
        // 앱이 죽었거나 연결이 끊겼다. 여기서 센 것은 "수락" 이 아니다.
        connectErr.add(1);
      }
    }, Math.max(1, Math.floor(1000 / (RATE / PUBLISHERS))));

    setTimeout(() => { clearInterval(timer); try { ws.close(); } catch (err) {} }, PUBLISH_MS);
  };
}

export function handleSummary(data) {
  const c = (k) => (data.metrics[k] && data.metrics[k].values.count) || 0;
  const p = (k, q) => (data.metrics[k] && data.metrics[k].values[q]) || 0;

  const summary = {
    published: c('archive_published'),
    accepted: c('archive_accepted'),
    duplicate: c('archive_duplicate'),
    connectErrors: c('archive_connect_errors'),
    e2eP99Ms: Math.round(p('archive_e2e_ms', 'p(99)')),
    e2eP50Ms: Math.round(p('archive_e2e_ms', 'p(50)')),
    // 목표가 아니라 **실제로 낸** 비율이다. 사다리에서 "못 따라간다" 를 판단할 때
    // 목표를 쓰면 안 된다 - 발행 자체가 못 나갔는데 저장이 못 따라간 것처럼 보인다.
    publishers: PUBLISHERS,
    publishMs: PUBLISH_MS,
    publishRate: Math.round(c('archive_published') / (PUBLISH_MS / 1000)),
    acceptRate: Math.round(c('archive_accepted') / (PUBLISH_MS / 1000)),
  };

  const out = [
    '',
    `  발행 시도      ${summary.published} (${summary.publishRate}/s · 발행자 ${PUBLISHERS})`,
    `  수락(구독 수신) ${summary.accepted} (${summary.acceptRate}/s)`,
    `  중복           ${summary.duplicate}`,
    `  연결 오류      ${summary.connectErrors}`,
    `  e2e p50/p99    ${summary.e2eP50Ms} / ${summary.e2eP99Ms} ms`,
    '',
  ].join('\n');

  const result = { stdout: out };
  if (__ENV.SUMMARY_JSON) result[__ENV.SUMMARY_JSON] = JSON.stringify(summary, null, 2);
  return result;
}
