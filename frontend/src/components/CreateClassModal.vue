<template>
  <div v-if="isOpen" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">화상수업 생성</h3>
        <button class="close-btn" @click="$emit('close')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6L18 18"/>
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <div class="form-group">
          <label for="title">화상수업 제목 *</label>
          <input
            id="title"
            v-model="title"
            type="text"
            placeholder="화상수업 제목을 입력하세요"
            class="form-input"
            :class="{ 'error': titleError }"
          />
          <span v-if="titleError" class="error-message">{{ titleError }}</span>
        </div>

        <div class="form-group">
          <label for="description">화상수업 설명</label>
          <textarea
            id="description"
            v-model="description"
            placeholder="화상수업에 대한 설명을 입력하세요 (선택사항)"
            class="form-textarea"
            rows="3"
          ></textarea>
        </div>

        <div class="form-group">
          <label for="creatorName">참여자 이름 *</label>
          <input
            id="creatorName"
            v-model="creatorName"
            type="text"
            placeholder="참여자 이름을 입력하세요"
            class="form-input"
            :class="{ 'error': creatorNameError }"
          />
          <span v-if="creatorNameError" class="error-message">{{ creatorNameError }}</span>
        </div>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" @click="$emit('close')">취소</button>
        <button
          class="create-btn"
          @click="handleCreate"
          :disabled="!isFormValid || isCreating"
        >
          <span v-if="isCreating" class="loading-spinner">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </span>
          {{ isCreating ? '생성 중...' : '화상수업 생성' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import apiClient from '@/utils/apiClient'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  defaultClassName: {
    type: String,
    default: ''
  },
  classId: {
    type: [String, Number],
    default: ''
  }
})

const emit = defineEmits(['close', 'create'])

const title = ref('')
const description = ref('')
const creatorName = ref('')
const isCreating = ref(false)

// 에러 메시지
const titleError = ref('')
const creatorNameError = ref('')

// 폼 유효성 검사
const isFormValid = computed(() => {
  return title.value.trim() && creatorName.value.trim() && !titleError.value && !creatorNameError.value
})

// 기본 클래스 이름이 있으면 자동으로 설정
watch(() => props.defaultClassName, (newValue) => {
  if (newValue) {
    title.value = newValue
  }
})

// 모달이 열릴 때마다 초기화
watch(() => props.isOpen, (newValue) => {
  if (newValue) {
    if (props.defaultClassName) {
      title.value = props.defaultClassName
    } else {
      title.value = ''
    }
    description.value = ''
    creatorName.value = ''
    titleError.value = ''
    creatorNameError.value = ''
    isCreating.value = false
  }
})

// 입력값 검증
const validateForm = () => {
  let isValid = true

  // 제목 검증
  if (!title.value.trim()) {
    titleError.value = '화상수업 제목을 입력해주세요.'
    isValid = false
  } else if (title.value.trim().length > 100) {
    titleError.value = '제목은 100자 이내로 입력해주세요.'
    isValid = false
  } else {
    titleError.value = ''
  }

  // 참여자 이름 검증
  if (!creatorName.value.trim()) {
    creatorNameError.value = '참여자 이름을 입력해주세요.'
    isValid = false
  } else if (creatorName.value.trim().length > 50) {
    creatorNameError.value = '이름은 50자 이내로 입력해주세요.'
    isValid = false
  } else {
    creatorNameError.value = ''
  }

  return isValid
}

const handleCreate = async () => {
  if (!validateForm()) {
    return
  }

  if (!props.classId) {
    alert('클래스 ID가 없습니다. 다시 시도해주세요.')
    return
  }

  isCreating.value = true

  try {
    // API 요청 데이터 준비 (백엔드 MeetingCreateRequestDto에 맞춤)
    const requestData = {
      title: title.value.trim(),
      participantName: creatorName.value.trim(),
      classId: Number(props.classId)
    }

    console.log('🔍 화상수업 생성 요청:', requestData)

    // POST /api/v1/meetingroom/token 엔드포인트 호출
    const response = await apiClient.post('/meetingroom/token', requestData)

    console.log('🔍 화상수업 생성 및 토큰 응답:', response.data)

    // localStorage에서 사용자 정보 가져오기
    const userStr = localStorage.getItem('user')
    let userEmail = ''
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userEmail = user.email || ''
      } catch (e) {
        console.error('사용자 정보 파싱 실패:', e)
      }
    }

    // 성공 시 부모 컴포넌트에 데이터 전달 (백엔드 응답 구조에 맞게 수정)
    emit('create', {
      meetingId: response.data.roomName || title.value.trim(), // roomName 또는 title 사용
      title: title.value.trim(), // 입력한 제목 사용
      email: userEmail, // localStorage에서 가져온 이메일 사용
      classId: props.classId,
      creatorName: creatorName.value.trim(),
      description: description.value.trim(),
      token: response.data.token, // 백엔드에서 반환하는 토큰
      roomName: response.data.roomName || title.value.trim(), // 원본 roomName 또는 title
      // 백엔드가 livekit.url 을 내려준다. 없으면 빌드 환경값까지만 본다.
      // 외부 SFU 주소를 하드코딩하면 운영 경로가 조용히 갈라진다.
      url: response.data.url || import.meta.env.VITE_LIVEKIT_URL || ''
    })

  } catch (error) {
    console.error('🔍 화상수업 생성 실패:', error)

    if (error.response?.status === 403) {
      alert('이 클래스의 화상수업을 생성할 권한이 없습니다.')
    } else if (error.response?.status === 400) {
      alert('잘못된 요청입니다. 입력값을 확인해주세요.')
    } else if (error.response?.status === 404) {
      alert('클래스를 찾을 수 없습니다.')
    } else {
      alert('화상수업 생성에 실패했습니다. 다시 시도해주세요.')
    }
  } finally {
    isCreating.value = false
  }
}

const handleOverlayClick = () => {
  if (!isCreating.value) {
    emit('close')
  }
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

.form-input.error {
  border-color: #f56565;
  box-shadow: 0 0 0 3px rgba(245, 101, 101, 0.1);
}

.form-textarea {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  font-size: 1rem;
  background: var(--bg-secondary);
  color: var(--text-primary);
  transition: all 0.2s ease;
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

.form-textarea:focus {
  outline: none;
  border-color: var(--brand-main);
  box-shadow: 0 0 0 3px rgba(34, 122, 83, 0.1);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: var(--text-tertiary);
}

.error-message {
  display: block;
  color: #f56565;
  font-size: 0.8rem;
  margin-top: 0.25rem;
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
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
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

.loading-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 다크모드 지원 */
.dark-mode .modal-content {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
}

.dark-mode .form-input,
.dark-mode .form-textarea {
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
