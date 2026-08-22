# CLAUDE.md — EduMeet 작업 규칙

> 이 파일은 세션이 바뀌어도 잊으면 안 되는 것만 담는다.
> 상세 계획은 [`docs/README.md`](docs/README.md) 를 먼저 본다.

---

## 1. 이 작업의 목적

**iMBC 플랫폼개발팀 웹개발(신입) 지원용 포트폴리오.**

### 공고 (2026)

```
담당업무
  · 온라인 서비스 백엔드 시스템 개발
  · AI 기술 기반 서비스 시스템 설계 및 구현     ← 담당업무에 명시

필수
  · ASP.NET / C# / Java 등 웹 서버 개발 및 MVC 아키텍처 기반 개발
  · RDBMS(MySQL, MS-SQL, Oracle) 설계 개발
  · HTML5, javascript & jQuery
  · 포트폴리오 제출 필수 (파일 또는 URL)

우대
  · AI 모델 연동 및 활용 경험 (MCP, OpenAI, PyTorch)      ← 최우선 대응
  · 클라우드 컴퓨팅 환경 서비스 개발/운영 (AWS, Azure, GCP)  ← 대응
  · MLOps, DevOps 등 자동화 또는 운영 환경 구축            ← 대응
  · 프로그래밍 및 소프트웨어 개발 전공                     ← 해당
  · 정보처리기사, SQLD, AWS Certified Developer           ← 정처기·SQLD 보유
```

**보유 자격증(정처기·SQLD)은 이력서에서 직접 처리한다. 포폴에 반영하지 않는다.**

### 보유 인프라

```
OCI  VM.Standard.A1.Flex (aarch64)
     2 OCPU / 12 GB RAM
     168.107.47.33 (public) / 10.0.0.11 (private)
     AD-1 / FD-3
```

---

## 2. ★ 포폴 하나를 만들 때마다 반드시 같이 갱신한다

**코드/문서만 만들고 끝내지 않는다. 세 곳을 같이 건드린다.**

| # | 위치 | 무엇 |
|---|---|---|
| 1 | `docs/` (이 저장소) | 측정 기록·근거 문서 |
| 2 | `~/Desktop/gitblog/src/content/blog/project/EduMeet/*.md` | **블로그 글** |
| 3 | `~/Desktop/gitblog/_workspace/modules/portfolio/*.md` | **포폴 모듈** |

### 블로그 글 형식

경로: `gitblog/src/content/blog/project/EduMeet/<slug>.md`

```yaml
---
title: '...'
description: >-
  ...
date: YYYY-MM-DD
tags: [EduMeet, Spring Boot, ...]
category: team/EduMeet
coverImage: /uploads/project/EduMeet/<subdir>/title.png
draft: false
series: "EduMeet"
seriesOrder: N        # 기존 글 다음 번호
---
```

이미지는 `gitblog/public/uploads/project/EduMeet/` 아래.

### 프로젝트 메타 갱신

`gitblog/src/data/projects/EduMeet.json` — 새 기술을 쓰면 `tags` 에 추가한다.
현재 태그: `Java, Spring Boot, JPA, QueryDSL, MySQL, AWS S3, Docker`

### 포폴 모듈 형식

`gitblog/_workspace/modules/portfolio/` 의 기존 파일(`dbtower.md`, `lakehouse.md` 등)을 참고.
빌드는 `_workspace/modules/build.py`.

---

## 3. 작업 규칙

- **이슈 → 브랜치 → PR → squash merge.** 예외 없다
- 커밋 메시지는 Conventional Commits + `perf` 타입. 본문에 **왜**를 적는다
- 상세: [`docs/team-convention.md`](docs/team-convention.md)

---

## 4. 측정 원칙

- **쿼리 수는 대리 지표다.** 구조가 고쳐졌음을 증명하지 "빨라졌다"를 증명하지 않는다
- **"빨라졌다"고 주장하려면 부하 도구로 지연·처리량을 재야 한다**
- **워밍업을 측정과 분리한다.** JIT·버퍼 풀이 덥혀진 뒤부터 잰다
- **운영과 같은 DB 로 잰다.** H2 로 잰 지연시간은 MySQL 의 지연시간이 아니다
- **개선 전 코드는 추측으로 재현하지 않는다.** git 이력에서 실제 코드를 가져온다
- **한계를 반드시 적는다.** 같은 머신에서 부하 도구를 돌렸으면 그렇게 쓴다

실행 스크립트: `scripts/run-benchmark.sh`, `run-session-benchmark.sh`,
`run-fault-injection.sh`, `run-lock-determinism.sh`

---

## 5. 서사 프레이밍

```
✗ "팀 프로젝트를 리팩토링했습니다"
✗ "Netty 로 직접 만들었습니다"
✗ "라이브 스트리밍을 하고 싶어서 스트리밍을 만들었습니다"

✓ "팀 프로젝트를 인수해서 구조를 정리하고, 성능을 측정해 고치고,
   숨어 있던 보안·기능 결함을 찾아 수정하고, 새 기능을 붙였습니다."

✓ "Spring 기본 구성으로 N명까지 측정했고, 여기서 병목을 확인해
   이 부분만 이렇게 해결했습니다."

✓ "처음엔 X 가 필요할 거라고 가정했는데 재보니 병목이 거기가 아니어서
   도입을 취소했습니다. 그 판단 기록을 남겨뒀습니다."
```

**"저수준을 팠다"가 아니라 "경계를 안다"가 목표다.**

---

## 6. 검토했지만 쓰지 않은 것 (계속 늘린다)

> **채택한 기술만큼 기각한 기술을 기록한다.**
> 신입 지원자 중 *"검토했지만 안 썼습니다, 이유는 이겁니다"* 를 문서로 가진 사람은 드물다.

| | 근거 |
|---|---|
| Spring Modulith | 순환 5개 중 4개가 JPA 양방향 연관 |
| Toxiproxy 상시 도입 | 잠금 버그 검출률이 지연 없이도 6/6 |
| 서킷 브레이커 | LiveKit 장애가 3초에 끝남. 그 3초가 문제라는 근거 없음 |
| Netty 직접 구현 | 30명에서 측정 가능한 차이 없음. 11가지를 손으로, 2~3주 |
| NATS | Core 도 at-most-once 라 Redis 와 보장 수준이 같음 |
| Kafka | 채팅 전파에는 지연·운영 부담이 과함 |
| LL-HLS | LiveKit egress 가 TS 세그먼트라 CMAF 가 아님 |

---

## 7. 지금 상태

```
✅ 완료   Port/Adapter 제거 · 4개 도메인 구조 통일
✅ 완료   N+1 제거 + MySQL k6 부하 측정
✅ 완료   세션 타입(BROADCAST) 도입 · 정원 비관적 잠금
✅ 완료   외부 호출 타임아웃 + Toxiproxy 장애 주입
✅ 완료   사전 조사 6문서 + 실행 계획

🔴 P0     보안·기능 결함 3건        반나절~1일
🟡 P1     GitHub Actions + OCI 배포  2~3일   ← DevOps 우대 정면
🟡 P2     AI 연동 재설계             3~4일   ← 담당업무 2번 정면
⬜ P3     채팅 붕괴 측정             6~8일
⬜ P4     HLS                       선택
```

### P0 — 반드시 먼저

1. **`/api/v1/**` 가 `permitAll` 에 있다** → API 전체가 인증 없이 열림
2. **정원이 반환되지 않는다** → `leaveSession` 호출부 없음 + LiveKit Webhook 미사용
3. **`endMeeting` 이 권한 검사를 안 하고 LiveKit 룸도 안 닫는다**

---

## 8. 알아둘 것

- **파이썬 AI 서버는 이 저장소에 없다.** 별도 저장소이거나 팀원 보유.
  Java 는 `POST /api/v1/meeting/summary/{classId}` 로 결과 파일(.md/.pdf)만 받아 S3 에 올린다
- **CI/CD 설정이 아예 없다.** GitLab 에서 했다면 그 저장소에 있고, 여기선 0 에서 만든다
- 측정용 Docker 환경: `docker-compose.perf.yml` (MySQL 3307 / Redis 6380 / LiveKit 7880 / Toxiproxy 8474)
- perf 프로파일은 포트 **8081** 을 쓴다 (개발 8080 과 충돌 방지)
