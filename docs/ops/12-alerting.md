# 경보 — 관측을 세웠는데 아무도 안 봤다

> **#139.** 규칙 7개 · Alertmanager · 도달 확인 스크립트.

---

## 아홉 번째였다

`ops/07-declared-but-unused.md` 에 **선언은 있는데 아무도 안 쓴다**를 여덟 번 적었다.
#83 에서 `prometheus.export.enabled` 가 꺼져 있어 관측이 시작조차 안 되던 것을 고쳤다.

**그건 "동작하게" 만든 것이었다. "누가 보는가" 는 비어 있었다.**

```
observability/prometheus.yml    rule_files    없음
Alertmanager                    컨테이너      없음
alert rule                      0건
```

Grafana 대시보드는 사람이 열어야 보인다. **새벽 3시에 큐가 차면 아무도 모른다.**

## 그리고 붙이려는데, 잴 것이 없었다

`WebSocketConfig` 의 JavaDoc 이 이렇게 적고 있었다.

> 거부 정책은 `CallerRunsPolicy` 다. (…)
> 그리고 **느려지는 것은 지표에 드러난다(큐 길이가 상한에 붙는다).**

**그 지표가 없었다.**

```java
// configureClientOutboundChannel 안에서 직접 만든다 — @Bean 이 아니다
registration.taskExecutor(boundedExecutor("chat-out-", OUTBOUND_QUEUE_CAPACITY));
```

Spring Boot 의 executor 계측 자동 설정은 **빈**만 계측한다.
이 실행기는 빈이 아니므로 아무도 계측하지 않았다.

**앞의 여덟은 "기능이 죽어 있었다" 였다. 이번 것은 주석이 존재를 주장한 지표였다.**
그래서 `chat.channel.queued` · `.capacity` · `.active` 를 직접 등록했다.

---

## 임계값마다 근거를 적었다

대부분의 경보는 `CPU > 80%` 처럼 근거 없는 숫자를 쓴다.
그러면 **울려도 무엇을 해야 할지 모르고, 안 울려도 안전한지 알 수 없다.**

이 저장소는 붕괴 지점을 이미 쟀다.

| 경보 | 임계값 | 근거 |
|---|---|---|
| `ChatOutboundQueueGrowing` | 상한의 **50%** | 정상(빠른 소비자) 최대가 525개 = 상한의 **2.6%**. 50% 는 그 20배 위라 오탐이 아니고, 상한까지 여유가 남아 사람이 볼 시간이 있다 → [`performance/07`](../performance/07-chat-unbounded-queue-oom.md) |
| `ChatOutboundQueueAtCapacity` | 상한의 **90%** | 이 시점부터 `CallerRunsPolicy` 로 발행 스레드가 전송을 떠안는다. 안 버려지지만 전체가 느려진다 |
| `ChatFanoutAmplificationHigh` | p95 **200명** | 200명에서 e2e p95 45ms, 500명에서 **1,313ms(29배)**. 증폭기가 커지는 것이 지연보다 먼저 보인다 → [`performance/09`](../performance/09-chat-capacity-oci.md) |
| `CaptionArchiveDropping` | **> 0** | 드롭은 상한을 넘겨 버린 것이다. 화면에는 이미 지나갔으므로 **복구할 원본이 없다** → [`performance/13`](../performance/13-caption-archive-transcript.md) |
| `WebSocketSessionsDroppingSteadily` | 3분에 **-5**, 발행 거의 없음 | `proxy_read_timeout` 60초 기본값에서 **60.9초에 3/3 끊김** → [`performance/10`](../performance/10-websocket-behind-proxy.md) |

### 절대값이 아니라 비율로 묻는다

```yaml
expr: chat_channel_queued{channel="out"} / chat_channel_capacity{channel="out"} > 0.5
```

상한을 코드에서 바꿨는데 경보가 옛 절대값(`> 10000`)을 물어보면
**경보는 조용히 무의미해진다.** 그래서 앱이 `capacity` 도 지표로 낸다.

### 에러를 세는 경보로는 못 잡는 것

`WebSocketSessionsDroppingSteadily` 는 모양이 다르다.

```
에러 로그가 안 남는다.  개발 중엔 안 보인다.  트래픽이 있으면 가려진다.
```

그래서 **"세션 수가 규칙적으로 깎이는데 그동안 발행이 거의 없다"** 를 본다.
조용한 연결만 끊기는 것이 이 버그의 지문이다.

---

## ★ 조용히 끊길 수 있는 곳이 네 군데다

경보는 "선언은 있는데 안 쓴다" 함정에 특히 잘 빠진다.
**규칙 파일을 커밋하면 일이 끝난 것처럼 보이기 때문이다.**

```
① Prometheus 가 규칙 파일을 아예 안 읽었다        경로 오타 · 마운트 누락
② 규칙은 읽었는데 물어보는 지표가 없다             → 빈 결과. 빈 결과는 "정상" 과 같다
③ firing 이 됐는데 Alertmanager 로 안 간다        alerting: 설정 누락
④ Alertmanager 가 받았는데 수신자가 없다           → 조용히 버린다
```

**②가 제일 위험하다.** 없는 지표를 물어보면 Prometheus 는 **에러가 아니라 빈 결과**를 낸다.
빈 결과는 "조건을 만족하지 않음" 과 구분되지 않으므로, 그 경보는 **영원히 안 울린다.**

### 그래서 두 겹으로 막았다

**CI 에서** — `AlertMetricsExposedTest` 가 규칙 파일을 읽어
거기 적힌 지표 이름이 `/actuator/prometheus` 에 실제로 있는지 대조한다.

```java
for (String metric : metricNamesIn(Files.readString(RULES))) {
    assertThat(body)
        .as("경보 규칙이 %s 를 물어보는데 앱이 그 지표를 내지 않는다", metric)
        .contains(metric);
}
```

> 처음엔 YAML 을 정규식으로 긁었다가 `expr` 블록이 어디서 끝나는지 못 잡아
> 뒤따르는 `labels:` 키를 지표로 오인했다. **들여쓰기를 아는 것은 파서다.**

**서버에서** — `scripts/verify-alerting.sh` 가 ①~④를 순서대로 확인한다.

```
./scripts/verify-alerting.sh          구조만 (빠름)
./scripts/verify-alerting.sh --fire   실제 경보를 하나 쏴서 끝까지 도달하는지
```

`--fire` 는 Alertmanager 에 직접 경보를 넣는다. 규칙을 기다리지 않는 이유는
확인하려는 것이 *"규칙이 맞나"* 가 아니라 **"받은 뒤 나가는가"** 이기 때문이다.
2분 뒤 자동 해제된다.

---

## 알림을 몇 개나 보낼 것인가

500명 방이 무너지면 같은 경보가 연달아 뜬다. **알림이 100개 오면 아무도 안 본다.**

| | |
|---|---|
| `group_by: [alertname, severity]` | 같은 종류를 묶는다 |
| `repeat_interval: 4h` | 짧으면 알림 피로로 무시하게 되고, 길면 잊는다. critical 은 1h |
| **억제 규칙** | 상한에 붙었으면(critical) 50% 경고는 같이 안 울린다 |
| **억제 규칙** | `AppMetricsUnreachable` 중에는 나머지를 전부 누른다 — 그때의 *"큐가 안 늘어난다"* 는 정상이 아니라 **관측이 죽은 것**이다 |

---

## 웹훅은 저장소에 없다

Discord/Slack 웹훅 URL 은 **그 자체가 시크릿**이다. 아는 사람은 누구나 그 방에 쓴다.
그래서 커밋되는 `alertmanager.yml` 의 기본 수신자는 **비어 있고**,
배포 시점에 Ansible 이 vault 값으로 렌더한다 — [`ops/05-secrets.md`](05-secrets.md) 와 같은 경로다.

**비어 있으면 조용히 버려지므로**, `verify-alerting.sh` 가 ④에서 그것을 실패로 잡는다.

Alertmanager UI 는 `127.0.0.1` 에만 묶는다. 인증이 없어서
열려 있으면 **누구나 경보를 침묵시킬 수 있다** — Grafana 를 묶은 이유(#89)와 같다.

---

## ★ 정정 — "사람이 볼 시간이 남는다" 는 틀렸다 (#143)

50% 를 고를 때 *"상한까지 여유가 있어 사람이 볼 시간이 남는다"* 고 적었다. **재 보니 아니었다.**

무한 큐 실험의 순증이 초당 **12,832건**(1,077,906 / 84초)이다. 상한 2만 기준으로

```
50% → 100%   0.78초
0   → 100%   1.56초
```

**`scrape_interval` 15초, `for` 1분이다.** 그 부하에서는 경보가 평가되기도 전에 큐가 찬다.

경보가 15초의 여유를 주려면 순증이 667건/s 이하여야 하는데, 그 부하의 총 전달 요구가
초당 36,000건이므로 **소비가 98.15% 이상 따라잡고 있을 때만** 그렇다.

> **급성 붕괴를 막는 것은 경보가 아니라 상한과 `CallerRunsPolicy` 다.**
> 이 경보는 완만한 열화를 잡고, 급성 구간에서는 사후 기록으로 쓴다.

임계값을 더 내리는 것은 답이 아니다 — 90% → 100% 도 0.16초이고, 낮출수록
완만한 구간에서 오탐만 늘어난다. **경보로 못 막는 구간이 있다는 것을 아는 편이 낫다고 봤다.**

→ [`14-three-questions-measured.md`](14-three-questions-measured.md)

---

## 안 한 것

| | 근거 |
|---|---|
| SLO·에러버짓 | 사용자가 아직 없다. 가용성 목표를 정할 근거가 없는데 숫자만 적으면 그것도 "선언" 이다 |
| 자동 복구(auto-restart on alert) | 경보가 울리는 조건 대부분이 **역압**이라 재시작으로 안 낫는다. 오히려 큐에 남은 것을 버린다 |
| PagerDuty·Opsgenie | 당번이 한 명이고 그 한 명이 웹훅을 본다 |
| Grafana 경보 | Prometheus 규칙과 두 벌이 된다. 규칙은 한 곳에만 둔다 |
