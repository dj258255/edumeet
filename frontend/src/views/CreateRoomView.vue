<template>
  <div class="create-room-view">
    <div class="container">
      <div class="header">
        <h1 class="title">화상수업 방 만들기</h1>
        <p class="subtitle">새로운 온라인 수업을 시작해보세요</p>
      </div>
      
      <div class="form-container">
        <form @submit.prevent="createRoom" class="room-form">
          <div class="form-group">
            <label for="roomTitle" class="form-label">방 제목</label>
            <input 
              type="text" 
              id="roomTitle" 
              v-model="roomData.title" 
              class="form-input"
              placeholder="수업 제목을 입력하세요"
              required
            />
          </div>
          
          <div class="form-group">
            <label for="roomDescription" class="form-label">방 설명</label>
            <textarea 
              id="roomDescription" 
              v-model="roomData.description" 
              class="form-textarea"
              placeholder="수업에 대한 간단한 설명을 입력하세요"
              rows="3"
            ></textarea>
          </div>
          
          <div class="form-group">
            <label for="roomPassword" class="form-label">비밀번호 (선택사항)</label>
            <input 
              type="password" 
              id="roomPassword" 
              v-model="roomData.password" 
              class="form-input"
              placeholder="방 비밀번호를 설정하세요"
            />
            <small class="form-help">비밀번호를 설정하면 초대받은 사람만 입장할 수 있습니다</small>
          </div>
          
          <div class="form-group">
            <label class="form-label">방 설정</label>
            <div class="checkbox-group">
              <label class="checkbox-label">
                <input 
                  type="checkbox" 
                  v-model="roomData.allowChat"
                  class="checkbox-input"
                />
                <span class="checkbox-text">채팅 허용</span>
              </label>
              
              <label class="checkbox-label">
                <input 
                  type="checkbox" 
                  v-model="roomData.allowScreenShare"
                  class="checkbox-input"
                />
                <span class="checkbox-text">화면 공유 허용</span>
              </label>
              
              <label class="checkbox-label">
                <input 
                  type="checkbox" 
                  v-model="roomData.recordSession"
                  class="checkbox-input"
                />
                <span class="checkbox-text">수업 녹화</span>
              </label>
            </div>
          </div>
          
          <div class="form-actions">
            <router-link to="/" class="btn btn-secondary">
              취소
            </router-link>
            <button type="submit" class="btn btn-primary" :disabled="isCreating">
              <span v-if="isCreating">생성 중...</span>
              <span v-else>방 만들기</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const isCreating = ref(false)

const roomData = reactive({
  title: '',
  description: '',
  password: '',
  allowChat: true,
  allowScreenShare: true,
  recordSession: false
})

const createRoom = async () => {
  if (!roomData.title.trim()) {
    alert('방 제목을 입력해주세요.')
    return
  }
  
  isCreating.value = true
  
  try {
    // 실제 API 호출 대신 시뮬레이션
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    // 방 생성 성공 시 방으로 이동
    const roomId = Math.random().toString(36).substr(2, 9)
    router.push(`/room/${roomId}`)
  } catch (error) {
    console.error('방 생성 실패:', error)
    alert('방 생성에 실패했습니다. 다시 시도해주세요.')
  } finally {
    isCreating.value = false
  }
}
</script>

<style scoped>
.create-room-view {
  min-height: 100vh;
  background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
  padding: var(--spacing-xl) 0;
}

.container {
  max-width: 600px;
  margin: 0 auto;
  padding: 0 var(--spacing-lg);
}

.header {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
}

.title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--brand-main);
  margin-bottom: var(--spacing-md);
}

.subtitle {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
  line-height: 1.6;
}

.form-container {
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-2xl);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
}

.room-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.form-label {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}

.form-input,
.form-textarea {
  padding: var(--spacing-md);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
  background: var(--bg-secondary);
  color: var(--text-primary);
  transition: all var(--transition-normal);
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--brand-main);
  box-shadow: 0 0 0 3px rgba(34, 122, 83, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

.form-help {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: var(--spacing-xs);
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  transition: background-color var(--transition-normal);
}

.checkbox-label:hover {
  background: var(--bg-tertiary);
}

.checkbox-input {
  width: 18px;
  height: 18px;
  accent-color: var(--brand-main);
}

.checkbox-text {
  font-size: var(--font-size-base);
  color: var(--text-primary);
  font-weight: 500;
}

.form-actions {
  display: flex;
  gap: var(--spacing-md);
  justify-content: flex-end;
  margin-top: var(--spacing-lg);
}

.btn {
  padding: var(--spacing-md) var(--spacing-xl);
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-normal);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  box-shadow: var(--shadow-sm);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-secondary {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 2px solid var(--border-color);
}

.btn-secondary:hover {
  background: var(--bg-tertiary);
  border-color: var(--brand-main);
  color: var(--brand-main);
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .container {
    padding: 0 var(--spacing-md);
  }
  
  .title {
    font-size: var(--font-size-3xl);
  }
  
  .subtitle {
    font-size: var(--font-size-base);
  }
  
  .form-container {
    padding: var(--spacing-lg);
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .btn {
    width: 100%;
  }
}
</style> 