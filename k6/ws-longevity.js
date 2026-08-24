// WebSocket 이 프록시 뒤에서 얼마나 오래 유지되는가. (#88)
//
//   k6 run -e BASE_URL=wss://api.example.com -e TOKEN=... -e MEETING_ID=1 k6/ws-longevity.js
//
// ── 무엇을 재나 ────────────────────────────────────────────────
//   nginx 의 proxy_read_timeout 기본값은 60초다.
//   업스트림에서 그 시간 동안 아무것도 안 오면 nginx 가 연결을 끊는다.
//
//   채팅은 조용한 구간이 길다. 수업 중 1분 동안 아무도 말을 안 하는 것은 흔하다.
//   그러면 연결이 끊기고, 다시 붙고, 사용자는 "가끔 끊겨요" 라고 말한다.
//
//   에러 로그도 안 남는다. nginx 는 정상 종료로 처리한다.
//
// ── 읽는 법 ────────────────────────────────────────────────────
//   survivedMs 가 DURATION 에 가까우면      유지된 것
//   survivedMs 가 60초 언저리에서 멈추면    proxy_read_timeout 에 걸린 것
//
//   구독만 하고 아무것도 보내지 않는다. 트래픽이 있으면 타임아웃이 갱신되어
//   문제가 가려진다 - 조용한 연결이어야 이 함정이 드러난다.

import { WebSocket } from 'k6/websockets';
import { Trend, Counter } from 'k6/metrics';
import { setTimeout } from 'k6/timers';

const BASE_URL = __ENV.BASE_URL || 'ws://localhost:8080';
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const DURATION_SEC = parseInt(__ENV.DURATION_SEC || '90');
const LABEL = __ENV.LABEL || 'unnamed';

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const survived = new Trend('ws_survived_ms', true);
const closedEarly = new Counter('ws_closed_early');
const survivedFull = new Counter('ws_survived_full');

export const options = {
  scenarios: {
    idle: { executor: 'per-vu-iterations', vus: 3, iterations: 1,
            maxDuration: `${DURATION_SEC + 20}s` },
  },
  thresholds: {},
};

const NUL = String.fromCharCode(0);
function frame(cmd, headers, body) {
  let out = cmd + '\n';
  for (const k of Object.keys(headers)) out += k + ':' + headers[k] + '\n';
  return out + '\n' + (body || '') + NUL;
}
const CONNECT = frame('CONNECT', {
  'accept-version': '1.2', 'heart-beat': '0,0', host: 'localhost',
  Authorization: 'Bearer ' + TOKEN,
});
const SUBSCRIBE = (id) => frame('SUBSCRIBE',
  { id, destination: '/topic/rooms/' + MEETING_ID });

export default function () {
  const openedAt = Date.now();
  let closedAt = 0;
  const ws = new WebSocket(BASE_URL + '/ws');

  ws.onopen = () => ws.send(CONNECT);
  ws.onmessage = (e) => {
    const t = typeof e.data === 'string' ? e.data : '';
    if (t.indexOf('CONNECTED') === 0) ws.send(SUBSCRIBE('idle-' + __VU));
    // 그 뒤로는 아무것도 보내지 않는다. 조용한 연결을 만드는 것이 목적이다.
  };
  ws.onclose = () => {
    if (!closedAt) {
      closedAt = Date.now();
      const ms = closedAt - openedAt;
      survived.add(ms);
      if (ms < (DURATION_SEC - 5) * 1000) closedEarly.add(1);
      else survivedFull.add(1);
    }
  };
  ws.onerror = () => {};

  // 목표 시간이 지나면 우리가 닫는다. 그 전에 닫혔다면 프록시가 끊은 것이다.
  setTimeout(() => { try { ws.close(); } catch (e) {} }, DURATION_SEC * 1000);
}

export function handleSummary(data) {
  const g = (n, s) => data.metrics[n] && data.metrics[n].values
    ? data.metrics[n].values[s] : undefined;
  const n = (v) => v === undefined ? '-' : (v / 1000).toFixed(1);
  const out = {};
  out[__ENV.SUMMARY_PATH || 'ws-longevity.json'] = JSON.stringify(data, null, 2);
  out.stdout = [
    '',
    `  [${LABEL}]  목표 ${DURATION_SEC}초, 조용한 연결 3개`,
    `    유지 min   ${n(g('ws_survived_ms', 'min'))} 초`,
    `    유지 med   ${n(g('ws_survived_ms', 'med'))} 초`,
    `    유지 max   ${n(g('ws_survived_ms', 'max'))} 초`,
    `    조기 종료  ${g('ws_closed_early', 'count') || 0} 개`,
    `    끝까지     ${g('ws_survived_full', 'count') || 0} 개`,
    '',
  ].join('\n');
  return out;
}
