# 개발 컨벤션

| | |
| --- | --- |
| **대상** | 팀 전원 |
| **최종 수정** | 2026-08-21 |
| **변경 절차** | 회고에서 합의 → 이 문서 수정 → 팀 채널 공지 |
| **부속 문서** | 팀이 일하는 방식 → [`team-rules.md`](./team-rules.md) |

**목차**

- [Part 1. 공통 (Git / 협업)](#part-1-공통-git--협업)
- [Part 2. 백엔드 (Java / Spring Boot)](#part-2-백엔드-java--spring-boot)
- [Part 3. 프론트엔드 (Vue)](#part-3-프론트엔드-vue)
- [이 규칙들을 정할 때 본 것](#이-규칙들을-정할-때-본-것)

---

# Part 1. 공통 (Git / 협업)

> 항목마다 **[필수] / [권장] / [선택]** 을 붙여뒀다.
> [필수] 는 리뷰에서 지적하는 것, [권장] 은 이유가 있으면 안 지켜도 되는 것,
> [선택] 은 취향인 것이다.
>
> **규칙마다 "왜" 를 같이 적었다.** 이유를 모르면 지키다가도 흐지부지되고,
> 상황이 바뀌었을 때 고쳐도 되는지 판단할 수 없기 때문이다.

## 1.1 브랜치 전략 — GitHub Flow [필수]

> **기본 브랜치는 `master` 다.** `main` 으로 바꾸자는 이야기가 나왔지만 하지 않기로 했다.
> 이미 쌓인 이력과 문서 링크가 전부 `master` 를 가리키고 있어서,
> 이름을 바꿔서 얻는 것보다 깨지는 게 많다.
>
> **`develop` 브랜치는 두지 않는다.** 배포 단위가 하나뿐이라 중간 통합 브랜치가 할 일이 없다.

**원칙**

1. `master` 브랜치는 **항상 배포 가능한 상태**를 유지한다.
2. 작업은 `master` 에서 분기한 작업 브랜치에서만 한다.
3. 작업 브랜치는 원격에 자주 push 한다.
4. 완료되면 **PR(Pull Request)** 을 올린다.
5. 리뷰 후 `master` 에 merge 한다.
6. merge 된 브랜치는 삭제한다.
7. `master` 에 직접 push 금지.

### 브랜치 네이밍 [필수]

```
<type>/<이슈번호>-<간단한-설명>
```

| 예시 | 설명 |
|---|---|
| `feat/12-session-type` | 기능 개발 |
| `fix/31-token-expiry` | 버그 수정 |
| `refactor/45-repository-layer` | 리팩터링 |
| `perf/48-n-plus-one` | 성능 개선 |
| `docs/50-readme` | 문서 |
| `chore/58-config-yml` | 설정·빌드 |

- 소문자 + 하이픈(`-`). 언더스코어 사용 금지.
- **이슈 번호 앞에 `#` 를 붙이지 않는다.**
  `#` 는 셸에서 주석 시작 문자라 `git push origin feat/#12-...` 가 따옴표 없이는 동작하지 않는다.
- **브랜치 type 은 커밋 type 과 동일한 목록을 쓴다.** (1.2 참고)

> 브랜치 type 을 4종으로 줄이자는 이야기가 있었는데, **커밋 type 과 같은 8종**으로 맞췄다.
> 줄이면 `perf` 작업이 `refactor` 에 섞여서 `git log --grep` 으로 성능 이력을 못 찾는다.
> 8개 외우는 부담보다 나중에 못 찾는 손해가 크다.

---

## 1.2 커밋 컨벤션 [필수]

**형식**

```
<type>: <subject>

<body>

<footer>
```

**Type 목록**

| Type | 용도 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 (동작 변경 없음) |
| `perf` | **성능 개선** |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 |
| `style` | 포맷팅, 세미콜론 등 (동작 변경 없음) |
| `chore` | 빌드·패키지·설정 |

> 흔히 쓰는 7종에 **`perf` 를 하나 더 넣어 8종**으로 쓴다.
> 이 저장소는 성능 작업이 많은데, `refactor` 에 섞어두면
> *"무엇을 얼마나 개선했나"* 를 나중에 복원할 방법이 없다.
>
> ```bash
> git log --grep="^perf:"    # 이 한 줄이 성능 이력 전체다
> ```

### Subject 규칙 [필수]

- **한국어로 작성한다.** 첫 글자 대문자 규칙은 적용하지 않는다.
- 50자 이내, 마침표 없음
- 무엇을 했는지 **명사형 또는 평서형**으로. `수정`, `update` 처럼 내용 없는 단어만 쓰지 않는다.

> 커밋 메시지는 **한국어로 쓴다.**
> 영어 명령형이 정석이라는 건 알지만, 이미 쌓인 이력이 전부 한국어이고 리뷰어도 한국어를 쓴다.
> 여기서 영어로 바꾸면 이력의 절반이 다른 언어가 되어 오히려 읽기 어려워진다.
>
> 다만 **"무엇을 왜"** 를 담는다는 원칙은 그대로다. 언어를 바꾼 거지 기준을 낮춘 게 아니다.

### Body 규칙 [선택]

- 72자에서 줄바꿈
- **무엇을, 왜** 했는지 쓴다. 어떻게는 코드가 설명한다.
- **성능 개선(`perf`) 커밋은 body 에 측정 결과를 넣는다.**

### Footer 규칙 [선택]

- 이슈 참조: `Resolves: #12`, `See also: #45`
- PR 본문에는 `Closes #12` 를 써서 머지 시 이슈가 자동으로 닫히게 한다.

### 좋은 예

```
perf: 과제 목록 조회의 N+1 제거

목록 조회가 과제마다 제출물 쿼리를 반복 호출하고 있었다.
페치 조인 쿼리는 이미 정의되어 있었으나 사용되지 않는 상태였다.

쿼리 수 83 -> 5 (과제 20건 기준)
측정: H2 인메모리, Hibernate Statistics

Resolves: #4
```

### 나쁜 예

```
수정
update
fix : 버그               ← 콜론 앞 공백
refactor:리팩            ← 콜론 뒤 공백 없음, 내용 없음
```

---

## 1.3 머지 방식 — Squash and merge [필수]

> 병합 방식을 정해두지 않으면 사람마다 다르게 눌러서 이력이 뒤섞인다.
> 논의 끝에 **Squash and merge 하나만** 쓰기로 했다.
>
> 말로만 정하면 결국 누군가는 다른 걸 누른다. **GitHub 설정에서 나머지 두 개를 꺼둔다.**
> (Settings → General → Pull Requests)

### 규칙

- `master` 로의 머지는 **전부 Squash and merge** 로 한다.
- **PR 하나 = `master` 커밋 하나 = 이슈 하나.**
- Squash 커밋 메시지는 **자동 생성된 것을 그대로 쓰지 않고 직접 정리한다.**

```
feat: 세션 타입(화상강의/라이브방송) 도입 - 정원 동시성 제어 (#12)

Meeting 에 SessionType 을 두어 세션 형태를 나눈다.
정원 검증 구간을 비관적 잠금으로 직렬화했다.

Closes #2
```

- 작업 브랜치 안에서는 커밋을 자유롭게 쌓아도 된다. **어차피 하나로 합쳐진다.**
  `wip`, `오타 수정` 같은 커밋이 `master` 에 남지 않는다.

### 왜 Squash 인가

| | Merge commit | **Squash and merge** | Rebase and merge |
|---|---|---|---|
| `master` 히스토리 | 브랜치가 갈라진 그래프 | **선형, PR 단위 1커밋** | 선형, 커밋 전부 유지 |
| 작업 브랜치 커밋 | 전부 보존 | **사라짐** (PR에 남음) | 전부 보존 |
| 충돌 해결 | 1회 | **1회** | **커밋마다 반복** |
| 롤백 | 머지 커밋 revert | **커밋 1개 revert** | 여러 커밋 revert |
| 이분 탐색(`git bisect`) | 중간 커밋이 빌드 안 될 수 있음 | **커밋 단위가 기능 단위라 유리** | 커밋 수만큼 탐색 |

- 우리 팀 규모에서 `master` 에 필요한 정보는 **"어떤 기능이 언제 들어왔나"** 이지
  "그 기능을 만드는 도중에 오타를 몇 번 고쳤나"가 아니다.
- 롤백 단위가 **PR 단위와 정확히 일치**한다. 장애 시 커밋 하나만 revert 하면 된다.
- Rebase는 충돌이 나면 **커밋마다** 해결해야 하고 force push 가 필요해 사고 위험이 크다.

### 대가로 잃는 것 (알고 쓰자)

- **작업 중간 커밋 이력이 `master` 에 남지 않는다.** "어느 시점에 어떤 판단을 했는지"는
  Git 이 아니라 **PR 본문과 리뷰 코멘트**에 남는다. → 그래서 PR 본문을 성실히 쓰는 게 규칙이다.
- 그래서 **PR 은 작아야 한다.** 큰 PR을 squash 하면 거대한 커밋 하나가 남아 추적이 어려워진다.
  (변경 파일 10개 / 300줄 이내 권장 — 팀 규칙 3 참고)

참고: [PR 병합 방식 비교](https://maily.so/gitminam/posts/32z8w9p8zn4) · [Merge / Squash / Rebase 정리](https://hudi.blog/git-merge-squash-rebase/)

---

## 1.4 PR (Pull Request) [필수]

> 리뷰 기준·코멘트 규칙·의견 충돌 처리는 **[팀 규칙 3. 코드 리뷰](./team-rules.md#3-코드-리뷰-필수)** 에 있다.
> 여기서는 형식만 다룬다.

- PR 은 **작게, 자주** 올린다. (변경 파일 10개 / 300줄 이내 권장)
- 이슈 제목·본문과 PR 제목·본문은 **한국어로 작성한다.**
  외부 라이브러리 이름, 명령어, 지표명은 원문을 유지해도 된다.
- 리뷰 요청 후 **24시간 이내** 1차 응답.
- 승인 1인 이상 후 **Squash and merge**.
- 머지 후 브랜치를 삭제한다.

### PR 템플릿

`.github/pull_request_template.md` 로 관리한다.

---

## 1.5 Issue [필수]

- 모든 작업은 **이슈 등록 → 브랜치 생성 → PR** 순서로 진행한다.
- 이슈에는 **Label** 을 지정한다. (Assignee / Milestone 은 [선택])
- **논의 끝에 내린 결정은 해당 이슈에 기록한다** — 결정 / 이유 / 폐기한 대안.

### 템플릿

`.github/ISSUE_TEMPLATE/` 아래에 둔다.

| 파일 | 용도 |
|---|---|
| `feature.md` | 기능 개발 |
| `bug.md` | 버그 리포트 |
| `performance.md` | **성능 개선** |

> 성능 이슈용 템플릿(`performance.md`)을 따로 뒀다.
> 버그 이슈는 *"어떻게 재현하나"* 를 묻지만, 성능 이슈는 **"어떻게 재고 기준선이 뭔가"** 를 물어야 한다.
> 같은 양식을 쓰면 "느린 것 같아요" 로 끝나는 이슈가 올라온다.

### Label 목록

| Label | 용도 |
|---|---|
| `enhancement` | 기능 개발 |
| `bug` | 버그 |
| `refactor` | 리팩터링 |
| `perf` | 성능 개선 |
| `documentation` | 문서 |
| `chore` | 설정·환경 |
| `design` | 설계 결정이 필요한 이슈 |

> `BE` / `FE` 라벨은 이 저장소가 백엔드 단일 저장소이므로 두지 않는다.

---

## 1.6 성능 측정 원칙 [필수]

> 성능 작업이 이 저장소의 큰 축이라 규칙을 따로 적어둔다.
> **"빨라졌다" 는 말은 숫자 없이 하면 아무 뜻이 없다.**

**1차 지표는 쿼리 수와 실행계획이다. 응답 시간(ms)이 아니다.**

로컬에서 측정한 시간은 다음 요인에 좌우되어 재현되지 않는다.

- JIT 워밍업 — JVM은 초기 호출이 느리다. 개선 전을 콜드, 개선 후를 웜으로 재면 아무것도 안 고쳐도 몇 배가 나온다
- 부하 생성기와 애플리케이션의 자원 경합
- OS 캐시·버퍼 풀 상태
- 컨테이너 오버헤드

반면 **쿼리 수와 실행계획은 환경과 무관하게 재현된다.**

### 다만 쿼리 수는 대리 지표다 [필수]

쿼리 수는 *"구조가 고쳐졌다"* 를 증명하지 *"빨라졌다"* 를 증명하지 않는다.
**둘은 단조 관계가 아니다.** 실측 사례:

| 조합 | 요청당 SQL | 처리량 |
|---|---:|---:|
| 앱 IN절만 | 155 | 40.7 req/s |
| 배치페치만 | **40** | **38.4 req/s** |

**SQL 이 1/4 인데 더 느리다.** 개수가 아니라 *쿼리가 하는 일의 총량* 이 비용이기
때문이다. 한 건이 카테시안 곱 행을 만들어내면 개수가 적어도 총합은 줄지 않는다.

그래서 규칙은 이렇다.

- **구조 개선의 증거로는 쿼리 수를 쓴다.** 재현 가능하고 환경에 안 흔들린다.
- **"빨라졌다"고 주장하려면 부하 도구로 지연시간·처리량을 재야 한다.**
  쿼리 수만 근거로 성능 개선을 주장하지 않는다.
- **개선하면 병목이 옮겨간다.** 쿼리를 줄이면 다음 병목은 대개 직렬화·응답 크기다.
  다음에 어디를 손댈지는 측정으로 정한다. ([03 문서](performance/03-mysql-load-test.md))

### 부하 측정 [필수]

부하로 시간을 잴 때는 다음을 지킨다.

- **운영과 같은 DB 로 잰다.** H2 로 잰 지연시간은 MySQL 의 지연시간이 아니다.
- **워밍업을 측정과 분리한다.** JVM 의 JIT 컴파일과 InnoDB 버퍼 풀이 덥혀진
  뒤부터 잰다. 워밍업 없이 재면 *먼저 돌았다는 이유만으로* 느리게 나온다.
- **커넥션 풀을 VU 수보다 크게 잡는다.** 풀이 병목이면 쿼리 개선 효과가 묻힌다.
- **비교군은 같은 프로세스에서 번갈아 잰다.** 전역 설정처럼 그럴 수 없는 것만
  프로세스를 나누고, 나눴다는 사실을 문서에 적는다.
- **개선 전 코드는 추측으로 재현하지 않는다.** git 이력에서 실제 코드를 가져온다.

### 시간을 함께 기록할 경우 반드시 병기할 것 [필수]

- 측정 환경 (CPU, 메모리, DB 버전)
- 데이터 규모
- 워밍업 횟수와 측정 반복 횟수
- **부하 생성기와 애플리케이션이 자원을 공유하는지 여부**
- 평균이 아니라 **p50 / p95**

### 측정 도구

- 쿼리 수(테스트): Hibernate `Statistics` (`spring.jpa.properties.hibernate.generate_statistics: true`)
- 쿼리 수(부하 중): `StatementInspector` + ThreadLocal.
  `Statistics` 는 SessionFactory 누적값이라 **동시 요청에서 요청별 델타를 못 낸다.**
  (`perf/QueryCountInspector`)
- 지연시간·처리량: k6. 결과 JSON 을 `docs/performance/data/` 에 커밋해 차트를 재생성 가능하게 둔다
- 실행계획: `EXPLAIN` (MySQL) — 개선 전/후 캡처를 PR에 첨부

### 측정 기록은 문서로 남긴다 [권장]

성능·리팩터링 작업은 결과를 `docs/` 에 남긴다.
문제 → 측정 → 시도(실패한 시도 포함) → 결과 → **한계** 순으로 쓴다.

| 위치 | 내용 |
|---|---|
| `docs/performance/` | 성능 개선 기록 + 비교 차트 |
| `docs/refactoring/` | 구조 변경 기록 + 규모 변화 차트 |
| `scripts/make_perf_chart.py` | 차트 재생성 (의존성 없이 SVG 생성) |
| `scripts/make_k6_chart.py` | k6 결과 JSON → 차트. **수치를 손으로 옮기지 않는다** |
| `scripts/run-benchmark.sh` | 과제 목록 조회 부하 측정 (2×2) |
| `scripts/run-session-benchmark.sh` | 세션 정원 동시성 검증 |
| `scripts/run-fault-injection.sh` | Toxiproxy 장애 주입 검증 |

---

# Part 2. 백엔드 (Java / Spring Boot)

## 2.1 코드 컨벤션

**[필수] 기준 문서:** [NAVER Hackday Java Conventions](https://github.com/naver/hackday-conventions-java)

### 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 패키지 | 전부 소문자, 단어 구분 없음 | `com.edu.edumeet.homework.service` |
| 클래스 / 인터페이스 | PascalCase | `AssignmentService`, `SubmissionRepository` |
| 메서드 / 변수 | camelCase | `findByClassId`, `submissionCount` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 테스트 메서드 | camelCase 또는 한글 허용 | `재고가_0이면_대여신청은_실패한다` |

### 축약어 금지 [필수]

```java
// Bad
String clsrn;
int cnt;
AssignmentService as;

// Good
String classroomName;
int submissionCount;
AssignmentService assignmentService;
```

- 널리 통용되는 약어(`id`, `url`, `api`, `dto`)만 예외로 허용한다.
- 약어를 연속 대문자로 쓰지 않는다. → `HTTPUrl` (X) / `HttpUrl` (O), `memberID` (X) / `memberId` (O)

### 포맷팅 [필수]

- 인코딩: UTF-8
- 들여쓰기: 스페이스 4칸
- 한 줄 최대 길이: 120자
- 중괄호는 K&R 스타일 (여는 중괄호는 같은 줄)
- 빈 블록이라도 중괄호는 생략하지 않는다

---

## 2.2 아키텍처

### 레이어드 아키텍처 [필수]

```
Controller  →  Service  →  Repository  →  DB
   (Web)      (Business)   (Persistence)
```

- 상위 레이어는 하위 레이어만 호출한다. 역방향 의존 금지.
- Controller 에서 Repository 직접 호출 금지.
- 레이어 간 데이터 전달은 DTO 로 한다. **Entity 를 Controller 밖으로 노출하지 않는다.**

### 패키지 구조 [필수] — 도메인 우선

```
com.edu.edumeet
├── homework
│   ├── controller
│   ├── service
│   ├── repository
│   ├── domain
│   └── dto
├── classroom
│   └── ...
└── global
    ├── config
    ├── exception
    └── util
```

> 도메인으로 먼저 나누고, 그 안에서 레이어로 나눈다.

### 구조 통일 현황

| 도메인 | 구조 | 상태 |
|---|---|---|
| `homework` | `controller` / `service` / `repository` / `domain` / `dto` | ✅ 적용 완료 (#3) |
| `classroom`, `member`, `meeting` | 동일 | ✅ 원래부터 준수 |
| `board`, `reply`, `attachment` | `application` / `domain` / `infrastructure` / `presentation` | ⬜ 미적용 |

`board` · `reply` · `attachment` 는 아직 헥사고날 계열 구조다. 순차 적용이 남아 있다.

---

### Repository 구조 [필수] — 분리하지 않는다

> 인터페이스와 구현체를 나눌지 말지로 의견이 갈렸다.
> **말로 정하는 대신 `homework` 도메인에 실제로 적용해보고 결정했다.** (#3)
> 결과는 아래 실측을 보면 된다 — **나누지 않기로 했다.**

**Service 는 Spring Data 리포지토리를 직접 주입받는다.**

```java
@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final AssignmentRepository assignmentRepository;   // extends JpaRepository
}
```

### 왜 분리하지 않는가 — 실측 근거

프로젝트에서 직접 정의한 인터페이스(Spring Data 제외) **15개 중 14개(93%)가 구현체 1개뿐**이었다.
`homework` 에 실제로 적용해본 결과 분리 비용이 다음과 같이 드러났다.

| 항목 | 결과 |
|---|---|
| 파일·코드 | 28파일 2,634줄 → **19파일 1,642줄** (코드 38% 감소) |
| Adapter | 422줄 제거 |
| **최적화 쿼리** | Port 가 노출하지 않아 **페치 조인 쿼리 2개가 죽은 코드**였다 |
| **DTO 프로젝션** | 도메인 모델 ↔ 엔티티 매핑 때문에 **쓸 수 없었다** |
| 도메인 행위 | 7개 중 5개가 호출되지 않는 죽은 코드였다 |

### 분리를 걷어낼 때 조용히 깨지는 것들

Port 계층이 **표준 이름 뒤에 다른 의미를 숨기고 있을 수 있다.** 실제로 두 건이 있었다.

| 메서드 | Port 의 의미 | Spring Data 의 의미 |
|---|---|---|
| `deleteById` | **소프트 삭제** (`deletedAt` 설정) | **물리 삭제** |
| `findById` | `findByIdAndDeletedAtIsNull` (**삭제 행 제외**) | 삭제된 행도 반환 |

**두 경우 모두 컴파일은 통과한다.** 이름과 시그니처가 같기 때문이다.
Port 를 제거할 때는 **구현체가 실제로 무엇을 하고 있었는지** 반드시 확인한다.

**필드명 규칙 [필수]**

```java
private final MemberRepository memberRepository;      // O
private final MemberRepository memberRepositoryImpl;  // X
```

---

## 2.3 기술 스택 (Persistence)

| 기술 | 사용 여부 |
|---|---|
| JPA / Hibernate | [필수] 기본 |
| QueryDSL | [선택] 동적 쿼리, 복잡한 조회 |
| MyBatis | [선택] 통계성 쿼리 등 필요 시 |

- 혼용은 허용하되, **같은 도메인 안에서는 하나로 통일**한다.
- 조회 전용 복잡 쿼리는 QueryDSL 또는 MyBatis 로 분리한다.

### N+1 대응 [필수]

- **컬렉션 두 개 이상을 동시에 `LEFT JOIN FETCH` 하지 않는다.**
  `Set` 이라 예외는 나지 않지만 **카테시안 곱(M×N 행)** 이 발생한다.
  (첨부 5 × 제출현황 20 = **100행**, 나눠 조회하면 26행)
  하나만 페치 조인하고 나머지는 배치 로딩으로 분리한다.
- **`@BatchSize` 를 컬렉션마다 붙이지 않는다.**
  새 컬렉션이 추가될 때 조용히 누락된다. 전역 설정을 기본으로 둔다.

  ```yaml
  spring.jpa.properties.hibernate.default_batch_fetch_size: 100
  ```

- **`@BatchSize` 는 지연 로딩 컬렉션에만 적용된다.**
  Service 루프 안에서 `repository.findByXxx(id)` 를 반복 호출하는 코드에는 효과가 없다.
- **Hibernate 6 는 엔티티 쿼리 결과를 자동으로 중복 제거한다.**
  JPQL 결과만 보면 행 폭발이 드러나지 않는다. 실제 행 수는 **네이티브 SQL** 로 확인한다.
- N+1 해소 여부는 **쿼리 수를 단언하는 테스트**로 검증한다. 시간 측정으로 대체하지 않는다.

### 소프트 삭제 [필수]

- 조회는 삭제 행을 제외한다. (`findByIdAndDeletedAtIsNull`)
- **복원 경로만 삭제 행을 포함해 조회한다.**
- Spring Data 의 `deleteById` 를 쓰지 않는다. **물리 삭제**다. 엔티티의 `delete()` 를 호출한다.

### 동시성 [필수]

- **개수 상한은 DB 제약으로 표현할 수 없다.** 유니크 제약은 중복만 막는다.
- "센다 → 비교한다 → 기록한다"가 원자적이어야 하면 **비관적 쓰기 잠금**으로 직렬화한다.

  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Meeting m WHERE m.id = :id")
  Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
  ```

- 동시성 테스트에는 **`@Transactional` 을 쓰지 않는다.**
  모든 스레드가 같은 커넥션을 공유해 **경쟁이 재현되지 않는다.**

### 트랜잭션 [필수] — 같은 클래스 안에서 부르면 걸리지 않는다

Spring 의 `@Transactional` 은 **프록시**로 동작한다. 빈을 주입받아 호출할 때
프록시가 트랜잭션을 열어준다. 그런데 **같은 객체 안에서 `this.method()` 로 부르면
프록시를 거치지 않으므로 애노테이션이 무시된다.** `@Async`, `@Cacheable`,
`@Retryable` 도 모두 같다.

```java
// ✗ transactional 이 걸리지 않는다
@RestController
class SomeController {
    public Response handle() {
        return doWork();          // this.doWork() — 프록시를 안 거친다
    }

    @Transactional
    public Response doWork() { ... }
}

// ✓ 다른 빈으로 분리해 주입받는다
@RestController
@RequiredArgsConstructor
class SomeController {
    private final SomeService someService;

    public Response handle() {
        return someService.doWork();   // 프록시를 거친다
    }
}
```

**컴파일도 되고 테스트도 통과하는 경우가 많아 눈에 띄지 않는다.**
Spring Data 리포지토리 메서드가 각자 트랜잭션을 열기 때문에 저장·조회는 그대로
동작하고, 원자성만 조용히 사라진다.

실제 사례 — 정원 제어의 대조군(잠금 없는 참가)을 컨트롤러 안에
`@Transactional` 메서드로 두었는데, 자기 호출이라 트랜잭션이 걸리지 않았다.
그대로 뒀다면 *"잠금만 뺀 같은 코드"* 가 아니라 *"잠금도 없고 트랜잭션도 없는 코드"* 를
대조군으로 쓸 뻔했다. 비교 자체가 무의미해진다.
(`perf/UnsafeJoinService`)

**판별법** — `@Transactional`(그리고 `@Async`·`@Cacheable`)이 붙은 메서드를
같은 클래스에서 호출하는 곳이 있는지 본다. 있으면 빈을 분리한다.

### 외부 호출 [필수] — 타임아웃 없는 호출을 만들지 않는다

**모든 외부 시스템 호출에 타임아웃을 건다.** 상대가 응답하지 않으면 요청 스레드가
무한정 잡히고, 톰캣 스레드 풀이 마르면 **그 외부 시스템과 무관한 요청까지 같이 죽는다.**

특히 위험한 기본값:

| | 기본 타임아웃 |
|---|---|
| `new RestTemplate()` | **connect·read 둘 다 무한(-1)** |
| OkHttp | 10초 |
| AWS SDK v2 | 소켓 30초. 단 `apiCallTimeout`(재시도 포함 전체)은 **없음** |
| Lettuce(Redis) | 설정하지 않으면 무한 대기 가능 |

```java
// ✗ 타임아웃 무한
RestTemplate rt = new RestTemplate();

// ✓ 타임아웃을 명시한 빈을 주입받는다
@Bean
RestTemplate restTemplate(RestTemplateBuilder b) {
    return b.connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .build();
}
```

**멈춤(stall) 타임아웃과 총량(total) 타임아웃을 구분한다.**
소켓 타임아웃은 "N초간 한 바이트도 안 오면 끊기"라 큰 전송을 깨지 않는다.
전체 호출 타임아웃은 **전송 시간까지 포함**하므로 짧게 걸면 정상적인 대용량
업로드를 죽인다. 파일을 주고받는 클라이언트에는 총량 타임아웃을 넉넉히 잡는다.

### 실패를 구분한다 [필수]

**"상대가 없다"와 "찾는 것이 없다"는 다른 상태다.**

```java
catch (HttpClientErrorException.NotFound e) {
    return null;                                  // 정상적인 조회 결과 -> 404
} catch (IOException e) {
    throw new LiveKitUnavailableException(...);   // 의존 시스템 장애 -> 503
}
```

둘을 404 로 뭉뚱그리면 클라이언트는 *"삭제됐구나"* 로 오해하고,
**운영에서는 장애가 보이지 않는다.**

실제로 이 구분을 넣자마자 `getRoomInfo` 가 **실제 LiveKit 에서 한 번도 동작한 적이
없다**는 것이 드러났다. 인증 방식이 틀려 항상 401 이었는데, 모든 실패가 똑같이
500 이라 아무도 몰랐다. ([05 문서](performance/05-fault-injection.md))

> 관측 가능성의 실질은 로그를 늘리는 것이 아니라
> **실패를 구분해서 다르게 처리하는 것**이다.

### 검증 [권장]

타임아웃·폴백을 넣었으면 **장애를 주입해 실제로 동작하는지 확인한다.**
Toxiproxy 로 지연·단절·무응답을 넣고, **수정 전 코드를 대조군으로 함께 잰다.**
막지 않았을 때를 재지 않으면 무엇을 막았는지 말할 수 없다.
(`scripts/run-fault-injection.sh`)

**순서를 지킨다** — 막을 장치를 먼저 만들고, 그 장치가 동작함을 증명하는 데
장애 주입을 쓴다. 장치 없이 넣으면 앱이 죽는 것을 증명할 뿐이다.

---

## 2.4 테스트

### 커버리지 [권장] — 숫자를 게이트로 쓰지 않는다

> 처음엔 *"커버리지 90% 이상 [필수]"* 로 잡았다가 **[권장] 으로 낮췄다.** 이유는 이렇다.
>
> - Martin Fowler: 커버리지 퍼센트는 **품질 지표로서 의미가 거의 없으며** 저품질 테스트로 쉽게 부풀릴 수 있다.
>   유일하게 정당한 용도는 **"테스트되지 않은 영역을 찾는 것"** 이다.
> - Google 코드 커버리지 모범사례: *"모든 팀에서 커버리지 몇 퍼센트 달성을 강제할 수는 없다."*
>   필요한 테스트 수준은 **비즈니스 영향도 · 변경 빈도 · 코드 수명**에 따라 달라진다.
>
> 숫자를 목표로 삼으면 getter/setter 테스트로 채우게 되고, 그건 아무것도 검증하지 않는다.

**대신 다음을 [필수] 로 한다.**

- Service 계층의 **비즈니스 규칙**에는 단위 테스트를 작성한다. 경계값과 실패 케이스를 포함한다.
- **상태 전이가 있는 로직**(제출 상태, 세션 참가 등)은 반드시 테스트한다.
- **동시성이 관여하는 로직**(정원 제한, 중복 제출 방지)은 동시 요청 테스트를 작성한다.
- 테스트 이름은 **무엇을 검증하는지 문장으로** 쓴다.

### 테스트가 실제로 검증하는지 확인할 것 [필수]

```java
// Bad — 이 테스트는 페치 조인을 전부 지워도 통과한다
long start = System.currentTimeMillis();
AssignmentDTO result = service.getAssignmentWithAllDetails(id);
long end = System.currentTimeMillis();
assertThat(result.getAttachmentFiles()).hasSize(2);

// Good — 쿼리 수를 단언한다
statistics.clear();
service.getAssignmentsByClassId(classId);
assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
```

**"검증하지 않는 테스트"는 거짓 안전감을 준다.** 개수를 채우는 것보다 나쁘다.

### 검증력을 확인하는 방법 [권장]

**고친 코드를 되돌려보고 테스트가 실패하는지 확인한다.**
통과만 보면 그 테스트가 진짜 검증인지 알 수 없다.

```
비관적 잠금 적용 → 동시 요청 20건, 성공 3건   (정원 3과 일치)
비관적 잠금 제거 → 동시 요청 20건, 성공 10건  (테스트 실패)
```

### 커버리지 측정 [선택]

측정은 하되 **게이트로 쓰지 않는다.** README 에 커버리지 배지를 달지 않는다.

---

## 2.5 ERD 설계

- **[필수]** 테이블/컬럼명은 `snake_case`
- **[필수]** PK 는 `{테이블명}_id` 형태로 통일 (예: `member_id`, `class_room_id`)
- **[권장]** 공통 컬럼 `created_at`, `updated_at` 은 `BaseEntity` 로 분리
- **[권장]** 삭제는 소프트 삭제(`deleted_at`)를 기본으로 하고, 정책을 도메인마다 통일한다
- ERD 변경 시 팀 채널에 공유 후 반영한다

---

## 2.6 DB / 환경 설정 [필수]

> 배포 환경의 계정을 어떻게 관리할지 한동안 미뤄뒀던 항목이다.
> **프로파일 파일 분리 + `.gitignore` + `.example` 템플릿** 으로 정리했다.
> 시크릿은 커밋하지 않되, **무엇을 채워야 하는지는 저장소만 보고 알 수 있어야** 한다.

### 설정 파일 구조

| 파일 | 내용 | 커밋 |
|---|---|---|
| `application.yml` | 공통 (포트, JPA, 로깅) | **O** |
| `application-test.yml` | H2 인메모리, 더미값 | **O** |
| `application-local.yml.example` | 로컬 설정 템플릿 (키만, 값은 빈 문자열) | **O** |
| `application-local.yml` | 로컬 시크릿 | **X** |
| `application-prod.yml` | 배포 시크릿 | **X** |

**규칙**

1. **시크릿이 들어가는 파일만 `.gitignore` 에 등록한다.** 공통 설정과 템플릿은 커밋한다.
2. 새 설정 키를 추가하면 **`.example` 에도 반드시 추가한다.**
3. `git clone` 직후 **추가 설정 없이 `./gradlew test` 가 통과해야 한다.**
4. `.gitignore` 에 `*.properties` 같은 광범위 패턴을 쓰지 않는다.
   설정 구조 자체가 사라져 저장소를 받은 사람이 아무것도 실행할 수 없게 된다.
   실제로 이 패턴 때문에 **clone 직후 테스트 109건이 실패**한 적이 있다. (#6)

### 로컬 DB 계정 [필수]

로컬 개발 환경 계정은 `application-local.yml` 에 둔다. **저장소에 커밋하지 않는다.**

---

## 2.7 API 문서화 — Swagger (springdoc-openapi) [필수]

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'
```

접속 경로: `http://localhost:8080/swagger-ui/index.html`

**작성 규칙**

- 모든 Controller 에 `@Tag` 를 붙인다.
- 모든 public 엔드포인트에 `@Operation(summary = ...)` 를 붙인다.
- 문서에 노출하지 않을 API 는 `@Hidden` 처리한다.

### Springfox → Springdoc 어노테이션 대응표

| Springfox (구) | Springdoc (신) |
|---|---|
| `@Api` | `@Tag` |
| `@ApiIgnore` | `@Parameter(hidden = true)` / `@Operation(hidden = true)` / `@Hidden` |
| `@ApiImplicitParam` | `@Parameter` |
| `@ApiImplicitParams` | `@Parameters` |
| `@ApiModel` | `@Schema` |
| `@ApiModelProperty` | `@Schema` |
| `@ApiModelProperty(hidden = true)` | `@Schema(accessMode = READ_ONLY)` |
| `@ApiOperation(value = "foo", notes = "bar")` | `@Operation(summary = "foo", description = "bar")` |
| `@ApiParam` | `@Parameter` |
| `@ApiResponse(code = 404, message = "foo")` | `@ApiResponse(responseCode = "404", description = "foo")` |

---

## 2.8 예외 처리 [필수]

> 예외를 어디서 잡을지 정해두지 않으면 **Controller 마다 `try-catch` 가 흩어진다.**
> 그러면 같은 상황에 서로 다른 상태 코드가 나가고, 그걸 나중에 맞추는 일이 훨씬 비싸다.

- **도메인 예외를 정의하고 전역 핸들러에서 HTTP 상태코드로 매핑한다.**
  `@RestControllerAdvice` 한 곳에서 처리하고 Controller 에서 `try-catch` 하지 않는다.
- **에러 응답 형식을 하나로 통일한다.** `{ code, message, traceId }`
- **예외를 삼키지 않는다.** `catch (Exception e) {}` 금지.
- 재던질 때 스택트레이스를 잃지 않는다.
- **DB 스키마·쿼리·스택트레이스를 응답에 노출하지 않는다.**
- **트랜잭션 경계와 예외 경계를 일치시킨다.**
  Service 메서드가 트랜잭션 단위이며, 예외 발생 시 롤백 후 상위로 전파한다.

---

# Part 3. 프론트엔드 (Vue)

## 3.1 네이밍 [필수]

| 대상 | 규칙 | 예시 |
|---|---|---|
| **컴포넌트** 파일명 | PascalCase | `UserAge.vue` |
| **composable** 파일명 | camelCase, `use` 접두 | `useUserAge.js` |
| **store** 파일명 | camelCase, `Store` 접미 | `userStore.js` |
| **유틸** 파일명 | camelCase | `formatDate.js` |
| 변수 / 함수 | camelCase | `userAge`, `fetchUserAge` |
| CSS 클래스 | **kebab-case** | `user-age` |
| 상수 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |

> **컴포넌트만 PascalCase, 나머지는 camelCase.**
> Vue 공식 스타일 가이드가 그렇고, 무엇보다 **파일명만 봐도 컴포넌트인지 아닌지 구분**된다.

> CSS 클래스는 **케밥 케이스**(`user-age`)다.
> 한때 문서에 "스네이크" 라고 적혀 있었는데 정작 예시는 케밥이었다. 예시가 맞다 —
> 스네이크는 `user_age` 이고 CSS 관례가 아니다.

---

## 3.2 코드 스타일 [필수]

### 중괄호 안쪽 양쪽 공백

```js
// Bad
import {ref, computed} from "vue";

// Good
import { ref, computed } from "vue";
```

### 함수는 화살표 함수, 매개변수 괄호는 항상 표기

```js
// Bad
function fetchUser() {}
const toName = user => user.name;

// Good
const fetchUser = () => {};
const toName = (user) => user.name;
```

### 문자열은 큰따옴표

```js
// Bad
const message = 'hello';

// Good
const message = "hello";
```

> 문자열에 큰따옴표가 포함되거나 변수를 끼워 넣을 때는 백틱을 쓴다.

### 변수 선언은 `const`

```js
// Bad
let count = 0;
count += 1;

// Good
const count = ref(0);
count.value += 1;
```

- 값이 변해야 하면 `let` 이 아니라 `ref` / `reactive` 로 만든 **반응형 변수**로 선언한다.
- `var` 사용 금지.

### ESLint / Prettier [필수]

> 이건 [권장] 이었다가 **[필수] 로 올렸다.**
> 위 규칙들은 사람이 외워서 지킬 항목이 아니라 **도구가 강제할 항목**이다.
>
> 리뷰에서 세미콜론 이야기를 하고 있으면 설계 이야기를 할 시간이 없다.

```json
{
  "rules": {
    "object-curly-spacing": ["error", "always"],
    "arrow-parens": ["error", "always"],
    "quotes": ["error", "double"],
    "prefer-const": "error",
    "no-var": "error",
    "func-style": ["error", "expression"]
  }
}
```

---

## 3.3 Vue 규칙 [필수]

### 디렉티브는 약어 사용

| 원형 | 약어 |
|---|---|
| `v-bind:src` | `:src` |
| `v-on:click` | `@click` |
| `v-slot:header` | `#header` |

```vue
<!-- Bad -->
<img v-bind:src="imageUrl" v-on:click="handleClick" />

<!-- Good -->
<img :src="imageUrl" @click="handleClick" />
```

### props 지향, emit 지양

- 데이터는 **부모 → 자식 단방향(props)** 흐름을 기본으로 한다.
- `emit` 은 꼭 필요한 사용자 액션 전달에만 쓴다.
- 여러 컴포넌트가 공유하는 상태는 emit 으로 올리지 말고 **Store 로 뺀다.**

### Store 는 기능별로 분리

```
stores/
├── userStore.js
├── classroomStore.js
└── authStore.js
```

- 하나의 거대한 store 금지. 도메인/기능 단위로 나눈다.
- store 이름은 `use{도메인}Store` 로 통일한다. (예: `useUserStore`)

### 스타일

```vue
<style scoped lang="scss">
/* Bad — 3단 중첩 */
.user-card {
  .user-info {
    .user-name { color: red; }
  }
}

/* Good — 1단까지만 */
.user-card { padding: 16px; }
.user-card__name { color: red; }
</style>
```

- `<style>` 에는 **`scoped` 를 필수로 붙인다.**
- 셀렉터 중첩은 **1단계까지만** 허용한다.

> "scope 범위 1단" 이라고만 적혀 있어서 무슨 뜻인지 갈렸던 항목이다.
> **① `scoped` 를 반드시 붙인다 ② 셀렉터 중첩은 1단까지** — 두 가지로 나눠 적는다.

---

# 이 규칙들을 정할 때 본 것

규칙마다 "왜" 를 적었지만, 그 판단의 바탕이 된 자료는 여기 모아둔다.
**규칙과 생각이 다르면 여기부터 읽고 회고에서 이야기하면 된다.**

| | |
|---|---|
| 팀 워킹 어그리먼트 | https://www.scrum.org/resources/creating-team-working-agreement |
| 코드 리뷰 (Google) | https://google.github.io/eng-practices/review/ |
| 코드 리뷰 (한국어 요약) | https://soojin.ro/review/ |
| 코드 리뷰 문화 | https://techblog.woowahan.com/7152/ |
| 데일리 스크럼 | https://helloworld.kurly.com/blog/daily-scrum-thinking/ |
| 머지 방식 비교 | https://hudi.blog/git-merge-squash-rebase/ |
| Java 코드 컨벤션 | https://github.com/naver/hackday-conventions-java |

---

**이 문서를 고치려면** — 회고에서 이야기하고, 고치고, 팀 채널에 무엇을 왜 바꿨는지 남긴다.
변경 이력은 git 이 갖고 있으니 문서 안에 표를 따로 두지 않는다.
