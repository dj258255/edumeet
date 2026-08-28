// 보낸 것이 실제로 다 도착하는지 센다. (#159)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 k6/chat-delivery.js
//
// 왜 이 시험이 필요한가
//   09 문서의 fan-out 측정에서 수신 건수가 기대값의 84.7% / 75.4% 였다.
//   그 미달분이 (a) 구독이 다 붙기 전에 발행이 시작된 구간 (b) 60초에서
//   집계를 끊어 못 받은 것 (c) 실제 유실 중 무엇인지 안 갈랐고,
//   그래서 그 문서는 지연만 말하고 전달률은 말하지 않는다.
//
//   미달분을 못 가르면 지연 수치도 못 믿는다. 늦게 온 것이 집계에서 빠졌다면
//   p95 는 실제보다 좋게 나온다.
//
// 셋을 어떻게 가르나
//   (a) 배리어    구독자가 전부 SUBSCRIBE 를 마친 것을 확인하고 발행을 시작한다
//   (b) drain     발행을 끝낸 뒤에도 집계를 계속 열어 두고, 그 구간에 온 것을
//                 "지연 도착" 으로 따로 센다
//   (c) 나머지    기대 - (제때 + 지연) = 유실
//
//   기대 건수는 발행 × 구독자로 계산하지 않는다. 그건 (a) 를 가정한 값이다.
//   구독자마다 자기가 붙은 뒤 발행된 것만 세도록, 발행자가 일련번호를 싣는다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter, Gauge } from 'k6/metrics';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const SUBSCRIBERS = parseInt(__ENV.SUBSCRIBERS || '200');
const PUBLISHERS = parseInt(__ENV.PUBLISHERS || '4');
const RATE = parseInt(__ENV.RATE || '20');
const PUBLISH_MS = parseInt(__ENV.PUBLISH_MS || '60000');   // 발행 구간
const SETTLE_MS = parseInt(__ENV.SETTLE_MS || '15000');     // 구독 배리어 여유
const DRAIN_MS = parseInt(__ENV.DRAIN_MS || '30000');       // 발행 뒤 늦게 오는 것을 받는 구간

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const onTime      = new Counter('deliv_on_time');       // 발행 구간에 받은 것
const late        = new Counter('deliv_late');          // drain 구간에 받은 것
const expected    = new Counter('deliv_expected');      // 이 구독자가 받았어야 하는 것
const dup         = new Counter('deliv_duplicate');
const e2e         = new Trend('deliv_e2e_ms', true);
const lateE2e     = new Trend('deliv_late_e2e_ms', true);
const published   = new Counter('deliv_published');
const connectErr  = new Counter('deliv_connect_errors');
const subscribed  = new Gauge('deliv_subscribed');

// 발행자는 SETTLE_MS 뒤에 시작한다. 구독자가 다 붙을 시간을 준다.
export const options = {
  scenarios: {
    subscribers: { executor: 'per-vu-iterations', exec: 'subscriber', vus: SUBSCRIBERS,
                   iterations: 1, maxDuration: `${SETTLE_MS + PUBLISH_MS + DRAIN_MS + 20000}ms` },
    publishers:  { executor: 'per-vu-iterations', exec: 'publisher', vus: PUBLISHERS,
                   iterations: 1, startTime: `${SETTLE_MS}ms`,
                   maxDuration: `${PUBLISH_MS + 20000}ms` },
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
  const seen = {};              // "p3-17" -> true. 중복을 따로 센다.
  let subscribedAt = 0;         // 이 구독자가 받기 시작한 시각
  let publishEndsAt = 0;
  const ws = new WebSocket(BASE_URL + '/ws');

  // 발행이 끝나는 시각을 미리 안다. 그 뒤에 온 것이 "지연 도착" 이다.
  publishEndsAt = Date.now() + SETTLE_MS + PUBLISH_MS;
  setTimeout(() => { try { ws.close(); } catch (e) {} },
             SETTLE_MS + PUBLISH_MS + DRAIN_MS);

  ws.onopen = () => ws.send(CONNECT);
  ws.onmessage = (e) => {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') === 0) {
      ws.send(`SUBSCRIBE\nid:sub-${__VU}\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`);
      subscribedAt = Date.now();
      subscribed.add(1);
      return;
    }
    if (text.indexOf('ERROR') === 0) { connectErr.add(1); try { ws.close(); } catch (e2) {} return; }
    if (text.indexOf('MESSAGE') !== 0) return;

    const body = bodyOf(text);
    if (!body) return;
    let msg;
    try { msg = JSON.parse(body); } catch (err) { return; }
    const m = /^p(\d+)-(\d+)-(\d+)$/.exec(msg.content || '');
    if (!m) return;
    const key = `${m[1]}-${m[2]}`;
    const sentAt = parseInt(m[3]);

    if (seen[key]) { dup.add(1); return; }
    seen[key] = true;

    // 내가 붙기 전에 발행된 것은 내 기대값이 아니다.
    if (sentAt < subscribedAt) return;
    expected.add(1);

    const now = Date.now();
    if (now <= publishEndsAt) { onTime.add(1); e2e.add(now - sentAt); }
    else                      { late.add(1);   lateE2e.add(now - sentAt); }
  };
  ws.onerror = () => connectErr.add(1);
}

export function publisher() {
  let n = 0;
  const ws = new WebSocket(BASE_URL + '/ws');
  ws.onopen = () => ws.send(CONNECT);
  ws.onmessage = (e) => {
    const text = typeof e.data === 'string' ? e.data : '';
    if (text.indexOf('CONNECTED') !== 0) return;
    const timer = setInterval(() => {
      n += 1;
      // 보낸 시각을 본문에 싣는다. 서버가 붙이는 시각과 별개로 클라이언트 기준을 잡는다.
      const payload = JSON.stringify({ content: `p${__VU}-${n}-${Date.now()}` });
      ws.send(`SEND\ndestination:/app/rooms/${MEETING_ID}/send\n` +
              `content-type:application/json\n\n${payload}\0`);
      published.add(1);
    }, Math.max(1, Math.floor(1000 / RATE)));
    setTimeout(() => { clearInterval(timer); try { ws.close(); } catch (err) {} }, PUBLISH_MS);
  };
  ws.onerror = () => connectErr.add(1);
}

export function handleSummary(data) {
  const c = (k) => (data.metrics[k] && data.metrics[k].values.count) || 0;
  const p95 = (k) => (data.metrics[k] && data.metrics[k].values['p(95)']) || 0;
  const exp = c('deliv_expected'), ok = c('deliv_on_time'), lt = c('deliv_late');
  // 기대값은 구독자가 실제로 붙은 뒤 발행된 것만 센 값이다. 유실은 그 나머지다.
  const lost = exp - ok - lt;
  const pct = (x) => exp ? (x / exp * 100).toFixed(2) + '%' : '-';
  return { stdout: [
    '',
    `  발행           ${c('deliv_published')}`,
    `  기대(구독 후)  ${exp}`,
    `  제때 도착      ${ok}  ${pct(ok)}`,
    `  지연 도착      ${lt}  ${pct(lt)}   (발행 종료 후 ${DRAIN_MS / 1000}초 안)`,
    `  유실           ${lost}  ${pct(lost)}`,
    `  중복           ${c('deliv_duplicate')}`,
    `  e2e p95        ${p95('deliv_e2e_ms').toFixed(0)} ms`,
    `  지연분 e2e p95 ${p95('deliv_late_e2e_ms').toFixed(0)} ms`,
    `  연결오류       ${c('deliv_connect_errors')}`,
    '', ''].join('\n') };
}
