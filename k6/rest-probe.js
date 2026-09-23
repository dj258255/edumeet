// REST 프로브만 도는 k6 스크립트. (#200)
//
// ★ 왜 따로 있나.
//   조각 업로드가 밀리는지 보려면 **채팅과 무관한 요청**이 같이 느려지는지 봐야 한다
//   (docs/performance/15 방식). 기존 프로브는 k6/caption-backpressure.js 안에 있는데,
//   그 스크립트는 자막 발행·구독까지 같이 한다 - 조건 A/B 의 부하를 우리가 정하려면
//   프로브만 떼어 낼 수 있어야 한다.
//
// ★ 경로를 401 로 잡는 이유.
//   JWT 필터에서 끊겨 DB 도 비즈니스 로직도 안 탄다. 즉 **서버가 요청을 받아들이는 능력**만
//   잰다 - 느려지면 그것은 톰캣 스레드나 커넥션이 밀린 것이다. (#151 · docs/performance/15)
//
// 사용:
//   HTTP_BASE=http://localhost:8081 PROBE_RATE=10 DURATION=120s \
//     k6 run --summary-export=probe.json k6/rest-probe.js
//   (요약을 파일로 받으려면 SUMMARY_PATH 를 준다 - 아래 handleSummary 참고)

import http from 'k6/http'
import { Trend, Counter } from 'k6/metrics'

// ★ 요약 텍스트는 직접 만든다 (k6/chat-fanout.js 와 같다).
//   jslib 를 원격에서 불러오면 측정 호스트에 인터넷이 없을 때 스크립트가 아예 안 돈다.

const HTTP_BASE = __ENV.HTTP_BASE || 'http://localhost:8081'
const PROBE_RATE = parseInt(__ENV.PROBE_RATE || '10')
const DURATION = __ENV.DURATION || '120s'

const restProbe = new Trend('rest_probe_ms', true)
const probe401 = new Counter('rest_probe_401')
const probeOther = new Counter('rest_probe_other')

export const options = {
  scenarios: {
    probe: {
      executor: 'constant-arrival-rate',
      rate: PROBE_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  // 요약에 p99 가 실리게 한다 (기본 요약에도 있지만 명시해 둔다)
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
}

export default function () {
  const res = http.get(`${HTTP_BASE}/api/v1/classroom`, {
    tags: { path: 'rest-probe' },
    timeout: '30s',
  })
  restProbe.add(res.timings.duration)
  if (res.status === 401) probe401.add(1)
  else probeOther.add(1)
}

/**
 * 요약을 JSON 으로도 남긴다.
 *
 * <p>셸이 읽기 쉽게 필요한 것만 추린다 - k6 기본 요약은 사람이 읽는 텍스트라
 * 거기서 p99 를 긁으면 형식이 바뀔 때마다 깨진다.
 */
export function handleSummary(data) {
  const metric = data.metrics.rest_probe_ms?.values ?? {}
  const summary = {
    probe: {
      count: metric.count ?? 0,
      p50: metric['p(50)'] ?? null,
      p95: metric['p(95)'] ?? null,
      p99: metric['p(99)'] ?? null,
      max: metric.max ?? null,
    },
    status401: data.metrics.rest_probe_401?.values?.count ?? 0,
    statusOther: data.metrics.rest_probe_other?.values?.count ?? 0,
  }
  const out = {}
  out[__ENV.SUMMARY_PATH || 'rest-probe-summary.json'] = JSON.stringify(summary, null, 2)
  out.stdout = [
    `REST 프로브: ${summary.probe.count}건 · p50 ${fmt(summary.probe.p50)}ms · ` +
      `p95 ${fmt(summary.probe.p95)}ms · p99 ${fmt(summary.probe.p99)}ms · max ${fmt(summary.probe.max)}ms`,
    `  401 ${summary.status401}건 · 그 밖의 상태 ${summary.statusOther}건 (401 이 아니면 프로브 경로가 바뀌었다)`,
    '',
  ].join('\n')
  return out
}

function fmt(v) {
  return v === null || v === undefined ? '-' : v.toFixed(2)
}
