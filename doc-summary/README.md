# 문서 요약 백엔드 API

한국어 문서 요약을 위한 백엔드 API 서버입니다.

## 🚀 설치 및 실행

### 1. 의존성 설치
```bash
cd backend
npm install
```

### 2. 환경 변수 설정
`.env` 파일에서 OpenAI API 키를 설정하세요:
```
OPENAI_API_KEY=your_actual_openai_api_key
```

### 3. 서버 실행
```bash
# 개발 모드 (자동 재시작)
npm run dev

# 프로덕션 모드
npm start
```

## 📋 API 엔드포인트

### 키워드/문장 추출
- **POST** `/api/extract-key-sentences`
- **Body**: `{ "text": "분석할 텍스트", "extractKeywords": true, "extractSentences": true }`
- **Response**: `{ "keywords": ["키워드1", "키워드2"], "keySentences": ["문장1", "문장2"] }`

### LLM 요약
- **POST** `/api/llm-summarize`
- **Body**: `{ "text": "요약할 텍스트" }`
- **Response**: `{ "summary": "요약된 텍스트" }`

### 건강 체크
- **GET** `/api/health`
- **Response**: `{ "status": "OK", "message": "Document Summary API is running!" }`

## 🔧 기술 스택

- **Express.js**: 웹 서버 프레임워크
- **Natural**: 자연어 처리
- **Hangul.js**: 한글 처리
- **TF-IDF**: 키워드 추출 알고리즘
- **OpenAI GPT-4o-mini**: LLM 요약

## 🌐 CORS 설정

모든 도메인에서 접근 가능하도록 CORS가 활성화되어 있습니다. 