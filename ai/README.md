# 교육 텍스트 요약 API

Express.js에서 FastAPI로 리팩토링된 교육 특화 텍스트 요약 및 분석 API입니다.

## 주요 기능

### 1. 텍스트 전처리 및 구어체 정제
- 한국어 구어체 표현을 문어체로 변환
- 감탄사, 반복 표현, 불필요한 표현 제거
- 교육 컨텍스트에 특화된 전처리

### 2. 키워드 및 핵심 문장 추출
- TF-IDF 기반 키워드 추출 (교육 키워드 가중치 적용)
- 교육 특화 핵심 문장 추출
- 위치 가중치 및 교육 관련 패턴 인식

### 3. LLM 요약
- OpenAI GPT 또는 SSAFY GMS API 연동
- 교육 전문 프롬프트로 학습 내용 요약
- 구조화된 요약 형식 제공

### 4. 유사도 기반 텍스트 필터링 ⭐ **새로운 기능**
- Sentence Transformers를 활용한 문장 임베딩
- 코사인 유사도 기반 핵심 내용 필터링
- API 비용 절감을 위한 전처리 도구

## 설치 및 실행

### 1. 의존성 설치
```bash
pip install -r requirements.txt
```

### 2. 환경 변수 설정
```bash
# .env 파일 생성
OPENAI_API_KEY=your_openai_api_key_here
PORT=8000
```

### 3. 서버 실행
```bash
# 개발 모드
uvicorn main:app --reload

# 프로덕션 모드
python main.py
```

## API 엔드포인트

### 1. 키워드/문장 추출
```http
POST /api/extract-key-sentences
Content-Type: application/json

{
  "text": "분석할 텍스트 내용",
  "extractKeywords": true,
  "extractSentences": true
}
```

### 2. LLM 요약
```http
POST /api/llm-summarize
Content-Type: application/json

{
  "text": "요약할 텍스트 내용"
}
```

### 3. 유사도 기반 텍스트 필터링
```http
POST /api/filter-text
Content-Type: application/json

{
  "text": "필터링할 텍스트 내용",
  "similarity_threshold": 0.3,
  "min_sentence_length": 20
}
```

### 4. 헬스 체크
```http
GET /
```

## 주요 개선 사항

### Express.js → FastAPI 마이그레이션
- **타입 안정성**: Pydantic 모델을 통한 요청/응답 검증
- **자동 문서화**: FastAPI 자동 생성 API 문서 (`/docs`)
- **비동기 처리**: async/await를 통한 성능 향상
- **에러 처리**: 구조화된 예외 처리

### 유사도 기반 필터링 추가
- **로컬 임베딩**: Sentence Transformers로 빠른 임베딩 생성
- **비용 절감**: LLM API 호출 전 불필요한 내용 제거
- **유연한 임계값**: 상황에 맞는 필터링 강도 조절
- **안전장치**: 최소 문장 수 보장으로 과도한 필터링 방지

## 기술 스택

- **FastAPI**: 고성능 웹 프레임워크
- **Sentence Transformers**: 문장 임베딩 및 유사도 계산
- **scikit-learn**: 기계학습 도구 (TF-IDF, 코사인 유사도)
- **NLTK**: 자연어 처리 도구
- **httpx**: 비동기 HTTP 클라이언트
- **numpy**: 수치 계산

## 사용 예시

### 유사도 필터링 워크플로우
1. 긴 수업 기록을 `/api/filter-text`로 전송
2. 핵심 내용만 필터링된 텍스트 수신
3. 필터링된 텍스트를 `/api/llm-summarize`로 요약
4. API 비용 절감과 품질 향상 동시 달성

### 교육 컨텐츠 분석
1. 수업 녹음을 텍스트로 변환
2. `/api/extract-key-sentences`로 핵심 키워드/문장 추출
3. 학습자를 위한 요약 자료 생성

## 향후 개선 계획

- [ ] 캐싱 시스템 구현 (Redis)
- [ ] 배치 처리 지원
- [ ] 더 정교한 한국어 형태소 분석기 연동
- [ ] 성능 모니터링 대시보드
- [ ] 다양한 임베딩 모델 지원