# DB 선택 기록 — 왜 MySQL 을 유지했나

> 작성 2026-08-25 · #125  
> 결론: **EduMeet 은 MySQL 을 유지한다.** PostgreSQL 이 나빠서가 아니라,
> 이 프로젝트에서 보여줄 값이 "DB 교체"가 아니라 **이미 운영 중인 RDBMS 위에서
> 병목과 동시성을 재현하고 고치는 능력**이기 때문이다.

## 0. 한 줄 결론

> **새로 만드는 iMBC 맞춤 프로젝트라면 MS-SQL 을 고른다.**
> 하지만 EduMeet 은 이미 MySQL 로 만든 팀 산출물을 운영 가능한 상태로 정리하는
> 프로젝트다. 여기서 PostgreSQL 로 바꾸면, 그동안 잰 N+1·락·Flyway·k6 수치가
> 전부 새 전제로 바뀐다.

---

## 1. 정상 상태

EduMeet 의 핵심 데이터는 전형적인 웹 서비스형 RDBMS 데이터다.

| 도메인 | 관계 |
|---|---|
| 회원·클래스 | `member` ↔ `class_member` ↔ `class_room` |
| 수업 세션 | `meeting` ↔ `meeting_participant` |
| 과제 | `homework` ↔ `homework_submission` ↔ `attachment` |
| 다시보기 채팅 | `chat_message(meeting_id, offset_millis)` 범위 조회 |

쓰기보다 읽기가 많고, 트랜잭션 경계가 분명하며, JSON/배열/문서형 쿼리는 핵심이 아니다.
그래서 MongoDB 같은 문서 DB 로 갈 이유는 없다. 쿼리 패턴은 관계형이 자연스럽다.

## 2. 후보

| 후보 | 장점 | 이 프로젝트에서의 판단 |
|---|---|---|
| MySQL 8 | 팀 프로젝트 원 스택, InnoDB 락 재현, Testcontainers 검증, OCI 운영과 동일 | **유지** |
| PostgreSQL | 표준 SQL·표현력·확장 기능이 강함. `jsonb`, 파티셔닝, 확장 생태계 우수 | 지금 요구사항에서는 전환 이득이 작음 |
| MS-SQL | iMBC 온프레미스 C#.NET 과 가장 잘 맞는 후보 | 새 포트폴리오라면 후보 1순위. EduMeet 교체 대상은 아님 |
| MongoDB | 문서 구조가 자주 바뀌거나 집계 단위가 문서 중심일 때 유리 | 현재는 관계와 제약이 핵심이라 부적합 |

## 3. 왜 PostgreSQL 로 바꾸지 않았나

PostgreSQL 은 좋은 DB 다. 하지만 이 프로젝트에서 바꾸면 **문제 해결이 아니라
스택 교체가 주제가 된다.**

현재 포트폴리오의 핵심 수치는 전부 MySQL 위에서 나온다.

| 항목 | MySQL 을 유지해야 하는 이유 |
|---|---|
| N+1 개선 | H2 가 아니라 MySQL 8 + k6 로 쿼리 수와 처리량을 같이 쟀다 |
| 세션 정원 동시성 | InnoDB 행 잠금과 트랜잭션 격리 동작을 기준으로 재현했다 |
| Flyway baseline | `engine=InnoDB`, `enum(...)`, FK 제약을 실제 MySQL 로 검증했다 |
| Testcontainers | 테스트 DB 와 운영 DB 의 차이를 줄이는 것이 목적이다 |
| 다시보기 채팅 | `meeting_id + offset_millis` 범위 조회 인덱스가 핵심이다 |

PostgreSQL 로 바꾸면 "왜 MySQL 이었나"라는 질문은 사라지지만,
대신 "왜 지금 DB 를 갈아엎었나", "측정값이 같은가", "운영 마이그레이션은 어떻게 하나"가 생긴다.
신입 포트폴리오에서는 이 질문이 오히려 본질을 흐린다.

## 4. MySQL 을 고른 것이 아니라 유지한 것이다

중요한 표현은 이것이다.

> **처음부터 DB 를 고르는 상황이 아니었다.**
> 이미 MySQL 로 만든 팀 산출물을 인수했고, 목표는 스택 교체가 아니라 운영 경로
> 정리였다. 그래서 MySQL 을 유지한 채 병목을 재현하고, H2 테스트를
> Testcontainers(MySQL)로 바꾸고, Flyway baseline 을 실제 MySQL 문법으로 검증했다.

이 문장이 있어야 "왜 PostgreSQL 이 아니냐"는 질문이 공격이 아니라 설명 가능한
트레이드오프가 된다.

## 5. iMBC 공고와의 연결

iMBC 공고는 RDBMS 를 `MySQL, MS-SQL, Oracle 등`으로 열어 둔다.
따라서 MySQL 자체가 미스매치는 아니다.

다만 iMBC 플랫폼개발팀은 C#.NET 온프레미스 중심이므로 새 프로젝트를 만든다면
MS-SQL 이 더 직접적인 선택이다. EduMeet 에서는 **MySQL 로 기본기를 보이고**,
면접에서는 다음처럼 연결하는 편이 낫다.

| 면접 질문 | 답변 방향 |
|---|---|
| "우리 쪽은 MS-SQL 인데요?" | "DB 제품보다 먼저 트랜잭션·잠금·실행계획·마이그레이션을 봅니다. EduMeet 에서는 MySQL 로 재현했고, iMBC 용 미니 프로젝트라면 MS-SQL 로 잡겠습니다." |
| "PostgreSQL 은 왜 안 썼나요?" | "jsonb·파티셔닝·pgvector 같은 PostgreSQL 장점이 요구사항의 핵심이 아니었습니다. 이미 MySQL 로 운영 중인 프로젝트라 교체 비용이 더 컸습니다." |
| "H2 는 왜 안 쓰나요?" | "H2 는 빠르지만 MySQL 네트워크 왕복·InnoDB 락·enum/FK 문법을 재현하지 못합니다. 성능과 마이그레이션 검증에는 실제 MySQL 이 필요했습니다." |

## 6. 남겨 둔 전환 조건

MySQL 을 영원히 고정한다는 뜻은 아니다. 아래 요구가 생기면 다시 본다.

| 조건 | 후보 |
|---|---|
| 분석성 쿼리·윈도우 함수·복잡한 리포팅이 핵심이 됨 | PostgreSQL |
| JSON 문서 저장과 부분 인덱싱이 핵심이 됨 | PostgreSQL `jsonb` 또는 문서 DB |
| 회사 운영 스택과 맞춘 포팅 프로젝트 | MS-SQL |
| 자막/문서 임베딩 검색을 DB 안에 넣어야 함 | PostgreSQL `pgvector` 또는 SQL Server 2025 VECTOR |

## 7. 포트폴리오 문장

> DB 는 MySQL 을 유지했습니다. PostgreSQL 이 부족해서가 아니라, 이미 MySQL 로 만든
> 팀 산출물을 운영 가능한 상태로 바꾸는 것이 프로젝트의 목표였기 때문입니다.
> H2 로는 InnoDB 잠금, MySQL enum/FK 문법, 네트워크 왕복 비용이 드러나지 않아
> Testcontainers(MySQL)와 k6 로 재현했습니다. 새 iMBC 맞춤 프로젝트라면 MS-SQL 을
> 고르겠지만, EduMeet 에서는 스택 교체보다 측정 가능한 운영 개선이 더 중요했습니다.
