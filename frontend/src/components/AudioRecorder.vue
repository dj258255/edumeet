<template>
  <div 
    v-if="isOpen" 
    class="audio-recorder-modal"
    :style="{ left: position.x + 'px', top: position.y + 'px' }"
    @mousedown="startDrag"
    @touchstart="startDrag"
  >
    <div class="modal-content">
      <button class="close-button" @click="emit('close')">✕</button>
      <div class="recorder-controls">
        <!-- 녹음 시작 버튼 -->
        <button 
          v-if="!isRecording && !isPaused && !hasRecordedData"
          @click="startRecording" 
          class="record-btn start-btn"
        >
          🎤 수업 녹화 시작
        </button>
        
        <!-- 정지 버튼 (녹음 중일 때만) -->
        <button 
          v-if="isRecording && !isPaused"
          @click="pauseRecording" 
          class="record-btn pause-btn"
        >
          ⏸️ 녹화 일시정지
        </button>
        
        <!-- 재개 버튼 -->
        <button 
          v-if="isPaused"
          @click="resumeRecording" 
          class="record-btn resume-btn"
        >
          ▶️ 녹화 재개
        </button>
        
        <!-- 문서 요약 버튼 -->
        <button 
          v-if="isPaused"
          @click="generateSummary" 
          :disabled="isGeneratingSummary"
          class="record-btn summary-btn"
        >
          {{ isGeneratingSummary ? '📝 요약 생성 중...' : '📝 문서 요약 생성' }}
        </button>
      </div>
      
      <div v-if="isRecording || isPaused" class="recording-status">
        <div class="status-indicator">
          <span class="recording-dot" :class="{ 'paused': isPaused }"></span>
          {{ isPaused ? '녹화 일시정지됨' : '수업 녹화 중...' }}
        </div>
        <div class="recording-time">
          {{ formatTime(recordingTime) }}
        </div>
        <div class="chunk-info">
          청크 {{ currentChunk }} / {{ totalChunks }}
        </div>
      </div>
      
      <div v-if="uploadStatus" class="upload-status">
        <div class="status-message" :class="uploadStatus.type">
          {{ uploadStatus.message }}
        </div>
        <div v-if="uploadStatus.progress" class="progress-bar">
          <div class="progress-fill" :style="{ width: uploadStatus.progress + '%' }"></div>
        </div>
      </div>
      
      <div v-if="summaryStatus" class="summary-status">
        <div class="status-message" :class="summaryStatus.type">
          {{ summaryStatus.message }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted, onMounted } from 'vue'

const props = defineProps({
  classId: {
    type: [String, Number],
    required: true
  },
  className: {
    type: String,
    default: ''
  },
  creatorName: {
    type: String,
    default: ''
  },
  meetingId: {
    type: [String, Number],
    default: null
  },
  isOpen: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['recording-started', 'recording-stopped', 'chunk-uploaded', 'close'])

// 모달 상태
const position = ref({ x: 50, y: 50 })
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

// 녹음 상태
const isRecording = ref(false)
const isPaused = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])
const recordingTime = ref(0)
const recordingTimer = ref(null)
const chunkTimer = ref(null)
const hasRecordedData = ref(false)

// 청크 관련
const CHUNK_DURATION = 60 * 1000 // 5초 (밀리초)
//const CHUNK_DURATION = 5 * 60 * 1000 // 5분 (밀리초)
const currentChunk = ref(0)
const totalChunks = ref(0)
const chunkStartTime = ref(0)

// 업로드 상태
const uploadStatus = ref(null)
const summaryStatus = ref(null)
const isGeneratingSummary = ref(false)

// API 기본 URL
const API_BASE_URL = 'https://api.studywithtymee.com'

// 드래그 시작
const startDrag = (event) => {
  event.preventDefault()
  isDragging.value = true
  
  const rect = event.currentTarget.getBoundingClientRect()
  dragOffset.value = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  }
  
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag)
  document.addEventListener('touchend', stopDrag)
}

// 드래그 중
const onDrag = (event) => {
  if (!isDragging.value) return
  
  event.preventDefault()
  
  const clientX = event.clientX || event.touches[0].clientX
  const clientY = event.clientY || event.touches[0].clientY
  
  position.value = {
    x: clientX - dragOffset.value.x,
    y: clientY - dragOffset.value.y
  }
}

// 드래그 종료
const stopDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
}

// 녹음 시작
const startRecording = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ 
      audio: {
        sampleRate: 44100,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true
      } 
    })
    
    // WAV 포맷을 위한 설정 (백엔드에서 WAV 파일을 기대하므로)
    const options = {
      mimeType: 'audio/wav'
    }
    
    // WAV가 지원되지 않는 경우 대체 포맷 사용
    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
      options.mimeType = 'audio/webm;codecs=opus'
      console.warn('WAV가 지원되지 않아 WebM/Opus 사용')
    }
    
    mediaRecorder.value = new MediaRecorder(stream, options)
    
    // 녹음 데이터 수집
    mediaRecorder.value.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.value.push(event.data)
      }
    }
    
    // 녹음 시작
    mediaRecorder.value.start(1000) // 1초마다 데이터 수집
    isRecording.value = true
    isPaused.value = false
    recordingTime.value = 0
    currentChunk.value = 1
    chunkStartTime.value = Date.now()
    
    // 타이머 시작
    startTimers()
    
    // 백엔드에 수업 시작 알림
    await notifyRecordingStart()
    
    emit('recording-started')
    
  } catch (error) {
    console.error('녹음 시작 실패:', error)
    alert('마이크 권한이 필요합니다.')
  }
}

// 녹음 정지 (일시정지)
const pauseRecording = async () => {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.pause()
    isRecording.value = false
    isPaused.value = true
    stopTimers()
    
    // 백엔드에 일시정지 알림
    await notifyRecordingPause()
  }
}

// 녹음 재개
const resumeRecording = async () => {
  if (mediaRecorder.value && isPaused.value) {
    mediaRecorder.value.resume()
    isRecording.value = true
    isPaused.value = false
    startTimers()
    
    // 백엔드에 재개 알림
    await notifyRecordingResume()
  }
}


// 타이머 시작
const startTimers = () => {
  // 녹음 시간 타이머
  recordingTimer.value = setInterval(() => {
    recordingTime.value += 1000
  }, 1000)
  
  // 청크 타이머
  chunkTimer.value = setInterval(() => {
    if (isRecording.value) {
      // 현재 청크 종료 및 새 청크 시작
      if (mediaRecorder.value) {
        mediaRecorder.value.stop()
        // 청크 전송 후 새 녹음 시작
        sendChunk().then(() => {
          if (isRecording.value && mediaRecorder.value) {
            mediaRecorder.value.start(1000)
          }
        })
      }
      currentChunk.value++
    }
  }, CHUNK_DURATION)
}

// 타이머 정지
const stopTimers = () => {
  if (recordingTimer.value) {
    clearInterval(recordingTimer.value)
    recordingTimer.value = null
  }
  if (chunkTimer.value) {
    clearInterval(chunkTimer.value)
    chunkTimer.value = null
  }
}

// 청크 전송 (HTTP API 사용)
const sendChunk = async () => {
  if (audioChunks.value.length === 0) return
  
  try {
    uploadStatus.value = {
      type: 'uploading',
      message: `오디오 청크 ${currentChunk.value} 전송 중...`,
      progress: 0
    }
    
    const audioBlob = new Blob(audioChunks.value, { 
      type: mediaRecorder.value.mimeType 
    })
    
    // FormData를 사용하여 파일 업로드
    const formData = new FormData()
    formData.append('audio', audioBlob, `chunk_${currentChunk.value}.wav`)
    
    // meetingId는 문서 요약 시에만 전송하므로 여기서는 제거
    
    // HTTP API를 통해 청크 업로드
    const response = await fetch(`${API_BASE_URL}/api/class/${props.classId}/update-recording`, {
      method: 'POST',
      body: formData
    })
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    const result = await response.json()
    console.log('✅ 청크 업로드 성공:', result)
    
    uploadStatus.value = {
      type: 'success',
      message: `오디오 청크 ${currentChunk.value} 업로드 완료`,
      progress: 100
    }
    
    emit('chunk-uploaded', {
      chunkNumber: currentChunk.value,
      filename: result.filename,
      timestamp: Date.now()
    })
    
    // 3초 후 상태 초기화
    setTimeout(() => {
      uploadStatus.value = null
    }, 3000)
    
  } catch (error) {
    console.error('청크 전송 실패:', error)
    uploadStatus.value = {
      type: 'error',
      message: `오디오 청크 ${currentChunk.value} 전송 실패: ${error.message}`,
      progress: 0
    }
  }
  
  // 청크 데이터 초기화
  audioChunks.value = []
}

// 문서 요약 생성
const generateSummary = async () => {
  try {
    console.log('🔍 generateSummary 호출됨')
    console.log('🔍 props.meetingId:', props.meetingId)
    console.log('🔍 props.meetingId 타입:', typeof props.meetingId)
    console.log('🔍 props.meetingId === undefined:', props.meetingId === undefined)
    console.log('🔍 props.meetingId === null:', props.meetingId === null)
    console.log('🔍 props.meetingId === ""', props.meetingId === "")
    console.log('🔍 props.classId:', props.classId)
    console.log('🔍 props.className:', props.className)
    console.log('🔍 props.creatorName:', props.creatorName)
    
    isGeneratingSummary.value = true
    summaryStatus.value = {
      type: 'uploading',
      message: '문서 요약 생성 중...'
    }
    
    // 마지막 청크가 있다면 먼저 전송
    if (audioChunks.value.length > 0) {
      await sendChunk()
    }
    
    const requestBody = {
      totalChunks: currentChunk.value,
      generateSummary: true,
      endTime: Date.now(),
      meetingId: props.meetingId // meetingId 추가
    }
    
    console.log('🔍 generateSummary - 요청 본문:', requestBody)
    console.log('🔍 generateSummary - JSON 문자열:', JSON.stringify(requestBody))
    console.log('🔍 generateSummary - 요청 URL:', `${API_BASE_URL}/api/class/${props.classId}/stop-recording`)
    
    // 백엔드에 문서 요약 요청
    const response = await fetch(`${API_BASE_URL}/api/class/${props.classId}/stop-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    })
    
    console.log('🔍 generateSummary - 응답 상태:', response.status)
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      console.log('🔍 generateSummary - 에러 응답:', errorData)
      throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`)
    }
    
    const result = await response.json()
    console.log('🔍 generateSummary - 성공 응답:', result)
    console.log('✅ 문서 요약 성공:', result)
    
    if (result.recordingStopped) {
      summaryStatus.value = {
        type: 'success',
        message: '문서 요약이 시작되었습니다! 처리 완료까지 잠시 기다려주세요.'
      }
      
      // 녹음 상태 초기화
      hasRecordedData.value = false
      currentChunk.value = 0
      totalChunks.value = 0
      recordingTime.value = 0
      
      // 스트림 정리
      if (mediaRecorder.value) {
        mediaRecorder.value.stream.getTracks().forEach(track => track.stop())
        mediaRecorder.value = null
      }
    } else {
      summaryStatus.value = {
        type: 'error',
        message: '문서 요약 처리에 실패했습니다.'
      }
    }
    
    // 5초 후 상태 초기화
    setTimeout(() => {
      summaryStatus.value = null
    }, 5000)
    
  } catch (error) {
    console.error('문서 요약 실패:', error)
    summaryStatus.value = {
      type: 'error',
      message: `문서 요약 실패: ${error.message}`
    }
  } finally {
    isGeneratingSummary.value = false
  }
}

// 수업 시작 알림
const notifyRecordingStart = async () => {
  try {
    console.log('🔍 notifyRecordingStart 호출됨')
    console.log('🔍 props.meetingId:', props.meetingId)
    console.log('🔍 props.classId:', props.classId)
    console.log('🔍 props.className:', props.className)
    console.log('🔍 props.creatorName:', props.creatorName)
    
    const requestBody = {
      className: props.className,
      creatorName: props.creatorName,
      startTime: Date.now()
    }
    
    console.log('🔍 notifyRecordingStart - 요청 본문:', requestBody)
    console.log('🔍 notifyRecordingStart - JSON 문자열:', JSON.stringify(requestBody))
    console.log('🔍 notifyRecordingStart - 요청 URL:', `${API_BASE_URL}/api/class/${props.classId}/start-recording`)
    
    const response = await fetch(`${API_BASE_URL}/api/class/${props.classId}/start-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    })
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    const result = await response.json()
    console.log('✅ 수업 시작 알림 성공:', result)
    
  } catch (error) {
    console.error('수업 시작 알림 실패:', error)
  }
}

// 일시정지 알림
const notifyRecordingPause = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/api/class/${props.classId}/pause-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        pauseTime: Date.now(),
        currentChunk: currentChunk.value
      })
    })
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    const result = await response.json()
    console.log('✅ 일시정지 알림 성공:', result)
    
  } catch (error) {
    console.error('일시정지 알림 실패:', error)
  }
}

// 재개 알림
const notifyRecordingResume = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/api/class/${props.classId}/resume-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        resumeTime: Date.now(),
        currentChunk: currentChunk.value
      })
    })
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    
    const result = await response.json()
    console.log('✅ 재개 알림 성공:', result)
    
  } catch (error) {
    console.error('재개 알림 실패:', error)
  }
}

// 시간 포맷팅
const formatTime = (milliseconds) => {
  const seconds = Math.floor(milliseconds / 1000)
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
}

// 컴포넌트 마운트 시 초기 위치 설정
onMounted(() => {
  // 화면 중앙에 위치
  const screenWidth = window.innerWidth
  const screenHeight = window.innerHeight
  position.value = {
    x: (screenWidth - 200) / 2,
    y: (screenHeight - 150) / 2
  }
})

// 컴포넌트 언마운트 시 정리
onUnmounted(async () => {
  if (isRecording.value || isPaused.value) {
    // 녹음 중이거나 정지된 상태라면 정리
    if (mediaRecorder.value) {
      mediaRecorder.value.stop()
      mediaRecorder.value.stream.getTracks().forEach(track => track.stop())
    }
    stopTimers()
    
    // 마지막 청크가 있다면 전송
    if (audioChunks.value.length > 0) {
      await sendChunk()
    }
    
    // 백엔드에 일시정지 알림
    try {
      const requestBody = {
        totalChunks: currentChunk.value,
        endTime: Date.now(),
        meetingId: props.meetingId // meetingId 추가
      }
      
      console.log('🔍 일시정지 - 요청 본문:', requestBody)
      console.log('🔍 일시정지 - JSON 문자열:', JSON.stringify(requestBody))
      
      await fetch(`${API_BASE_URL}/api/class/${props.classId}/pause-recording`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
      })
    } catch (error) {
      console.error('일시정지 알림 실패:', error)
    }
  }
  stopDrag()
})

// 부모 제어를 위한 메서드/상태 노출
defineExpose({
  startRecording,
  pauseRecording,
  resumeRecording,
  generateSummary,
  isRecording,
  isPaused
})
</script>

<style scoped>
.audio-recorder-modal {
  position: fixed;
  width: 200px;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 12px;
  color: white;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  z-index: 1000;
  user-select: none;
  cursor: move;
}

.modal-content {
  padding: 16px;
  position: relative;
}

.recorder-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.record-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 100px;
}

.start-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
}

.pause-btn {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
}

.pause-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(245, 158, 11, 0.3);
}

.resume-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
}

.resume-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3);
}

.summary-btn {
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  color: white;
}

.summary-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(139, 92, 246, 0.3);
}

.summary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.recording-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 12px;
}

.recording-dot {
  width: 6px;
  height: 6px;
  background: #ef4444;
  border-radius: 50%;
  animation: pulse 1s infinite;
}

.recording-dot.paused {
  background: #f59e0b;
  animation: none;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.recording-time {
  font-family: monospace;
  font-size: 16px;
  font-weight: 600;
}

.chunk-info {
  font-size: 11px;
  opacity: 0.8;
}

.upload-status, .summary-status {
  margin-top: 8px;
}

.status-message {
  font-size: 12px;
  margin-bottom: 6px;
  text-align: center;
}

.status-message.uploading {
  color: #fbbf24;
}

.status-message.success {
  color: #10b981;
}

.status-message.error {
  color: #ef4444;
}

.progress-bar {
  width: 100%;
  height: 3px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #059669);
  transition: width 0.3s ease;
}

.close-button {
  position: absolute;
  top: 6px;
  right: 6px;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

/* 드래그 중일 때 스타일 */
.audio-recorder-modal:active {
  cursor: grabbing;
}

/* 모바일 터치 지원 */
@media (max-width: 768px) {
  .audio-recorder-modal {
    width: 180px;
  }
}
</style>
