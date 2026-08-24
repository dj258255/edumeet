# 🖥 Edumeet

## 📦 저장소 구성

**모노레포다.** 팀이 GitLab 에 나눠 두었던 저장소를 이력째 합쳤다.

```
backend/       Spring Boot 3.5 · Java 17 — API · WebSocket 채팅 · LiveKit 세션
frontend/      Vue 3 · Vite — 화상강의 · 라이브방송 · 채팅 UI
ai/            FastAPI · Python — STT 자막 파이프라인
doc-summary/   Express · Node — 문서 요약 API

docs/          측정 기록과 판단 근거 (전송비용 · HLS · 채팅 용량 · 배포)
k6/            부하 측정 스크립트
deploy/  ansible/  observability/  scripts/
```

**이력을 보존했다.** `filter-repo --to-subdirectory-filter` 로 각 프로젝트의 모든 커밋 경로를
목적지 기준으로 재작성한 뒤 합쳤다. 그래서 `git log -- frontend/src/App.vue` 도,
`git blame` 도 2025년 팀 커밋까지 그대로 이어진다.

> `subtree add` 로 붙이면 병합 이전 커밋의 경로가 루트라서 `git log -- frontend/...` 가
> 아무것도 못 찾는다. **blame 은 되는데 log 는 안 되는** 어정쩡한 상태가 된다.

`.mailmap` 으로 한 사람이 여러 정체성으로 세어지던 것을 합쳤다
(`범수`/`BeomSu`, `권민환`/`kwonminhwan` 등). 커밋 SHA 는 건드리지 않는다 —
다시 쓰면 머지된 PR 90여 개의 참조가 전부 깨진다.

---

## 🪪 개요

### 교육의 기회는 누구에게나 열려있는 세계에서

### 사회적 약자들을 위한 첫 발판인 청각의 어려움 해소를 위해!

교육의 기회는 누구에게나

열려 있어야 합니다.

특히 사회적 약자들에게는

그 첫 발판이 되어야 합니다.

그 시작은 바로,

청각의 어려움을 해소하는 것에서부터 시작됩니다.

## 🚩 개발기간

|           |        [프로젝트 일정]        |
| :-------: |:-----------------------:|
| 진행 기간 | 2025.07.14 - 2025.08.22 |
|   인원    |           6명            |

<br/>

## 🚀 실행 방법

```bash
git clone https://github.com/dj258255/edumeet.git
cd edumeet

# 테스트는 추가 설정 없이 바로 실행됩니다 (H2 인메모리 사용)
./gradlew test

# 애플리케이션 실행에는 로컬 설정이 필요합니다
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# application-local.yml 의 빈 값을 채운 뒤
./gradlew bootRun
```

### 설정 파일 구조

| 파일 | 내용 | 커밋 |
|---|---|---|
| `application.yml` | 공통 설정 (시크릿 없음) | O |
| `application-test.yml` | 테스트 전용 (H2, 시크릿 없음) | O |
| `application-local.yml.example` | 로컬 설정 템플릿 | O |
| `application-local.yml` | 실제 시크릿 | **X (git 무시)** |

## 📌 개발 컨벤션

| 문서 | 내용 |
|---|---|
| **[docs/team-rules.md](docs/team-rules.md)** | 팀이 일하는 방식 (스크럼·토론·코드 리뷰·소통) |
| **[docs/team-convention.md](docs/team-convention.md)** | 코드·협업 컨벤션 (Git·백엔드·프론트엔드) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 기여 절차 요약 |

```
브랜치   <type>/<이슈번호>-<설명>      예: perf/4-n-plus-one
커밋     <type>: <한국어 subject>
type     feat · fix · refactor · perf · test · docs · style · chore
```

## 📃 개발 환경

### ⚒️ Back-End

- Springboot
- InteliJ
- JDK 17
- AWS EC2
- AWS S3
- MySQL
- Redis
- Nginx
- OpenVidu

### ⚒️ Front-End

- Vue
- OpenVidu

### ⚒️ CI/CD

- Jenkins

### ⚒️ 협업 툴

- Notion
- Jira
- GitLab

## 📝 API 명세서

### 노션 링크 첨부 🔗

https://charm-custard-27a.notion.site/API-2328d31b258e80acb086cd7b8f40c9e5

## ⚙️ ERD 다이어그램

![ERD](asset/img/erd.png)


## ⚙ 서비스 아키텍쳐

![Architecture](asset/img/infra.png)

## 🎬 디자인 및 기능

### 메인화면

![Main](asset/img/Main.png)
<br>
<br>
<br>
<br>

### 로그인

![Main](asset/img/Login.png)

<br>
<br>
<br>
<br>

### 게시판

![Main](asset/img/board.png)

<br>
<br>
<br>
<br>

### 과제제출 게시판

선생님 입장.


<img src="/asset/img/assignment.png" alt="Main" style="width:400px; height:auto;">


<br>
<br>
<br>

<img src="/asset/img/assignmentFail.png" alt="Main" style="width:400px; height:auto;">


<br>
<br>
<br>

<img src="/asset/img/assignmentSuccess.png" alt="Main" style="width:400px; height:auto;">

<br>
<br>
<br>
<br>

학생입장

<img src="/asset/img/submission.png" alt="Main" style="width:400px; height:auto;">

<br>
<br>
<br>
<br>

### 화상강의 및 STT

![Main](asset/img/openvidu.png)

<br>
<br>
<br>
<br>

### AI 문서요약

![Main](asset/img/document.png)
![Main](asset/img/AIDocument.png)

<br>
<br>
<br>
<br>
