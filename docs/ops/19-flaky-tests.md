# 19. 가끔 빨간 시험을 없앤다 (#172)

브랜치 보호를 걸자마자(#169) 머지가 무작위로 막히기 시작했다.
같은 커밋을 다시 돌리면 통과했다. **시험이 실행마다 다르게 깨지고 있었다.**

"재실행하면 되는 것" 으로 두면 안 되는 이유는 하나다 —
**사람이 빨간 것을 보고 먼저 재실행을 누르게 된다.** 그러면 진짜 실패도 같이 묻힌다.
보호를 건 시점에서 이건 더 이상 불편이 아니라 통과 여부를 결정하는 문제가 됐다.

---

## 측정

로컬에서 전체 시험을 5회씩 돌려 셌다.
도커가 필요한 시험 5개(Flyway·readiness·refresh token·오디오 마이그레이션)는 이 기계에 도커가
없어 제외하고 센다. 제외 목록을 코드에 박아두고 세므로, 제외한 것이 무엇인지가 결과에 남는다.

| | 실패(도커 제외) |
|---|---|
| 고치기 전 | 0 / 0 / 2 / 0 / 2 |
| 원인 두 개 고친 뒤 | 0 / 0 / 0 / 0 / 0 / 0 (6회) |
| 남은 지뢰까지 좁힌 뒤 | 0 / 0 / 0 / 0 / 0 / 0 (6회) |

---

## 원인 1 — 컨텍스트가 여럿인데 데이터베이스가 하나였다

시험 프로파일이 이름을 고정한 인메모리 DB 를 썼다(`jdbc:h2:mem:edumeet`).
이 저장소에는 설정이 다른 시험이 14개라 스프링 컨텍스트가 여러 벌 뜬다.
전부 같은 물리 DB 를 잡았다.

거기에 `ddl-auto: create-drop` 이 겹치면 이렇게 된다.

```
컨텍스트 A 가 스키마를 만들고 class_room_seq 에서 1~50 을 미리 받아 둔다
컨텍스트 B 가 뜨면서 create-drop 이 그 시퀀스를 다시 만든다
B 도 1~50 을 받는다
둘이 같은 id 로 INSERT       ->  PRIMARY KEY 위반
```

증상이 고약하다. **시험 코드에는 아무 문제가 없고**, 어느 시험이 깨지는지가 실행 순서에 따라 바뀐다.

컨텍스트마다 다른 DB 를 주는 것으로 끝난다.

```yaml
url: jdbc:h2:mem:edumeet-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1
```

`${random.uuid}` 는 컨텍스트마다 한 번 풀린다. 컨텍스트가 캐시되면 값이 유지되므로
한 컨텍스트 안의 시험들은 지금처럼 데이터를 공유한다. 격리 단위가 컨텍스트로 맞춰진다.

---

## 원인 2 — `@AfterEach` 안에 있어서 지역적으로 보이던 전역 삭제

정원 동시성 시험이 정리에서 표를 통째로 비우고 있었다.

```java
participantRepository.deleteAll();
meetingRepository.deleteAll();
classRepository.deleteAll();
memberRepository.deleteAll();
```

`@AfterEach` 안에 있으니 이 시험 안의 일처럼 읽힌다. 그런데 컨텍스트는 시험 클래스끼리
공유되므로 **다른 시험이 쓰는 중인 행까지 지운다.**

깨지는 모양은 이랬다.

```
Referential integrity constraint violation:
  CLASS_MEMBER FOREIGN KEY(CLASS_ROOM_ID) REFERENCES CLASS_ROOM
  delete from class_room where class_room_id=7
```

이 시험은 수강생을 만들지 않는다. `joinSession` 은 `class_member` 에 손대지 않는다.
7번 강의실도 이 시험 것이 아니다. **남이 만든 강의실을 지우려다 남이 만든 수강생에 걸린 것이다.**
그 순간 다른 시험이 그런 강의실을 남겨 뒀는지에 따라 갈리니 실행마다 결과가 다르다.

자기가 만든 id 로 범위를 좁혔다.

```java
em.createQuery("DELETE FROM Meeting m WHERE m.id IN :ids")
        .setParameter("ids", meetingIds).executeUpdate();
```

### 빗나간 진단 하나

처음엔 비동기 저장 큐를 의심했다. 채팅은 큐에 넣고 배치가 1초마다 가져가므로(#61),
배치가 삭제와 삭제 사이에 끼면 방금 지운 회의를 참조하는 행이 생길 수 있다 —
말이 되는 설명이었고, 첫 오류가 `CHAT_MESSAGE` 외래키였다.

그래서 시험 프로파일에서 배치 주기를 1시간으로 늘려 사실상 껐다. 그랬더니
**비동기 저장을 검증하는 시험 4개가 전부 깨졌다.** 그 시험들은 `flush()` 를 직접 부르지 않고
배치를 기다린다. 끄면 검증 대상 자체가 사라진다.

오류 메시지가 `CHAT_MESSAGE` 에서 `CLASS_MEMBER` 로 바뀐 것이 신호였는데,
둘 다 "외래키 위반" 이라 같은 원인으로 읽었다. **증상의 분류가 같다고 원인이 같지 않다.**

---

## 다시 들어오지 못하게 막는다

원인 2는 코드를 읽어서는 잘 안 보인다. 규칙으로 고정했다 —
`TestCleanupScopeTest` 가 시험 소스를 훑어 **여러 시험이 함께 쓰는 표**를
조건 없이 비우는 곳을 찾는다. 대상은 회의·참가자·채팅·자막·강의실·수강생·회원이다.
게시판이나 댓글처럼 그 시험들만 쓰는 표는 통째로 비워도 남에게 영향이 없어 뺐다.
목록을 늘리는 기준은 "다른 시험도 이 표에 행을 만드는가" 다.

규칙을 넣을 때 **위반을 심어 실제로 빨개지는지 먼저 확인했다.**
같은 파일에 시험을 하나 더 넣었다가 JUnit 이 발견하지 못해 조용히 초록이었던 적이 있어서다(#170).
안 잡히는 규칙은 없는 것과 같고, 있다고 믿는 만큼 더 나쁘다.

규칙 자체가 잡는지도 시험으로 고정했다.

```java
assertThat(BULK.matcher("\"DELETE FROM Meeting\"").find()).isTrue();
assertThat(BULK.matcher("\"DELETE FROM Meeting m WHERE m.id = :id\"").find()).isFalse();
```

이 시험이 잡아낸 남은 지뢰가 두 곳 더 있었다 — 채팅 STOMP 시험과 자막 재접속 시험.
둘 다 아직 깨지지는 않았지만 조건 없이 회의를 지우고 있었다.

---

## 곁가지 — 설정 파일을 "파싱되는지" 로 확인하면 안 된다

고치는 중에 YAML 한 곳을 잘못 넣어 시험 249개를 깼다.
`edumeet:` 를 `spring:` 블록 한가운데 0열에 넣었더니, 뒤따르던 `mail:` · `cloud:` · `security:` 가
전부 `spring` 이 아니라 `edumeet` 의 자식이 됐다. 메일·S3·카카오 설정이 통째로 사라진 것이다.

**문법 오류가 아니라서 파서는 아무 말도 하지 않는다.** 같은 문서에 중복 키가 생긴 것도
조용히 뒤엣것으로 덮인다. "YAML 이 파싱되는지" 를 확인한 것으로는 못 잡는다.

그래서 확인을 바꿨다 — 파싱 여부가 아니라 **어떤 키가 생겼는지**를 찍어 본다.

```
spring.mail.username                         test@example.com
spring.cloud.aws.s3.bucket                   test-bucket
edumeet.chat.archive.flush-interval-ms       3600000
edumeet 자식: ['caption','chat','email','internal','upload']
```
