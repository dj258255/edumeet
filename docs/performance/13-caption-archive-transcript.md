# 자막 저장과 요약 입력 — hot path 와 토큰을 같이 보호한다

> 작성 2026-08-26 · #131  
> 결론: **자막은 먼저 보여주고, final 자막만 비동기 저장한 뒤 요약 입력으로 쓴다.**

## 1. 분석

실시간 자막에는 두 요구가 동시에 있다.

- 발화 뒤 빨리 화면에 보여야 한다.
- 회의 후에는 요약·검색·다시보기에 재사용할 수 있어야 한다.

가장 단순한 저장 방식은 자막 요청을 받을 때마다 DB 에 넣고 나서 브로드캐스트하는 것이다.

```text
STT → Java → DB insert → STOMP
```

하지만 이러면 DB 지연이 곧 자막 지연이다.
자막은 접근성 hot path 이므로 저장소가 느려져도 화면 표시까지 같이 늦어지면 안 된다.

반대로 저장을 아예 하지 않으면 요약 입력을 STT 파일에만 의존한다.
사용자에게 실제로 보낸 자막과 요약에 들어간 transcript 가 갈라질 수 있다.

또 하나의 비용 문제가 있다. streaming STT 는 partial 자막을 여러 번 낸다.

```text
파
파이
파이썬
파이썬은
파이썬은 비동기
파이썬은 비동기 처리에 좋습니다.
```

이 중간 결과를 전부 요약 입력에 넣으면 같은 발화가 여러 번 들어간다.
모델 과금은 토큰 기준이므로, partial 을 저장하면 지연뿐 아니라 요약 비용도 늘어난다.

## 2. 선택지

### 1. 저장하지 않는다

가장 빠르다. 기존 구조가 이 방식이었다.

하지만 회의 후 요약과 검색 입력으로 재사용할 수 없다.
자막은 화면에 지나갔고, 데이터로는 남지 않는다.

### 2. 내부 API 요청에서 동기 저장한다

구현은 단순하다.
하지만 자막 표시가 DB 쓰기를 기다린다.

장애 주입에서 DB 나 네트워크를 늦추면 자막 지연이 그대로 늘어날 구조다.
접근성 hot path 에는 맞지 않는다.

### 3. 브로드캐스트 후 유계 큐에 넣어 배치 저장한다

이번에 선택한 방식이다.

```text
STT
  → Java caption ingest
  → STOMP broadcast
  → bounded archive queue
  → batch insert caption_segment
  → caption transcript
  → summary input
```

화면 표시는 저장을 기다리지 않는다.
큐가 차면 무한히 쌓지 않고 버린다. 이때는 `caption.archive.dropped` 지표가 오른다.

### 4. Kafka 같은 외부 큐를 둔다

재처리와 내구성은 좋아진다.
하지만 지금은 단일 노드·포트폴리오 규모다.
외부 큐를 붙이기 전에 유계 큐와 배치 저장으로 충분한지 먼저 봐야 한다.

## 3. 구현

새 테이블은 `caption_segment` 다.

| 컬럼 | 이유 |
|---|---|
| `meeting_id` | 회의별 transcript 생성 |
| `sequence` | 자막 순서와 재시도 멱등성 |
| `spoken_at` | STT 지연 측정 |
| `received_at` | Java 수신 시각 |
| `published_at` | STOMP 발행 시각 |
| `text` | 요약·다시보기 입력 |
| `final_segment` | partial 제외 |

인덱스는 두 개다.

```sql
UNIQUE KEY ux_caption_segment_meeting_sequence (meeting_id, sequence)
KEY idx_caption_segment_transcript (meeting_id, final_segment, sequence, spoken_at, id)
```

`ux_caption_segment_meeting_sequence` 는 같은 final 자막 재시도가 transcript 에 두 번 들어가는 것을 막는다.
`idx_caption_segment_transcript` 는 회의별 final 자막을 sequence 순서로 읽는 조회에 맞춘다.

## 4. 전후

| 항목 | 전 | 후 |
|---|---|---|
| 자막 화면 표시 | STOMP 브로드캐스트 | STOMP 브로드캐스트 |
| 자막 저장 | 없음 | final 자막만 비동기 저장 |
| 요청 경로 DB 쓰기 | 없음 | 없음. 큐에만 넣음 |
| 요약 입력 | Python 로컬 `transcript.txt` | Java caption archive 우선, 실패 시 local fallback |
| partial 자막 | 없음 | 화면용. 저장·요약 제외 |
| 중복 final sequence | 판단 없음 | 저장 전 제거 + DB unique key |

이 변화의 핵심은 "저장 기능을 추가했다"가 아니다.
저장 요구를 넣으면서도 **자막 표시 경로에 DB 쓰기를 넣지 않은 것**이다.

## 5. 검증

실행한 테스트:

```bash
./gradlew test --tests com.edu.edumeet.integration.meeting.CaptionBroadcastTest \
  --tests com.edu.edumeet.integration.contract.InternalApiContractTest

./gradlew test --tests com.edu.edumeet.integration.migration.FlywayMigrationTest

uv run --with-requirements ai/requirements.txt --with responses \
  pytest -q ai/tests/test_caption_ingest_contract.py

TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test

uv run --with-requirements ai/requirements.txt --with responses \
  pytest -q ai/tests

npm test -- --run
```

확인한 것:

| 검증 | 결과 |
|---|---|
| final 자막 요청 반환 직후 | DB row 0, queue 1 |
| queue flush 후 | `caption_segment` row 1 |
| partial 자막 | queue 0, DB row 0 |
| sequence 2 → 1 → 1 재시도 | transcript 는 `1 → 2`, segmentCount 2 |
| 공유 계약 파일 변경 | Java 계약 테스트 + Python 계약 테스트가 동시에 반응 |
| MySQL 마이그레이션 | V9 테이블·컬럼·인덱스 실제 MySQL 8 Testcontainers 통과 |
| 전체 회귀 | 백엔드 317건, 파이썬 30건, 프론트 28건 통과 |

이건 지연 성능 수치가 아니다.
이번 단계에서 잰 것은 **요청 경로에 DB 쓰기가 들어오지 않았는가**와
**요약 입력이 final 자막만 쓰는가**다.
caption p95 와 dropped 상한은 streaming STT 가 붙은 뒤 같은 방식으로 재야 한다.

## 6. 포트폴리오 문장

> 실시간 자막을 요약 입력으로 재사용하려면 저장이 필요하지만,
> 자막 표시 경로에 DB 쓰기를 넣으면 접근성 기능이 저장소 지연에 묶인다고 봤습니다.
> 그래서 Java 는 자막을 먼저 STOMP 로 브로드캐스트하고, `finalSegment=true` 인 조각만
> 유계 큐에 넣어 배치 저장하도록 바꿨습니다. streaming STT 의 partial 자막은 화면에만 보여주고
> 저장·요약에서는 제외했습니다. 같은 발화의 중간 결과를 모두 요약에 넣으면 토큰 비용과
> 결과 흔들림이 같이 커지기 때문입니다. 회의 후 요약은 저장된 final 자막 transcript 를 우선 쓰고,
> 저장 배치가 아직 끝나지 않았으면 로컬 STT transcript 로 되돌아가도록 했습니다.
