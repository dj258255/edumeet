# 관측 기반 — 붕괴를 보려면 먼저 보여야 한다

> 작성 2026-08-22 · #28 · Phase 0

## 0. 왜 지금인가

[전송 비용 모델](02-egress-cost-model.md)의 결론이 뒤집히면서 이 프로젝트의 주제가
**비용 최적화가 아니라 가용성 한계 측정**이 됐다.

> 무료 10TB 를 넘기려면 시청자 7,400명 × 1시간.
> **그 전에 2 OCPU / 12GB VM 이 먼저 죽는다.**

한계를 측정하려면 **관측이 먼저** 있어야 한다.
지금까지는 k6 로 밖에서 때리고 응답만 봤다. 안에서 무엇이 먼저 포화되는지는 볼 수 없었다.

## 1. 발견 — 설정만 있고 동작하지 않았다

```yaml
management.endpoints.web.exposure.include: health, info, metrics, prometheus
```

**`/actuator/prometheus` 는 404 였다.** `micrometer-registry-prometheus` 의존성이 없었기 때문이다.

> `include` 는 **"있으면 열어라"** 이지 **"만들어라"** 가 아니다.

### 그리고 의존성을 넣어도 여전히 404 였다

```
@ConditionalOnEnabledMetricsExport management.defaults.metrics.export.enabled is considered false
```

`management.prometheus.metrics.export.enabled: true` 를 **명시해야** 레지스트리가 생성된다.

찾기 어려웠던 이유 — **`/actuator/metrics` 는 200 이었다.**
"액추에이터는 되는데 prometheus 만 안 된다" 로 보여서 보안 설정을 한참 뒤졌다.

**설정 파일만 보고 "관측이 되어 있다" 고 믿으면 안 된다.**
그래서 [`PrometheusEndpointTest`](../../src/test/java/com/edu/edumeet/integration/observability/PrometheusEndpointTest.java) 로 고정했다.

## 2. 곁다리로 나온 진짜 버그 — 모든 에러가 401 로 둔갑했다

디버깅 중 발견했다.

```
Secured GET /actuator/prometheus     ← 보안은 통과
Securing GET /error                  ← 404 라서 /error 로 포워드
→ 401 Unauthorized                   ← /error 가 인증을 요구한다
```

**Spring 은 처리되지 않은 요청을 `/error` 로 포워드하는데, 이 경로가 인증을 요구하고 있었다.**
그래서 **404 든 500 이든 전부 401 로 나갔다.** 액추에이터만의 문제가 아니라 **API 전체의 문제**였다.

오타 난 URL 을 호출하면 "인증이 잘못됐나" 를 뒤지게 된다. `/error` 를 permitAll 로 열었다.

## 3. 설계 — 지표를 인터넷에 두지 않는다

```
8080  서비스 포트   publish   ← 인터넷
9090  관리 포트     expose    ← 컨테이너 네트워크 안에서만
```

지표는 **URI 패턴·호출량·커넥션 수** 같은 내부 정보를 담는다.
인증을 붙이는 대신 **네트워크로 격리**했다. Prometheus 는 같은 네트워크 안에 있다.

| 서비스 | 공개 |
|---|---|
| app | **8080** (9090 은 내부만) |
| grafana | **3001** |
| mysql / redis / prometheus | 내부만 |

### 부작용 — 헬스체크가 옮겨간다

**포트를 분리하면 액추에이터가 서비스 포트에서 통째로 사라진다.**
`Dockerfile` 의 `HEALTHCHECK` 가 `localhost:8080/actuator/health` 를 보고 있었으므로
**컨테이너가 영영 unhealthy 로 남았을 것이다.** 관리 포트로 바꿨다.

이것도 테스트로 고정했다 (`health_moves_to_management_port`).

### 포트 판정을 손으로 하지 않는다

```java
if (ManagementPortType.get(environment) == ManagementPortType.DIFFERENT) { ... }
```

`management.server.port=0`(임의 포트)이면 `server.port` 도 0 일 수 있어서
**직접 비교하면 "같은 포트" 로 잘못 판정**된다. Boot 가 이미 이 판단을 한다.

## 4. 대시보드는 손으로 만들지 않는다

```bash
python3 scripts/make_dashboard.py    # → observability/grafana/dashboards/edumeet-baseline.json
```

UI 로 만든 대시보드는 **재현이 안 되고, 누가 무엇을 왜 넣었는지 남지 않는다.**
생성 스크립트에 의도를 주석으로 남기고 결과 JSON 을 커밋한다.

| 패널 | 보는 이유 |
|---|---|
| HTTP 처리율 | 멈추면 포화. 다만 **첫 신호는 여기가 아니다** |
| **p50 / p95 / p99** | **평균은 포화를 숨긴다. p99 가 먼저 벌어진다** |
| 5xx 비율 | 0 이어도 안심할 수 없다 — 지연으로만 나타나는 포화가 있다 |
| 느린 엔드포인트 top | 어디가 느린지 URI 단위 |
| JVM 힙 / GC 정지 | 브로드캐스트가 힙을 밀면 여기부터 반응 |
| CPU | 2 OCPU. **여기가 먼저 찬다** |
| **Hikari 대기(pending)** | **0 이 아니면 커넥션을 기다리는 스레드가 있다** — 트랜잭션 안 느린 I/O 가 여기서 드러난다 |

### `or vector(0)` 가 없으면 "No data" 가 뜬다

5xx 가 하나도 없으면 시계열 자체가 생기지 않아 패널이 비어 보인다.
**"에러 0" 과 "지표 고장" 이 구분되지 않는다.** 그래서 `or vector(0)` 를 붙였다.

## 5. 검증 — 실제로 긁히는지 확인했다

파일만 쓰고 "됐다" 고 하지 않는다.

```
스크레이프 대상    edumeet-app   up   /actuator/prometheus
지표 라인          160개, application="edumeet" 태그 확인
서비스 포트         /actuator/prometheus → 404 (의도대로)
대시보드 PromQL     9개 전부 값 반환
```

`promtool check config` 로 Prometheus 설정 문법도 확인했다.

## 6. 다음 — 채팅이 있어야 붙는다

이 문서의 범위는 **지금 있는 코드**에 대한 기준선이다.

붕괴 5단계 중 ②(스레드 풀 포화)·③(느린 클라이언트)·⑤(다중 인스턴스)를 보려면
**WebSocket 채팅을 먼저 만들어야 한다.** 이 리포에는 아직 없다.

| 나중에 추가 | |
|---|---|
| `WebSocketMessageBrokerStats` → Micrometer | Spring 이 이미 계산하는데 30초마다 INFO 로그로만 나간다 |
| inbound/outbound **큐 길이** | 기본 큐가 무한이라 **포화가 에러가 아니라 지연으로만** 나타난다 |
| 세션 버퍼 / 강제 종료 수 | 느린 클라이언트 |

## 7. 미확인

- **Grafana 대시보드를 실제 화면으로 확인하지 않았다.** PromQL 이 값을 내는 것까지만 검증했다
- 알림(Alertmanager) 없음. 기준선을 잡기 전에는 임계값을 정할 수 없다
- Prometheus 보존 15일은 임의값. 디스크 사용량을 보고 조정한다
