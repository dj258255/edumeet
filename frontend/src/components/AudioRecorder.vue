<template>
  <div class="audio-recorder">
    <div class="recorder-controls">
      <button 
        @click="startRecording" 
        :disabled="isRecording"
        class="record-btn start-btn"
      >
        🎤 수업 시작
      </button>
      
      <button 
        @click="stopRecording" 
        :disabled="!isRecording"
        class="record-btn stop-btn"
      >
        ⏹️ 수업 종료
      </button>
    </div>
    
    <div v-if="isRecording" class="recording-status">
      <div class="status-indicator">
        <span class="recording-dot"></span>
        녹음 중...
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
  </div>
</template>

<script setup>
import { ref, onUnmounted, computed } from 'vue'

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
  }
})

const emit = defineEmits(['recording-started', 'recording-stopped', 'chunk-uploaded'])

// 녹음 상태
const isRecording = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])
const recordingTime = ref(0)
const recordingTimer = ref(null)
const chunkTimer = ref(null)

// WebSocket 관련
const websocket = ref(null)
const isWebSocketConnected = ref(false)

// 청크 관련
const CHUNK_DURATION = 5 * 60 * 1000 // 5분 (밀리초)
const currentChunk = ref(0)
const totalChunks = ref(0)
const chunkStartTime = ref(0)

// 업로드 상태
const uploadStatus = ref(null)

// WebSocket 연결
const connectWebSocket = () => {
  try {
    // Spring Boot WebSocket 서버 URL
    const wsUrl = `ws://localhost:8080/ws`
    websocket.value = new WebSocket(wsUrl)
    
    websocket.value.onopen = () => {
      console.log('🎤 WebSocket 연결 성공')
      isWebSocketConnected.value = true
    }
    
    websocket.value.onmessage = (event) => {
      const data = JSON.parse(event.data)
      console.log('📥 WebSocket 메시지 수신:', data)
      
      if (data.type === 'chunk-uploaded') {
        uploadStatus.value = {
          type: 'success',
          message: `청크 ${data.chunkNumber} 업로드 완료`,
          progress: 100
        }
        
        emit('chunk-uploaded', {
          chunkNumber: data.chunkNumber,
          timestamp: data.timestamp
        })
        
        // 3초 후 상태 초기화
        setTimeout(() => {
          uploadStatus.value = null
        }, 3000)
      } else if (data.type === 'error') {
        uploadStatus.value = {
          type: 'error',
          message: data.message || '전송 실패',
          progress: 0
        }
      }
    }
    
    websocket.value.onerror = (error) => {
      console.error('🎤 WebSocket 오류:', error)
      isWebSocketConnected.value = false
    }
    
    websocket.value.onclose = () => {
      console.log('🎤 WebSocket 연결 종료')
      isWebSocketConnected.value = false
    }
    
  } catch (error) {
    console.error('🎤 WebSocket 연결 실패:', error)
  }
}

// WebSocket 연결 해제
const disconnectWebSocket = () => {
  if (websocket.value) {
    websocket.value.close()
    websocket.value = null
    isWebSocketConnected.value = false
  }
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
    
    // MP3 인코딩을 위한 설정
    const options = {
      mimeType: 'audio/mp3',
      audioBitsPerSecond: 128000
    }
    
    // MP3가 지원되지 않는 경우 대체 포맷 사용
    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
      options.mimeType = 'audio/webm;codecs=opus'
      console.warn('MP3가 지원되지 않아 WebM/Opus 사용')
    }
    
    mediaRecorder.value = new MediaRecorder(stream, options)
    
    // 녹음 데이터 수집
    mediaRecorder.value.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.value.push(event.data)
      }
    }
    
    // 청크 전송
    mediaRecorder.value.onstop = () => {
      sendChunk()
    }
    
    // WebSocket 연결
    connectWebSocket()
    
    // 녹음 시작
    mediaRecorder.value.start(1000) // 1초마다 데이터 수집
    isRecording.value = true
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

// 녹음 종료
const stopRecording = async () => {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.stop()
    mediaRecorder.value.stream.getTracks().forEach(track => track.stop())
    
    isRecording.value = false
    stopTimers()
    
    // 마지막 청크 전송
    if (audioChunks.value.length > 0) {
      await sendChunk()
    }
    
    // WebSocket 연결 해제
    disconnectWebSocket()
    
    // 백엔드에 수업 종료 알림
    await notifyRecordingStop()
    
    emit('recording-stopped')
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
        mediaRecorder.value.start(1000)
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

// 청크 전송 (WebSocket 사용)
const sendChunk = async () => {
  if (audioChunks.value.length === 0) return
  
  try {
    uploadStatus.value = {
      type: 'uploading',
      message: `청크 ${currentChunk.value} 전송 중...`,
      progress: 0
    }
    
    const audioBlob = new Blob(audioChunks.value, { 
      type: mediaRecorder.value.mimeType 
    })
    
    // WebSocket을 통해 청크 데이터 전송
    if (websocket.value && isWebSocketConnected.value) {
      const chunkData = {
        type: 'audio-chunk',
        classId: props.classId,
        chunkNumber: currentChunk.value,
        timestamp: Date.now(),
        duration: CHUNK_DURATION,
        audioData: await blobToBase64(audioBlob)
      }
      
      websocket.value.send(JSON.stringify(chunkData))
      
      // 진행률 시뮬레이션 (실제로는 서버에서 응답)
      uploadStatus.value.progress = 50
      setTimeout(() => {
        uploadStatus.value.progress = 100
      }, 1000)
      
    } else {
      throw new Error('WebSocket 연결이 없습니다.')
    }
    
  } catch (error) {
    console.error('청크 전송 실패:', error)
    uploadStatus.value = {
      type: 'error',
      message: `청크 ${currentChunk.value} 전송 실패`,
      progress: 0
    }
  }
  
  // 청크 데이터 초기화
  audioChunks.value = []
}

// Blob을 Base64로 변환
const blobToBase64 = (blob) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const base64 = reader.result.split(',')[1] // data:audio/mp3;base64, 부분 제거
      resolve(base64)
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

// 수업 시작 알림
const notifyRecordingStart = async () => {
  try {
    await fetch(`/api/class/${props.classId}/start-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        className: props.className,
        creatorName: props.creatorName,
        startTime: Date.now()
      })
    })
  } catch (error) {
    console.error('수업 시작 알림 실패:', error)
  }
}

// 수업 종료 알림
const notifyRecordingStop = async () => {
  try {
    await fetch(`/api/class/${props.classId}/stop-recording`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        endTime: Date.now(),
        totalChunks: currentChunk.value
      })
    })
  } catch (error) {
    console.error('수업 종료 알림 실패:', error)
  }
}

// 시간 포맷팅
const formatTime = (milliseconds) => {
  const seconds = Math.floor(milliseconds / 1000)
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
}

// 컴포넌트 언마운트 시 정리
onUnmounted(() => {
  if (isRecording.value) {
    stopRecording()
  }
  disconnectWebSocket()
})
</script>

<style scoped>
.audio-recorder {
  padding: 20px;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 12px;
  color: white;
}

.recorder-controls {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.record-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.start-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.start-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
}

.stop-btn {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: white;
}

.stop-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(239, 68, 68, 0.3);
}

.record-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.recording-status {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.recording-dot {
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.recording-time {
  font-family: monospace;
  font-size: 18px;
  font-weight: 600;
}

.chunk-info {
  font-size: 12px;
  opacity: 0.8;
}

.upload-status {
  margin-top: 12px;
}

.status-message {
  font-size: 14px;
  margin-bottom: 8px;
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
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #059669);
  transition: width 0.3s ease;
}
</style>
