# AI 자막·요약 파이프라인 — 실시간 경로와 배치 경로를 나눈다

> 작성 2026-08-25 · #129  
> 결론: **실시간 자막과 문서 요약을 한 파이프라인으로 보되, 같은 처리 경로에 넣지는 않는다.**
> 자막은 지연과 장애 격리가 먼저이고, 요약은 토큰·문맥·품질 관리가 먼저다.

## 1. 분석

실시간 자막과 수업 요약은 같은 transcript 에서 나오지만 요구사항이 다르다.

| 기능 | 사용자 기대 | 병목 | 실패했을 때 |
|---|---|---|---|
| 실시간 자막 | 말한 뒤 1~3초 안에 화면 표시 | STT 지연, WebSocket fan-out, 외부 API 지연 | 접근성 기능이 멈춘다 |
| 문서 요약 | 수업 종료 후 읽을 만한 결과 | 토큰 수, 장문 문맥, 파일 업로드 | 다시 시도하면 된다 |
| 검색 색인 | 나중에 찾을 수 있는 단위 | 청킹, 정규화, 메타데이터 | 색인 재생성이 가능하다 |

그래서 세 기능을 "AI 기능" 하나로 묶으면 설계가 흐려진다.
핵심은 **실시간 데이터 경로와 배치 데이터 경로를 분리하는 것**이다.

현재 구현은 이렇다.

```text
녹음 파일 완료
  → CLOVA batch STT
  → transcript.txt
  ├─ 문장 단위 자막 전송     realtime=false, approximate_timing=true
  └─ LLM 요약 생성
      → summary.md / summary.pdf
      → Java internal API 업로드
```

이 경로는 요약에는 맞지만 실시간 자막에는 맞지 않는다.
회의가 끝난 뒤 텍스트가 나오기 때문이다.

실시간 자막으로 가려면 데이터 모양이 바뀐다.

```text
마이크 오디오 조각
  → streaming STT
      partial caption
      final caption
  → 기술 용어 사전 보정
  → Java internal caption API
  → STOMP /topic/rooms/{meetingId}/captions
  → 프론트 자막 영역

회의 종료 후
  → final transcript
  → LLM 요약 / 검색 색인 / 자막 정리
```

여기서 중요한 것은 **partial 과 final 을 구분하는 것**이다.
partial 은 계속 바뀔 수 있고, final 은 다시보기·요약·검색에 남길 수 있다.
둘을 같은 이벤트로 다루면 화면에는 빠르게 보이지만 저장 데이터가 흔들린다.

## 2. 비용

STT 와 LLM 은 과금 단위가 다르다.

| 구간 | 과금 단위 | 현재 확인 단가 |
|---|---|---:|
| CLOVA Speech batch | 오디오 시간, 15초 올림 | 예시 기준 20원/분 |
| OpenAI `gpt-live-transcribe` | 오디오 시간 | $0.017/분 |
| OpenAI `gpt-transcribe` | 오디오 시간 | $0.0045/분 |
| GPT-4.1 nano 용어 보정 가정 | 토큰 | 1시간 4만 토큰 가정 약 10원 |

토큰 보정 비용만 보면 작다. 그래도 자막 hot path 에 LLM 을 넣지 않는다.

이유는 비용보다 지연과 장애 범위다.

- 자막 조각마다 외부 LLM 호출이 붙으면 모델 지연이 매번 붙는다.
- partial caption 은 계속 바뀌므로 호출 수가 늘고 결과 일관성이 흔들린다.
- LLM 장애가 접근성 기능 장애로 번진다.
- 같은 용어가 조각마다 다르게 바뀌면 자막 품질이 더 나빠진다.

그래서 현재 hot path 에서는 결정적 용어 사전만 쓴다.

```text
python      → 파이썬
spring boot → 스프링 부트
websocket   → WebSocket
mysql       → MySQL
redis       → Redis
```

비용 0원, 네트워크 지연 0ms, 같은 입력에 같은 결과다.

## 3. 선택지

### 1. batch STT 결과를 자막과 요약에 같이 쓴다

가장 단순하다. 지금 구현이 이 방식이다.

장점은 계약을 먼저 검증할 수 있다는 것이다.
Python 이 만든 자막과 요약이 Java 내부 API 를 거쳐 화면·저장소까지 도달하는지 확인할 수 있다.

단점은 실시간이 아니라는 것이다.
따라서 응답에는 `realtime: false`, `approximate_timing: true` 를 남긴다.

### 2. streaming STT 는 자막에만 쓰고, 요약은 회의 후에 만든다

실시간 자막의 목표 구조다.

STT 는 빠른 transcript delta 를 만들고, LLM 은 회의가 끝난 뒤 final transcript 를 받아 요약한다.
이렇게 하면 자막 지연과 요약 품질을 각각 최적화할 수 있다.

### 3. streaming STT 뒤에 LLM 보정을 매 조각마다 붙인다

기술적으로 가능하지만 지금은 제외한다.

토큰 비용은 작아도 조각마다 외부 호출이 들어간다.
자막에서는 토큰값보다 **p95 지연과 장애 격리**가 더 중요하다.

### 4. LangChain / LangGraph 로 요약 파이프라인을 만든다

LangChain 은 "LLM 호출 한 번"을 감싸려고 쓰는 도구가 아니다.
요약이 다음처럼 커질 때 가치가 생긴다.

- transcript 를 구간별로 나누고 map 요약을 만든다.
- map 결과를 reduce 하거나 refine 한다.
- 액션 아이템, 질문, 키워드, 다시보기 챕터를 각각 구조화한다.
- 검색 색인과 요약을 같은 transcript 원천에서 만든다.
- 단계별 실패와 재시도를 관측한다.

최신 LangChain 문서는 LangGraph 기반 workflow, `SummarizationNode`,
middleware 를 안내한다. 반대로 `langchain-classic` 의 `load_summarize_chain`
계열은 reference 에서 deprecated 로 표시된다.

따라서 도입 조건은 이렇다.

| 조건 | 판단 |
|---|---|
| 요약 호출이 한 번이다 | 직접 OpenAI SDK 호출이 낫다 |
| map/reduce/refine, 평가, 재시도, 검색 색인이 같이 필요하다 | LangGraph 후보 |
| 자막 hot path 다 | 제외. 프레임워크보다 지연 상한이 먼저다 |
| 테스트 없는 471줄 함수를 감싼다 | 제외. 복잡도를 숨기는 것뿐이다 |

## 4. 결정

현재 결정은 다음과 같다.

| 구간 | 결정 |
|---|---|
| 실시간 자막 | streaming STT 로 전환할 때까지 현재 경로는 `realtime=false` 로 둔다 |
| 기술 용어 보정 | hot path 에서는 결정적 사전만 적용한다 |
| 문서 요약 | 회의 종료 후 batch 경로에서 수행한다 |
| LangChain / LangGraph | 지금은 미도입. 요약 파이프라인을 테스트 가능한 단계로 쪼갠 뒤 도입한다 |
| 검색 색인 | raw transcript 와 display transcript 를 분리한 뒤 검토한다 |

이 결정의 목적은 기능을 줄이는 것이 아니다.
자막은 접근성 기능이므로 **빠른 실패와 지연 상한**이 먼저이고,
요약은 학습 보조 기능이므로 **재시도와 품질**이 먼저다.

## 5. 장애 주입으로 봐야 할 것

Toxiproxy 는 이미 LiveKit 타임아웃과 느린 WebSocket 소비자를 검증하는 데 썼다.
AI 경로에도 같은 방식의 장애 주입이 필요하다.

단, 순서가 있다.
막을 장치가 없는 상태에서 장애를 넣으면 앱이 죽는 것을 증명할 뿐이다.
먼저 상한과 격리를 만들고, 그 다음 Toxiproxy 로 확인한다.

| 장애 | 주입 방법 | 기대 동작 |
|---|---|---|
| STT 응답 지연 | STT 앞 latency toxic | 자막은 지연되지만 채팅·회의는 살아 있어야 한다 |
| STT 무응답 | timeout toxic | 정해진 시간 뒤 502, 실패 상태가 모니터링에 보여야 한다 |
| Java caption API 지연 | internal API 앞 latency toxic | STT 루프가 무한 대기하지 않아야 한다 |
| Java caption API 500 | stub 또는 proxy down | 일부 자막 실패가 `failed[]` 에 남아야 한다 |
| LLM 429/timeout | 요약 모델 앞 timeout/응답 스텁 | 요약만 실패하고 자막 결과는 버리지 않아야 한다 |
| S3 업로드 지연 | S3 앞 latency toxic | 업로드 실패 시 로컬 산출물을 지우지 않아야 한다 |

이미 막은 것은 있다.

- STT 실패는 200 이 아니라 502 로 나간다.
- 요약 실패도 502 로 나간다.
- 자막 일부 실패는 `ok=false`, `failed[]` 로 보고한다.
- 요약 업로드가 성공했을 때만 로컬 산출물을 삭제한다.
- `/api/v1/internal/**` 은 `X-Internal-Token` 계약으로 묶었다.

아직 못 막은 것도 있다.

- streaming STT 루프가 없다.
- caption 전송은 현재 동기 POST 이므로, 진짜 streaming 에서는 유계 큐가 필요하다.
- raw/display transcript 분리가 없다.
- LLM 요약 함수는 471줄이고 테스트가 거의 없어서, LangGraph 로 감싸기 전에 먼저 쪼개야 한다.

## 6. 포트폴리오 문장

> 실시간 자막과 문서 요약은 같은 transcript 를 쓰지만 같은 경로에 두면 안 된다고 판단했습니다.
> 자막은 발화 후 1~3초 안에 화면에 도달해야 하는 접근성 기능이고,
> 요약은 회의 종료 후 재시도 가능한 배치 산출물입니다. 그래서 hot path 에는 LLM 을 넣지 않고
> `python → 파이썬`, `websocket → WebSocket` 같은 결정적 용어 사전만 적용했습니다.
> LangChain/LangGraph 는 요약을 map/reduce, 액션 아이템, 검색 색인, 평가 단계로 나눌 때 도입할 후보로 두고,
> 현재처럼 단일 LLM 호출과 테스트 없는 471줄 함수를 감싸는 용도로는 쓰지 않았습니다.
> 장애 주입도 같은 기준으로 봤습니다. STT·LLM·Java 내부 API·S3 를 각각 끊었을 때
> 자막, 채팅, 요약, 업로드가 어디까지 영향을 받는지 분리해서 재야 한다고 보고,
> 이미 502 상태 코드·부분 실패 보고·업로드 성공 후 삭제 같은 경계부터 고정했습니다.

## 7. 근거

- OpenAI realtime transcription pricing: <https://openai.com/api/pricing/>
- GPT-Live-Transcribe model card: <https://developers.openai.com/api/docs/models/gpt-live-transcribe>
- Naver Cloud CLOVA Speech pricing: <https://www.ncloud.com/charge/region/ko>
- Naver CLOVA Speech spec: <https://guide.ncloud-docs.com/docs/en/clovaspeech-spec>
- Naver CLOVA Speech troubleshooting: <https://guide.ncloud-docs.com/docs/en/clovaspeech-troubleshoot-common>
- LangChain Python / LangGraph workflow docs: <https://docs.langchain.com/oss/python/langgraph/workflows-agents>
- LangChain reference, summarize chain: <https://reference.langchain.com/python/langchain-classic/chains/summarize/chain>
- Toxiproxy toxics: <https://github.com/Shopify/toxiproxy>
