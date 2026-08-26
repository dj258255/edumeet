# MCP 서버 — 계약의 세 번째 소비자

> **#133.** 저장된 강의 자막을 MCP 클라이언트에서 찾고 읽는다.
> 코드는 [`mcp-server/`](../../mcp-server/README.md).

---

## 왜 만들었나 — 그리고 왜 이 모양인가

MCP 서버를 붙이는 방법은 두 가지였다.

```
(A)  MCP  ──►  caption_segment  (DB 직접)
(B)  MCP  ──►  Java 내부 API  ──►  caption_segment
```

(A) 가 빠르고 의존성도 적다. 그런데 `CaptionSegmentRepository` 의 정렬 규칙이
단순하지 않다.

```java
ORDER BY
  CASE WHEN c.sequence IS NULL THEN 1 ELSE 0 END,   -- sequence 없는 조각은 뒤로
  c.sequence ASC, c.spokenAt ASC, c.id ASC
```

그리고 그 JavaDoc 이 직접 경고한다.

> **"여기서 순서가 흔들리면 요약 결과도 흔들린다."**

(A) 를 고르면 이 규칙을 파이썬에서 한 번 더 구현해야 한다.
**두 구현은 반드시 갈라진다.** 갈라지는 순간 어느 쪽이 맞는지 알 방법도 없다 —
둘 다 "자막을 순서대로 준다" 고 주장하고, 둘 다 테스트가 통과한다.

그래서 (B) 다. 이미 있는 내부 API 를 경유한다.

## 그래서 계약 소비자가 셋이 됐다

```
contracts/internal-api.json
   ├── backend/    InternalApiContractTest      노출한 경로가 계약과 같은가
   ├── ai/         test_*_contract.py           부르는 경로가 계약과 같은가
   └── mcp-server/ tests/test_contract.py       도구가 부르는 경로가 계약과 같은가
```

Java 쪽 시험은 **양방향**이다.

```java
assertThat(actualInternal)
    .as("계약에 없는 내부 엔드포인트가 있다")
    .isSubsetOf(declared);
```

계약에 안 적고 엔드포인트만 늘리는 길이 없다.
이번에 `captionMeetings` 를 추가할 때 그 시험이 먼저 물어봤다.

## 새로 만든 엔드포인트 하나

`list_caption_meetings` 를 하려면 목록이 필요했다. 없으면 도구를 쓰는 쪽이
`meetingId` 를 **이미 알고 있어야** 하고, 그러면 도구가 아니라 조회 API 다.

```
GET /api/v1/internal/meetings/captions?limit=50
```

설계 판단 세 개를 적어 둔다.

| | |
|---|---|
| **자막에서 출발한다** | `FROM CaptionSegment JOIN c.meeting` — 회의에서 출발해 거르지 않는다. 회의가 늘어도 스캔 대상은 자막이다. 자막 없는 회의는 조인에서 자연히 빠진다 |
| **정렬은 `spokenAt`** | `createdAt` 은 저장 시각이다. 자막은 **비동기 저장**이라 배치가 밀리면 순서가 뒤집힌다. 사람이 기대하는 순서는 "언제 말했나" 다 |
| **`limit` 상한 200** | 응답이 그대로 모델 컨텍스트로 들어간다. 그리고 `returned` 를 같이 준다 — 잘렸는지 부르는 쪽이 알아야 한다 |

응답을 배열이 아니라 객체로 감싼 것도 같은 이유다. 최상위가 배열이면
나중에 커서나 총계를 붙일 때 호환을 깨야 한다.

---

## ★ 조용히 틀리는 것 하나를 찾았다

**저장된 자막은 이미 정규화돼 있다.**

```
STT   "python 을 배웠습니다"
        │  ai/caption_normalizer.py  (hot path, LLM 없이 결정적 사전)
        ▼
저장   "파이썬 을 배웠습니다"
```

그래서 `python` 으로 검색하면 **0건**이 나온다.

**오류가 아니라 빈 결과다.** 그래서 더 나쁘다 —
*"이 회의에서 파이썬 얘기를 안 했나 보다"* 로 읽힌다.
도구가 틀렸다는 신호가 어디에도 없다.

정규화를 **저장 쪽에만** 걸어 둔 결과다. 자막을 만들 때는 맞는 판단이었다
(`ops/09-realtime-caption-cost-quality.md` — hot path 에서 LLM 을 안 부른다).
읽는 쪽이 생기면서 비대칭이 드러났다.

### 사전을 어디에 두는가

세 가지를 놓고 봤다.

| | 판단 |
|---|---|
| MCP 에 사전을 복사 | **기각.** CLAUDE.md 가 이미 적어 뒀다 — *"손으로 베끼면 한쪽만 고쳐도 시험이 통과해 버린다"* |
| `contracts/caption-glossary.json` 신설 | **기각.** `ai/` 는 도커 빌드 컨텍스트가 `./ai` 라 `contracts/` 를 런타임에 못 읽는다. 루트를 컨텍스트로 주면 프론트·ai 의 수백 MB 가 도커 데몬으로 간다 — [`ops/01`](01-cicd-and-deploy.md) 에서 이미 거부한 선택이다 |
| `ai/caption_normalizer.py` 를 import | **채택** |

세 번째가 되는 이유는 **MCP stdio 서버가 컨테이너가 아니기 때문**이다.
MCP 클라이언트 옆에서, 저장소 체크아웃 위에서 돈다. 배포 단위가 없으니
빌드 컨텍스트 문제도 없다. 원본이 하나로 유지된다.

> **배포 형태가 설계를 정한 경우다.** 같은 문제라도 `ai/` 였다면 3번을 못 골랐다.

### 그리고 원문도 같이 찾는다

정규화형만 쓰면 반대 방향으로 놓친다 — 사전이 못 바꾼 영어 표기가 자막에 남아 있으면
그것이 안 걸린다. 그래서 질의 원문과 정규화형을 **둘 다** 쓰고,
결과에 **무엇으로 걸렸는지** 적는다.

빈 결과에도 찾은 말을 같이 적는다.

```
'쿠버네티스' — 찾은 말: 쿠버네티스 / 전체 42조각

걸린 조각이 없다.
```

사전이 질의를 바꿨는데 그 사실을 안 알려 주면, 0건이 다시 "안 했다" 로 읽힌다.

---

## 도구를 세 개로 나눈 이유

| | |
|---|---|
| `list_caption_meetings` | 고른다 |
| `search_transcript` | 찾는다 — 조각 단위 + 앞뒤 문맥 |
| `get_transcript` | 통째로 읽는다 |

`get_transcript` 하나만 두면 모델이 자막 전체를 컨텍스트에 넣는다.
한 시간짜리 강의면 그것만으로 예산이 끝난다.

문맥을 **글자 수가 아니라 조각 수**로 자른다. transcript 의 한 줄이 자막 조각
하나이기 때문이다(Java 가 `\n` 으로 이어 붙인다). 그래서 줄 번호가 곧 조각 번호이고,
앞뒤 줄이 곧 앞뒤 발화다.

---

## 시험은 세 갈래다

| | |
|---|---|
| `test_contract.py` | 경로·헤더가 계약과 같은가 |
| `test_transcript_search.py` | 무엇을 돌려주는가 — 순수 함수만 |
| `test_server_tools.py` | **도구가 실제로 등록되었는가** |

마지막 것이 이 저장소의 반복된 함정에 대한 대응이다
([`ops/07-declared-but-unused.md`](07-declared-but-unused.md)).

> 부품은 맞다. 그런데 연결되어 있는지는 아무도 안 물었다.

MCP 도구도 같은 모양이다 — 함수가 옳게 동작해도 `@server.tool()` 이 안 붙어
있으면 클라이언트에는 **아무것도 안 보인다.** 그래서 함수를 부르는 시험과 별개로
서버에 직접 `list_tools()` 를 물어본다.

실패도 시험한다. 도구가 예외를 던지면 클라이언트에는 "도구 실패" 로만 보인다.
설정 실수는 대부분 **서버가 아니라 MCP 클라이언트 설정**에 있으므로,
이유가 사람에게 문장으로 가야 한다.

```python
def test_backend_failure_becomes_a_sentence_not_a_traceback(...):
    assert out.startswith("실패:")
    assert "EDUMEET_API_BASE_URL" in out
```

---

## 알아둘 것

**MCP 파이썬 SDK 2.0 에서 `FastMCP` 가 `MCPServer` 로 이름이 바뀌었다.**

```
ModuleNotFoundError: No module named 'mcp.server.fastmcp'.
This is mcp 2.x, where FastMCP was renamed to MCPServer ...
```

1.x 예제가 인터넷에 훨씬 많으므로 `mcp>=2,<3` 으로 메이저를 못 박았다.

---

## 안 한 것

| | 근거 |
|---|---|
| 자막 전문 검색을 DB 로 | 지금은 회의 하나를 받아 파이썬에서 찾는다. 회의 간 검색이 필요해지면 그때 MySQL full-text 나 색인을 본다 — **아직 회의 하나 안에서 못 찾은 적이 없다** |
| 임베딩·벡터 검색 | 자막은 조각 단위 텍스트고 질의는 대부분 용어다. 의미 검색이 필요하다는 근거가 아직 없다 |
| MCP 서버 컨테이너화 | stdio 서버는 클라이언트 옆에서 돈다. 컨테이너로 만들면 **용어 사전 공유가 오히려 깨진다** |
| 쓰기 도구 | 읽기만 노출한다. 토큰 하나로 내부 API 전체가 열리므로, 도구가 쓸 수 있으면 MCP 클라이언트를 신뢰하는 범위가 갑자기 넓어진다 |
