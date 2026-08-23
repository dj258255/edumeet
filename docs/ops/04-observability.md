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

## 6. 채팅 계측 (#39)

채팅이 생겼으므로(#33) 붙일 대상이 생겼다.

### 직접 바인딩하려다 프레임워크와 충돌했다

처음에는 STOMP 실행기를 `ExecutorServiceMetrics` 로 **직접 바인딩하는 설정을 썼다.**
그리고 부하 시험에서 이 경고가 나왔다.

```
The meter (executor.completed, tags=[application, name]) registration has failed:
Prometheus requires that all meters with the same name have the same set of tag keys.
There is already an existing meter named 'executor_completed_tasks'
containing tag keys [application, component, name].
```

**Spring Boot 의 `TaskExecutorMetricsAutoConfiguration` 이 이미 모든
`ThreadPoolTaskExecutor` 빈을 바인딩하고 있었다.** 내 설정이 먼저 등록되면서
`component=stomp` 태그를 붙였고, 뒤이은 Boot 의 등록이 **키 집합 불일치로 실패**했다.

설정을 통째로 지우고 테스트를 다시 돌렸더니 **지표가 그대로 나왔다.**
내가 쓴 코드는 처음부터 필요 없었다.

> **프레임워크가 이미 하는 일을 다시 하면, 안 되는 게 아니라 충돌한다.**
> 그리고 그 충돌은 경고 한 줄로만 드러나서 부하 시험 로그를 읽기 전까지 몰랐다.

### `WebSocketMessageBrokerStats` 를 쓰지 않았다

Spring 이 같은 값을 이미 계산한다. 다만 **전부 `String` 으로만 노출한다.**

```java
String getClientInboundExecutorStatsInfo()   // "pool size = 8, active threads = 0, ..."
```

문자열을 파싱하면 Spring 이 형식을 바꾸는 순간 **조용히 깨진다.**
그래서 **실행기 객체를 Micrometer 에 직접 바인딩**하고, 세션·방·발행량은 이벤트로 직접 센다.

### 무엇을 재는가

| 지표 | 무엇을 보는가 | 붕괴 단계 |
|---|---|---|
| **`executor_queued_tasks`** | **대기열 길이 — 포화의 첫 신호** | ②③ |
| `executor_active_threads` / `pool_size` | active 가 pool 에 붙으면 그 뒤는 전부 큐로 간다 | ②③ |
| `chat_sessions_active` | 커넥션 한계 | ④ |
| `chat_rooms_active` | 활성 방 수 | ① |
| `chat_messages_published_total` | 발행량 | ① |
| **`chat_fanout_recipients`** | **실제 전달 대상 수** | ① |

> **fan-out 배수가 브로드캐스트 비용의 본체다.**
> 발행량만 세면 30명 방과 3,000명 방이 같아 보인다. **실제 쓰기량 = 발행량 × 이 값**이다.

### 정리하지 않으면 지표가 부풀려진다

`SessionUnsubscribeEvent` 는 **목적지를 담지 않는다.** 구독 id 만 온다.
그리고 브라우저를 닫으면 **UNSUBSCRIBE 없이 세션이 사라진다** — 이게 정상 경로다.

그래서 `(세션, 구독id) → 목적지` 를 들고 있다가 끊길 때 정리한다.
안 하면 **방이 비었는데 구독자 수가 줄지 않아 fan-out 배수가 계속 부풀려진다.**
테스트로 고정했다 (`fanout_records_recipient_count` 마지막 단언).

### `_bucket` 이 없으면 패널이 빈 화면이다

`chat.fanout.recipients` 에 `publishPercentileHistogram()` 을 붙였다.
빠지면 `chat_fanout_recipients_bucket` 이 안 나오고 대시보드의
`histogram_quantile` 패널이 통째로 **"No data"** 가 된다. §4 의 5xx 패널과 같은 함정이다.

이것도 테스트로 고정했다.

## 7. 남은 것

| | |
|---|---|
| 세션 버퍼 / 강제 종료 수 | 느린 클라이언트 (붕괴 ③) — Phase 3 |
| Redis Pub/Sub 전파 지연 | 다중 인스턴스 (붕괴 ⑤) — Phase 5 |

## 8. 미확인

- **Grafana 대시보드를 실제 화면으로 확인하지 않았다.** PromQL 이 값을 내는 것까지만 검증했다
- 알림(Alertmanager) 없음. 기준선을 잡기 전에는 임계값을 정할 수 없다
- Prometheus 보존 15일은 임의값. 디스크 사용량을 보고 조정한다
