<template>
  <div class="live-caption">
    <div class="caption-header">
      <h3>🎤 실시간 자막</h3>
      <div class="caption-controls">
        <button 
          @click="toggleCaption" 
          :class="{ active: isListening }"
          :disabled="!isSupported"
        >
          {{ isListening ? '⏸️ 자막 중지' : '▶️ 자막 시작' }}
        </button>
      </div>
    </div>
    
    <div v-if="!isSupported" class="error-message">
      이 브라우저는 음성인식을 지원하지 않습니다.
    </div>
    
    <div v-if="transcript" class="caption-display">
      <div class="caption-text">
        <span v-if="isListening" class="listening-indicator">🎤</span>
        {{ transcript }}
      </div>
      <div v-if="confidence > 0" class="confidence-bar">
        <div class="confidence-fill" :style="{ width: (confidence * 100) + '%' }"></div>
        <span class="confidence-text">{{ (confidence * 100).toFixed(0) }}%</span>
      </div>
    </div>
    
    <div v-else-if="isListening" class="waiting-message">
      🎤 음성을 기다리는 중...
    </div>
    
    <div v-if="status" class="caption-status">
      {{ status }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  lang: {
    type: String,
    default: 'ko-KR'
  },
  continuous: {
    type: Boolean,
    default: true
  },
  interimResults: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['transcript', 'error', 'status'])

const isSupported = ref(false)
const isListening = ref(false)
const isManuallyStopped = ref(false)
const transcript = ref('')
const confidence = ref(0)
const status = ref('')

let recognition = null

onMounted(() => {
  // Web Speech API 지원 확인
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  const SpeechGrammarList = window.SpeechGrammarList || window.webkitSpeechGrammarList
  
  if (SpeechRecognition) {
    isSupported.value = true
    initializeSpeechRecognition()
  } else {
    isSupported.value = false
    status.value = '음성인식을 지원하지 않는 브라우저입니다.'
  }
})

onUnmounted(() => {
  if (recognition) {
    recognition.stop()
  }
})

function initializeSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  const SpeechGrammarList = window.SpeechGrammarList || window.webkitSpeechGrammarList
  
  recognition = new SpeechRecognition()
  const speechRecognitionList = new SpeechGrammarList()
  
  // 설정
  recognition.grammars = speechRecognitionList
  recognition.lang = props.lang
  recognition.interimResults = props.interimResults
  recognition.continuous = props.continuous
  recognition.maxAlternatives = 1
  
  // 이벤트 핸들러 설정
  recognition.onstart = () => {
    isListening.value = true
    status.value = '자막 시작'
    emit('status', 'start')
  }
  
  recognition.onresult = (event) => {
    let finalTranscript = ''
    let interimTranscript = ''
    
    // 모든 결과를 순회하며 최종 결과와 중간 결과를 분리
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i]
      const transcript = result[0].transcript
      
      if (result.isFinal) {
        finalTranscript += transcript
      } else {
        interimTranscript += transcript
      }
    }
    
    // 최종 결과가 있으면 업데이트, 없으면 중간 결과 표시
    if (finalTranscript) {
      transcript.value = finalTranscript
      confidence.value = event.results[event.results.length - 1][0].confidence
    } else {
      transcript.value = interimTranscript
      confidence.value = 0
    }
    
    // 실시간 결과 emit
    emit('transcript', {
      text: transcript.value,
      confidence: confidence.value,
      isFinal: finalTranscript.length > 0
    })
    
    console.log('실시간 자막:', transcript.value)
    console.log('신뢰도:', confidence.value)
  }
  
  recognition.onerror = (event) => {
    console.error('자막 오류:', event.error)
    status.value = `오류: ${event.error}`
    emit('error', event.error)
    isListening.value = false
  }
  
  recognition.onend = () => {
    isListening.value = false
    status.value = '자막 종료'
    emit('status', 'end')
    
    // 연속 모드이고 사용자가 중지하지 않았을 때만 자동으로 다시 시작
    if (props.continuous && !isManuallyStopped.value) {
      setTimeout(() => {
        if (recognition && !isManuallyStopped.value) {
          recognition.start()
        }
      }, 100)
    }
  }
  
  recognition.onaudiostart = () => {
    status.value = '오디오 캡처 시작'
  }
  
  recognition.onaudioend = () => {
    status.value = '오디오 캡처 종료'
  }
  
  recognition.onsoundstart = () => {
    status.value = '소리 감지됨'
  }
  
  recognition.onsoundend = () => {
    status.value = '소리 감지 종료'
  }
  
  recognition.onspeechstart = () => {
    status.value = '음성 감지됨'
  }
  
  recognition.onnomatch = () => {
    status.value = '음성을 인식할 수 없습니다'
  }
}

function toggleCaption() {
  if (!recognition) return
  
  if (isListening.value) {
    // 수동으로 중지
    isManuallyStopped.value = true
    recognition.stop()
  } else {
    // 수동으로 시작
    isManuallyStopped.value = false
    recognition.start()
  }
}

function startCaption() {
  if (recognition && !isListening.value) {
    isManuallyStopped.value = false
    recognition.start()
  }
}

function stopCaption() {
  if (recognition && isListening.value) {
    isManuallyStopped.value = true
    recognition.stop()
  }
}

// 외부에서 호출 가능한 메서드들
defineExpose({
  startCaption,
  stopCaption,
  toggleCaption
})
</script>

<style scoped>
.live-caption {
  background: var(--card-bg);
  border-radius: 12px;
  padding: 1rem;
  margin-bottom: 1rem;
  border: 1px solid var(--border-color);
}

.caption-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.caption-header h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--text-color);
}

.caption-controls button {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: #3b82f6;
  color: white;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.caption-controls button:hover:not(:disabled) {
  background: #2563eb;
}

.caption-controls button.active {
  background: #ef4444;
}

.caption-controls button.active:hover {
  background: #dc2626;
}

.caption-controls button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.error-message {
  color: #ef4444;
  font-size: 14px;
  padding: 8px;
  background: #fef2f2;
  border-radius: 4px;
}

.caption-display {
  background: #f8fafc;
  border-radius: 8px;
  padding: 1rem;
  border: 1px solid #e2e8f0;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.caption-text {
  font-size: 16px;
  color: #111827;
  line-height: 1.5;
  min-height: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.listening-indicator {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.confidence-bar {
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  position: relative;
  overflow: hidden;
}

.confidence-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #3b82f6);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.confidence-text {
  position: absolute;
  right: 4px;
  top: -18px;
  font-size: 10px;
  color: #6b7280;
  background: white;
  padding: 2px 4px;
  border-radius: 2px;
}

.waiting-message {
  text-align: center;
  color: #6b7280;
  font-style: italic;
  padding: 1rem;
}

.caption-status {
  font-size: 12px;
  color: #6b7280;
  font-style: italic;
  margin-top: 8px;
}

/* 다크모드 지원 */
.dark-mode .live-caption {
  background: var(--card-bg);
  border-color: var(--border-color);
}

.dark-mode .caption-header h3 {
  color: var(--text-color);
}

.dark-mode .caption-display {
  background: #374151;
  border-color: #4b5563;
}

.dark-mode .caption-text {
  color: #f9fafb;
}

.dark-mode .confidence-text {
  background: #374151;
  color: #9ca3af;
}

.dark-mode .waiting-message {
  color: #9ca3af;
}

.dark-mode .caption-status {
  color: #9ca3af;
}
</style> 