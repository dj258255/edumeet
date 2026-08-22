# Ansible — 서버 프로비저닝과 시크릿

> #37 · 로컬에서만 돌린다. **CI 에 vault 비밀번호를 넣지 않는다.**

## 왜

#34 에서 배포가 실패한 원인 중 하나는 **서버에 `~/edumeet` 디렉터리가 없었던 것**이다.
고치면서 손으로 한 일들이 문서에만 남았다 — **서버가 날아가면 다시 손으로 해야 한다.**

그리고 `.env` 는 서버에만 존재해서 **값이 무엇이었는지, 언제 바뀌었는지 기록이 없다.**

## 역할 분담

```
Ansible          프로비저닝 + 시크릿 배치   ← 사람이 가끔, 로컬에서
GitHub Actions   이미지 pull / compose up   ← push 마다, 자동
```

**vault 비밀번호를 GitHub Secret 에 넣지 않는다.**
넣으면 *"암호화했는데 복호화 키를 같은 곳에 둔"* 모양이 된다.

## 처음 한 번

```bash
cd ansible

# 1. vault 비밀번호 파일. 저장소에 안 들어간다(.gitignore).
echo '<충분히 긴 문자열>' > .vault-pass && chmod 600 .vault-pass

# 2. 시크릿 파일 생성
ansible-vault create group_vars/prod/vault.yml
```

`vault.yml` 에 넣을 키는 `group_vars/prod/vars.yml` 아래쪽 참조 목록과 같다.
전부 `vault_` 로 시작한다.

```yaml
vault_mysql_root_password: "..."
vault_jwt_secret: "..."
# ...
```

## 평소

```bash
ansible-playbook playbook.yml --check --diff   # 무엇이 바뀌는지만 본다
ansible-playbook playbook.yml                  # 적용
ansible-vault edit group_vars/prod/vault.yml   # 값 변경
ansible-vault view group_vars/prod/vault.yml   # 값 확인
```

## 이 서버는 다른 것도 쓴다

`balruno-monitoring`, `incident-lab` 세션이 같은 서버에 돈다.
그래서 **플레이북이 패키지 설치나 시스템 설정을 하지 않는다.** 확인만 하고, 없으면 멈춘다.

방화벽도 기본은 닫아 둔다(`open_app_port: false`).
공개 노출은 판단이 필요한 결정이고, OCI 는 VCN 보안목록에서도 따로 막는다.

## Ansible 없이도 배포할 수 있어야 한다

`env.prod.example` 은 그대로 둔다. Ansible 을 못 쓰는 상황에서
**손으로 `.env` 를 만들어 배포하는 경로가 막히면 안 된다.**
플레이북은 그 절차를 자동화한 것이지 유일한 경로가 아니다.

## 왜 SOPS 가 아닌가

| | Ansible Vault | SOPS + age |
|---|---|---|
| 설치 | 이미 있다 | 둘 다 필요 |
| 프로비저닝 | 같이 된다 | 시크릿만 |
| 부분 암호화 | 파일 단위 | **값 단위 (diff 가 읽힌다)** |

SOPS 의 값 단위 암호화가 diff 가독성은 낫다.
다만 프로비저닝이 같이 필요했고 Ansible 이 이미 있어서, **도구 수를 줄이는 쪽**을 택했다.
시크릿 파일이 커지고 diff 를 읽을 일이 잦아지면 그때 SOPS 로 옮긴다.
