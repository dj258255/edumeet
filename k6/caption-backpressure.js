// 자막 발행 역압이 REST API 를 막는지 잰다. (#151)
//
//   k6 run -e TOKEN=... -e MEETING_ID=1 k6/caption-backpressure.js
//
// 무엇을 재나
//   아웃바운드가 포화한 상태에서 자막을 계속 밀어 넣으면서
//   (1) 자막 POST 자체가 얼마나 걸리는지
//   (2) 자막과 무관한 REST 호출이 얼마나 걸리는지
//   를 동시에 잰다.
//
//   고치기 전에는 brokerChannel 에 실행기가 없어 자막 POST 를 받은 Tomcat 스레드가
//   그대로 전송까지 떠안았다. 그러면 (1) 도 (2) 도 같이 늘어난다.
//   고친 뒤에는 요청 스레드가 큐에 넣고 바로 돌아오므로 (2) 가 영향을 안 받아야 한다.
//
// 왜 REST 프로브가 401 경로인가
//   JWT 필터에서 끊겨 DB 를 안 탄다. 그래서 이 숫자가 늘었다면
//   DB 나 비즈니스 로직이 아니라 "요청 스레드를 못 받았다" 는 뜻이다.

import http from 'k6/http';
import { WebSocket } from 'k6/websockets';
import { Trend, Counter } from 'k6/metrics';
import { setTimeout } from 'k6/timers';

const WS_BASE   = __ENV.WS_BASE   || 'ws://localhost:18081';   // Toxiproxy 경유(느린 소비자)
const HTTP_BASE = __ENV.HTTP_BASE || 'http://localhost:8081';  // 직접(측정 대상)
const TOKEN = __ENV.TOKEN;
const MEETING_ID = __ENV.MEETING_ID;
const INTERNAL_TOKEN = __ENV.INTERNAL_TOKEN || 'test-internal-token';
const SUBSCRIBERS = parseInt(__ENV.SUBSCRIBERS || '150');
const CAPTION_RATE = parseInt(__ENV.CAPTION_RATE || '50');
const PROBE_RATE = parseInt(__ENV.PROBE_RATE || '10');
const DURATION = __ENV.DURATION || '90s';

if (!TOKEN || !MEETING_ID) throw new Error('TOKEN 과 MEETING_ID 가 필요하다');

const captionIngest = new Trend('caption_ingest_ms', true);
const restProbe = new Trend('rest_probe_ms', true);
const captionSent = new Counter('caption_sent');
const captionFailed = new Counter('caption_failed');

export const options = {
  scenarios: {
    subscribers: { executor: 'per-vu-iterations', exec: 'subscriber', vus: 1, iterations: 1,
                   maxDuration: DURATION },
    captions:    { executor: 'constant-arrival-rate', exec: 'caption', rate: CAPTION_RATE,
                   timeUnit: '1s', duration: DURATION, preAllocatedVUs: 40, maxVUs: 200,
                   startTime: '10s' },
    probe:       { executor: 'constant-arrival-rate', exec: 'probe', rate: PROBE_RATE,
                   timeUnit: '1s', duration: DURATION, preAllocatedVUs: 10, maxVUs: 60,
                   startTime: '10s' },
  },
  thresholds: {},
};

// 구독자를 붙여 아웃바운드에 실을 부하를 만든다.
// Toxiproxy 로 대역폭을 조여 두면 이 연결들이 느린 소비자가 된다.
export function subscriber() {
  const sockets = [];
  for (let i = 0; i < SUBSCRIBERS; i++) {
    const ws = new WebSocket(`${WS_BASE}/ws`);
    ws.onopen = () => {
      ws.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${TOKEN}\n\n\0`);
      ws.send(`SUBSCRIBE\nid:sub-${i}\ndestination:/topic/rooms/${MEETING_ID}/captions\n\n\0`);
      ws.send(`SUBSCRIBE\nid:chat-${i}\ndestination:/topic/rooms/${MEETING_ID}\n\n\0`);
    };
    ws.onerror = () => {};
    sockets.push(ws);
  }
  setTimeout(() => sockets.forEach((s) => { try { s.close(); } catch (e) {} }),
             parseInt(DURATION) * 1000 || 90000);
}

export function caption() {
  const res = http.post(
    `${HTTP_BASE}/api/v1/internal/meetings/${MEETING_ID}/captions`,
    JSON.stringify({ text: '역압 측정용 자막', sequence: __ITER, spokenAt: Date.now(), finalSegment: false }),
    { headers: { 'Content-Type': 'application/json', 'X-Internal-Token': INTERNAL_TOKEN },
      tags: { path: 'caption-ingest' }, timeout: '30s' });
  captionIngest.add(res.timings.duration);
  if (res.status === 200) captionSent.add(1); else captionFailed.add(1);
}

// 자막과 아무 상관 없는 REST 호출. 인증에서 끊겨 DB 를 안 탄다.
// 이것이 느려졌다면 원인은 하나뿐이다 - 요청 스레드를 못 받았다.
export function probe() {
  const res = http.get(`${HTTP_BASE}/api/v1/classroom`,
    { tags: { path: 'rest-probe' }, timeout: '30s' });
  restProbe.add(res.timings.duration);
}
