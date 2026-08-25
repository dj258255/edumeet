# 서버 간 API 규약 — 파이썬 AI 서버 ↔ Spring

> 작성 2026-08-22 · #27

## 왜 별도 경로인가

파이썬 AI 서버는 **사용자 JWT 를 가질 수 없다.** 사람이 로그인해서 부르는 게 아니다.

#23 이전에는 `/api/v1/**` 이 permitAll 이라 이 경로가 **인증 없이 열려 있었다.**
누구나 임의 클래스에 요약본을 덮어쓸 수 있었다.
`anyRequest().authenticated()` 를 살리자 파이썬 호출이 **401 이 됐다** — 버그가 드러난 것이다.

그래서 **경로를 분리**했다. 사용자 인증 규칙과 서비스 인증 규칙이 한 목록에 섞이면
어느 쪽이 적용되는지 읽어서 알 수 없다.

```
/api/v1/**            사용자 JWT
/api/v1/internal/**   X-Internal-Token  (공유 시크릿)
```

## 요약본 업로드

```http
POST /api/v1/internal/meetings/{meetingId}/summary
X-Internal-Token: <공유 시크릿>
Content-Type: multipart/form-data

summary_md  : (선택) .md
summary_pdf : (선택) .pdf
```

**둘 중 최소 하나**는 있어야 한다. **빈 파일은 없는 것으로 본다.**

| 응답 | 뜻 |
|---|---|
| `201 Created` | 이번 호출로 기록됐다 |
| `200 OK` | 이미 있어서 아무것도 바꾸지 않았다 (재시도로 간주) |
| `400` | 파일 없음 / 형식·크기 오류 / 없는 `meetingId` |
| `401` | 토큰 불일치 |

```json
{
  "meetingId": 12,
  "classId": 3,
  "markdownUrl": "https://.../summary_20260822_140000.md",
  "pdfUrl": "https://.../summary_20260822_140000.pdf",
  "alreadyExisted": false
}
```

## 실시간 자막 (#65)

```http
POST /api/v1/internal/meetings/{meetingId}/captions
X-Internal-Token: <공유 시크릿>
Content-Type: application/json

{ "text": "안녕하세요", "sequence": 42, "spokenAt": 1756000000000, "finalSegment": true }
```

| 필드 | |
|---|---|
| `text` | 인식된 문장. 최대 500자 |
| `sequence` | 발화 순서. **클라이언트가 네트워크 재정렬을 감지하려면 필요하다** |
| `spokenAt` | **원본 오디오에서의 발화 시각**(epoch millis). 서버 수신 시각이 아니다 |
| `finalSegment` | 저장·요약에 쓸 수 있는 최종 자막인가. 생략하면 `true` 로 본다 |

시청자는 `/topic/rooms/{meetingId}/captions` 를 구독한다.
**채팅(`/topic/rooms/{meetingId}`)과 목적지가 다르다** — 클라이언트가 자막만 켜거나 끌 수 있어야 한다.

### 응답에 시각이 세 개 담긴다

```json
{ "spokenAt": ..., "receivedAt": ..., "publishedAt": ... }
```

```
spokenAt ──STT 추론──> receivedAt ──자바 처리──> publishedAt ──전송──> 수신
         └─ STT 지연 ─┘          └─ 홉 비용 ─┘  └─ 전달 지연 ─┘
```

**어디서 시간이 가는지 나눠서 재기 위해서다.** 하나로 합치면
*"자막이 느리다"* 는 나오는데 **무엇을 고쳐야 하는지는 안 나온다.**

### 던지고 잊는다

응답은 **측정용**이다. **파이썬은 기다리지 않아야 한다** —
기다리면 STT 루프가 전송에 묶인다.

### LLM 으로 자막을 고치지 않는다

기술 용어 표기 보정은 `ai/caption_normalizer.py` 의 결정적 사전으로 먼저 처리한다.
예를 들어 `python → 파이썬`, `websocket → WebSocket` 처럼 모델 호출 없이 고칠 수
있는 것만 hot path 에 둔다.

LLM 호출은 회의 후 요약·검색 색인·자막 정리처럼 지연을 허용하는 경로로 보낸다.
실시간 자막에서 모델 호출을 매 조각마다 넣으면 토큰 비용보다 지연·장애점·결과
일관성이 더 큰 문제가 된다.

→ [실시간 자막 비용·지연·품질 제약](09-realtime-caption-cost-quality.md)

### 저장은 발행 경로 밖에서 한다

처음에는 자막을 저장하지 않았다. 그러면 화면에는 보이지만 회의 후 요약·검색 입력으로
재사용할 수 없다.

그렇다고 내부 API 요청에서 바로 DB 에 쓰면 자막 표시가 DB 지연을 기다린다.
그래서 Java 는 먼저 STOMP 로 브로드캐스트하고, `finalSegment=true` 인 자막만
유계 큐에 넣어 배치 저장한다(#131). `partial` 자막은 계속 바뀌므로 저장하지 않는다.
요약 입력에 중간 결과를 넣으면 같은 말이 반복되어 토큰을 낭비한다.

## 자막 transcript 조회 (#131)

```http
GET /api/v1/internal/meetings/{meetingId}/captions/transcript
X-Internal-Token: <공유 시크릿>
```

저장된 final 자막을 `sequence` 순서로 이어 회의 후 요약 입력을 만든다.

```json
{
  "meetingId": 12,
  "segmentCount": 2,
  "text": "첫 번째 문장\n두 번째 문장",
  "generatedAt": 1756000001000
}
```

Python 은 이 경로를 우선 시도하고, 아직 배치 저장이 끝나지 않았거나 조회에 실패하면
STT 가 만든 로컬 `transcript.txt` 로 되돌아간다. 저장 지연 때문에 요약 전체를
실패시키면 접근성 경로와 학습 보조 경로가 다시 묶인다.

---

## 파이썬이 바꿔야 하는 것

| 이전 | 이후 |
|---|---|
| `POST /api/v1/meeting/summary/{classId}` | `POST /api/v1/internal/meetings/{meetingId}/summary` |
| 인증 없음 | `X-Internal-Token` 헤더 필수 |
| `class_id`, `meeting_id` 폼 필드 | **`meetingId` 는 경로에 필수.** `class_id` 는 보내지 않는다 |
| `meeting_id` 생략 가능 | **생략 불가** |
| 자막 전송 없음 | `captionIngest` 로 final 자막을 보내고, `captionTranscript` 로 요약 입력을 읽는다 |

### `meeting_id` 를 필수로 바꾼 이유

이전 구현은 `meeting_id` 가 없으면
1. 해당 클래스의 **최신 회의에 덮어쓰거나**
2. 회의가 없으면 **"AI 요약 미팅" 을 새로 생성**했다

열린 적 없는 회의가 DB 에 생기고, 엉뚱한 회의에 요약본이 붙었다.
**요약본은 이미 끝난 회의의 산출물**이므로 회의를 만들 이유가 없다.

## 재시도

**같은 회의에 두 번 올리면 두 번째는 무시된다** (`200`, `alreadyExisted: true`).
S3 업로드 자체를 건너뛰므로 중복 객체가 쌓이지 않는다.

타임아웃 후 재시도해도 안전하다. 다만 **요약본을 새로 만들어 덮어쓰고 싶다면
현재 정책으로는 불가능**하다. 그런 요구가 생기면 명시적인 재생성 경로를 따로 만든다.

## 토큰 운영

```bash
INTERNAL_API_TOKEN=<충분히 긴 랜덤 문자열>
```

- **비어 있으면 `/api/v1/internal/**` 은 전부 거부된다** (fail-closed)
- 비교는 상수 시간이다 (`MessageDigest.isEqual`) — 응답 시간으로 한 글자씩 맞출 수 없다
- **한계: 회전 절차가 없다.** 유출되면 환경변수를 바꾸고 재배포해야 한다.
  호출 빈도가 낮고(회의당 1회) 내부 호출이라 감수한다

## ★ 정정 — 이 문서만으로는 안 됐다 (2026-08-24, #91)

이 문서의 마지막 줄은 원래 이랬다.

> *"파이썬 저장소가 이 리포에 없어서 **클라이언트 쪽 변경은 미반영**이다"*

모노레포로 합치고 나서 `ai/` 를 열어 보니 **정말로 미반영이었다.**

```
[upload:url] http://.../meetings/{meetingId}/summary   <- 치환조차 안 됐다
[upload:data] {'class_id':'5','meeting_id':'77'}       <- meetingId 를 폼으로
headers = {"Accept": "application/json"}                <- X-Internal-Token 없음
```

Java 는 `%7BmeetingId%7D` 를 `Long` 으로 파싱하려다 400 을 내고,
경로가 맞았더라도 `hasRole("INTERNAL")` 에서 403 이다.
**#27 이후로 AI 요약본이 서비스에 도달한 적이 없다.**

### 무엇이 부족했나

이 문서에는 "파이썬이 바꿔야 하는 것" 표가 **이미 있었다.**
몰라서 안 고친 게 아니라 **고칠 수 없는 위치에 있었고, 문서는 CI 를 실패시키지 못한다.**

그래서 계약을 **기계가 읽는 파일**로 옮겼다.

```
contracts/internal-api.json     Java 테스트와 파이썬 테스트가 함께 읽는다
```

| | |
|---|---|
| `InternalApiContractTest` (Java) | 실제 노출 경로·헤더 이름이 계약과 같은지 |
| `test_summary_upload_contract.py` (파이썬) | 실제로 나가는 HTTP 요청이 계약과 같은지 |

**손으로 옮겨 적으면 의미가 없다.** Java 가 경로를 바꿔도 파이썬 테스트는 초록으로 남는다.
그게 정확히 이 버그가 오래 산 이유다.

### Gradle 이 그 파일을 몰라서 테스트를 건너뛰었다

계약만 바꿨을 때 `test` 태스크가 **UP-TO-DATE 로 넘어갔다.**
`--rerun-tasks` 를 줘야만 잡혔다 — **CI 가 초록인데 계약이 갈라지는 상태다.**

```kotlin
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../contracts/internal-api.json"))
}
```

되돌려서 확인했다. 이제 계약만 바꿔도 양쪽이 동시에 깨진다.

## 남는 것

- **토큰 회전 절차가 없다.** 유출되면 환경변수를 바꾸고 재배포해야 한다
- 계약 파일이 **경로와 헤더 이름까지만** 덮는다. 본문 스키마는 아직 양쪽이 따로 안다
- 요약본 재생성 경로 (현재는 첫 기록이 이긴다)
