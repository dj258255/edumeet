<template>
  <div class="create-class-container">
    <!-- 헤더 섹션 -->
    <div class="header-section">
      <h1 class="page-title">🎓 새 반 만들기</h1>
      <p class="page-subtitle">학생들과 함께 학습할 새로운 반을 만들어보세요</p>
    </div>

    <div class="content-layout">
      <!-- 반 생성 폼 -->
      <div class="create-form-section">
        <div class="form-card">
          <h2 class="form-title">📝 반 정보 입력</h2>
          
          <form @submit.prevent="handleCreateClass" class="create-form">
            <!-- 반 이름 입력 -->
            <div class="form-group">
              <label for="className" class="form-label">
                <span class="label-icon">📚</span>
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
              <div class="input-focus-border"></div>
            </div>

            <!-- 반 설명 입력 -->
            <div class="form-group">
              <label for="classDescription" class="form-label">
                <span class="label-icon">📖</span>
                반 설명
              </label>
              <textarea 
                id="classDescription"
                v-model="classDescription" 
                class="form-textarea"
                placeholder="이 반에서 어떤 것을 배우나요? 목표나 특징을 설명해주세요."
                rows="4"
              ></textarea>
              <div class="input-focus-border"></div>
            </div>

            <!-- 이미지 업로드 -->
            <div class="form-group">
              <label class="form-label">
                <span class="label-icon">🖼️</span>
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
                  <span class="upload-icon">📁</span>
                  <span class="upload-text">{{ imageFileName ? '파일 변경' : '이미지 선택' }}</span>
                </label>
                <div v-if="imageFileName" class="file-info">
                  <span class="file-name">{{ imageFileName }}</span>
                  <button type="button" @click="removeImage" class="remove-file-btn">✕</button>
                </div>
              </div>
            </div>

            <!-- 태그 입력 -->
            <div class="form-group">
              <label for="classTags" class="form-label">
                <span class="label-icon">🏷️</span>
                태그
              </label>
              <input 
                id="classTags"
                v-model="classTags" 
                type="text" 
                class="form-input"
                placeholder="예: 수학, 기초, 중급 (쉼표로 구분)"
              />
              <div class="input-focus-border"></div>
              <div class="tags-preview" v-if="classTags">
                <span v-for="tag in tagsArray" :key="tag" class="tag-item">
                  {{ tag }}
                </span>
              </div>
            </div>

            <!-- 생성 버튼 -->
            <button 
              type="submit" 
              class="create-btn"
              :disabled="isCreating || !className.trim()"
              :class="{ 'loading': isCreating }"
            >
              <span v-if="!isCreating" class="btn-text">✨ 반 만들기</span>
              <span v-else class="loading-spinner">⏳</span>
            </button>
          </form>

          <!-- 에러 메시지 -->
          <div v-if="createError" class="error-message">
            <span class="error-icon">⚠️</span>
            {{ createError }}
          </div>
        </div>
      </div>

      <!-- 내 반 목록 -->
      <div class="my-classes-section">
        <div class="classes-header">
          <h2 class="section-title">📋 내 반 목록</h2>
          <div class="classes-count">{{ totalClassesCount }}개의 반</div>
        </div>

        <!-- 탭 버튼 -->
        <div class="tab-buttons">
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'created' }"
            @click="activeTab = 'created'"
          >
            👑 내가 만든 반 ({{ classStore.getMyCreatedClasses.length }})
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'joined' }"
            @click="activeTab = 'joined'"
          >
            👥 내가 속한 반 ({{ classStore.getMyJoinedClasses.length }})
          </button>
        </div>

        <!-- 로딩 상태 -->
        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">⏳</div>
          <p>반 목록을 불러오는 중...</p>
        </div>

        <!-- 빈 상태 -->
        <div v-else-if="currentClasses.length === 0 && !listError" class="empty-state">
          <div class="empty-icon">📚</div>
          <h3>{{ activeTab === 'created' ? '아직 만든 반이 없어요' : '아직 속한 반이 없어요' }}</h3>
          <p>{{ activeTab === 'created' ? '위에서 새로운 반을 만들어보세요!' : '친구가 만든 반에 참여해보세요!' }}</p>
        </div>

        <!-- 반 목록 -->
        <div v-else class="class-cards-container">
          <div class="class-cards-grid">
            <ClassCard
              v-for="(classItem, idx) in currentClasses"
              :key="`${activeTab}-${classItem.id}-${classItem.title}`"
              :card="classItem"
              :animationDelay="idx * 0.1"
              @enroll="goToVideoRoom"
              class="class-card-item"
            />
          </div>
        </div>

        <!-- 에러 상태 -->
        <div v-if="listError" class="error-message">
          <span class="error-icon">⚠️</span>
          {{ listError }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useClassStore } from '@/stores/class'
import ClassCard from '../components/ClassCard.vue'
import '../styles/ClassRelated.css'

const className = ref('')
const classDescription = ref('')
const classImageFile = ref(null)
const imageFileName = ref('')
const classTags = ref('')

const createError = ref('')
const listError = ref('')
const isCreating = ref(false)

const router = useRouter()
const classStore = useClassStore()

// 태그 배열 계산
const tagsArray = computed(() => {
  return classTags.value
    .split(',')
    .map(tag => tag.trim())
    .filter(tag => tag.length > 0)
})

// 페이지 진입 시 목록 로드
onMounted(async () => {
  try {
    listError.value = ''
    await classStore.fetchMyClasses()
  } catch (error) {
    console.error('클래스 목록 로드 에러:', error)
    listError.value = '클래스 목록을 불러오는 데 실패했습니다.'
  }
})

// 파일이 선택되었을 때 실행되는 함수
function handleImageUpload(event) {
  const file = event.target.files[0];
  if (file) {
    // 파일 크기 체크 (5MB 제한)
    if (file.size > 5 * 1024 * 1024) {
      alert('파일 크기는 5MB 이하여야 합니다.');
      return;
    }
    
    // 이미지 파일 타입 체크
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
  // input 값도 초기화
  const fileInput = document.getElementById('classImageInput');
  if (fileInput) {
    fileInput.value = '';
  }
}

// 반 생성
async function handleCreateClass() {
  if (!className.value.trim()) {
    createError.value = '반 이름을 입력해주세요.'
    return
  }

  try {
    createError.value = ''
    isCreating.value = true

    const formData = new FormData();
    formData.append('name', className.value.trim());
    formData.append('description', classDescription.value.trim());
    if (classImageFile.value) {
      formData.append('image', classImageFile.value);
    }
    formData.append('tags', tagsArray.value.join(','));

    const newClass = await classStore.createClass(formData);

    // 목록 다시 갱신
    await classStore.fetchMyClasses();

    // 성공 메시지
    alert(`반 "${newClass.title}" 이(가) 성공적으로 생성되었습니다! 🎉`);
    
    // 폼 초기화
    className.value = '';
    classDescription.value = '';
    removeImage();
    classTags.value = '';
    
  } catch (error) {
    console.error('클래스 생성 에러:', error);
    createError.value = '반 생성에 실패했습니다. 다시 시도해주세요.';
  } finally {
    isCreating.value = false;
  }
}

// ClassCard의 enroll 이벤트로 호출됨
function goToVideoRoom(classId) {
  router.push(`/class/${classId}/video`);
}

// 현재 활성화된 탭에 따른 반 목록 계산
const activeTab = ref('created'); // 'created' 또는 'joined'
const currentClasses = computed(() => {
  if (activeTab.value === 'created') {
    return classStore.getMyCreatedClasses;
  } else {
    return classStore.getMyJoinedClasses;
  }
});

// 전체 반 개수 계산
const totalClassesCount = computed(() => {
  return classStore.getMyCreatedClasses.length + classStore.getMyJoinedClasses.length;
});
</script>

<style scoped>
/* 컨테이너 */
.create-class-container {
  min-height: 100vh;
  background: var(--bg-color);
  padding: 2rem;
}

/* 헤더 섹션 */
.header-section {
  text-align: center;
  margin-bottom: 3rem;
}

.page-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--text-color);
  margin-bottom: 0.5rem;
}

.page-subtitle {
  font-size: 1.1rem;
  color: var(--text-secondary);
  margin: 0;
}

/* 콘텐츠 레이아웃 */
.content-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3rem;
  max-width: 1400px;
  margin: 0 auto;
}

/* 폼 섹션 */
.create-form-section {
  order: 1;
}

.form-card {
  background: var(--card-bg);
  border-radius: 16px;
  padding: 2rem;
  box-shadow: 0 8px 32px var(--shadow-color);
  border: 1px solid var(--border-color);
}

.form-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 2rem;
  text-align: center;
}

/* 폼 스타일 */
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
  color: var(--text-color);
  margin-bottom: 0.5rem;
}

.label-icon {
  font-size: 1.1rem;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  background: var(--input-bg);
  color: var(--text-color);
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

/* 파일 업로드 */
.file-upload-area {
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  padding: 1.5rem;
  text-align: center;
  transition: all 0.3s ease;
  background: var(--input-bg);
}

.file-upload-area:hover {
  border-color: var(--primary-color);
  background: rgba(59, 130, 246, 0.05);
}

.file-upload-area.has-file {
  border-style: solid;
  border-color: var(--success-color);
  background: rgba(16, 185, 129, 0.05);
}

.file-input {
  display: none;
}

.file-upload-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  color: var(--text-color);
  transition: color 0.3s ease;
}

.file-upload-label:hover {
  color: var(--primary-color);
}

.upload-icon {
  font-size: 2rem;
}

.upload-text {
  font-weight: 500;
}

.file-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  background: var(--success-color);
  color: white;
  border-radius: 6px;
}

.file-name {
  font-size: 0.9rem;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 1rem;
  padding: 0.25rem;
  border-radius: 4px;
  transition: background 0.2s ease;
}

.remove-file-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* 태그 미리보기 */
.tags-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.tag-item {
  background: var(--primary-color);
  color: white;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 500;
}

/* 생성 버튼 */
.create-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 1rem 2rem;
  border-radius: 8px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1rem;
}

.create-btn:hover:not(:disabled) {
  background: var(--primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.create-btn:disabled {
  background: var(--disabled-color);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.create-btn.loading {
  background: var(--primary-color);
}

.loading-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 내 반 목록 섹션 */
.my-classes-section {
  order: 2;
}

.classes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
}

.classes-count {
  background: var(--primary-color);
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
}

/* 탭 버튼 */
.tab-buttons {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}

.tab-btn {
  background: var(--card-bg);
  color: var(--text-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.tab-btn:hover:not(.active) {
  background: rgba(0, 0, 0, 0.05);
  border-color: var(--border-color);
}

.tab-btn.active {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

/* 로딩 상태 */
.loading-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.loading-state .loading-spinner {
  font-size: 2rem;
  margin-bottom: 1rem;
}

/* 빈 상태 */
.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-state h3 {
  color: var(--text-color);
  margin-bottom: 0.5rem;
}

/* 반 카드 컨테이너 */
.class-cards-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 0.5rem;
}

/* 스크롤바 스타일 */
.class-cards-container::-webkit-scrollbar {
  width: 8px;
}

.class-cards-container::-webkit-scrollbar-track {
  background: var(--border-color);
  border-radius: 4px;
}

.class-cards-container::-webkit-scrollbar-thumb {
  background: var(--primary-color);
  border-radius: 4px;
}

.class-cards-container::-webkit-scrollbar-thumb:hover {
  background: var(--primary-hover);
}

.class-category {
  background: var(--card-bg);
  border-radius: 16px;
  padding: 2rem;
  box-shadow: 0 8px 32px var(--shadow-color);
  border: 1px solid var(--border-color);
  margin-bottom: 1.5rem;
}

.category-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 1.5rem;
  text-align: center;
}

.class-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.class-card-item {
  transition: transform 0.3s ease;
}

.class-card-item:hover {
  transform: translateY(-4px);
}
 
/* 에러 메시지 */
.error-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--danger-color);
  color: white;
  padding: 1rem;
  border-radius: 8px;
  margin-top: 1rem;
  font-weight: 500;
}

.error-icon {
  font-size: 1.1rem;
}

/* 반응형 디자인 */
@media (max-width: 1024px) {
  .content-layout {
    grid-template-columns: 1fr;
    gap: 2rem;
  }
  
  .create-form-section {
    order: 2;
  }
  
  .my-classes-section {
    order: 1;
  }
}

@media (max-width: 768px) {
  .create-class-container {
    padding: 1rem;
  }
  
  .page-title {
    font-size: 2rem;
  }
  
  .form-card {
    padding: 1.5rem;
  }
  
  .classes-header {
    flex-direction: column;
    gap: 1rem;
    align-items: flex-start;
  }
  
  .tab-buttons {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .tab-btn {
    text-align: center;
    padding: 1rem;
  }
  
  .class-cards-container {
    max-height: 50vh;
  }
}
</style>