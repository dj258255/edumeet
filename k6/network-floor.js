// 노트북 -> OCI 네트워크 바닥값. 앞으로 재는 모든 지연에서 이만큼은 서버 탓이 아니다.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 5,
  duration: '15s',
  // ★ http_req_failed 를 임계값으로 쓰지 않는다.
  //   k6 는 non-2xx 를 전부 실패로 센다. 여기서는 401 이 정상 응답이라
  //   이 지표가 100% 로 나오는데 실패가 아니다. checks 로 판정한다.
  thresholds: { checks: ['rate>0.99'] },
};

// 주소는 인자로 받는다. 이 저장소는 공개다. (#41)
//   k6 run -e BASE=http://<host>:8080 k6/network-floor.js
const BASE = __ENV.BASE || 'http://localhost:8080';

export default function () {
  // 인증 거부(401): 필터에서 끊기므로 DB 를 안 탄다. 거의 순수 네트워크 + 서블릿 왕복
  const denied = http.get(`${BASE}/api/v1/classroom`, { tags: { path: 'auth-reject' } });
  check(denied, { '401': (r) => r.status === 401 });
}
