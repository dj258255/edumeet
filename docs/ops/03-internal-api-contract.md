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

{ "text": "안녕하세요", "sequence": 42, "spokenAt": 1756000000000 }
```

| 필드 | |
|---|---|
| `text` | 인식된 문장. 최대 500자 |
| `sequence` | 발화 순서. **클라이언트가 네트워크 재정렬을 감지하려면 필요하다** |
| `spokenAt` | **원본 오디오에서의 발화 시각**(epoch millis). 서버 수신 시각이 아니다 |

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

### 저장하지 않는다

실시간 자막은 지나가면 끝이다. 다시보기용 저장은 녹음과 함께 다룬다(#61).
**발행 경로에 DB 쓰기를 넣으면 #43 에서 본 것처럼 브로드캐스트 측정이 쓰기에 묻힌다.**

---

## 파이썬이 바꿔야 하는 것

| 이전 | 이후 |
|---|---|
| `POST /api/v1/meeting/summary/{classId}` | `POST /api/v1/internal/meetings/{meetingId}/summary` |
| 인증 없음 | `X-Internal-Token` 헤더 필수 |
| `class_id`, `meeting_id` 폼 필드 | **`meetingId` 는 경로에 필수.** `class_id` 는 보내지 않는다 |
| `meeting_id` 생략 가능 | **생략 불가** |

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

## 남는 것

- 파이썬 저장소가 이 리포에 없어서 **클라이언트 쪽 변경은 미반영**이다
- 요약본 재생성 경로 (현재는 첫 기록이 이긴다)
