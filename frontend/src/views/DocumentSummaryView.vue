<template>
  <div class="min-h-screen bg-gray-50 py-8">
    <div class="max-w-4xl mx-auto px-4">
      
      <!-- 입력 영역 -->
      <div class="bg-white rounded-lg shadow-md p-6 mb-6">
        <textarea
          v-model="inputText"
          rows="12"
          maxlength="20000"
          class="w-full p-4 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none mb-4"
          placeholder="수업 내용을 입력하세요..."
        ></textarea>
        
        <div class="text-center">
          <button
            @click="startSummary"
            :disabled="!inputText.trim() || isLoading"
            class="px-12 py-4 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 transition-colors font-bold text-lg"
          >
            {{ isLoading ? '요약 중...' : '요약하기' }}
          </button>
        </div>
      </div>

      <!-- 결과 영역 -->
      <div v-if="summaryResult || isLoading" class="bg-white rounded-lg shadow-md p-6">
        <div v-if="isLoading" class="text-center py-8">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p class="text-gray-600">{{ loadingMessage }}</p>
        </div>
        
        <div v-else-if="summaryResult" class="prose prose-lg max-w-none">
          <div 
            class="text-gray-800 leading-8 space-y-4"
            v-html="formatSummary(summaryResult)"
          ></div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

// 반응형 데이터
const inputText = ref('')
const isLoading = ref(false)
const loadingMessage = ref('')
const summaryResult = ref('')

// 텍스트 포맷팅 함수
function formatSummary(text) {
  if (!text) return ''
  
  return text
    // 이모지가 있는 제목들을 더 크고 굵게
    .replace(/^(📚|🎯|💡|📝)\s*\*\*(.*?)\*\*/gm, '<h3 class="text-xl font-bold text-blue-700 mt-6 mb-3 flex items-center"><span class="text-2xl mr-2">$1</span>$2</h3>')
    
    // 일반 **굵은글씨** 처리
    .replace(/\*\*(.*?)\*\*/g, '<strong class="font-semibold text-gray-900">$1</strong>')
    
    // 불릿 포인트 (- 로 시작하는 줄)
    .replace(/^[\s]*-[\s]*(.*?)$/gm, '<div class="flex items-start my-2"><span class="text-blue-500 mr-3 mt-1">•</span><span class="flex-1">$1</span></div>')
    
    // 빈 줄을 간격으로 처리
    .replace(/\n\s*\n/g, '<div class="h-4"></div>')
    
    // 일반 줄바꿈
    .replace(/\n/g, '<br>')
    
    // 경고 문구 스타일링
    .replace(/⚠️\s*(.*?)(?=<br>|$)/g, '<div class="bg-yellow-100 border-l-4 border-yellow-500 p-3 my-4 rounded"><span class="text-yellow-700">⚠️ $1</span></div>')
}

// 메서드
async function startSummary() {
  if (!inputText.value.trim()) {
    alert('문서를 입력해주세요.')
    return
  }

  isLoading.value = true
  
  // 결과 초기화
  summaryResult.value = ''

  try {
    // 1단계: 키워드/문장 추출
    loadingMessage.value = '분석 중...'
    
    const extractResponse = await fetch('https://i13c205.p.ssafy.io/api/extract-key-sentences', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        text: inputText.value,
        extractKeywords: true,
        extractSentences: true
      })
    })

    if (!extractResponse.ok) throw new Error('추출 API 호출 실패')

    const extractData = await extractResponse.json()
    const keySentences = extractData.keySentences || []

    // 2단계: LLM 요약
    loadingMessage.value = '요약 생성 중...'
    
    const textToSummarize = keySentences.length > 0 
      ? keySentences.join(' ') 
      : inputText.value

    const summaryResponse = await fetch('https://i13c205.p.ssafy.io/api/llm-summarize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: textToSummarize })
    })

    if (!summaryResponse.ok) throw new Error('요약 API 호출 실패')

    const summaryData = await summaryResponse.json()
    summaryResult.value = summaryData.summary

  } catch (error) {
    console.error('요약 오류:', error)
    alert('요약 중 오류가 발생했습니다. 다시 시도해주세요.')
  } finally {
    isLoading.value = false
    loadingMessage.value = ''
  }
}
</script>

<style scoped>
/* 추가 스타일이 필요한 경우 여기에 작성 */
</style> 