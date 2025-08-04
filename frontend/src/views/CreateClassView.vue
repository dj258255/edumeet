<template>
  <div class="create-class-container">
    <!-- 헤더 섹션 -->
    <div class="header-section">
      <h1 class="page-title">새 반 만들기</h1>
      <p class="page-subtitle">학생들과 함께 학습할 새로운 반을 만들어보세요</p>
      <button @click="showCreateForm = true" class="create-btn-header">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5V19M5 12H19"/>
        </svg>
        새 반 만들기
      </button>
    </div>

    <div class="content-layout">
      <!-- 반 목록 (좌측) -->
      <div class="classes-section">
        <div class="classes-header">
          <h2 class="section-title">내 반 목록</h2>
          <div class="classes-count">{{ totalClassesCount }}개의 반</div>
        </div>

        <!-- 탭 버튼 -->
        <div class="tab-buttons">
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'created' }"
            @click="activeTab = 'created'"
          >
            내가 만든 반 ({{ classStore.getMyCreatedClasses.length }})
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'joined' }"
            @click="activeTab = 'joined'"
          >
            내가 속한 반 ({{ classStore.getMyJoinedClasses.length }})
          </button>
        </div>

        <!-- 로딩 상태 -->
        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </div>
          <p>반 목록을 불러오는 중...</p>
        </div>

        <!-- 빈 상태 -->
        <div v-else-if="currentClasses.length === 0 && !listError" class="empty-state">
          <div class="empty-icon">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M2 3H6L7.68 14.39C7.77 14.99 8.31 15.44 8.92 15.44H19.5C20.1 15.44 20.64 14.99 20.73 14.39L22 6H6"/>
            </svg>
          </div>
          <h3>{{ activeTab === 'created' ? '아직 만든 반이 없어요' : '아직 속한 반이 없어요' }}</h3>
          <p>{{ activeTab === 'created' ? '위에서 새로운 반을 만들어보세요!' : '친구가 만든 반에 참여해보세요!' }}</p>
        </div>

        <!-- 반 목록 -->
        <div v-else class="class-cards-container">
          <div class="class-cards-grid">
            <div 
              v-for="(classItem, idx) in currentClasses" 
              :key="`${activeTab}-${classItem.id}-${classItem.title}`"
              class="class-card-item"
              :class="{ 'selected': selectedClass?.id === classItem.id }"
              @click="selectClass(classItem)"
            >
              <ClassCard
                :card="classItem"
                :animationDelay="idx * 0.1"
                @enroll="goToVideoRoom"
              />
            </div>
          </div>
        </div>

        <!-- 에러 상태 -->
        <div v-if="listError" class="error-message">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18A2 2 0 0 0 3.54 21H20.46A2 2 0 0 0 22.18 18L13.71 3.86A2 2 0 0 0 10.29 3.86Z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          {{ listError }}
        </div>
      </div>

      <!-- 클래스 정보 (우측) -->
      <div class="class-info-section">
        <div v-if="selectedClass" class="class-info-wrapper">
          <ClassInfo 
            :classData="selectedClass"
            @enter-class="goToVideoRoom"
            @view-details="viewClassDetails"
          />
        </div>
        <div v-else class="no-selection">
          <div class="no-selection-icon">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6A2 2 0 0 0 4 4V20A2 2 0 0 0 6 22H18A2 2 0 0 0 20 20V8L14 2Z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
          </div>
          <h3>반을 선택해주세요</h3>
          <p>좌측에서 반을 선택하면 상세 정보를 확인할 수 있습니다.</p>
        </div>
      </div>
    </div>

    <!-- 반 생성 폼 모달 -->
    <CreateClassForm 
      :isVisible="showCreateForm"
      @close="showCreateForm = false"
      @created="handleClassCreated"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useClassStore } from '@/stores/class'
import ClassCard from '../components/ClassCard.vue'
import CreateClassForm from '../components/CreateClassForm.vue'
import ClassInfo from '../components/ClassInfo.vue'
import '../styles/ClassRelated.css'

const listError = ref('')
const showCreateForm = ref(false)
const selectedClass = ref(null)

const router = useRouter()
const classStore = useClassStore()

// 페이지 진입 시 목록 로드
onMounted(async () => {
  try {
    listError.value = ''
    await classStore.fetchMyCreatedClasses()
    await classStore.fetchMyJoinedClasses()
  } catch (error) {
    console.error('클래스 목록 로드 에러:', error)
    listError.value = '클래스 목록을 불러오는 데 실패했습니다.'
  }
})

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

// 반 선택
function selectClass(classItem) {
  selectedClass.value = classItem;
}

// ClassCard의 enroll 이벤트로 호출됨
function goToVideoRoom(classId) {
  router.push(`/class/${classId}/video`);
}

// 반 상세 보기
function viewClassDetails(classId) {
  router.push(`/class/${classId}`);
}

// 반 생성 완료 처리
function handleClassCreated(newClass) {
  showCreateForm.value = false;
  // 새로 생성된 반을 선택
  selectedClass.value = newClass;
}
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
  position: relative;
}

.page-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--text-color);
  margin-bottom: 0.5rem;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-hover));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-subtitle {
  font-size: 1.1rem;
  color: var(--text-secondary);
  margin: 0 0 2rem 0;
}

.create-btn-header {
  background: linear-gradient(135deg, var(--brand-main), var(--brand-accent));
  color: white;
  border: none;
  padding: 1rem 2rem;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  box-shadow: 0 4px 15px rgba(34, 122, 83, 0.3);
}

.create-btn-header:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(34, 122, 83, 0.4);
}

/* 콘텐츠 레이아웃 */
.content-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3rem;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 600px;
}

/* 반 목록 섹션 */
.classes-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.classes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.section-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
}

.classes-count {
  font-size: 0.9rem;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  padding: 0.5rem 1rem;
  border-radius: 20px;
}

/* 탭 버튼 */
.tab-buttons {
  display: flex;
  gap: 0.5rem;
  background: var(--bg-tertiary);
  padding: 0.5rem;
  border-radius: 12px;
}

.tab-btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.tab-btn.active {
  background: var(--bg-primary);
  color: var(--text-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 반 카드 그리드 */
.class-cards-container {
  flex: 1;
  overflow-y: auto;
}

.class-cards-grid {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.class-card-item {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
  overflow: hidden;
}

.class-card-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.class-card-item.selected {
  border: 2px solid var(--brand-main);
  box-shadow: 0 0 0 4px rgba(34, 122, 83, 0.1);
}

/* 클래스 정보 섹션 */
.class-info-section {
  display: flex;
  flex-direction: column;
}

.class-info-wrapper {
  height: 100%;
}

.no-selection {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: var(--text-secondary);
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 3rem;
  border: 2px dashed var(--border-color);
}

.no-selection-icon {
  margin-bottom: 1rem;
  color: var(--text-tertiary);
}

.no-selection h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.no-selection p {
  font-size: 0.9rem;
  margin: 0;
}

/* 로딩 상태 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.loading-spinner {
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 빈 상태 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  text-align: center;
  color: var(--text-secondary);
}

.empty-icon {
  margin-bottom: 1rem;
  color: var(--text-tertiary);
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-state p {
  font-size: 0.9rem;
  margin: 0;
}

/* 에러 메시지 */
.error-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #f56565;
  background: rgba(245, 101, 101, 0.1);
  padding: 1rem;
  border-radius: 8px;
  font-size: 0.9rem;
}

/* 반응형 */
@media (max-width: 1200px) {
  .content-layout {
    grid-template-columns: 1fr;
    gap: 2rem;
  }
  
  .class-info-section {
    order: -1;
  }
}

@media (max-width: 768px) {
  .create-class-container {
    padding: 1rem;
  }
  
  .page-title {
    font-size: 2rem;
  }
  
  .content-layout {
    gap: 1.5rem;
  }
  
  .tab-buttons {
    flex-direction: column;
  }
  
  .tab-btn {
    text-align: center;
  }
}
</style>