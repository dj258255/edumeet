# 시크릿 관리 — 무엇이 어디에 있나

> 작성 2026-08-23 · #41

## 원칙

**평문 시크릿은 저장소에 넣지 않는다.** 그리고 그 규칙이 지켜지는지 **검증한다.**
규칙만 쓰고 넘어가면 다음 사람이 우회하는 것을 막지 못한다.

## 무엇이 어디에

| 값 | 위치 | 형태 |
|---|---|---|
| 앱 시크릿(DB·JWT·AWS·Kakao·LiveKit) | `ansible/group_vars/prod/vault.yml` | **AES256 암호화** |
| vault 비밀번호 | `ansible/.vault-pass` | **로컬만.** gitignore |
| 배포용 SSH 키 | GitHub Secrets `OCI_SSH_KEY` | **전용 키.** 개인 키가 아니다 |
| 서버 주소 | vault (`vault_ansible_host`) | 암호화 |
| 운영 `.env` | 서버 `~/edumeet/.env` (0600) | Ansible 이 생성 |

### vault 비밀번호를 CI 에 넣지 않는다

넣으면 **"암호화했는데 복호화 키를 같은 곳에 둔"** 모양이 된다.
그래서 **Ansible 은 로컬 전용**이고 CI 는 배포(이미지 pull/up)만 한다.

### 배포 키를 따로 만드는 이유

개인 키를 GitHub 에 올리면 **유출 시 그 키로 접근하던 모든 곳이 위험**해진다.
전용 키는 `authorized_keys` 에서 그 줄만 지우면 끝난다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/edumeet_deploy -N "" -C "github-actions@edumeet"
```

## 감사 (2026-08-23)

| 확인 | 결과 |
|---|---|
| 추적 파일의 AWS 키·개인키·GitHub 토큰 패턴 | **없음** |
| 하드코딩된 비밀번호 | perf 프로필 더미값 2건뿐 (`perf-only-secret-not-used-in-production`) |
| 히스토리에 커밋된 위험 파일 | `CLAUDE.md` — 내용은 **IP 주소뿐, 자격증명 없음** |
| `vault.yml` | `$ANSIBLE_VAULT;1.1;AES256` — 암호화 상태로만 커밋됨 |

### gitignore 를 검증한다

규칙을 쓰는 것과 동작하는 것은 다르다. `git check-ignore` 로 확인했다.

```
무시됨    .env  prod.env  .env.local  *.pem  *.key  *.jks
          id_rsa  id_ed25519  .envrc  **/secrets/
          ansible/.vault-pass  application-local.yml

추적됨    env.prod.example  application-local.yml.example
          ansible/group_vars/prod/vault.yml   ← 암호화 상태
```

예시 파일은 `*.env.*` 규칙에 걸리므로 **명시적으로 negation** 을 걸어야 한다.

## 히스토리를 다시 쓰지 않은 이유

히스토리에 남은 것은 **공개 IP 주소**이고 자격증명이 아니다. 그리고 서버가 이미 잠겨 있다.

```
passwordauthentication  no
permitrootlogin         no
fail2ban                active
7일간 비밀번호 실패 시도  0
```

**키 전용 인증에서는 IP·계정명을 알아도 실질적인 도움이 안 된다.**
반면 공개 저장소의 히스토리를 다시 쓰면 **모든 커밋 SHA 가 바뀌어** 클론·참조가 전부 깨진다.

> 얻는 것(공개 IP 은닉)보다 잃는 것(히스토리 일관성)이 크다고 판단했다.
> **다만 현재 트리에서는 뺐다.** 비용이 없기 때문이다.

만약 **자격증명이 히스토리에 있었다면 판단이 반대다** — 그때는 재작성이 아니라
**즉시 회전(rotate)** 이 먼저다. 재작성해도 이미 클론한 사람의 사본은 남는다.

## 값을 바꾸려면

```bash
cd ansible
ansible-vault edit group_vars/prod/vault.yml
ansible-playbook playbook.yml --check --diff    # 무엇이 바뀌는지 먼저 본다
ansible-playbook playbook.yml
```

**서버에서 `.env` 를 직접 고치지 않는다.** 다음 실행 때 되돌아간다.

## 아직 비어 있는 값

외부 서비스 자격증명이라 사람이 채워야 한다.

```
mail_username  mail_password
aws_access_key  aws_secret_key  aws_s3_bucket
kakao_client_id  kakao_client_secret
livekit_url  livekit_api_key  livekit_api_secret
front_url  front_url2
```

DB·JWT·내부 API 토큰·Grafana 비밀번호는 **난수로 생성해 채웠다.**
사람이 고른 값보다 낫고, 어차피 사람이 기억할 필요가 없다.
