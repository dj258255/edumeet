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
- [부록 A. 원본 컨벤션 대비 변경 사항](#부록-a-원본-컨벤션-대비-변경-사항)
- [부록 B. 참고 링크](#부록-b-참고-링크)
- [부록 C. 문서 관리](#부록-c-문서-관리)

---

# Part 1. 공통 (Git / 협업)

> 각 항목은 **[필수] / [권장] / [선택]** 으로 구분한다.
> 원본 팀 컨벤션(GitLab 기준)을 이 저장소(GitHub) 환경에 맞게 확정한 내용이다.
> 원본에서 미확정이던 항목은 **[확정]** 표시와 함께 근거를 남겼다.

## 1.1 브랜치 전략 — GitHub Flow [필수]

> **[확정]** 원본은 GitLab/MR 기준이었다. 이 저장소는 GitHub이므로
> **MR → PR**, **`main` → `master`** 로 용어와 대상을 맞춘다.
> 기본 브랜치를 `main` 으로 바꾸는 것은 기존 이력·링크에 영향을 주므로 하지 않는다.

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

> **[확정]** 원본 TODO — "브랜치 type 을 커밋 type 7종과 동일하게 갈지, 4종으로 줄일지"
> → **동일한 목록(8종)을 쓴다.** 종류를 줄이면 `perf` 작업이 `refactor` 로 섞여
> `git log --grep` 기반 이력 추적이 깨진다. 외우는 부담보다 추적 이득이 크다.

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

> **[확정]** 원본 Udacity 7종에 **`perf` 를 추가해 8종**으로 한다.
> 성능 개선 이력을 `git log --grep="^perf:"` 한 줄로 추적하기 위함이다.
> `refactor` 에 섞으면 "무엇을 얼마나 개선했는가"를 나중에 복원할 수 없다.

### Subject 규칙 [필수]

- **한국어로 작성한다.** 첫 글자 대문자 규칙은 적용하지 않는다.
- 50자 이내, 마침표 없음
- 무엇을 했는지 **명사형 또는 평서형**으로. `수정`, `update` 처럼 내용 없는 단어만 쓰지 않는다.

> **[확정]** 원본은 Udacity 원문대로 "영어 명령형·첫 글자 대문자"였다.
> 이 저장소의 기존 커밋 이력이 전부 한국어이고, 리뷰어도 한국어 사용자다.
> 영어로 바꾸면 이력의 절반이 다른 언어가 되어 오히려 일관성이 깨진다.
> **한국어로 확정하되, "무엇을 왜"를 담는다는 Udacity 의 핵심 원칙은 유지한다.**

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

> **[확정]** 원본 컨벤션에 병합 방식 규정이 없었다. 팀 논의 결과 **Squash and merge** 로 확정한다.
> GitHub 저장소 설정에서 **Squash merging 만 남기고 나머지 두 개는 비활성화**한다.
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

> **[확정]** 원본에 없던 `performance.md` 를 추가한다.
> 성능 이슈는 "재현 방법"이 아니라 **"측정 방법과 기준선"** 을 요구하므로 양식이 다르다.

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

> **[확정]** 원본에 없던 절이다. 이 저장소는 성능 개선을 주요 작업으로 다루므로 규칙을 명문화한다.

**1차 지표는 쿼리 수와 실행계획이다. 응답 시간(ms)이 아니다.**

로컬에서 측정한 시간은 다음 요인에 좌우되어 재현되지 않는다.

- JIT 워밍업 — JVM은 초기 호출이 느리다. 개선 전을 콜드, 개선 후를 웜으로 재면 아무것도 안 고쳐도 몇 배가 나온다
- 부하 생성기와 애플리케이션의 자원 경합
- OS 캐시·버퍼 풀 상태
- 컨테이너 오버헤드

반면 **쿼리 수와 실행계획은 환경과 무관하게 재현된다.**

### 시간을 함께 기록할 경우 반드시 병기할 것 [필수]

- 측정 환경 (CPU, 메모리, DB 버전)
- 데이터 규모
- 워밍업 횟수와 측정 반복 횟수
- **부하 생성기와 애플리케이션이 자원을 공유하는지 여부**
- 평균이 아니라 **p50 / p95**

### 측정 도구

- 쿼리 수: Hibernate `Statistics` (`spring.jpa.properties.hibernate.generate_statistics: true`)
- 실행계획: `EXPLAIN` (MySQL) — 개선 전/후 캡처를 PR에 첨부

### 측정 기록은 문서로 남긴다 [권장]

성능·리팩터링 작업은 결과를 `docs/` 에 남긴다.
문제 → 측정 → 시도(실패한 시도 포함) → 결과 → **한계** 순으로 쓴다.

| 위치 | 내용 |
|---|---|
| `docs/performance/` | 성능 개선 기록 + 비교 차트 |
| `docs/refactoring/` | 구조 변경 기록 + 규모 변화 차트 |
| `scripts/make_perf_chart.py` | 차트 재생성 (의존성 없이 SVG 생성) |

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
| `classroom`, `member`, `openvidu` | 동일 | ✅ 원래부터 준수 |
| `board`, `reply`, `attachment` | `application` / `domain` / `infrastructure` / `presentation` | ⬜ 미적용 |

`board` · `reply` · `attachment` 는 아직 헥사고날 계열 구조다. 순차 적용이 남아 있다.

---

### Repository 구조 [필수] — 분리하지 않는다

> **[확정]** 원본은 인터페이스와 구현체 분리를 [권장] 하되 *"팀 내에서 통일한다"* 고 열어두었다.
> `homework` 도메인에 **먼저 적용해보고 판단**하는 실험을 거쳐 **분리하지 않는 것**으로 확정했다. (#3)

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

### ⚠ 분리를 걷어낼 때 주의할 점

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

---

## 2.4 테스트

### 커버리지 [권장] — ⚠️ 원본의 [필수] 90% 에서 완화

> **[확정]** 원본은 *"소형 테스트 커버리지 90% 이상 [필수]"* 였다. 다음 이유로 **[권장]** 으로 낮춘다.
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

> **[확정]** 원본 TODO — "배포 환경 계정 관리 방식 확정"
> → **프로파일 파일 분리 + `.gitignore` + `.example` 템플릿** 으로 확정한다.

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

> **[확정]** 원본에 없던 절이다. 레이어드 아키텍처에서 예외 경계가 정해지지 않으면
> Controller 마다 `try-catch` 가 흩어진다.

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

> **[확정]** 원본 TODO — "PascalCase 를 컴포넌트에만 적용할지 전체에 적용할지"
> → **컴포넌트만 PascalCase, 나머지는 camelCase** 로 확정한다.
> Vue 공식 스타일 가이드와 생태계 관례가 그렇고, 파일명만 봐도 컴포넌트인지 아닌지 구분된다.

> **[확정]** 원본 ⚠️ — "css는 스네이크 케이스"라고 적혀 있으나 예시는 `user-age` (케밥)였다.
> → **케밥 케이스**로 확정한다. 스네이크는 `user_age` 이며 CSS 관례가 아니다.

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

> **[확정]** 원본 [권장] 에서 **[필수]** 로 올린다.
> 위 규칙은 사람이 지킬 항목이 아니라 도구가 강제할 항목이다.
> 리뷰에서 포맷팅을 지적하느라 설계 논의 시간을 잃는 것을 막는다.

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

> **[확정]** 원본 ⚠️ — "scope 범위 1단" 을
> **① `scoped` 필수 + ② 셀렉터 중첩 1단 제한** 두 가지로 해석해 확정한다.

---

# 부록 A. 원본 컨벤션 대비 변경 사항

| # | 항목 | 원본 | 확정 | 이유 |
|---|---|---|---|---|
| 1 | 플랫폼 | GitLab / MR / `main` | **GitHub / PR / `master`** | 이 저장소의 실제 환경 |
| 2 | 브랜치 이슈번호 | (미정) | **`#` 없이** `feat/12-...` | `#` 는 셸 주석 문자라 따옴표 없이 push 불가 |
| 3 | 브랜치 type 종류 | TODO (7종 vs 4종) | **커밋 type 과 동일 8종** | 종류를 줄이면 `perf` 이력 추적이 깨짐 |
| 4 | 커밋 type | Udacity 7종 | **+`perf` = 8종** | `git log --grep="^perf:"` 로 성능 이력 추적 |
| 5 | 커밋 subject 언어 | 영어 명령형·대문자 | **한국어** | 기존 이력이 전부 한국어. 섞으면 일관성이 더 깨짐 |
| 6 | **머지 방식** | 없음 | **Squash and merge (1.3)** | PR 1개 = 커밋 1개. 롤백 단위가 PR과 일치 |
| 7 | 테스트 커버리지 | **90% [필수]** | **[권장], 게이트 아님** | Fowler·Google 모범사례. 숫자는 저품질 테스트로 부풀려짐 |
| 8 | ESLint/Prettier | [권장] | **[필수]** | 포맷팅은 사람이 아니라 도구가 강제할 항목 |
| 9 | Vue 파일명 | TODO | **컴포넌트만 Pascal** | Vue 공식 스타일 가이드 관례 |
| 10 | CSS 네이밍 | "스네이크" (예시는 케밥) | **케밥** | 예시가 맞고 CSS 관례에 부합 |
| 11 | `<style scoped>` | "scope 1단" (모호) | **scoped 필수 + 중첩 1단** | 두 가지로 분해해 명문화 |
| 12 | 배포 계정 관리 | TODO | **프로파일 분리 + `.example`** | 시크릿과 설정 구조를 분리 |
| 13 | 성능 측정 원칙 | 없음 | **신설 (1.6)** | 쿼리 수·실행계획을 1차 지표로 |
| 14 | 예외 처리 | 없음 | **신설 (2.8)** | 전역 핸들러·트랜잭션 경계 명문화 |
| 15 | N+1 대응 | 없음 | **신설 (2.3)** | 컬렉션 다중 페치 조인 금지 등 |
| 16 | 성능 이슈 템플릿 | 없음 | **신설 (1.5)** | 성능 이슈는 재현이 아니라 측정을 요구 |
| 17 | **코드 리뷰 기준** | 접두사 규칙만 | **리뷰 관점 7항목 신설** (팀 규칙 3) | 무엇을 볼지 정해두지 않으면 스타일 지적만 남음 |
| 18 | **데일리 스크럼** | 없음 | **신설** (팀 규칙 1) | 진행 규칙 없이 하면 보고회가 되고 시간이 늘어짐 |
| 19 | **Repository 분리** | [권장], 팀 결정 | **분리하지 않음으로 확정 (2.2)** | `homework` 에 적용 실험 → 코드 38% 감소, 최적화 쿼리가 죽어 있었음 (#3) |
| 20 | **소프트 삭제 규칙** | 없음 | **신설 (2.3)** | Port 제거 시 `deleteById`·`findById` 의미가 조용히 바뀜 |
| 21 | **동시성 규칙** | 없음 | **신설 (2.3)** | 개수 상한은 DB 제약으로 표현 불가 |
| 22 | **검증력 확인** | 없음 | **신설 (2.4)** | 고친 것을 되돌려 실패를 확인해야 검증인지 알 수 있음 |

---

# 부록 B. 참고 링크

| 항목 | 링크 |
|---|---|
| 팀 워킹 어그리먼트 | https://www.scrum.org/resources/creating-team-working-agreement |
| 코드 리뷰 (Google) | https://google.github.io/eng-practices/review/ |
| 코드 리뷰 (한국어 요약) | https://soojin.ro/review/ |
| 코드 리뷰 문화 | https://techblog.woowahan.com/7152/ |
| 데일리 스크럼 | https://helloworld.kurly.com/blog/daily-scrum-thinking/ |
| 머지 방식 비교 | https://hudi.blog/git-merge-squash-rebase/ |
| Java 코드 컨벤션 | https://github.com/naver/hackday-conventions-java |
| 브랜치 전략 | https://inpa.tistory.com/entry/GIT-%E2%9A%A1%EF%B8%8F-github-flow-git-flow-%F0%9F%93%88-%EB%B8%8C%EB%9E%9C%EC%B9%98-%EC%A0%84%EB%9E%B5 |
| 커밋 컨벤션 | https://udacity.github.io/git-styleguide/ |
| ERD 설계 | https://sabarada.tistory.com/49 |
| 테스트 커버리지 | https://martinfowler.com/bliki/TestCoverage.html |
| Google 커버리지 모범사례 | https://edykim.com/ko/post/code-coverage-best-practices/ |
| Vue 스타일 가이드 | https://vuejs.org/style-guide/ |

---

# 부록 C. 문서 관리

- 이 문서는 **코드·협업 규칙**의 단일 기준이다. 팀 운영 방식은 [`team-rules.md`](./team-rules.md) 에 있다.
- 규칙이 현실과 어긋나면 몰래 어기지 말고 **회고 안건으로 올려 이 문서를 고친다.**

### 변경 이력

| 날짜 | 내용 | 작성자 |
|---|---|---|
| 2026-08-21 | GitHub 환경 반영 · Squash merge · 성능 측정 · 예외 처리 신설 | |
| 2026-08-21 | Repository 분리 여부 확정(분리하지 않음) · 소프트 삭제 · 동시성 · 검증력 확인 신설 | |

### 미확정 항목

| 항목 | 위치 | 상태 |
|---|---|---|
| `board` · `reply` · `attachment` 구조 통일 | 2.2 | 미착수 |
