// 세션 정원 제어를 실제 부하에서 검증한다.
//
//   k6 run -e LOCK=true  -e LABEL=with-lock    k6/session-capacity.js
//   k6 run -e LOCK=false -e LABEL=without-lock k6/session-capacity.js
//
// 정원보다 훨씬 많은 사용자가 "동시에" 입장을 시도한다.
// 잠금이 있으면 성공이 정확히 정원만큼이어야 하고, 없으면 넘친다.
//
// 이건 처리량 측정이 아니라 정합성 측정이다. p95 가 아니라
// "정원 30인데 몇 명이 들어갔나"를 본다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const LOCK = __ENV.LOCK !== 'false';
const LABEL = __ENV.LABEL || (LOCK ? 'with-lock' : 'without-lock');
const CAPACITY = parseInt(__ENV.CAPACITY || '30');
const ATTEMPTS = parseInt(__ENV.ATTEMPTS || '150');

const joined = new Counter('join_success');
const rejected = new Counter('join_rejected');
const errored = new Counter('join_error');

export const options = {
  scenarios: {
    // 한 명당 한 번씩, 전원이 동시에 몰린다. 램프업을 두면 경합이 흩어져
    // 잠금 없이도 우연히 정원을 지킬 수 있다.
    burst: { executor: 'per-vu-iterations', vus: ATTEMPTS, iterations: 1, maxDuration: '60s' },
  },
  thresholds: {},
};

export function setup() {
  const info = http.get(`${BASE_URL}/api/perf/sessions/seeded`).json();
  const meetingId = info.meetingId;
  http.post(`${BASE_URL}/api/perf/sessions/reset?meetingId=${meetingId}&limit=${CAPACITY}`);
  return { meetingId };
}

export default function (data) {
  // 참가 기록에 (meeting_id, participant_email) 유니크 제약이 있다.
  // 같은 이메일로 다시 오면 정원을 재소비하지 않으므로 VU 마다 달라야 한다.
  const email = `load-vu${__VU}@edumeet.test`;

  const res = http.post(
    `${BASE_URL}/api/perf/sessions/join?meetingId=${data.meetingId}` +
      `&email=${encodeURIComponent(email)}&lock=${LOCK}`,
    null,
    { tags: { label: LABEL } }
  );

  if (res.status === 200) joined.add(1);
  else if (res.status === 409) rejected.add(1);
  else errored.add(1);

  check(res, { '200 또는 409': (r) => r.status === 200 || r.status === 409 });
}

export function teardown(data) {
  const st = http.get(`${BASE_URL}/api/perf/sessions/state?meetingId=${data.meetingId}`).json();

  const verdict = st.overflow > 0
    ? `정원 초과 ${st.overflow}명 — 정원 제어 실패`
    : '정원 준수';

  console.log(
    `\n── ${LABEL} (lock=${LOCK}) ──\n` +
    `  동시 시도       ${ATTEMPTS}명\n` +
    `  정원            ${st.limit}명\n` +
    `  실제 입장       ${st.active}명\n` +
    `  초과            ${st.overflow}명\n` +
    `  판정            ${verdict}\n`
  );

  // 여기서 리셋하지 않는다. 실행 스크립트가 최종 상태를 다시 읽어
  // 결과 JSON 에 합쳐야 하기 때문이다.
}

export function handleSummary(data) {
  const m = data.metrics;
  const c = (n) => (m[n] && m[n].values.count) || 0;
  const summary = {
    label: LABEL,
    lock: LOCK,
    attempts: ATTEMPTS,
    capacity: CAPACITY,
    join_success: c('join_success'),
    join_rejected: c('join_rejected'),
    join_error: c('join_error'),
    latency_p95_ms: m.http_req_duration ? m.http_req_duration.values['p(95)'] : null,
  };
  return {
    stdout: `\n  ${LABEL}: 성공 ${summary.join_success} / 거절 ${summary.join_rejected} / 오류 ${summary.join_error}\n`,
    [`docs/performance/data/k6-session-${LABEL}.json`]: JSON.stringify(summary, null, 2),
  };
}
