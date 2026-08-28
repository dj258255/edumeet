// 연결 수립이 느려지는 원인이 서버인지 부하 도구인지 가른다. (#163)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 -e CONNECTIONS=300 k6/connect-time.js
//
// 09 문서가 남겨 둔 자리
//   참가자를 늘리자 연결 수립 p95 가 415 -> 713 -> 1,313ms 로 인원에 거의 정비례했다.
//   그런데 p95 와 max 가 거의 붙어 있었다(1,313 vs 1,354). 고정 속도로 빠지는 큐의
//   모양인데, 그 큐가 서버가 아니라 k6 안에 있을 수도 있다.
//   "가르려면 분산 k6 가 필요하다" 고 적고 덮어 뒀다.
//
// 분산 없이 가르는 법
//   같은 인원을 서버 안과 서버 밖(터널) 두 곳에서 열어 본다.
//
//     k6 안의 큐가 원인이면   두 곳이 비슷하게 느리다. 왕복이 길어져도 큐가 병목이라
//     서버가 원인이면        밖이 더 느리다. 수립마다 왕복이 한 번씩 더 붙으므로
//
//   그리고 첫 연결과 마지막 연결의 시각 차(= 램프 전체 길이)를 같이 본다.
//   클라이언트가 직렬화하고 있으면 이 값이 연결 수 x 고정 간격으로 나온다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter, Gauge } from 'k6/metrics';
import { setTimeout } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8081';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const CONNECTIONS = parseInt(__ENV.CONNECTIONS || '200');
const HOLD_MS = parseInt(__ENV.HOLD_MS || '20000');

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

// 소켓 열기 -> CONNECTED 프레임까지. STOMP 핸드셰이크를 포함한 체감 수립 시간이다.
const connectMs = new Trend('connect_ms', true);
// 소켓 열기 -> onopen 까지. TCP + WS 업그레이드만.
const openMs = new Trend('ws_open_ms', true);
const connected = new Counter('connected');
const failed = new Counter('connect_failed');
const rampMs = new Gauge('ramp_total_ms');   // 첫 연결과 마지막 연결의 시각 차

export const options = {
  scenarios: {
    connect: { executor: 'per-vu-iterations', exec: 'run', vus: 1, iterations: 1,
               maxDuration: `${HOLD_MS + 60000}ms` },
  },
  thresholds: {},
};

export function run() {
  const sockets = [];
  let first = 0, last = 0, done = 0;
  const started = Date.now();

  for (let i = 0; i < CONNECTIONS; i++) {
    const t0 = Date.now();
    const ws = new WebSocket(BASE_URL + '/ws');
    ws.onopen = () => {
      openMs.add(Date.now() - t0);
      ws.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${TOKEN}\n\n\0`);
    };
    ws.onmessage = (e) => {
      const text = typeof e.data === 'string' ? e.data : '';
      if (text.indexOf('CONNECTED') !== 0) return;
      const now = Date.now();
      connectMs.add(now - t0);
      connected.add(1);
      if (!first) first = now;
      last = now;
      done += 1;
      if (done === CONNECTIONS) rampMs.add(last - first);
      ws.send(`SUBSCRIBE\nid:sub-${i}\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`);
    };
    ws.onerror = () => failed.add(1);
    sockets.push(ws);
  }

  // 다 붙은 뒤 잠깐 들고 있는다. 서버 쪽 세션 지표를 밖에서 읽을 시간을 준다.
  setTimeout(() => {
    if (done && done < CONNECTIONS) rampMs.add(last - first);
    sockets.forEach((s) => { try { s.close(); } catch (e) {} });
  }, HOLD_MS + (Date.now() - started > 0 ? 0 : 0));
}

export function handleSummary(data) {
  const m = (k, f) => (data.metrics[k] && data.metrics[k].values[f]) || 0;
  const c = (k) => (data.metrics[k] && data.metrics[k].values.count) || 0;
  return { stdout: [
    '',
    `  연결 요청      ${CONNECTIONS}`,
    `  수립 성공      ${c('connected')}   실패 ${c('connect_failed')}`,
    `  수립 p50       ${m('connect_ms', 'p(50)').toFixed(0)} ms`,
    `  수립 p95       ${m('connect_ms', 'p(95)').toFixed(0)} ms`,
    `  수립 최대      ${m('connect_ms', 'max').toFixed(0)} ms`,
    `  (업그레이드만) p95 ${m('ws_open_ms', 'p(95)').toFixed(0)} ms`,
    `  램프 전체      ${m('ramp_total_ms', 'value').toFixed(0)} ms`,
    `  연결당 램프    ${(m('ramp_total_ms', 'value') / Math.max(1, c('connected'))).toFixed(1)} ms`,
    '', ''].join('\n') };
}
