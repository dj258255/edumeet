// 끊긴 연결이 다시 붙는가, 붙는 동안 무엇을 놓치는가. (#164)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 k6/ws-reconnect.js
//
// 왜 이 시험이 빠져 있었나
//   지금까지의 채팅 측정은 전부 "붙어서 안 끊긴다" 를 전제했다.
//   실제로는 지하철·엘리베이터·와이파이 전환에서 수시로 끊긴다.
//   강의 중에 끊긴 학습자가 자막을 다시 받기 시작하는지, 그 사이 몇 건을 놓치는지가
//   이 서비스에서는 접속 수보다 중요하다.
//
// 무엇을 재나
//   재접속 성공률 · 재접속에 걸린 시간 · 끊긴 동안 못 받은 건수
//
//   못 받은 건수를 세는 방법 - 발행자가 일련번호를 실어 보내므로,
//   끊기기 직전 번호와 다시 붙은 뒤 첫 번호의 차가 곧 구멍이다.
//   서버가 재전송을 안 하므로(STOMP 기본) 이 구멍은 메워지지 않는다.
//   그 사실을 숫자로 확인하는 것이 이 시험의 목적이다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter } from 'k6/metrics';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const SUBSCRIBERS = parseInt(__ENV.SUBSCRIBERS || '50');
const PUBLISHERS = parseInt(__ENV.PUBLISHERS || '2');
const RATE = parseInt(__ENV.RATE || '10');
const CYCLES = parseInt(__ENV.CYCLES || '3');          // 몇 번 끊었다 붙일 것인가
const UP_MS = parseInt(__ENV.UP_MS || '10000');        // 붙어 있는 구간
const DOWN_MS = parseInt(__ENV.DOWN_MS || '3000');     // 끊어 두는 구간
const SETTLE_MS = parseInt(__ENV.SETTLE_MS || '8000');

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const reconnectMs = new Trend('reconnect_ms', true);
const reconnectOk = new Counter('reconnect_ok');
const reconnectFail = new Counter('reconnect_fail');
const gapCount = new Counter('missed_while_down');   // 끊긴 동안 못 받은 건수
const received = new Counter('received');
const published = new Counter('published');

const TOTAL = SETTLE_MS + CYCLES * (UP_MS + DOWN_MS) + 5000;

export const options = {
  scenarios: {
    subscribers: { executor: 'per-vu-iterations', exec: 'subscriber', vus: SUBSCRIBERS,
                   iterations: 1, maxDuration: `${TOTAL + 20000}ms` },
    publishers:  { executor: 'per-vu-iterations', exec: 'publisher', vus: PUBLISHERS,
                   iterations: 1, startTime: `${SETTLE_MS}ms`, maxDuration: `${TOTAL}ms` },
  },
  thresholds: {},
};

const CONNECT = `CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${TOKEN}\n\n\0`;

function bodyOf(text) {
  const i = text.indexOf('\n\n');
  if (i < 0) return null;
  let b = text.slice(i + 2);
  if (b.charCodeAt(b.length - 1) === 0) b = b.slice(0, -1);
  return b;
}

export function subscriber() {
  // 발행자별 마지막 번호. 다시 붙었을 때 이 값과의 차가 구멍이다.
  let last = {};
  let cycle = 0;

  function connectOnce(isReconnect) {
    const t0 = Date.now();
    const ws = new WebSocket(BASE_URL + '/ws');
    let sawFirstAfterUp = isReconnect ? {} : null;

    ws.onopen = () => ws.send(CONNECT);
    ws.onmessage = (e) => {
      const text = typeof e.data === 'string' ? e.data : '';
      if (text.indexOf('CONNECTED') === 0) {
        if (isReconnect) { reconnectMs.add(Date.now() - t0); reconnectOk.add(1); }
        ws.send(`SUBSCRIBE\nid:sub-${__VU}\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`);
        return;
      }
      if (text.indexOf('MESSAGE') !== 0) return;
      const body = bodyOf(text);
      if (!body) return;
      let msg; try { msg = JSON.parse(body); } catch (err) { return; }
      const m = /^p(\d+)-(\d+)$/.exec(msg.content || '');
      if (!m) return;
      received.add(1);
      const pub = m[1], n = parseInt(m[2]);
      // 다시 붙은 뒤 이 발행자의 첫 메시지에서만 구멍을 센다.
      if (sawFirstAfterUp && !sawFirstAfterUp[pub]) {
        sawFirstAfterUp[pub] = true;
        if (last[pub] !== undefined && n > last[pub] + 1) gapCount.add(n - last[pub] - 1);
      }
      if (last[pub] === undefined || n > last[pub]) last[pub] = n;
    };
    ws.onerror = () => { if (isReconnect) reconnectFail.add(1); };

    // 붙어 있다가 끊고, 잠시 뒤 다시 붙는다. 정해진 횟수만큼 반복한다.
    setTimeout(() => {
      try { ws.close(); } catch (err) {}
      cycle += 1;
      if (cycle <= CYCLES) setTimeout(() => connectOnce(true), DOWN_MS);
    }, isReconnect ? UP_MS : SETTLE_MS + UP_MS);
  }

  connectOnce(false);
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
      ws.send(`SEND\ndestination:/app/rooms/${MEETING_ID}/send\n` +
              `content-type:application/json\n\n${JSON.stringify({ content: `p${__VU}-${n}` })}\0`);
      published.add(1);
    }, Math.max(1, Math.floor(1000 / RATE)));
    setTimeout(() => { clearInterval(timer); try { ws.close(); } catch (err) {} }, TOTAL - SETTLE_MS);
  };
}

export function handleSummary(data) {
  const c = (k) => (data.metrics[k] && data.metrics[k].values.count) || 0;
  const t = (k, f) => (data.metrics[k] && data.metrics[k].values[f]) || 0;
  const tries = c('reconnect_ok') + c('reconnect_fail');
  return { stdout: [
    '',
    `  구독자 ${SUBSCRIBERS} · 끊었다 붙이기 ${CYCLES}회 · 끊어 둔 시간 ${DOWN_MS / 1000}초`,
    `  재접속 시도    ${tries}`,
    `  재접속 성공    ${c('reconnect_ok')}   실패 ${c('reconnect_fail')}`,
    `  재접속 p95     ${t('reconnect_ms', 'p(95)').toFixed(0)} ms`,
    `  재접속 최대    ${t('reconnect_ms', 'max').toFixed(0)} ms`,
    `  발행           ${c('published')}`,
    `  수신           ${c('received')}`,
    `  끊긴 동안 놓친 건수 ${c('missed_while_down')}`,
    '', ''].join('\n') };
}
