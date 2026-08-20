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

## 📌 커밋 컨벤션

Conventional Commits 형식(`type: 설명`)을 따릅니다.

```
feat:     기능 추가
fix:      버그 수정
refactor: 리팩토링 (동작 변경 없음)
perf:     성능 개선
test:     테스트 추가/수정
docs:     문서
chore:    빌드·설정
```

`perf`를 별도 타입으로 둡니다. 성능 개선 이력을 `git log --grep="^perf:"` 로 추적하기 위함입니다.

자세한 브랜치 전략과 PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

## 📌 코드 컨벤션

```
Back-End
- 함수명을 작성할 때는 동사+명사 형태로 구성
- 함수명은 Camel-Case로 작성
- Endpoint는 해당 리소스를 나타낼 수 있도록 작성
- 한줄 주석은 //, 여러 줄 주석은 /** */으로 작성
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


## 📌 브랜치 


### Git-Flow 전략 및 Github-Flow을 기반으로 한 커스텀 전략 채택

- 사용 브랜치

    - feature : 기능개발
    - fix : 긴급 수정
    - develop : CI/CD Hook 브랜치

- 브랜치 명명 규칙
    - feature/#이슈번호-기능 명시
        - FE Example : feature/#1-login
        - BE Example : fix/#24-login

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
