<template>
  <div class="create-form-section" v-if="isVisible">
    <div class="form-card">
      <div class="form-header">
        <h2 class="form-title">반 정보 입력</h2>
        <button @click="$emit('close')" class="close-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6L18 18"/>
          </svg>
        </button>
      </div>
      
      <form @submit.prevent="handleSubmit" class="create-form">
        <div class="form-group">
          <label for="className" class="form-label">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M2 3H6L7.68 14.39C7.77 14.99 8.31 15.44 8.92 15.44H19.5C20.1 15.44 20.64 14.99 20.73 14.39L22 6H6"/>
            </svg>
            반 이름
          </label>
          <input 
            id="className"
            v-model="className" 
            type="text" 
            class="form-input"
            placeholder="예: 수학 기초반, 영어 회화반"
            required
          />
        </div>

        <div class="form-group">
          <label for="classDescription" class="form-label">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6A2 2 0 0 0 4 4V20A2 2 0 0 0 6 22H18A2 2 0 0 0 20 20V8L14 2Z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
            반 설명
          </label>
          <textarea 
            id="classDescription"
            v-model="classDescription" 
            class="form-textarea"
            placeholder="이 반에서 어떤 것을 배우나요? 목표나 특징을 설명해주세요."
            rows="4"
          ></textarea>
        </div>

        <div class="form-group">
          <label class="form-label">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21,15 16,10 5,21"/>
            </svg>
            반 이미지
          </label>
          <div class="file-upload-area" :class="{ 'has-file': imageFileName }">
            <input 
              type="file" 
              id="classImageInput" 
              @change="handleImageUpload" 
              accept="image/*" 
              class="file-input"
            />
            <label for="classImageInput" class="file-upload-label">
              <div class="upload-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15"/>
                  <path d="M7 10L12 15L17 10"/>
                  <path d="M12 15V3"/>
                </svg>
              </div>
              <span class="upload-text">{{ imageFileName ? '파일 변경' : '이미지 선택' }}</span>
            </label>
            <div v-if="imageFileName" class="file-info">
              <span class="file-name">{{ imageFileName }}</span>
              <button type="button" @click="removeImage" class="remove-file-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 6L6 18M6 6L18 18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label for="classTags" class="form-label">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.59 13.41L13.42 20.58C13.2343 20.766 13.0037 20.9095 12.7514 20.9989C12.4991 21.0883 12.2317 21.1218 11.9645 21.0961C11.6973 21.0704 11.4375 20.9862 11.2019 20.8492C10.9663 20.7122 10.7608 20.5258 10.6 20.3L3 13V3H13L20.6 10.6C20.8258 10.7608 21.0122 10.9663 21.1492 11.2019C21.2862 11.4375 21.3704 11.6973 21.3961 11.9645C21.4218 12.2317 21.3883 12.4991 21.2989 12.7514C21.2095 13.0037 21.066 13.2343 20.88 13.42L20.59 13.41Z"/>
            </svg>
            태그
          </label>
          <input 
            id="classTags"
            v-model="classTags" 
            type="text" 
            class="form-input"
            placeholder="예: 수학, 기초, 중급 (쉼표로 구분)"
          />
          <div class="tags-preview" v-if="classTags">
            <span v-for="tag in tagsArray" :key="tag" class="tag-item">
              {{ tag }}
            </span>
          </div>
        </div>

        <button 
          type="submit" 
          class="create-btn"
          :disabled="isCreating || !className.trim()"
          :class="{ 'loading': isCreating }"
        >
          <span v-if="!isCreating" class="btn-text">반 만들기</span>
          <div v-else class="loading-spinner">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </div>
        </button>
      </form>

      <div v-if="error" class="error-message">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10.29 3.86L1.82 18A2 2 0 0 0 3.54 21H20.46A2 2 0 0 0 22.18 18L13.71 3.86A2 2 0 0 0 10.29 3.86Z"/>
          <line x1="12" y1="9" x2="12" y2="13"/>
          <line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        {{ error }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useClassStore } from '@/stores/class'
import axios from 'axios'; // axios를 import하여 이미지 업로드에 사용

const props = defineProps({
  isVisible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'created'])

const className = ref('')
const classDescription = ref('')
const classImageFile = ref(null)
const imageFileName = ref('')
const classTags = ref('')
const error = ref('')
const isCreating = ref(false)

const classStore = useClassStore()

// 태그 배열 계산
const tagsArray = computed(() => {
  return classTags.value
    .split(',')
    .map(tag => tag.trim())
    .filter(tag => tag.length > 0)
})

// 파일이 선택되었을 때 실행되는 함수
function handleImageUpload(event) {
  const file = event.target.files[0];
  if (file) {
    if (file.size > 5 * 1024 * 1024) {
      alert('파일 크기는 5MB 이하여야 합니다.');
      return;
    }
    
    if (!file.type.startsWith('image/')) {
      alert('이미지 파일만 업로드 가능합니다.');
      return;
    }
    
    classImageFile.value = file;
    imageFileName.value = file.name;
  } else {
    classImageFile.value = null;
    imageFileName.value = '';
  }
}

// 이미지 제거
function removeImage() {
  classImageFile.value = null;
  imageFileName.value = '';
  const fileInput = document.getElementById('classImageInput');
  if (fileInput) {
    fileInput.value = '';
  }
}

// 반 생성
async function handleSubmit() {
  if (!className.value.trim()) {
    error.value = '반 이름을 입력해주세요.'
    return
  }

  try {
    error.value = ''
    isCreating.value = true
    let thumbnailUrl = null;

    // 1. 이미지가 있다면 먼저 이미지 업로드 API를 호출하여 URL을 받습니다.
    if (classImageFile.value) {
      const imageFormData = new FormData();
      imageFormData.append('file', classImageFile.value);
      
      // TODO: 실제 이미지 업로드 API 엔드포인트로 변경하세요.
      // 이 예제에서는 더미 응답을 사용합니다.
      // const imageUploadResponse = await axios.post('/api/upload/image', imageFormData);
      // thumbnailUrl = imageUploadResponse.data.url;
      
      // 임시로 더미 URL을 할당
      thumbnailUrl = 'https://example.com/uploaded_image_url.jpg';
      console.log('Image uploaded successfully:', thumbnailUrl);
    }
    
    // 2. DTO 객체를 생성합니다.
    const classDto = {
      title: className.value.trim(),
      description: classDescription.value.trim(),
      thumbnailUrl: thumbnailUrl,
      limit: 100, // 이 값은 프론트엔드에 입력 필드가 없으므로 임의로 지정하거나 추가해야 합니다.
      tags: tagsArray.value
    };

    // 3. 생성된 JSON 객체로 반 생성 API를 호출합니다.
    const newClass = await classStore.createClass(classDto);

    // 목록 다시 갱신
    await classStore.fetchMyCreatedClasses();
    // await classStore.fetchMyJoinedClasses();
    console.log('newClass',newClass)
    // 성공 메시지
    alert(`반 "${classDto.title}" 이(가) 성공적으로 생성되었습니다!`);
    
    // 폼 초기화
    className.value = '';
    classDescription.value = '';
    removeImage();
    classTags.value = '';
    
    // 생성된 클래스 이름을 localStorage에 저장
    localStorage.setItem('lastCreatedClassName', classDto.title);
    console.log('🔍 CreateClassForm - 저장된 클래스 이름:', classDto.title);
    console.log('🔍 CreateClassForm - localStorage 확인:', localStorage.getItem('lastCreatedClassName'));
    
    // 부모 컴포넌트에 생성 완료 알림
    emit('created', newClass);
    
  } catch (err) {
    console.error('클래스 생성 에러:', err);
    error.value = `반 생성에 실패했습니다: ${err.message || '알 수 없는 오류'}`;
  } finally {
    isCreating.value = false;
  }
}
</script>

<style scoped>
/* (이하 스타일 코드는 원본과 동일) */
.create-form-section {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.form-card {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 2rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  max-width: 500px;
  width: 90%;
  max-height: 90vh;
  overflow-y: auto;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.form-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.close-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.close-btn:hover {
  background: var(--bg-card);
  border-color: var(--border-dark);
  color: var(--text-primary);
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  position: relative;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 1rem;
  transition: all 0.3s ease;
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

.file-upload-area {
  border: 2px dashed var(--border-color);
  border-radius: 12px;
  padding: 2rem;
  text-align: center;
  transition: all 0.3s ease;
  background: var(--bg-secondary);
}

.file-upload-area:hover {
  border-color: var(--brand-main);
  background: var(--bg-tertiary);
}

.file-upload-area.has-file {
  border-style: solid;
  border-color: var(--brand-main);
}

.file-input {
  display: none;
}

.file-upload-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  cursor: pointer;
  color: var(--text-secondary);
}

.upload-icon {
  color: var(--brand-main);
}

.upload-text {
  font-weight: 500;
}

.file-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.file-name {
  font-size: 0.9rem;
  color: var(--text-primary);
}

.remove-file-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: all 0.3s ease;
}

.remove-file-btn:hover {
  color: #f56565;
  background: rgba(245, 101, 101, 0.1);
}

.tags-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.tag-item {
  background: var(--brand-main);
  color: white;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 500;
}

.create-btn {
  background: linear-gradient(135deg, var(--brand-main), var(--brand-accent));
  color: white;
  border: none;
  padding: 1rem 2rem;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.create-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(34, 122, 83, 0.3);
}

.create-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.error-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #f56565;
  background: rgba(245, 101, 101, 0.1);
  padding: 1rem;
  border-radius: 8px;
  margin-top: 1rem;
  font-size: 0.9rem;
}

/* 다크모드 대응 */
:global(.dark-mode) .form-card {
  background: var(--bg-primary);
  border-color: var(--border-color);
}

:global(.dark-mode) .form-input,
:global(.dark-mode) .form-textarea {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border-color: var(--border-color);
}

:global(.dark-mode) .file-upload-area {
  background: var(--bg-secondary);
  border-color: var(--border-color);
}
</style>