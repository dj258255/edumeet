<template>
  <div class="shared-caption-overlay" v-if="isVisible">
    <div class="caption-container">
      <div class="caption-header">
        <span class="caption-icon">🎤</span>
        <span class="caption-title">실시간 자막</span>
        <span v-if="isCreator" class="creator-indicator">👑 생성자</span>
        <button 
          v-if="isCreator" 
          @click="toggleCaption" 
          :class="{ active: isListening }"
          class="caption-toggle-btn"
          :disabled="!isSupported"
        >
          {{ isListening ? '⏸️ 자막 중지' : '▶️ 자막 시작' }}
        </button>
      </div>
      
      <div v-if="!isSupported && isCreator" class="error-message">
        이 브라우저는 음성인식을 지원하지 않습니다.
      </div>
      
             <div v-if="transcript || (sharedCaption && isSharedCaptionActive)" class="caption-content">
         <div class="caption-text">
           {{ isSharedCaptionActive && sharedCaption ? sharedCaption : transcript }}
         </div>
         <div v-if="(confidence > 0) || (sharedCaptionConfidence > 0)" class="confidence-indicator">
           <div class="confidence-bar">
             <div class="confidence-fill" :style="{ width: ((isSharedCaptionActive ? sharedCaptionConfidence : confidence) * 100) + '%' }"></div>
           </div>
           <span class="confidence-text">{{ ((isSharedCaptionActive ? sharedCaptionConfidence : confidence) * 100).toFixed(0) }}%</span>
         </div>
       </div>
       
       <div v-else-if="isListening && !isCreator" class="waiting-message">
         🎤 생성자의 음성을 기다리는 중...
       </div>
      
      <div v-else-if="isListening" class="waiting-message">
        🎤 음성을 기다리는 중...
      </div>
      
      <div v-if="status" class="caption-status">
        {{ status }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  isCreator: {
    type: Boolean,
    default: false
  },
  isVisible: {
    type: Boolean,
    default: true
  },
  sharedCaption: {
    type: String,
    default: ''
  },
  sharedCaptionConfidence: {
    type: Number,
    default: 0
  },
  isSharedCaptionActive: {
    type: Boolean,
    default: false
  },
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
    if (props.isCreator) {
      initializeSpeechRecognition()
    }
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

// 생성자 여부가 변경될 때 음성 인식 시작/중지
watch(() => props.isCreator, (newValue) => {
  if (newValue && isSupported.value) {
    initializeSpeechRecognition()
  } else if (recognition) {
    recognition.stop()
    isListening.value = false
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
    
    console.log('공유 실시간 자막:', transcript.value)
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
    
    // 수동 제어로 변경하여 자동 재시작하지 않음
    // if (props.continuous && !isManuallyStopped.value && props.isCreator) {
    //   setTimeout(() => {
    //     if (recognition && !isManuallyStopped.value && props.isCreator) {
    //       recognition.start()
    //     }
    //   }, 100)
    // }
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
  
  // 생성자인 경우 자동 시작하지 않음 (수동 제어)
  // if (props.isCreator) {
  //   recognition.start()
  // }
}

function startCaption() {
  if (recognition && !isListening.value && props.isCreator) {
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

function toggleCaption() {
  if (isListening.value) {
    stopCaption()
  } else {
    startCaption()
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
.shared-caption-overlay {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  width: 90%;
  max-width: 800px;
}

.caption-container {
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.caption-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
}

.caption-icon {
  font-size: 16px;
}

.caption-title {
  flex: 1;
}

.creator-indicator {
  background: rgba(255, 215, 0, 0.3);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  color: #ffd700;
}

.caption-toggle-btn {
  background-color: #3b82f6;
  color: white;
  padding: 6px 12px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: background-color 0.3s ease;
  white-space: nowrap;
}

.caption-toggle-btn:hover:not(:disabled) {
  background-color: #2563eb;
}

.caption-toggle-btn:disabled {
  background-color: #9ca3af;
  cursor: not-allowed;
  opacity: 0.7;
}

.caption-toggle-btn.active {
  background-color: #ef4444;
}

.error-message {
  color: #f87171;
  font-size: 12px;
  text-align: center;
  margin-top: 10px;
  padding: 8px;
  background-color: rgba(255, 0, 0, 0.1);
  border-radius: 8px;
}

.caption-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.caption-text {
  color: white;
  font-size: 20px;
  line-height: 1.6;
  font-weight: 500;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.7);
  min-height: 32px;
}

.confidence-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confidence-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.confidence-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #3b82f6);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.confidence-text {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  min-width: 30px;
}

.waiting-message {
  color: rgba(255, 255, 255, 0.7);
  font-style: italic;
  text-align: center;
  padding: 8px;
}

.caption-status {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  font-style: italic;
  margin-top: 8px;
}

/* 반응형 */
@media (max-width: 768px) {
  .shared-caption-overlay {
    width: 95%;
    bottom: 10px;
  }
  
  .caption-container {
    padding: 12px;
  }
  
  .caption-text {
    font-size: 18px;
  }
}
</style>
