<template>
  <div class="create-class-container">
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
      <div class="classes-section">
        <div class="classes-header">
          <h2 class="section-title">내 반 목록</h2>
          <div class="classes-count">{{ totalClassesCount }}개의 반</div>
        </div>

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

        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </div>
          <p>반 목록을 불러오는 중...</p>
        </div>

        <div class="cards-section">
          <div class="cards-scroll-container">
            <div class="class-cards-grid">
              <div 
                v-for="(classItem, idx) in currentClasses" 
                :key="`${activeTab}-${classItem.id}-${classItem.title}`"
                class="class-card-item"
                @click="selectClass(classItem)"
              >
                <ClassCard
                  :card="classItem"
                  :animationDelay="idx * 0.1"
                  :isMyCreatedClass="activeTab === 'created'"
                  @enroll="goToVideoRoom"
                  @joinClass="handleJoinClass"
                  @createClass="handleCreateClass"
                  @deleteClass="handleDeleteClass"
                  @viewDetail="selectClass"
                  @viewMembers="handleViewMembers"
                  @viewSummary="handleViewSummary"
                />
              </div>
            </div>
          </div>
        </div>

        <div v-if="listError" class="error-message">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18A2 2 0 0 0 3.54 21H20.46A2 2 0 0 0 22.18 18L13.71 3.86A2 2 0 0 0 10.29 3.86Z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          {{ listError }}
        </div>

        <div v-if="currentClasses.length === 0 && !listError" class="empty-state">
          <div class="empty-icon">
            {{ activeTab === 'created' ? '📚' : '👥' }}
          </div>
          <h3>{{ activeTab === 'created' ? '아직 만든 반이 없어요' : '아직 속한 반이 없어요' }}</h3>
          <p>{{ activeTab === 'created' ? '위에서 새로운 반을 만들어보세요!' : '친구가 만든 반에 참여해보세요!' }}</p>
        </div>
      </div>

      <div class="class-info-section">
        <div v-if="selectedClass" class="class-info-wrapper">
          <ClassInfo 
            :classData="selectedClass"
            :isMyCreatedClass="activeTab === 'created'" @enter-class="goToVideoRoom"
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

    <CreateClassForm 
      :isVisible="showCreateForm"
      @close="showCreateForm = false"
      @created="handleClassCreated"
    />

    <JoinClassModal
      :isOpen="isJoinModalOpen"
      :className="selectedClassForJoin?.className || ''"
      :classDescription="selectedClassForJoin?.classDescription || ''"
      :classId="selectedClassForJoin?.classId || ''"
      @close="closeJoinModal"
      @join="handleJoinClassConfirm"
    />

    <CreateClassModal
      :isOpen="showCreateClassModal"
      :defaultClassName="pendingClassData?.className || ''"
      :classId="pendingClassData?.classId || ''"
      @close="handleCreateClassModalClose"
      @create="handleCreateClassConfirm"
    />

    <MembersModal
      :isVisible="isMembersModalOpen"
      :classId="selectedClassForMembers?.classId || ''"
      :className="selectedClassForMembers?.className || ''"
      @close="closeMembersModal"
    />

    <LiveInfoModal
      :isVisible="isLiveInfoModalOpen"
      :classId="selectedClassForLiveInfo?.classId || ''"
      @close="closeLiveInfoModal"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useClassStore } from '@/stores/class'
import { useAuthStore } from '@/stores/auth'
import ClassCard from '../components/ClassCard.vue'
import CreateClassForm from '../components/CreateClassForm.vue'
import CreateClassModal from '../components/CreateClassModal.vue'
import JoinClassModal from '../components/JoinClassModal.vue'
import MembersModal from '../components/MembersModal.vue'
import LiveInfoModal from '../components/LiveInfoModal.vue'
import ClassInfo from '../components/ClassInfo.vue'
import '../styles/ClassRelated.css'

const listError = ref('')
const showCreateForm = ref(false)
const selectedClass = ref(null)
const showCreateClassModal = ref(false)
const pendingClassData = ref(null)

// 수업 참여 모달 관련 상태
const isJoinModalOpen = ref(false)
const selectedClassForJoin = ref(null)

// 학생 목록 모달 관련 상태
const isMembersModalOpen = ref(false)
const selectedClassForMembers = ref(null)

// LiveInfo 모달 관련 상태
const isLiveInfoModalOpen = ref(false)
const selectedClassForLiveInfo = ref(null)

const router = useRouter()
const classStore = useClassStore()
const authStore = useAuthStore()
const isLoggedIn = computed(() => authStore.isLoggedIn)


// 현재 활성화된 탭에 따른 반 목록 계산
const activeTab = ref('created'); // 'created' 또는 'joined'
const currentClasses = computed(() => {
  if (activeTab.value === 'created') {
    return classStore.getMyCreatedClasses;
  } else {
    return classStore.getMyJoinedClasses;
  }
});

// `activeTab`이 변경될 때 `selectedClass`를 초기화하는 watch 함수 추가
watch(activeTab, () => {
  selectedClass.value = null;
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

// ClassCard의 joinClass 이벤트로 호출됨 (내가 속한 반의 수업 참여)
function handleJoinClass(classData) {
  console.log('🔍 handleJoinClass - classData:', classData)
  selectedClassForJoin.value = classData
  isJoinModalOpen.value = true
}

// 수업 참여 모달 닫기
function closeJoinModal() {
  isJoinModalOpen.value = false
  selectedClassForJoin.value = null
}

// 수업 참여 확인 처리
async function handleJoinClassConfirm(joinData) {
  console.log('수업 참여 데이터:', joinData)
  
  try {
    // 백엔드에서 토큰 요청
    const accessToken = localStorage.getItem('accessToken')
    if (!accessToken) {
      alert('로그인이 필요합니다.')
      return
    }
    
    console.log('🔍 토큰 요청 시작')
    console.log('🔍 요청 URL:', `https://i13c205.p.ssafy.io/api/v1/meetingroom/token`)
    console.log('🔍 요청 헤더:', {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken.substring(0, 20)}...`
    })
    console.log('🔍 accessToken 전체:', accessToken)
    console.log('🔍 accessToken 길이:', accessToken.length)
    console.log('🔍 요청 본문:', {
      title: joinData.roomName,
      participantName: joinData.participantName,
      classId: joinData.classId
    })
    
    const response = await fetch(`https://i13c205.p.ssafy.io/api/v1/meetingroom/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${accessToken}`
      },
      body: JSON.stringify({
        title: joinData.roomName,
        participantName: joinData.participantName,
        classId: joinData.classId
      })
    })
    
    console.log('🔍 응답 상태:', response.status)
    console.log('🔍 응답 헤더:', Object.fromEntries(response.headers.entries()))
    
    if (!response.ok) {
      // 오류 응답 본문을 확인
      const errorText = await response.text()
      console.log('🔍 오류 응답 본문:', errorText)
      
      // JSON 파싱 시도
      let errorData
      try {
        errorData = JSON.parse(errorText)
      } catch (e) {
        errorData = { error: errorText }
      }
      
      // 백엔드에서 반환하는 구체적인 에러 메시지 사용
      const errorMessage = errorData.error || `토큰 요청 실패: ${response.status}`
      throw new Error(errorMessage)
    }
    
    // 응답 본문 확인
    const responseText = await response.text()
    console.log('🔍 백엔드 응답 본문:', responseText)
    
    if (!responseText || responseText.trim() === '') {
      console.error('🔍 백엔드에서 빈 응답을 받았습니다.')
      throw new Error('서버에서 빈 응답을 받았습니다.')
    }
    
    // JSON 파싱 시도
    let data
    try {
      data = JSON.parse(responseText)
    } catch (parseError) {
      console.error('🔍 JSON 파싱 실패:', parseError)
      throw new Error('서버 응답을 파싱할 수 없습니다.')
    }
    
    console.log('🔍 백엔드에서 받은 토큰 데이터:', data)
    
    if (!data.token) {
      throw new Error('토큰이 없습니다.')
    }
    
    // 화상 수업 페이지로 이동 (토큰 포함)
    const queryParams = {
      roomName: joinData.roomName,
      className: joinData.className,
      participantName: joinData.participantName,
      isCreator: 'false', // 참여자는 생성자가 아님
      token: data.token // 백엔드에서 받은 토큰
    }
    
    // URL 쿼리 파라미터로 데이터 전달
    const queryString = new URLSearchParams(queryParams).toString()
    router.push(`/class/${joinData.classId}/video?${queryString}`)
    
    // 모달 닫기
    closeJoinModal()
    
  } catch (error) {
    console.error('토큰 요청 실패:', error)
    alert('수업 참여에 실패했습니다. 다시 시도해주세요.')
  }
}

// ClassCard의 createClass 이벤트로 호출됨 (내가 만든 반의 수업 생성)
function handleCreateClass(classData) {
  // 모달을 열고 클래스 데이터를 저장
  pendingClassData.value = classData
  showCreateClassModal.value = true
}

// 모달에서 화상수업 생성 확인 시 호출됨
function handleCreateClassConfirm(modalData) {
  console.log('🔍 handleCreateClassConfirm - modalData:', modalData)
  
  // ClassVideoRoomView로 이동하면서 meetingId와 생성된 화상수업 정보 전달
  router.push({
    path: `/class/${modalData.classId}/video`,
    query: {
      meetingId: modalData.meetingId,
      title: modalData.title,
      email: modalData.email,
      isCreator: 'true',
      creatorName: modalData.creatorName,
      description: modalData.description,
      token: modalData.token // 백엔드에서 받은 토큰
    }
  });
  showCreateClassModal.value = false
  pendingClassData.value = null
}

// 모달 닫기
function handleCreateClassModalClose() {
  showCreateClassModal.value = false
  pendingClassData.value = null
}

// 반 상세 보기
function viewClassDetails(classId) {
  router.push(`/class/${classId}`);
}

// 반 생성 완료 처리
function handleClassCreated(newClass) {
  console.log('🔍 handleClassCreated 호출됨:', newClass);
  showCreateForm.value = false;

  // 새로 생성된 클래스를 즉시 목록에 추가
  if (newClass) {
    console.log('🔍 새 클래스 데이터:', newClass);
    
    // classStore에 직접 추가
    if (classStore.addCreatedClass) {
      classStore.addCreatedClass(newClass);
      console.log('🔍 새 클래스가 목록에 추가됨');
    } else {
      console.warn('🔍 classStore.addCreatedClass 메서드가 없습니다!');
      // 대안: 수동으로 배열에 추가
      if (Array.isArray(classStore.myCreatedClasses)) {
        classStore.myCreatedClasses.unshift(newClass);
        console.log('🔍 수동으로 새 클래스를 목록에 추가함');
      }
    }
    
    // 새로 생성된 반을 선택
    selectedClass.value = newClass;
    
    // 탭을 'created'로 변경하여 새로 만든 클래스가 보이도록 함
    activeTab.value = 'created';
    
    console.log('🔍 현재 선택된 클래스:', selectedClass.value);
    console.log('🔍 현재 활성 탭:', activeTab.value);
    console.log('🔍 현재 목록의 클래스 수:', classStore.getMyCreatedClasses.length);
  } else {
    console.error('🔍 handleClassCreated - newClass가 null 또는 undefined입니다!');
  }
}

// 클래스 목록 새로고침 함수
async function loadClasses() {
  try {
    listError.value = ''
    await classStore.fetchMyCreatedClasses()
    await classStore.fetchMyJoinedClasses()
    
    // 디버깅: 클래스 데이터 구조 확인
    console.log('🔍 Created Classes:', classStore.getMyCreatedClasses)
    console.log('🔍 Joined Classes:', classStore.getMyJoinedClasses)
    
    if (classStore.getMyCreatedClasses.length > 0) {
      console.log('🔍 First Created Class:', classStore.getMyCreatedClasses[0])
      console.log('🔍 First Created Class Keys:', Object.keys(classStore.getMyCreatedClasses[0]))
    }
  } catch (error) {
    console.error('클래스 목록 로드 에러:', error)
    listError.value = '클래스 목록을 불러오는 데 실패했습니다.'
  }
}

// 클래스 삭제 처리
async function handleDeleteClass(classId) {
  console.log('🔍 CreateClassView - 삭제할 classId:', classId)
  console.log('🔍 CreateClassView - classId 타입:', typeof classId)
  
  if (!classId) {
    alert('클래스 ID가 없습니다. 다시 시도해주세요.')
    return
  }
  
  try {
    await classStore.deleteClass(classId)
    
    // 삭제 성공 후 목록 새로고침
    await loadClasses()
    
    // 삭제된 클래스가 현재 선택된 클래스였다면 선택 해제
    if (selectedClass.value?.id === classId || selectedClass.value?.classId === classId) {
      selectedClass.value = null
    }
    
    alert('클래스가 성공적으로 삭제되었습니다.')
  } catch (error) {
    console.error('클래스 삭제 실패:', error)
    alert('클래스 삭제에 실패했습니다. 다시 시도해주세요.')
  }
}

// 학생 목록 모달 열기
function handleViewMembers(classData) {
  console.log('학생 목록 조회:', classData)
  selectedClassForMembers.value = classData
  isMembersModalOpen.value = true
}

// LiveInfo 모달 열기
function handleViewSummary(classData) {
  console.log('🔍 handleViewSummary - classData:', classData)
  selectedClassForLiveInfo.value = classData
  isLiveInfoModalOpen.value = true
}

// 학생 목록 모달 닫기
function closeMembersModal() {
  isMembersModalOpen.value = false
  selectedClassForMembers.value = null
}

// LiveInfo 모달 닫기
function closeLiveInfoModal() {
  isLiveInfoModalOpen.value = false
  selectedClassForLiveInfo.value = null
}


// 복귀/온라인 이벤트 핸들러는 setup 동기 구간에서 정의
const onVisibilityChange = () => {
  if (document.visibilityState === 'visible' && isLoggedIn.value) {
    loadClasses()
  }
}
const onFocus = () => { if (isLoggedIn.value) loadClasses() }
const onOnline = () => { if (isLoggedIn.value) loadClasses() }
const onPageShow = () => { if (isLoggedIn.value) loadClasses() }

// 페이지 진입 시 목록 로드 (로그인 보장 후 실행)
onMounted(async () => {
  if (isLoggedIn.value) {
    await loadClasses()
  } else {
    const stop = watch(isLoggedIn, async (v) => {
      if (v) {
        stop()
        await loadClasses()
      }
    })
  }

  // 절전/복귀 및 네트워크 재연결 시 재조회 이벤트 등록
  window.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('focus', onFocus)
  window.addEventListener('online', onOnline)
  window.addEventListener('pageshow', onPageShow)
})

onBeforeUnmount(() => {
  window.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('pageshow', onPageShow)
})

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
  color: #000000 !important; /* 라이트 모드에서 검정색으로 고정 */
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.tab-btn.active {
  background: #ffffff !important; /* 활성 상태에서 배경색을 흰색으로 */
  color: #000000 !important; /* 활성 상태에서 텍스트는 검정색으로 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 다크 모드에서만 색상 변경 */
.dark-mode .tab-btn {
  color: var(--text-secondary);
}

.dark-mode .tab-btn.active {
  color: var(--text-primary);
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
  align-items: center;
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