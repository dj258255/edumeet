# CI/CD 와 배포 구조

> 이슈 [#25](https://github.com/dj258255/edumeet/issues/25)

## 왜 만들었나

이 저장소에는 **CI 도 CD 도 없었다.** PR 을 올려도 빌드·테스트가 자동으로 돌지 않고
배포는 수동이었다. 그리고 기존 `Dockerfile` / `docker-compose.yml` 에 문제가 있었다.

---

## 1. 배포 대상과 그것이 만든 제약

```
OCI  VM.Standard.A1.Flex
     aarch64 (ARM64) · 2 OCPU · 12 GB RAM
```

**아키텍처가 ARM64 다.** GitHub Actions 기본 러너는 x86_64 이므로 그냥 빌드한 이미지는 안 돈다.

| 선택지 | 판단 |
|---|---|
| QEMU 크로스 빌드 (`docker/setup-qemu-action`) | 에뮬레이션이라 **수 배~10배 느리다** |
| **ARM 네이티브 러너 (`ubuntu-24.04-arm`)** | **채택.** public 저장소는 무료, 네이티브 속도 |

```yaml
runs-on: ubuntu-24.04-arm
platforms: linux/arm64
```

> **CI 도 ARM 러너에서 돌린다.** 배포 대상과 같은 아키텍처에서 테스트하는 편이
> "여기선 되는데 저기선 안 되는" 문제를 줄인다.

---

## 2. Dockerfile 에서 고친 것

| 이전 | 문제 | 이후 |
|---|---|---|
| `FROM openjdk:17-jdk-slim` | **Docker Hub 의 `openjdk` 는 2022년 deprecated** | `eclipse-temurin:17-jre` |
| 런타임에 JDK | 컴파일러가 필요 없다. 이미지만 커진다 | **JRE** |
| `curl` 설치하고 안 씀 | — | **HEALTHCHECK 에 사용** |
| `spring.profiles.active=prod` 하드코딩 | 환경변수로 못 바꾼다 | `ENV SPRING_PROFILES_ACTIVE` |
| JVM 옵션 없음 | **컨테이너 메모리 제한을 인식 못 해 OOMKilled** | 아래 |

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
```

- `MaxRAMPercentage` — 이 옵션이 없으면 JVM 이 **호스트 전체 메모리**를 보고 힙을 잡는다.
  컨테이너 제한을 넘어서면 커널이 `OOMKilled` 로 죽인다. JVM 은 자기가 왜 죽었는지도 모른다
- `ExitOnOutOfMemoryError` — OOM 이면 죽는다. **좀비 상태로 버티는 것보다 재시작이 낫다**

---

## 3. docker-compose 에서 고친 것

### 🔴 DB·Redis 가 인터넷에 열려 있었다

```yaml
# 이전
mysql:
  ports:
    - "3306:3306"      # ← 공인 IP 서버에서 이러면 인터넷 전체에 DB 가 노출된다
redis:
  ports:
    - "6379:6379"
```

```yaml
# 이후
mysql:
  expose:
    - "3306"           # compose 네트워크 안에서만 보인다
```

같은 네트워크 안에서는 **서비스명(`mysql`, `redis`)으로 접근**되므로 호스트 포트 공개가 불필요하다.

### 🟡 MySQL 준비 전에 앱이 떴다

```yaml
depends_on:
  mysql:
    condition: service_healthy    # ← 이게 없으면 프로세스가 뜬 순간 다음으로 넘어간다
```

`depends_on` 만 쓰면 **컨테이너가 시작된 것**만 보장한다. MySQL 이 커넥션을 받을 준비가
됐는지는 모른다. `healthcheck` + `condition: service_healthy` 가 있어야 한다.

---

## 4. Actuator

컨테이너 `HEALTHCHECK` 가 `/actuator/health` 를 부르므로 추가했다.
**노출 엔드포인트는 제한한다.**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus   # env, beans, heapdump 는 열지 않는다
  endpoint:
    health:
      show-details: when-authorized                  # 내부 정보는 인증된 사용자에게만
      probes:
        enabled: true
```

`/actuator/health` 는 `SecurityConfig` 에서 `permitAll` 이다.
컨테이너와 로드밸런서가 인증 없이 불러야 하기 때문이고,
`show-details: when-authorized` 로 막아 **내부 정보는 나가지 않는다.**

---

## 5. 워크플로

### `ci.yml` — PR 마다

```
checkout → JDK 17 → Gradle 캐시 → build (테스트 제외) → test → 리포트
```

- **테스트 리포트를 PR 에 붙인다** (`mikepenz/action-junit-report`)
- 실패 시 HTML 리포트를 아티팩트로 올린다
- `concurrency` 로 같은 브랜치의 이전 실행을 취소해 러너 시간을 아낀다

### `deploy.yml` — master push

```
build-image (ARM 러너)          deploy (SSH)
  ├ GHCR 로그인                   ├ 이미지 pull
  ├ 메타데이터(latest + sha)       ├ compose up -d
  ├ arm64 빌드 & push             └ 헬스체크 통과까지 대기 (최대 150초)
  └ GHA 레이어 캐시                    실패하면 로그 100줄 출력 후 실패 처리
```

- **`concurrency: cancel-in-progress: false`** — 배포는 취소하지 않는다.
  중간에 끊기면 상태가 애매해진다
- **`script_stop: true`** — SSH 스크립트가 한 줄이라도 실패하면 즉시 중단
- **배포 후 헬스체크를 기다린다.** 통과 못 하면 워크플로가 실패하고 로그를 남긴다.
  이게 없으면 "배포는 성공했는데 앱은 죽어 있는" 상태를 놓친다
- 이미지 태그에 **커밋 SHA** 를 쓴다. `latest` 만 쓰면 롤백할 대상을 지정할 수 없다

---

## 6. 필요한 Secrets

저장소 Settings → Secrets and variables → Actions

| 이름 | 용도 |
|---|---|
| `OCI_HOST` | 배포 대상 서버 주소 |
| `OCI_USER` | SSH 계정 |
| `OCI_SSH_KEY` | SSH 개인키 (전체 내용) |

`GITHUB_TOKEN` 은 자동으로 주어지므로 따로 등록하지 않는다.
GHCR push 권한은 워크플로의 `permissions: packages: write` 로 얻는다.

### 서버 쪽 사전 준비

```bash
mkdir -p ~/edumeet && cd ~/edumeet
# docker-compose.prod.yml 을 올려둔다
# .env 를 만든다 (저장소에 커밋하지 않는다)
```

`.env` 에 들어갈 것: `MYSQL_*`, `SPRING_DATASOURCE_*`, `JWT_SECRET`,
`AWS_*`, `OPENVIDU_*` 등. `application-local.yml.example` 참고.

---

## 7. 롤백

```bash
export APP_IMAGE=ghcr.io/dj258255/edumeet:<이전_커밋_SHA>
docker compose -f docker-compose.prod.yml up -d
```

이미지에 커밋 SHA 태그를 붙여둔 이유가 이것이다.

---

## 8. 하지 않은 것

| | 이유 |
|---|---|
| Kubernetes | 단일 노드 2 OCPU 에 K8s 를 올리면 **컨트롤 플레인이 자원의 상당 부분을 먹는다** |
| 블루-그린 / 카나리 | 노드가 하나라 **두 벌을 띄울 메모리가 없다** |
| Terraform | 인스턴스가 하나이고 이미 만들어져 있다. IaC 의 값이 나오려면 재생성이 잦아야 한다 |
| 자체 러너 | GitHub 호스티드 ARM 러너가 public 저장소에 무료다 |

**필요해지면 그때 넣는다. 지금은 근거가 없다.**


---

## 배포 준비 — 한 번만 하는 것 (#34)

배포 워크플로가 매번 실패하고 있었다. 원인이 하나가 아니었고, 그중 절반은 **서버 쪽 준비**였다.

### 1. GitHub Secrets

| 이름 | 값 |
|---|---|
| `OCI_HOST` | 서버 공인 IP |
| `OCI_USER` | `rocky` (Rocky Linux 기본 계정) |
| `OCI_SSH_KEY` | **배포 전용** SSH 개인 키 |

개인 키를 그대로 올리지 않는다. **전용 키를 따로 만들어** 서버 `authorized_keys` 에 넣고
그 키만 등록한다. 유출되거나 더 이상 필요 없어지면 그 키만 지우면 된다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/edumeet_deploy -N "" -C "github-actions@edumeet"
ssh-copy-id -i ~/.ssh/edumeet_deploy.pub rocky@<서버>

gh secret set OCI_HOST  --body "<서버 IP>"
gh secret set OCI_USER  --body "rocky"
gh secret set OCI_SSH_KEY < ~/.ssh/edumeet_deploy
```

### 2. 서버 디렉터리와 `.env`

> **Ansible 로 자동화했다 (#37).** 아래 수동 절차는 Ansible 을 못 쓰는 상황을 위해 남긴다.
> `ansible/README.md` 를 먼저 본다.
>
> ```bash
> cd ansible && ansible-playbook playbook.yml --check --diff   # 미리 보기
> cd ansible && ansible-playbook playbook.yml                  # 적용
> ```


배포 스크립트는 `cd ~/edumeet` 로 시작한다. **이 디렉터리가 없으면 거기서 끝난다.**

```bash
ssh rocky@<서버> 'mkdir -p ~/edumeet'
scp env.prod.example rocky@<서버>:~/edumeet/.env
ssh rocky@<서버> 'chmod 600 ~/edumeet/.env'   # 그 뒤 값을 채운다
```

**`.env` 는 배포가 건드리지 않는다.** 워크플로는 `docker-compose.prod.yml` 과
`observability/` 만 덮어쓴다. 시크릿은 서버에만 존재한다.

### 3. 방화벽

Rocky Linux 는 firewalld 가 켜져 있고 기본 허용은 `ssh / http / https / cockpit` 이다.
**8080 은 막혀 있다.**

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp && sudo firewall-cmd --reload
```

OCI 는 VCN **보안 목록/NSG** 에서도 따로 막는다. 둘 다 열어야 외부에서 닿는다.

> 다만 8080 을 그대로 여는 것보다 **80/443 에 리버스 프록시**를 두는 편이 낫다.
> http·https 는 이미 firewalld 에 열려 있고, TLS 종료 지점도 필요하다.

## 헬스체크는 컨테이너에게 묻는다

```bash
docker inspect --format='{{.State.Health.Status}}' edumeet-app
```

호스트에서 `curl localhost:8080/actuator/health` 를 하면 **절대 통과하지 못한다.**
#28 에서 액추에이터를 관리 포트(9090)로 옮겼고 **그 포트는 publish 하지 않기** 때문이다.

Dockerfile 의 `HEALTHCHECK` 가 컨테이너 안에서 이미 그 포트를 부르고 있으므로
**컨테이너가 보고하는 상태를 그대로 읽으면 된다.** 판정 로직이 두 곳에 흩어지지 않는다.

## 이 서버는 이미 다른 것이 쓰고 있다

실사해 보니 관측 스택이 통째로 돌고 있었다.

```
balruno-monitoring         prometheus(9090) · grafana(3000) · loki · alertmanager
balruno-monitoring-agent   alloy · cadvisor · node-exporter
a02-mdl-storm              incident-lab 세션 2개
```

**2 vCPU 서버에 Prometheus 를 두 벌 돌릴 이유가 없다.** 그리고 기존 스택은
다른 프로젝트 소유라 그 설정 파일을 함부로 고칠 수 없다.

그래서 edumeet 의 관측 스택을 **compose profile 로 분리**했다.

```bash
docker compose -f docker-compose.prod.yml up -d                          # 앱만
docker compose -f docker-compose.prod.yml --profile observability up -d  # 독립 스택까지
```

기존 Prometheus 가 긁게 하려면 앱 관리 포트를 사설 IP 에 노출한다.

```bash
# ~/edumeet/.env
MANAGEMENT_BIND=10.0.0.11
```

그리고 기존 `prometheus.yml` 에 잡을 추가한다(**다른 프로젝트 파일이므로 소유자 확인 후**).

```yaml
  - job_name: edumeet-app
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["10.0.0.11:9091"]
        labels:
          service: edumeet
```
