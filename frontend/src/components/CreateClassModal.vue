<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">수업 생성</h3>
        <button class="close-btn" @click="$emit('close')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6L18 18"/>
          </svg>
        </button>
      </div>
      
      <div class="modal-body">
        <div class="form-group">
          <label for="className">수업명</label>
          <input 
            id="className"
            v-model="className" 
            type="text" 
            placeholder="수업명을 입력하세요"
            class="form-input"
          />
        </div>
        
        <div class="form-group">
          <label for="creatorName">생성자 이름</label>
          <input 
            id="creatorName"
            v-model="creatorName" 
            type="text" 
            placeholder="생성자 이름을 입력하세요"
            class="form-input"
          />
        </div>
        
        <div class="form-group">
          <label for="roomName">반 이름</label>
          <input
            id="roomName"
            v-model="roomName" 
            type="text" 
            placeholder="방 이름을 입력하세요"
            class="form-input"
          />
        </div>
      </div>
      
      <div class="modal-footer">
        <button class="cancel-btn" @click="$emit('close')">취소</button>
        <button 
          class="create-btn" 
          @click="handleCreate"
          :disabled="!className || !creatorName || !roomName"
        >
          수업 생성
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
  defaultClassName: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close', 'create'])

const className = ref('')
const creatorName = ref('')
const roomName = ref('')

// 기본 클래스 이름이 있으면 자동으로 설정
watch(() => props.defaultClassName, (newValue) => {
  if (newValue) {
    className.value = newValue
    roomName.value = newValue
  }
})

// 기본 클래스 이름이 있으면 자동으로 설정
if (props.defaultClassName) {
  className.value = props.defaultClassName;
  roomName.value = props.defaultClassName;
  console.log('🔍 CreateClassModal - 기본 클래스 이름 설정됨:', props.defaultClassName);
}

// 모달이 열릴 때마다 초기화
watch(() => props.isOpen, (newValue) => {
  if (newValue) {
    if (props.defaultClassName) {
      className.value = props.defaultClassName
      roomName.value = props.defaultClassName
      console.log('🔍 CreateClassModal - 모달 열림 시 클래스 이름 설정:', props.defaultClassName);
    } else {
      className.value = ''
      roomName.value = ''
    }
    creatorName.value = ''
  }
})

const handleCreate = () => {
  if (className.value && creatorName.value && roomName.value) {
    emit('create', {
      className: className.value,
      creatorName: creatorName.value,
      roomName: roomName.value
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

.create-btn {
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

.create-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(34, 122, 83, 0.3);
}

.create-btn:disabled {
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
  .create-btn {
    flex: none;
  }
}
</style> 