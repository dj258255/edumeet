<template>
  <div class="room-view">
    <div class="room-header">
      <div class="room-info">
        <h1 class="room-title">{{ roomTitle }}</h1>
        <p class="room-description">{{ roomDescription }}</p>
      </div>
      <div class="room-actions">
        <button class="btn btn-secondary" @click="copyRoomLink">
          링크 복사
        </button>
        <button class="btn btn-danger" @click="leaveRoom">
          나가기
        </button>
      </div>
    </div>
    
    <div class="room-content">
      <div class="video-container">
        <div class="main-video">
          <div class="video-placeholder">
            <div class="video-icon">📹</div>
            <p>메인 비디오</p>
          </div>
        </div>
        
        <div class="participants-grid">
          <div class="participant" v-for="i in 4" :key="i">
            <div class="participant-video">
              <div class="video-placeholder small">
                <div class="video-icon">👤</div>
              </div>
            </div>
            <div class="participant-name">참가자 {{ i }}</div>
          </div>
        </div>
      </div>
      
      <div class="controls">
        <div class="control-group">
          <button class="control-btn" :class="{ active: isMicOn }" @click="toggleMic">
            <span class="control-icon">{{ isMicOn ? '🎤' : '🔇' }}</span>
            <span class="control-label">{{ isMicOn ? '마이크 켜짐' : '마이크 꺼짐' }}</span>
          </button>
          
          <button class="control-btn" :class="{ active: isCameraOn }" @click="toggleCamera">
            <span class="control-icon">{{ isCameraOn ? '📹' : '🚫' }}</span>
            <span class="control-label">{{ isCameraOn ? '카메라 켜짐' : '카메라 꺼짐' }}</span>
          </button>
          
          <button class="control-btn" @click="toggleScreenShare">
            <span class="control-icon">🖥️</span>
            <span class="control-label">화면 공유</span>
          </button>
          
          <button class="control-btn" @click="toggleChat">
            <span class="control-icon">💬</span>
            <span class="control-label">채팅</span>
          </button>
        </div>
        
        <div class="main-controls">
          <button class="control-btn danger" @click="leaveRoom">
            <span class="control-icon">📞</span>
            <span class="control-label">통화 종료</span>
          </button>
        </div>
      </div>
    </div>
    
    <!-- 채팅 패널 -->
    <div class="chat-panel" v-if="showChat">
      <div class="chat-header">
        <h3>채팅</h3>
        <button class="close-btn" @click="toggleChat">✕</button>
      </div>
      <div class="chat-messages">
        <div class="message" v-for="message in messages" :key="message.id">
          <div class="message-sender">{{ message.sender }}</div>
          <div class="message-content">{{ message.content }}</div>
          <div class="message-time">{{ message.time }}</div>
        </div>
      </div>
      <div class="chat-input">
        <input 
          type="text" 
          v-model="newMessage" 
          @keyup.enter="sendMessage"
          placeholder="메시지를 입력하세요..."
          class="message-input"
        />
        <button class="send-btn" @click="sendMessage">전송</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const roomTitle = ref('새로운 수업')
const roomDescription = ref('온라인 수업이 진행 중입니다')
const isMicOn = ref(true)
const isCameraOn = ref(true)
const showChat = ref(false)
const newMessage = ref('')
const messages = ref([
  { id: 1, sender: '선생님', content: '안녕하세요! 수업을 시작하겠습니다.', time: '14:30' },
  { id: 2, sender: '학생1', content: '네, 선생님!', time: '14:31' },
  { id: 3, sender: '학생2', content: '준비되었습니다.', time: '14:31' }
])

const toggleMic = () => {
  isMicOn.value = !isMicOn.value
}

const toggleCamera = () => {
  isCameraOn.value = !isCameraOn.value
}

const toggleScreenShare = () => {
  alert('화면 공유 기능은 준비 중입니다.')
}

const toggleChat = () => {
  showChat.value = !showChat.value
}

const sendMessage = () => {
  if (newMessage.value.trim()) {
    messages.value.push({
      id: Date.now(),
      sender: '나',
      content: newMessage.value,
      time: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
    })
    newMessage.value = ''
  }
}

const copyRoomLink = () => {
  const roomLink = window.location.href
  navigator.clipboard.writeText(roomLink).then(() => {
    alert('방 링크가 복사되었습니다!')
  })
}

const leaveRoom = () => {
  if (confirm('정말로 방을 나가시겠습니까?')) {
    router.push('/')
  }
}

onMounted(() => {
  // 실제 구현에서는 WebRTC 연결 등을 여기서 설정
  console.log('방 ID:', route.params.roomId)
})
</script>

<style scoped>
.room-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg) var(--spacing-xl);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
}

.room-info {
  flex: 1;
}

.room-title {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--brand-main);
  margin: 0 0 var(--spacing-xs) 0;
}

.room-description {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0;
}

.room-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn {
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-normal);
  border: none;
}

.btn-secondary {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-secondary:hover {
  background: var(--bg-tertiary);
}

.btn-danger {
  background: #e74c3c;
  color: white;
}

.btn-danger:hover {
  background: #c0392b;
}

.room-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
}

.video-container {
  flex: 1;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.main-video {
  flex: 1;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--border-color);
}

.video-placeholder {
  text-align: center;
  color: var(--text-secondary);
}

.video-placeholder.small {
  font-size: var(--font-size-sm);
}

.video-icon {
  font-size: 3rem;
  margin-bottom: var(--spacing-sm);
}

.video-placeholder.small .video-icon {
  font-size: 1.5rem;
  margin-bottom: var(--spacing-xs);
}

.participants-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-md);
}

.participant {
  text-align: center;
}

.participant-video {
  background: var(--bg-primary);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  margin-bottom: var(--spacing-sm);
}

.participant-name {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-weight: 500;
}

.controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg) var(--spacing-xl);
  background: var(--bg-primary);
  border-top: 1px solid var(--border-color);
}

.control-group {
  display: flex;
  gap: var(--spacing-md);
}

.control-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  min-width: 80px;
}

.control-btn:hover {
  background: var(--bg-tertiary);
  transform: translateY(-2px);
}

.control-btn.active {
  background: var(--brand-main);
  color: var(--text-inverse);
  border-color: var(--brand-main);
}

.control-btn.danger {
  background: #e74c3c;
  color: white;
  border-color: #e74c3c;
}

.control-btn.danger:hover {
  background: #c0392b;
}

.control-icon {
  font-size: var(--font-size-lg);
}

.control-label {
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.main-controls {
  display: flex;
  gap: var(--spacing-md);
}

.chat-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 300px;
  background: var(--bg-primary);
  border-left: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.chat-header h3 {
  margin: 0;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  font-size: var(--font-size-lg);
  cursor: pointer;
  color: var(--text-secondary);
}

.chat-messages {
  flex: 1;
  padding: var(--spacing-md);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.message {
  padding: var(--spacing-sm);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.message-sender {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--brand-main);
  margin-bottom: var(--spacing-xs);
}

.message-content {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.chat-input {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border-top: 1px solid var(--border-color);
}

.message-input {
  flex: 1;
  padding: var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
}

.send-btn {
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--brand-main);
  color: var(--text-inverse);
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--font-size-sm);
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .room-header {
    flex-direction: column;
    gap: var(--spacing-md);
    text-align: center;
  }
  
  .room-actions {
    width: 100%;
    justify-content: center;
  }
  
  .controls {
    flex-direction: column;
    gap: var(--spacing-md);
  }
  
  .control-group {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .control-btn {
    min-width: 70px;
  }
  
  .chat-panel {
    width: 100%;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1000;
  }
}
</style> 