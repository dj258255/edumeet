const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3001;

// CORS 및 JSON 파싱 설정
app.use(cors());
app.use(express.json({ limit: '10mb' }));

// 한국어 불용어 리스트 (교육 특화)
const koreanStopwords = new Set([
  '이', '그', '저', '것', '수', '있', '하', '되', '의', '가', '을', '를', '에', '와', '과', '도', '는', '은', '로', '으로',
  '이다', '있다', '하다', '되다', '아니다', '없다', '같다', '크다', '작다', '많다', '적다', '좋다', '나쁘다',
  '그리고', '그런데', '하지만', '그러나', '또한', '따라서', '그래서', '그러므로', '즉', '예를 들어',
  '우리', '저희', '나', '너', '당신', '그들', '이들', '저들', '여기', '거기', '저기', '어디',
  '언제', '어떻게', '왜', '무엇', '누구', '얼마', '몇', '어느', '어떤',
  // 구어체 불용어 추가
  '음', '어', '그냥', '뭔가', '막', '좀', '진짜', '정말', '완전', '엄청', '되게', '너무'
]);

// 교육 관련 중요 키워드 (가중치 부여)
const educationKeywords = new Set([
  '개념', '정의', '원리', '법칙', '이론', '공식', '정리', '증명', '예시', '사례',
  '문제', '해결', '방법', '과정', '단계', '절차', '결과', '결론', '요약', '정리',
  '중요', '핵심', '주요', '기본', '기초', '응용', '활용', '실습', '연습', '복습',
  '시험', '평가', '과제', '숙제', '학습', '이해', '암기', '기억', '분석', '비교'
]);

/**
 * 교육 특화 텍스트 전처리 (구어체 → 문어체)
 */
function preprocessEducationText(text) {
  return text
    // 반복 표현 정리
    .replace(/(.{1,10}?)\1{2,}/g, '$1')
    // 감탄사 제거
    .replace(/\b(음|어|아|오|와|헉|어머|아이고)\b/g, '')
    // 구어체 표현 정리
    .replace(/그냥\s+/g, '')
    .replace(/뭔가\s+/g, '')
    .replace(/막\s+/g, '')
    // 과도한 공백 정리
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * 교육 특화 형태소 분석
 */
function analyzeMorphology(text) {
  // 교육 텍스트 전처리
  const preprocessedText = preprocessEducationText(text);
  
  // 문장 부호 제거 및 공백으로 단어 분리
  const words = preprocessedText
    .replace(/[^\w\sㄱ-ㅎㅏ-ㅣ가-힣]/g, ' ')
    .split(/\s+/)
    .filter(word => word.length > 0)
    .map(word => word.toLowerCase());
  
  // 불용어 제거
  const filteredWords = words.filter(word => 
    !koreanStopwords.has(word) && 
    word.length > 1 && 
    /[가-힣]/.test(word)
  );
  
  return filteredWords;
}

/**
 * 교육 특화 TF-IDF 키워드 추출
 */
function extractKeywords(text, topK = 15) {
  const sentences = text.split(/[.!?]+/).filter(s => s.trim().length > 0);
  const allWords = analyzeMorphology(text);
  
  // 단어 빈도 계산 (TF) - 교육 키워드 가중치 적용
  const wordFreq = {};
  allWords.forEach(word => {
    const weight = educationKeywords.has(word) ? 1.5 : 1.0; // 교육 키워드 50% 가중치
    wordFreq[word] = (wordFreq[word] || 0) + weight;
  });
  
  // 문서 빈도 계산 (DF)
  const docFreq = {};
  sentences.forEach(sentence => {
    const sentenceWords = new Set(analyzeMorphology(sentence));
    sentenceWords.forEach(word => {
      docFreq[word] = (docFreq[word] || 0) + 1;
    });
  });
  
  // TF-IDF 계산
  const tfidf = {};
  const totalDocs = sentences.length;
  
  Object.keys(wordFreq).forEach(word => {
    const tf = wordFreq[word] / allWords.length;
    const idf = Math.log(totalDocs / (docFreq[word] || 1));
    tfidf[word] = tf * idf;
  });
  
  // 상위 키워드 반환 (교육용으로 더 많이)
  return Object.entries(tfidf)
    .sort(([,a], [,b]) => b - a)
    .slice(0, topK)
    .map(([word]) => word);
}

/**
 * 교육 특화 핵심 문장 추출 (섹션별)
 */
function extractKeySentences(text, topK = 6) {
  const sentences = text.split(/[.!?]+/).filter(s => s.trim().length > 15);
  const keywords = extractKeywords(text, 25);
  const keywordSet = new Set(keywords);
  
  // 교육 특화 문장 점수 계산
  const sentenceScores = sentences.map((sentence, index) => {
    const words = analyzeMorphology(sentence);
    const keywordCount = words.filter(word => keywordSet.has(word)).length;
    
    // 기본 키워드 점수
    let score = keywordCount / Math.max(words.length, 1);
    
    // 교육 특화 가중치
    if (/중요|핵심|기본|개념|정의|원리/.test(sentence)) score *= 1.3;
    if (/예를 들어|예시|사례|실습/.test(sentence)) score *= 1.2;
    if (/정리하면|요약하면|결론|마무리/.test(sentence)) score *= 1.4;
    if (/시험|평가|과제/.test(sentence)) score *= 1.2;
    
    // 위치 가중치 (도입부와 마무리 부분 중요)
    const position = index / sentences.length;
    if (position < 0.2 || position > 0.8) score *= 1.1;
    
    return {
      sentence: sentence.trim(),
      score: score,
      length: sentence.length,
      position: index
    };
  });
  
  // 점수순으로 정렬하되, 적절한 길이의 문장만 선별
  return sentenceScores
    .filter(item => item.length > 25 && item.length < 200)
    .sort((a, b) => b.score - a.score)
    .slice(0, topK)
    .sort((a, b) => a.position - b.position) // 원래 순서로 재정렬
    .map(item => item.sentence);
}

/**
 * 키워드/문장 추출 API
 */
app.post('/api/extract-key-sentences', (req, res) => {
  try {
    const { text, extractKeywords: shouldExtractKeywords = true, extractSentences: shouldExtractSentences = true } = req.body;
    
    if (!text || text.trim().length === 0) {
      return res.status(400).json({ error: '텍스트를 입력해주세요.' });
    }
    
    const result = {};
    
    if (shouldExtractKeywords) {
      result.keywords = extractKeywords(text, 10);
    }
    
    if (shouldExtractSentences) {
      result.keySentences = extractKeySentences(text, 3);
    }
    

    
    res.json(result);
  } catch (error) {
    console.error('키워드/문장 추출 오류:', error);
    res.status(500).json({ error: '키워드/문장 추출 중 오류가 발생했습니다.' });
  }
});

/**
 * LLM 요약 API
 */
app.post('/api/llm-summarize', async (req, res) => {
  try {
    const { text } = req.body;
    
    if (!text || text.trim().length === 0) {
      return res.status(400).json({ error: '요약할 텍스트를 입력해주세요.' });
    }
    
    // API 키가 없으면 더미 응답
    if (!process.env.OPENAI_API_KEY || process.env.OPENAI_API_KEY === 'your_openai_api_key_here') {
      const summary = `📝 **요약 결과**

핵심 내용: ${text.substring(0, 80)}...

이 문서는 중요한 정보를 포함하고 있으며, 주요 논점들이 체계적으로 제시되어 있습니다. 전반적으로 유용한 참고 자료로 활용할 수 있을 것으로 판단됩니다.

⚠️ *실제 AI 요약을 사용하려면 .env 파일에 OPENAI_API_KEY를 설정하세요*`;
      
      return res.json({ summary });
    }

    // SSAFY GMS API 또는 OpenAI API 호출
    const isGmsApiKey = process.env.OPENAI_API_KEY.startsWith('S13P11C');
    const apiUrl = isGmsApiKey 
      ? 'https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions'
      : 'https://api.openai.com/v1/chat/completions';

    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: 'gpt-4o-mini',
        messages: [
          {
            role: 'system',
            content: `당신은 교육 전문 요약 AI입니다. 선생님의 1시간 강의 내용을 학생들이 이해하기 쉽게 요약해주세요.

요약 형식:
📚 **주요 학습 내용**
- 핵심 개념과 정의
- 중요한 원리나 법칙

🎯 **핵심 포인트**  
- 꼭 기억해야 할 내용
- 시험에 나올 만한 중요 사항

💡 **실습/예시**
- 구체적인 사례나 예시
- 실제 적용 방법

📝 **정리**
- 전체 내용을 한 문장으로 요약
- 다음 학습과의 연결점`
          },
          {
            role: 'user',
            content: `다음은 선생님이 1시간 동안 진행한 수업 내용입니다. 학생들의 학습을 위해 체계적으로 요약해주세요:\n\n${text}`
          }
        ],
        temperature: 0.3,
        max_tokens: 500
      })
    });
    
    if (!response.ok) {
      const errorData = await response.text();
      console.error('OpenAI API 오류:', response.status, errorData);
      return res.status(500).json({ error: 'LLM API 호출에 실패했습니다.' });
    }
    
    const data = await response.json();
    const summary = data.choices[0]?.message?.content;
    
    if (!summary) {
      return res.status(500).json({ error: '요약 생성에 실패했습니다.' });
    }
    

    
    res.json({ summary: summary.trim() });
  } catch (error) {
    console.error('LLM 요약 오류:', error);
    res.status(500).json({ error: 'LLM 요약 중 오류가 발생했습니다.' });
  }
});



/**
 * 서버 시작
 */
app.listen(PORT, () => {
  console.log(`🚀 문서요약 API 서버 실행 중: http://localhost:${PORT}`);
}); 