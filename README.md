# 🖥 Edumeet

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

![Architecture](/asset/img/infra.png)

## 🎬 디자인 및 기능

### 메인화면

![Main](/asset/img/Main.png)
<br>
<br>
<br>
<br>

### 로그인

![Main](/asset/img/Login.png)

<br>
<br>
<br>
<br>

### 게시판

![Main](/asset/img/board.png)

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

![Main](/asset/img/openvidu.png)

<br>
<br>
<br>
<br>

### AI 문서요약

![Main](/asset/img/document.png)
![Main](/asset/img/AIDocument.png)

<br>
<br>
<br>
<br>
