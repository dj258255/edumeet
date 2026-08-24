<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">수업 참여</h3>
        <button class="close-btn" @click="$emit('close')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6L18 18"/>
          </svg>
        </button>
      </div>
      
      <div class="modal-body">
        <div class="class-info">
          <h4 class="class-title">{{ className }}</h4>
          <p class="class-description">{{ classDescription }}</p>
        </div>
        
        <div class="form-group">
          <label for="participantName">참여자 이름</label>
          <input 
            id="participantName"
            v-model="participantName" 
            type="text" 
            placeholder="참여자 이름을 입력하세요"
            class="form-input"
            @keyup.enter="handleJoin"
          />
        </div>
      </div>
      
      <div class="modal-footer">
        <button class="cancel-btn" @click="$emit('close')">취소</button>
        <button 
          class="join-btn" 
          @click="handleJoin"
          :disabled="!participantName"
        >
          수업 참여
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  className: {
    type: String,
    default: ''
  },
  classDescription: {
    type: String,
    default: ''
  },
  classId: {
    type: [String, Number],
    default: ''
  }
})

const emit = defineEmits(['close', 'join'])

const participantName = ref('')

// 모달이 열릴 때마다 초기화
watch(() => props.isOpen, (newValue) => {
  if (newValue) {
    participantName.value = ''
  }
})

const handleJoin = () => {
  console.log('🔍 JoinClassModal - props.classId:', props.classId)
  console.log('🔍 JoinClassModal - props.className:', props.className)
  console.log('🔍 JoinClassModal - participantName:', participantName.value)
  
  if (participantName.value) {
    emit('join', {
      classId: props.classId,
      className: props.className,
      participantName: participantName.value,
      roomName: props.className // className을 roomName으로 사용
    })
  }
}

const handleOverlayClick = () => {
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 0;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: modalSlideIn 0.3s ease-out;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem 1.5rem 0 1.5rem;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 1rem;
}

.modal-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-body {
  padding: 1.5rem;
}

.class-info {
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: var(--bg-secondary);
  border-radius: 8px;
  border-left: 4px solid var(--brand-main);
}

.class-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.class-description {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin: 0;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.form-input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  font-size: 1rem;
  background: var(--bg-secondary);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--brand-main);
  box-shadow: 0 0 0 3px rgba(34, 122, 83, 0.1);
}

.form-input::placeholder {
  color: var(--text-tertiary);
}

.modal-footer {
  display: flex;
  gap: 1rem;
  padding: 1rem 1.5rem 1.5rem 1.5rem;
  border-top: 1px solid var(--border-color);
}

.cancel-btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cancel-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--text-secondary);
}

.join-btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: white;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.join-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(34, 122, 83, 0.3);
}

.join-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

/* 다크모드 지원 */
.dark-mode .modal-content {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
}

.dark-mode .form-input {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.dark-mode .cancel-btn {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

/* 반응형 */
@media (max-width: 768px) {
  .modal-content {
    width: 95%;
    margin: 1rem;
  }
  
  .modal-footer {
    flex-direction: column;
  }
  
  .cancel-btn,
  .join-btn {
    flex: none;
  }
}
</style> 