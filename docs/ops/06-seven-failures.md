# 배포가 일곱 번 실패했다 — 매번 한 단계씩 더 갔다

> 2026-08-23 · CI/CD 실사 기록

## 0. 왜 이 문서가 있나

*"배포 자동화를 구축했습니다"* 는 한 줄이면 끝난다.
**실제로는 일곱 번 실패했고, 각 실패가 다른 이유였다.**

그리고 **각 실패는 이전보다 한 단계 더 간 결과**였다 —
파이프라인이 실제로 진전하고 있다는 뜻이고, 동시에
**앞 단계가 막혀 있는 동안에는 뒤 단계의 결함이 보이지 않았다**는 뜻이다.

> 이 문서의 값어치는 "무엇을 고쳤나" 가 아니라
> **"처음 보는 실패를 어떻게 좁혀 들어갔나"** 다.

---

## 1. 일곱 번

| # | 증상 | 실제 원인 | 어디서 막혔나 |
|---|---|---|---|
| 1 | `error: missing server host` | GitHub Secrets 미등록 | SSH 시도 전 |
| 2 | — | 서버에 `~/edumeet` 없음 | SSH 직후 |
| 3 | 헬스체크 타임아웃 | `curl localhost:8080/actuator/health` — **액추에이터는 9090 으로 옮겼다** | 컨테이너 기동 후 |
| 4 | `manifest unknown` | 빌드는 **7자** SHA 로 push, 배포는 **40자** SHA 로 pull | 이미지 pull |
| 5 | `JdbcSQLSyntaxErrorException ... engine=InnoDB` | **운영이 H2 로 떴다** | 앱 부팅 |
| 6 | `Access key ID cannot be blank` | `.env` 의 **빈 문자열이 yml 기본값을 이겼다** | 앱 부팅 |
| 7 | `Up (unhealthy)` — 앱은 정상 기동 | **메일 헬스 DOWN → 전체 health DOWN** | 헬스 판정 |

**1번을 고치자 2번이 보였고, 2번을 고치자 3번이 보였다.**
이건 실패가 아니라 **좁혀 들어가는 과정**이다.

---

## 2. 가장 무서웠던 것 — 5번

```
Caused by: org.h2.jdbc.JdbcSQLSyntaxErrorException:
  Syntax error in SQL statement "... engine[*]=InnoDB"
```

MySQL 문법인 마이그레이션을 **H2 에 실행하려다** 죽었다.

### 원인이 두 개 겹쳤다

**(a) 설정 실수** — 프로필을 통합하면서 `${DB_URL}` 을 `local` 문서에만 넣었다.
`prod` 문서에는 datasource 가 없어서 `.env` 값을 아무도 읽지 않았다.

**(b) 그 실수를 조용하게 만든 것**

```kotlin
runtimeOnly("com.h2database:h2")          // ← 운영 클래스패스에 H2 가 있었다
```

**H2 가 있어서 "datasource 를 못 찾겠다" 로 죽지 않고 임베디드 H2 로 떴다.**

> **(a)는 언제든 또 난다. 고쳐야 하는 건 (b)다.**
>
> 설정 실수는 사람이 하는 일이라 반복된다.
> **그 실수가 "부팅 실패" 가 아니라 "이상한 DB 로 뜸" 으로 나타나게 만드는 것**이 진짜 문제였다.

같은 판단을 세 곳에 적용했다.

| | 조용하게 만들던 것 | 처리 |
|---|---|---|
| H2 | 운영 jar 에 있음 | `testRuntimeOnly` 로만 |
| `perf` 패키지 8개 클래스 | 운영 jar 에 있음 (`@Profile` 로 비활성이지만) | **별도 소스셋** |
| `/error` 가 인증을 요구 | **404·500 이 전부 401 로 둔갑** | `permitAll` |

---

## 3. 미설정과 빈 문자열은 다르다 — 6번

```yaml
access-key: ${AWS_ACCESS_KEY:not-configured}    # 기본값을 넣었는데
```

```bash
AWS_ACCESS_KEY=                                  # .env 에 빈 값으로 있었다
```

**기본값은 "변수가 없을 때" 만 적용된다.**
변수가 **있고 값이 빈 문자열**이면 그게 이긴다.

Ansible 템플릿이 값이 비어도 줄을 쓰고 있었다. **비면 줄 자체를 쓰지 않도록** 바꿨다.

```jinja
{% raw %}{% if aws_access_key %}AWS_ACCESS_KEY={{ aws_access_key }}{% endif %}{% endraw %}
```

> 그 과정에서 **내가 만든 정규식이 파일을 망가뜨렸다** — `[A-Z_]+` 가 숫자를 포함하지 않아
> `AWS_S3_BUCKET` 이 `AWS_S` 로 잘렸다. 정규식으로 여러 줄 템플릿을 고치려 한 것이 잘못이었다.
> **되돌리고 템플릿을 다시 썼다.**

---

## 4. 컨테이너가 물어야 하는 질문 — 7번

앱은 **32초 만에 정상 기동했다.** 그런데 컨테이너가 unhealthy 였다.

```
Started EduMeetApplication in 32.812 seconds
edumeet-app   Up 2 minutes (unhealthy)
```

HEALTHCHECK 가 `/actuator/health` **전체**를 봤고, 메일 설정이 없어 메일 헬스가 DOWN 이었다.

| | 묻는 것 |
|---|---|
| `/actuator/health` | **모든 의존성이 살아 있나** |
| `/actuator/health/readiness` | **이 앱이 트래픽을 받을 수 있나** |

**컨테이너가 알아야 하는 건 두 번째다.**
메일이 안 되는 것과 앱이 못 뜨는 것은 다른 문제인데 구조가 둘을 같게 취급했다.

기준은 **"이게 없으면 서비스가 의미 없는가"** 로 잡았다.

```
DB    없으면 대부분의 요청이 실패한다        → readiness 에 포함
Redis 인증 코드 저장소. 로그인이 막힌다      → 포함
메일  인증 코드 발송만 막힌다                → 제외
S3    첨부·썸네일만 막힌다                  → 제외
```

> **Spring Boot 에 이미 답이 있었다.** `probes.enabled: true` 는 **켜져 있었고 쓰기만 하면 됐다.**

---

## 5. 추측을 세 번 틀리고 로그를 켰다

배포와 별개로, `/actuator/prometheus` 가 404 인 문제를 쫓을 때다.

| 추측 | 근거 | 결과 |
|---|---|---|
| 1 | `ManagementWebSecurityAutoConfiguration` 이 막는다 | 자동 구성을 제외 → **변화 없음** |
| 2 | `EndpointRequest.toAnyEndpoint()` 가 자식 컨텍스트라 매칭 실패 | 경로 매칭으로 변경 → **변화 없음** |
| 3 | `requestMatchers(String)` 이 MVC 핸들러 매핑에 의존한다 | URI 매처로 변경 → **변화 없음** |

세 추측 모두 **그럴듯했고**, 매번 코드를 고쳐 확인했고, **전부 헛수고였다.**

네 번째로 로그를 켰다.

```
Secured GET /actuator/prometheus     ← 보안은 통과했다
Securing GET /error                  ← 404 라서 /error 로 포워드
→ 401 Unauthorized                   ← /error 가 인증을 요구한다
```

**보안 문제가 아니었다.** 엔드포인트가 404 였고, 그게 `/error` 를 거치며 401 로 둔갑했다.

진짜 원인은 조건 평가 리포트(`debug=true`)에 있었다.

```
@ConditionalOnEnabledMetricsExport management.defaults.metrics.export.enabled is considered false
```

찾기 어려웠던 결정적 이유 — **`/actuator/metrics` 는 200 이었다.**
*"액추에이터는 되는데 prometheus 만 안 된다"* 로 보이니 계속 보안을 뒤졌다.

> **두 번째 실패에서 멈췄어야 했다.**
> 추측이 두 번 빗나갔다는 건 **모델이 틀렸다는 신호**지 세 번째 추측이 맞을 확률이 올랐다는 뜻이 아니다.

---

## 6. 이 기록에서 남는 것

### 반복해서 나온 한 문장

> **설정 실수는 언제든 또 난다. 조용해지는 쪽을 막는 게 더 중요하다.**

5번(H2), `perf` 소스셋, `/error` 401 둔갑 — **전부 같은 문제의 다른 얼굴**이었다.
증상이 다르고 파일이 다르고 원인이 달랐지만, **고쳐야 할 것은 같았다.**

### 반대 방향의 실수도 있었다

같은 날, **Redis 를 쓰려고 문제를 만들어낸 적이 있다.**

Redis 가 스택에 있는데 아무것도 안 하는 상태가 이상해 보였고,
refresh token 을 Redis 로 옮길 근거를 찾았다 —
*"만료된 행을 지우는 배치가 없으니 테이블이 무한히 자란다."*

**확인해보니 틀렸다.** `member_id` 에 UNIQUE 제약이 있어 **회원당 한 행**이고,
로그인 때 추가가 아니라 갱신이다. 행 수는 **로그인 횟수가 아니라 회원 수**에 비례한다.

> **문제를 찾아 기술을 고른 게 아니라, 기술을 놓고 문제를 찾았다.** 순서가 뒤집혀 있었다.

**결론은 옮기는 쪽이 됐다(#70). 다만 근거를 바꿔서다** —
*"MySQL 에 결함이 있다"* 가 아니라 *"refresh token 은 도메인 데이터가 아니라 세션 상태라
관계형 저장소에 있을 이유가 없다"* 로. **같은 결론이어도 근거가 틀리면 다음 판단이 틀어진다.**
>
> 이건 위의 일곱 번과 반대 방향의 실수다.
> 저쪽은 **증상에서 원인으로 좁혀 들어간 것**이고, 이쪽은 **결론에서 근거로 거슬러 올라간 것**이다.
> 후자는 거의 항상 틀린다.

### 그리고 전부 테스트로 고정했다

| 무엇 | 테스트 |
|---|---|
| 운영이 H2 로 뜨지 않는다 | `ProfileDataSourceTest` |
| readiness 가 메일을 안 본다 | `ReadinessProbeTest` |
| 관리 포트에서만 지표가 나온다 | `PrometheusEndpointTest` |
| AWS 키 없이도 앱이 뜬다 | `S3OptionalCredentialsTest` |

**고쳤다는 주장이 아니라 회귀하면 깨지는 장치**로 남겼다.
그리고 **버그를 되돌려 테스트가 실제로 잡는지 확인**했다 —
`ProfileDataSourceTest` 는 datasource 를 다시 빼자 3개 전부 실패했다.

### 마지막에 성공했을 때

```
Deploy: completed/success
edumeet-app     Up 45 seconds (healthy)
readiness       UP
API             /api/v1/classroom → 401 (인증이 실제로 걸린다)
Flyway          V1·V2·V3 전부 적용, 테이블 20개
지표            760줄
```

---

## 7. 한계

- **이 기록은 한 사람이 하루에 겪은 것**이다. 팀 환경에서는 다른 실패가 나온다
- 7번 중 **3·4·5번은 내가 앞선 작업에서 만든 것**이다.
  관리 포트 분리(3), 태그 형식(4), 프로필 통합(5) 전부 내 변경이 원인이었다.
  **자동화가 없었다면 이 셋은 배포 시점에야 드러났을 것이다**
- OCI VCN 보안목록은 확인하지 못했다. 8080 을 외부에 열려면 firewalld 와 양쪽을 봐야 한다
