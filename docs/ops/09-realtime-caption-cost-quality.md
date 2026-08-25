# 실시간 자막 비용·지연·품질 제약

> 작성 2026-08-25 · #125  
> 결론: **실시간 자막 hot path 에 LLM 을 넣지 않는다.**
> STT 는 빠르게 받고, 기술 용어 표기는 사전으로 보정하고,
> LLM 은 회의 후 요약·검색 색인·자막 정리처럼 지연을 허용하는 경로로 보낸다.

## 0. 지금 상태

현재 파이썬 AI 경로는 **진짜 실시간 자막이 아니다.**

```
녹음 파일 완료
  → CLOVA batch STT
  → transcript 한 덩어리
  → 문장 단위로 쪼개 Java 내부 API 로 전송
  → STOMP 로 화면에 표시
  → final 자막만 비동기 저장
  → 저장 자막 transcript 를 요약 입력으로 우선 사용
```

그래서 `send_captions_to_api()` 는 `realtime: false`,
`approximate_timing: true` 를 반환한다. 이걸 숨기면 안 된다.
다만 저장 대상은 final 자막으로 제한했다. streaming STT 의 partial 자막까지
요약 입력에 넣으면 같은 발화가 여러 번 들어가 토큰을 낭비한다.

지금 만든 것은 **자막이 Java 와 프론트까지 도달하는 경로**다.
실시간 STT 소스는 별도 작업이다.

---

## 1. 실시간 자막의 제약

| 축 | 제약 |
|---|---|
| 속도 | 발화 후 partial caption 은 1~1.5초, final caption 은 2~3초 안쪽이 목표 |
| 비용 | STT 는 오디오 시간 과금, LLM 은 토큰 과금. 단위가 다르다 |
| 품질 | 한국어 강의 안에 `python`, `Spring Boot`, `QueryDSL` 같은 영어 기술어가 섞인다 |
| 안정성 | 자막은 접근성 기능이다. 외부 API 하나가 느려졌다고 채팅·회의까지 묶이면 안 된다 |
| 측정 | `spokenAt → receivedAt → publishedAt → clientReceivedAt` 을 나눠야 원인을 안다 |

**"자막이 느리다"는 결과일 뿐이다.**
STT 가 느린지, Java hop 이 느린지, WebSocket fan-out 이 밀리는지 나눠야 고칠 수 있다.

## 2. 가격 기준

확인일: 2026-08-25. 환율 계산은 2026-08-24 USD/KRW 1,382원 기준이다.

| 경로 | 공식 단가 | 원화 환산 |
|---|---:|---:|
| CLOVA Speech batch | 15초 단위, 10초 사용 예시 5원 | **20원/분 = 1,200원/시간** |
| OpenAI `gpt-live-transcribe` | $0.017/min | **약 23.5원/분 = 1,410원/시간** |
| OpenAI `gpt-transcribe` | $0.0045/min | 약 6.2원/분 = 373원/시간 |
| OpenAI `gpt-4o-mini-transcribe` | estimated $0.003/min | 약 4.1원/분 = 249원/시간 |

출처:

- Naver Cloud CLOVA Speech 가격: <https://www.ncloud.com/api-cms/service-product/static/clovaSpeech>
- CLOVA Speech 지원 범위: <https://guide.ncloud-docs.com/docs/en/clovaspeech-spec>
- OpenAI API pricing: <https://openai.com/api/pricing/>
- GPT-4.1 token pricing: <https://openai.com/index/gpt-4-1/>
- 환율 가정: <https://kr.investing.com/currencies/usd-krw-historical-data>

### 해석

CLOVA batch 와 OpenAI live STT 의 차이는 시간당 약 210원 수준이다.
따라서 **소규모 수업에서 STT 비용 자체는 결정타가 아니다.**
결정타는 다음 둘이다.

1. **실시간성** — batch STT 는 녹음이 끝난 뒤 결과가 나온다.
2. **혼합 언어 품질** — 한국어 설정에서 영어 단어가 빠지거나 엉뚱하게 나올 수 있다.

Naver 문서도 한국어로 인식할 때 영어 단어가 인식되지 않을 수 있으며,
장문 인식에서는 `params.lang=enko` 로 한/영 동시 인식을 쓰라고 안내한다.
하지만 스트리밍 인식 지원 언어 목록에는 한국어·영어·일본어만 있고,
한/영 동시 인식은 장문 인식 쪽에만 적혀 있다.

이 차이가 설계를 만든다.

## 3. LLM 보정은 왜 hot path 에 넣지 않나

기술 용어 보정을 LLM 으로 하면 토큰 비용은 생각보다 작다.

가정:

- 1시간 수업 = 자막 조각 1,000개
- 조각당 입력 30토큰, 출력 10토큰
- 총 입력 30,000토큰, 출력 10,000토큰

| 모델 | 계산 | 비용 |
|---|---|---:|
| GPT-4.1 nano | 30k × $0.10/M + 10k × $0.40/M | $0.007 ≈ **10원/시간** |
| GPT-4.1 mini | 30k × $0.40/M + 10k × $1.60/M | $0.028 ≈ **39원/시간** |

토큰비만 보면 싸다. 그래도 hot path 에 넣지 않는다.

| 문제 | 이유 |
|---|---|
| 지연 | 자막 조각마다 외부 모델 호출이 들어가면 모델 지연과 네트워크 지연이 매번 붙는다 |
| 흔들림 | 같은 용어도 문맥에 따라 다르게 고칠 수 있다. 자막에서는 일관성이 중요하다 |
| 장애 범위 | LLM 장애가 곧 접근성 기능 장애가 된다 |
| partial caption | 중간 자막은 계속 바뀐다. 매번 LLM 으로 고치면 비용보다 호출 수와 일관성이 문제다 |

그래서 hot path 에서는 **결정적 용어 사전**만 쓴다.

```text
python      → 파이썬
spring boot → 스프링 부트
websocket   → WebSocket
mysql       → MySQL
redis       → Redis
```

이 보정은 `ai/caption_normalizer.py` 에 있고, Java 로 보내기 전에 적용한다.
모델 호출이 없으므로 비용 0원, 네트워크 지연 0ms, 결과가 항상 같다.

저장도 같은 기준이다. final 자막만 `caption_segment` 에 남기고 partial 자막은
화면 표시용으로만 둔다. partial 은 계속 바뀌므로 저장하면 요약 입력이 길어지고,
같은 발화가 여러 번 들어가 토큰을 낭비한다.

## 4. 원문과 보정문을 분리해야 한다

장기적으로는 자막 이벤트를 이렇게 가져가야 한다.

```json
{
  "rawText": "today python query dsl",
  "displayText": "today 파이썬 QueryDSL",
  "normalizerVersion": "tech-ko-v1",
  "sequence": 42,
  "spokenAt": 1756000000000
}
```

지금 계약은 `text` 하나만 받는다. 그래서 현재는 표시용 보정만 한다.
검색·재학습·오류 분석까지 하려면 원문을 별도 보관해야 한다.

## 5. 목표 아키텍처

```text
발화
  → Streaming STT
      partial result
      final result
  → 용어 사전 보정
  → Java internal caption API
  → STOMP /topic/rooms/{meetingId}/captions
  → 프론트 자막 영역

회의 종료 후
  → transcript 원문 + 보정문
  → LLM 요약
  → 검색 색인/다시보기 자막 정리
```

**STT 와 LLM 을 분리한다.**
STT 는 접근성 hot path 이고, LLM 은 학습 보조·검색·요약 경로다.
요약 파이프라인과 LangChain/LangGraph 도입 기준은
[`10-ai-caption-summary-pipeline.md`](10-ai-caption-summary-pipeline.md)에 따로 정리했다.

## 6. 측정해야 할 것

현재 이미 Java 자막 응답에는 시각이 세 개 담긴다.

```text
spokenAt → receivedAt → publishedAt → clientReceivedAt
```

다음 측정은 이 순서다.

| 질문 | 지표 |
|---|---|
| STT 가 실시간으로 쓸 수 있나 | `receivedAt - spokenAt` p50/p95 |
| Java hop 이 문제인가 | `publishedAt - receivedAt` p95 |
| 프론트까지 밀리는가 | `clientReceivedAt - publishedAt` p95 |
| 자막과 채팅이 서로 방해하나 | 같은 회의에서 caption p95 / chat p95 동시 측정 |
| 보정이 품질을 올리나 | 기술 용어 평가셋 정답률. 예: `python`, `QueryDSL`, `HLS`, `WebRTC` |
| 비용이 얼마인가 | 회의별 오디오 초 × STT 단가 + LLM 토큰 사용량 |

## 7. 포트폴리오 문장

> 실시간 자막은 모델을 많이 붙이는 문제가 아니라, 지연·비용·품질 제약을 나누는
> 문제라고 봤습니다. STT 는 오디오 시간 과금이고 LLM 은 토큰 과금이라 단위부터
> 다릅니다. 한국어 강의의 영어 기술어는 batch STT 에서 흔들릴 수 있으므로
> hot path 에 LLM 을 넣지 않고, 우선 결정적 용어 사전으로 `python → 파이썬`,
> `websocket → WebSocket` 같은 보정만 수행했습니다. 현재 경로는 `realtime: false`
> 로 명시해 과장하지 않았고, streaming STT 로 전환할 때는
> `spokenAt → receivedAt → publishedAt → clientReceivedAt` 을 나눠 p95 를 재도록
> 계약을 먼저 잡았습니다.
