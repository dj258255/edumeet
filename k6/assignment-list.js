// 과제 목록 조회 부하 측정.
//
//   k6 run -e STRATEGY=naive -e LABEL=before k6/assignment-list.js
//
// 환경변수
//   STRATEGY  naive | batch   조회 전략
//   LABEL     결과 파일 이름에 쓰는 라벨
//   BASE_URL  기본 http://localhost:8081
//   VUS       최대 동시 사용자, 기본 50
//   DURATION  유지 시간, 기본 60s
//   WARMUP    1 이면 워밍업 모드(짧게 돌고 결과를 저장하지 않는다)

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const STRATEGY = __ENV.STRATEGY || 'batch';
const LABEL = __ENV.LABEL || STRATEGY;
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP === '1';

// 응답 헤더로 돌아오는 요청당 SQL 개수. 지연시간이 왜 그렇게 나왔는지 설명해준다.
const queryCount = new Trend('sql_query_count');
const queryCountTotal = new Counter('sql_query_total');

export const options = WARMUP
  ? {
      // 워밍업: JIT 컴파일과 InnoDB 버퍼 풀을 채운다. 결과는 버린다.
      vus: 10,
      duration: '30s',
      thresholds: {},
    }
  : {
      stages: [
        { duration: '15s', target: VUS },  // 램프업
        { duration: DURATION, target: VUS }, // 유지 — 이 구간이 측정 대상이다
        { duration: '5s', target: 0 },
      ],
      thresholds: {
        // 실패시켜 중단하지 않는다. 개선 전 수치를 끝까지 받아야 비교가 된다.
        http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
      },
      // k6 기본 요약에는 p(99) 가 없다. 꼬리 지연을 보려면 명시해야 한다.
      summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    };

export default function () {
  // 클래스 5개를 고르게 돈다. 한 클래스만 때리면 MySQL 쿼리 캐시와
  // 버퍼 풀이 지나치게 유리하게 작동해 실제보다 좋게 나온다.
  const classId = (__ITER % 5) + 1;

  const res = http.get(
    `${BASE_URL}/api/perf/assignments?classId=${classId}&strategy=${STRATEGY}`,
    { tags: { strategy: STRATEGY, label: LABEL } }
  );

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    '결과가 비어 있지 않다': (r) => parseInt(r.headers['X-Result-Size'] || '0') > 0,
  });

  if (ok && res.headers['X-Query-Count']) {
    const n = parseInt(res.headers['X-Query-Count']);
    queryCount.add(n);
    queryCountTotal.add(n);
  }
}

export function handleSummary(data) {
  if (WARMUP) return {};

  const m = data.metrics;
  const g = (name, stat) => (m[name] && m[name].values[stat] != null ? m[name].values[stat] : null);

  const summary = {
    label: LABEL,
    strategy: STRATEGY,
    vus: VUS,
    duration: DURATION,
    batch_fetch_size: __ENV.PERF_BATCH_SIZE || 'unknown',
    requests: g('http_reqs', 'count'),
    rps: g('http_reqs', 'rate'),
    failed_rate: g('http_req_failed', 'rate'),
    latency_ms: {
      avg: g('http_req_duration', 'avg'),
      med: g('http_req_duration', 'med'),
      p90: g('http_req_duration', 'p(90)'),
      p95: g('http_req_duration', 'p(95)'),
      p99: g('http_req_duration', 'p(99)'),
      max: g('http_req_duration', 'max'),
    },
    sql_per_request: {
      avg: g('sql_query_count', 'avg'),
      med: g('sql_query_count', 'med'),
      max: g('sql_query_count', 'max'),
    },
    sql_total: g('sql_query_total', 'count'),
  };

  const line = (k, v) => `  ${k.padEnd(22)} ${v}`;
  const text = [
    ``,
    `── ${LABEL} (strategy=${STRATEGY}, batch_fetch_size=${summary.batch_fetch_size}) ──`,
    line('요청 수', summary.requests),
    line('처리량 RPS', summary.rps ? summary.rps.toFixed(1) : '-'),
    line('실패율', summary.failed_rate != null ? (summary.failed_rate * 100).toFixed(2) + '%' : '-'),
    line('지연 평균', fmt(summary.latency_ms.avg)),
    line('지연 p95', fmt(summary.latency_ms.p95)),
    line('지연 p99', fmt(summary.latency_ms.p99)),
    line('요청당 SQL', summary.sql_per_request.avg ? summary.sql_per_request.avg.toFixed(1) : '-'),
    ``,
  ].join('\n');

  return {
    stdout: text,
    [`docs/performance/data/k6-${LABEL}.json`]: JSON.stringify(summary, null, 2),
  };
}

function fmt(v) {
  return v == null ? '-' : v.toFixed(1) + ' ms';
}
