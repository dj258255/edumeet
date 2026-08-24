<template>
  <div class="home-view">
    <!-- 인트로(인트로 텍스트/버튼) -->
    <section class="hero">
      <div class="hero-container">
        <div class="hero-content">
          <div class="hero-badge">EduMeet 소개</div>
          <h1 class="hero-title">
            언제 어디서든
            <span class="title-line">수업 공간을 만드는</span>
            <span class="title-line">AI와 함께하는</span>
            <span class="title-line highlight">교육 플랫폼</span>
          </h1>
          <p class="hero-description">
            EduMeet와 함께 더 많은 것을 성취하세요 : 교육에 새로운 공간을 만드는, AI의 많은 기능들이 포함된 미래 지향적 우선 교육 플랫폼으로, 
            추가 비용 없이 미래의 학습을 경험해보세요.
          </p>
          <div class="hero-actions">
            <router-link  v-if="!isLoggedIn" to="/login" class="btn btn-primary">
              시작하기
            </router-link>
            <router-link v-else to="/class/create" class="btn btn-primary">
              시작하기
            </router-link>
            <router-link to="/about" class="btn btn-secondary">
              EduMeet 알아보기
            </router-link>
          </div>
        </div>
        <div class="hero-visual">
          <div class="hero-images">
            <div class="image-container">
              <img 
                src="@/assets/main_hero/laebtob-eul-sayonghaneun-asia-sa-eobga-geosil-eseo-hwasang-tonghwa-hoeui-gyehoeg-e-daehae-donglyoege-iyagihabnida.jpg" 
                alt="화상회의 중인 비즈니스맨들" 
                class="hero-image image-1"
              />
              <div class="ui-bubble bubble-1">
                <div class="bubble-header">
                  <span class="sparkle-icon">✨</span>
                  <span class="bubble-title">오늘의 과제</span>
                </div>
                <div class="bubble-input">
                  <span class="input-text">과제를 제출 해주세요.</span>
                  <span class="send-icon">➤</span>
                </div>
              </div>
            </div>
            <div class="image-container">
              <img 
                src="@/assets/main_hero/yuchiwon-jol-eob-eul-chughahaneun-aideul.jpg" 
                alt="AI와 함께하는 학생들" 
                class="hero-image image-2"
              />
              <div class="ui-bubble bubble-2">
                <div class="bubble-content">
                  <span class="doc-icon">📄</span>
                  <span class="sparkle-icon">✨</span>
                  <span class="bubble-text">수업 요약서</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    
    <!-- 설명 Section -->
    <MainSection />
    
         <!-- 드래그 가능한 카드 Section -->
     <section class="draggable-cards-section">
       <div class="section-header">
         <div class="header-badge">내 반 목록</div>
         <h2 class="section-title">내가 만든 반과 속한 반을 확인하세요</h2>
         <p class="section-subtitle">마우스로 드래그하여 더 많은 콘텐츠를 확인하세요</p>
         
         <!-- 탭 버튼 -->
         <div class="tab-buttons">
           <button 
             class="tab-btn" 
             :class="{ active: activeTab === 'created' }"
             @click="activeTab = 'created'"
           >
             내가 만든 반 ({{ createdClassesCount }})
           </button>
           <button 
             class="tab-btn" 
             :class="{ active: activeTab === 'joined' }"
             @click="activeTab = 'joined'"
           >
             내가 속한 반 ({{ joinedClassesCount }})
           </button>
         </div>
       </div>
       
       <!-- 로딩 상태 -->
       <div v-if="isLoading" class="loading-state">
         <div class="loading-spinner">
           <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
             <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
           </svg>
         </div>
         <p>클래스 목록을 불러오는 중...</p>
       </div>
       
       <!-- 에러 상태 -->
       <div v-else-if="error" class="error-state">
         <div class="error-icon">⚠️</div>
         <p>{{ error }}</p>
         <button @click="loadClasses" class="retry-btn">다시 시도</button>
       </div>
       
       <!-- 카드 목록 -->
       <div v-else class="cards-container-wrapper">
         <button class="nav-button prev-button" @click="goToPrev" :disabled="translateX >= 0">
           <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
             <polyline points="15,18 9,12 15,6"></polyline>
           </svg>
         </button>
         <div class="cards-container" 
              ref="cardsContainer"
              @mousedown="startDrag"
              @mousemove="onDrag"
              @mouseup="stopDrag"
              @mouseleave="stopDrag">
           <div class="cards-wrapper" 
                :class="{ dragging: isDragging }"
                :style="{ transform: `translateX(${translateX}px)` }"
                ref="cardsWrapper">
             <ClassCard 
               v-for="(card, index) in currentTabCards" 
               :key="card.id"
               :card="card"
               :animation-delay="index * 0.1"
               :isMyCreatedClass="card.isMyCreatedClass"
               :viewType="'home'"
               @enroll="handleEnroll"
               @joinClass="handleJoinClass"
               @createClass="handleCreateClass"
               @deleteClass="handleDeleteClass"
               @viewDetail="handleViewDetail"
               @viewMembers="handleViewMembers"
             />
           </div>
         </div>
         <button class="nav-button next-button" @click="goToNext" :disabled="translateX <= minTranslate">
           <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
             <polyline points="9,18 15,12 9,6"></polyline>
           </svg>
         </button>
       </div>
       
       <!-- 인디케이터 -->
       <div v-if="!isLoading && !error" class="cards-indicator">
         <div class="indicator-dots">
           <div 
             v-for="(_, index) in Math.ceil(currentTabCards.length / visibleCards)" 
             :key="index"
             class="indicator-dot"
             :class="{ active: Math.abs(Math.round(translateX / cardWidth)) === index }"
             @click="() => animateToPosition(-index * cardWidth)"
           ></div>
         </div>
       </div>
     </section>
    
    <!-- 팀원 카드 Section -->
    <section class="team-section">
      <div class="team-layout">
        <!-- 왼쪽 팀원 상세 정보 -->
        <div class="team-detail-panel">
          <transition name="slide-fade" mode="out-in">
            <div class="team-member-detail" v-if="selectedMember" :key="selectedMember.name">
              <div class="member-info">
                <div class="member-badge">{{ selectedMember.role }}</div>
                <h3 class="member-name animate-text">{{ selectedMember.name }}</h3>
                <p class="member-quote animate-text">"{{ selectedMember.quote }}"</p>
                <p class="member-description animate-text">{{ selectedMember.description }}</p>
                <div class="member-social">
                  <button class="social-btn">LinkedIn</button>
                  <button class="social-btn">GitHub</button>
                </div>
              </div>
            </div>
            <div class="team-overview" v-else key="overview">
              <div class="overview-badge">우리 팀</div>
              <h2 class="team-title">함께 만들어가는 멤버들입니다</h2>
              <p class="team-description">
                EduMeet는 혁신적인 교육 플랫폼을 만들기 위해 다양한 분야의 전문가들이 모여 있습니다. 
                각자의 전문성을 바탕으로 사용자에게 최고의 교육 경험을 제공하기 위해 노력하고 있습니다.
              </p>
            </div>
          </transition>
        </div>
        
        <!-- 오른쪽 팀원 카드 그리드 -->
        <div class="team-cards-panel">
          <div class="card-grid">
            <div 
              class="member-card" 
              v-for="member in members" 
              :key="member.name"
              @click="selectMember(member)"
              :class="{ active: selectedMember && selectedMember.name === member.name }"
            >
              <div class="card-icon">
                <i :class="member.icon"></i>
              </div>
              <p class="card-name">{{ member.name }}</p>
              <p class="card-role">{{ member.role }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
  
  <!-- 수업 참여 모달 -->
  <JoinClassModal
    :isOpen="isJoinModalOpen"
    :className="selectedClass?.className || ''"
    :classDescription="selectedClass?.classDescription || ''"
    :classId="selectedClass?.classId || ''"
    @close="closeJoinModal"
    @join="handleJoinClassConfirm"
  />
  
  <!-- 수업 생성 모달 -->
  <CreateClassModal
    :isOpen="isCreateModalOpen"
    :defaultClassName="selectedClassForCreate?.className || ''"
    @close="closeCreateModal"
    @create="handleCreateClassConfirm"
  />
  
  <footer class="footer">
    <div class="footer-content">
      <span class="footer-title">EduMeet</span>
      <span class="footer-divider">|</span>
      <span>© 2024 EduMeet. All rights reserved.</span>
      <span class="footer-divider">|</span>
      <span>Contact: info@edumeet.com</span>
    </div>
  </footer>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from "vue"
import { useRouter } from "vue-router"
import { useAuthStore } from "../stores/auth.js"
import { useClassStore } from "../stores/class.js"
import gsap from "gsap"
import ScrollTrigger from "gsap/ScrollTrigger"
import ClassCard from "../components/ClassCard.vue"
import MainSection from "../components/MainSection.vue"
import JoinClassModal from "../components/JoinClassModal.vue"
import CreateClassModal from "../components/CreateClassModal.vue"
import "../styles/HomeView.css"
gsap.registerPlugin(ScrollTrigger)

const router = useRouter()
const authStore = useAuthStore()
const classStore = useClassStore()

// 사용자 상태
const user = ref(null)
const isLoggedIn = computed(() => authStore.isLoggedIn)

// 팀 멤버 정보
const members = [
  {
    name: "권시온",
    role: "백엔드 개발",
    quote: "AI로 교육의 미래를 만듭니다",
    image: "",
    icon: "fas fa-brain",
    description: "머신러닝과 딥러닝 기술을 활용하여 개인화된 학습 경험을 제공하는 AI 시스템을 개발합니다. 자연어 처리와 음성 인식을 전문으로 하며, 교육 분야에 특화된 AI 솔루션을 연구합니다."
  },
  {
    name: "이승민",
    role: "프론트엔드 개발",
    quote: "사용자 경험을 최우선으로",
    image: "",
    icon: "fas fa-server",
    description: "Vue.js와 React를 활용한 사용자 친화적인 웹 인터페이스를 개발합니다. 사용자 경험을 최우선으로 생각하며, 깔끔하고 직관적인 UI/UX를 구현하는 것을 전문으로 합니다."
  },
  {
    name: "박시은",
    role: "풀스택 개발",
    quote: "안정적인 서비스를 보장합니다",
    image: "",
    icon: "fas fa-palette",
    description: "Spring Boot와 Java를 기반으로 안정적이고 확장 가능한 서버 아키텍처를 구축합니다. 데이터베이스 설계와 API 개발을 담당하며, 시스템의 성능과 보안을 최적화합니다."
  },
  {
    name: "전준영",
    role: "백엔드 개발",
    quote: "코드를 통해 세상을 연결합니다",
    image: "",
    icon: "fas fa-cloud",
    description: "Spring Boot와 AWS를 활용한 클라우드 인프라를 구축하고 관리합니다. 데이터베이스 설계와 API 개발을 담당하며, 시스템의 안정성과 확장성을 보장합니다."
  },

  {
    name: "권민환",
    role: "프론트엔드 개발",
    quote: "사용자 중심의 인터페이스",
    image: "",
    icon: "fas fa-chart-bar",
    description: "반응형 웹 디자인과 모바일 최적화를 전문으로 합니다. Vue.js와 JavaScript를 활용하여 다양한 디바이스에서 일관된 사용자 경험을 제공하는 인터페이스를 개발합니다."
  },
  {
    name: "범수",
    role: "백엔드 개발",
    quote: "안정적인 서버 아키텍처 구축",
    image: "",
    icon: "fas fa-code",
    description: "Spring Boot와 Java를 기반으로 안정적이고 확장 가능한 서버 아키텍처를 구축합니다. 데이터베이스 설계와 API 개발을 담당하며, 시스템의 성능과 보안을 최적화합니다."
  },
]

// 선택된 팀원 상태
const selectedMember = ref(null)

// 수업 참여 모달 관련 상태
const isJoinModalOpen = ref(false)
const selectedClass = ref(null)

// 수업 생성 모달 관련 상태
const isCreateModalOpen = ref(false)
const selectedClassForCreate = ref(null)

// 드래그 가능한 카드 데이터
const draggableCards = ref([])
const isLoading = ref(false)
const error = ref(null)

// 탭 관련 상태
const activeTab = ref('created') // 'created' 또는 'joined'

// 현재 탭에 따른 카드 목록 계산
const currentTabCards = computed(() => {
  if (activeTab.value === 'created') {
    return draggableCards.value.filter(card => card.isMyCreatedClass)
  } else {
    return draggableCards.value.filter(card => !card.isMyCreatedClass)
  }
})

// 각 탭별 카드 개수 계산
const createdClassesCount = computed(() => {
  return draggableCards.value.filter(card => card.isMyCreatedClass).length
})

const joinedClassesCount = computed(() => {
  return draggableCards.value.filter(card => !card.isMyCreatedClass).length
})

// 기본 클래스 데이터 (백엔드 없을 때 사용)
const defaultClasses = [
  {
    id: 1,
    title: "Vue.js 마스터 클래스",
    description: "Vue.js의 핵심 개념부터 고급 기능까지 체계적으로 학습하세요. 실무에서 바로 활용할 수 있는 실습 중심의 강의입니다.",
    image: "",
    tags: ["프론트엔드", "Vue.js", "JavaScript"]
  },
  {
    id: 2,
    title: "React 완전 정복",
    description: "React의 기본부터 고급 패턴까지. Hooks, Context API, 상태 관리 등 현대적인 React 개발을 배워보세요.",
    image: "",
    tags: ["프론트엔드", "React", "JavaScript"]
  },
  {
    id: 3,
    title: "Node.js 백엔드 개발",
    description: "Express.js와 MongoDB를 활용한 실전 백엔드 개발. RESTful API 설계부터 배포까지 완벽 가이드.",
    image: "",
    tags: ["백엔드", "Node.js", "Express"]
  },
  {
    id: 4,
    title: "Python 데이터 분석",
    description: "Pandas, NumPy, Matplotlib을 활용한 데이터 분석과 시각화. 실무 데이터로 배우는 데이터 사이언스.",
    image: "",
    tags: ["데이터분석", "Python", "Pandas"]
  },
  {
    id: 5,
    title: "AWS 클라우드 아키텍처",
    description: "AWS 서비스를 활용한 확장 가능한 클라우드 인프라 구축. 실무 중심의 클라우드 아키텍처 설계.",
    image: "",
    tags: ["클라우드", "AWS", "인프라"]
  },
  {
    id: 6,
    title: "Docker 컨테이너 기술",
    description: "Docker와 Kubernetes를 활용한 컨테이너 기반 애플리케이션 배포. DevOps 실무 스킬을 익혀보세요.",
    image: "",
    tags: ["DevOps", "Docker", "Kubernetes"]
  },
  {
    id: 7,
    title: "UI/UX 디자인 기초",
    description: "사용자 중심의 디자인 원칙과 Figma를 활용한 프로토타이핑. 실제 프로젝트로 배우는 디자인 워크플로우.",
    image: "",
    tags: ["디자인", "UI/UX", "Figma"]
  },
  {
    id: 8,
    title: "머신러닝 입문",
    description: "Scikit-learn과 TensorFlow를 활용한 머신러닝 기초. 실제 데이터로 배우는 AI 모델 개발.",
    image: "",
    tags: ["AI", "머신러닝", "TensorFlow"]
  }
]

// 클래스 데이터 로드 함수
const loadClasses = async () => {
  isLoading.value = true
  error.value = null
  
  try {
    if (isLoggedIn.value) {
      console.log('🔍 HomeView - 로그인된 사용자, 실제 데이터 로드 시작')
      
      // CreateClassView와 동일한 방식으로 실제 데이터 가져오기
      await Promise.all([
        classStore.fetchMyCreatedClasses(),
        classStore.fetchMyJoinedClasses()
      ])
      
      console.log('🔍 HomeView - Store에서 가져온 데이터:')
      console.log('🔍 Created Classes from Store:', classStore.getMyCreatedClasses)
      console.log('🔍 Joined Classes from Store:', classStore.getMyJoinedClasses)
      
      // CreateClassView와 동일한 방식으로 데이터 처리
      const processedCreatedClasses = classStore.getMyCreatedClasses.map(cls => ({ 
        ...cls, 
        isMyCreatedClass: true
      }))
      
      const processedJoinedClasses = classStore.getMyJoinedClasses.map(cls => ({ 
        ...cls, 
        isMyCreatedClass: false
      }))
      
      draggableCards.value = [...processedCreatedClasses, ...processedJoinedClasses]
      
      console.log('🔍 HomeView - 최종 처리된 클래스 데이터:')
      console.log('🔍 Created Classes:', processedCreatedClasses)
      console.log('🔍 Joined Classes:', processedJoinedClasses)
      console.log('🔍 Total Cards:', draggableCards.value.length)
      
      // 실제 데이터가 없으면 기본 데이터 사용
      if (draggableCards.value.length === 0) {
        console.log('🔍 HomeView - 실제 데이터가 없어서 기본 데이터 사용')
        draggableCards.value = defaultClasses.map(cls => ({ ...cls, isMyCreatedClass: false }))
      }
    } else {
      // 로그인하지 않은 사용자: 기본 데이터 사용
      console.log('🔍 HomeView - 로그인하지 않은 사용자, 기본 데이터 사용')
      draggableCards.value = defaultClasses.map(cls => ({ ...cls, isMyCreatedClass: false }))
    }
  } catch (err) {
    console.error('클래스 데이터 로드 실패:', err)
    error.value = '클래스 데이터를 불러오는데 실패했습니다.'
    
    // 에러 시 기본 데이터 사용
    console.log('🔍 HomeView - 에러 발생으로 기본 데이터 사용')
    draggableCards.value = defaultClasses.map(cls => ({ ...cls, isMyCreatedClass: false }))
  } finally {
    isLoading.value = false
  }
}

// 드래그 관련 상태
const isDragging = ref(false)
const startX = ref(0)
const translateX = ref(0)
const cardsContainer = ref(null)
const cardsWrapper = ref(null)

// 카드 스냅 관련 상태
const cardWidth = 324 // 카드 너비(300px) + 간격(24px)
const visibleCards = 4
const maxTranslate = 0

// minTranslate를 computed로 변경하여 반응형으로 계산
const minTranslate = computed(() => {
  return -(currentTabCards.value.length - visibleCards) * cardWidth
})

// 드래그 시작
const startDrag = (e) => {
  isDragging.value = true
  startX.value = e.clientX - translateX.value
  
  // 드래그 중일 때 커서 스타일 변경
  if (cardsContainer.value) {
    cardsContainer.value.style.cursor = 'grabbing'
  }
  
  e.preventDefault()
}

// 드래그 중
const onDrag = (e) => {
  if (!isDragging.value) return
  
  const currentX = e.clientX - startX.value
  translateX.value = Math.max(minTranslate.value, Math.min(maxTranslate, currentX))
}

// 드래그 종료 - 스냅 기능 추가
const stopDrag = () => {
  if (!isDragging.value) return
  
  isDragging.value = false
  
  // 커서 스타일 복원
  if (cardsContainer.value) {
    cardsContainer.value.style.cursor = 'grab'
  }
  
  // 현재 위치에서 가장 가까운 카드 위치로 스냅
  const currentPosition = Math.abs(translateX.value)
  const snapIndex = Math.round(currentPosition / cardWidth)
  const snapPosition = snapIndex * cardWidth
  
  // 부드러운 애니메이션으로 스냅
  animateToPosition(-snapPosition)
}

// 부드러운 애니메이션으로 위치 이동
const animateToPosition = (targetPosition) => {
  const startPosition = translateX.value
  const distance = targetPosition - startPosition
  const duration = 300 // 300ms
  const startTime = performance.now()
  
  const animate = (currentTime) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    
    // easeOutCubic 애니메이션 함수
    const easeProgress = 1 - Math.pow(1 - progress, 3)
    
    translateX.value = startPosition + (distance * easeProgress)
    
    if (progress < 1) {
      requestAnimationFrame(animate)
    }
  }
  
  requestAnimationFrame(animate)
}

// 다음/이전 카드로 이동하는 함수들
const goToNext = () => {
  const currentIndex = Math.abs(Math.round(translateX.value / cardWidth))
  const nextIndex = Math.min(currentIndex + 1, draggableCards.value.length - visibleCards)
  const nextPosition = -nextIndex * cardWidth
  animateToPosition(nextPosition)
}

const goToPrev = () => {
  const currentIndex = Math.abs(Math.round(translateX.value / cardWidth))
  const prevIndex = Math.max(currentIndex - 1, 0)
  const prevPosition = -prevIndex * cardWidth
  animateToPosition(prevPosition)
}

// 팀원 선택 함수
const selectMember = (member) => {
  // 같은 멤버를 다시 클릭하면 선택 해제
  if (selectedMember.value && selectedMember.value.name === member.name) {
    selectedMember.value = null
  } else {
    selectedMember.value = member
  }
}



// 수강 신청 처리
const handleEnroll = async (classId) => {
  if (!isLoggedIn.value) {
    alert('로그인이 필요한 서비스입니다.')
    router.push('/login')
    return
  }
  
  try {
    // CreateClassView와 동일한 방식으로 처리
    console.log('수강 신청 시뮬레이션:', classId)
    alert('수강 신청이 완료되었습니다! (시뮬레이션)')
    
    // 클래스 목록 새로고침
    await loadClasses()
  } catch (error) {
    console.error('수강 신청 실패:', error)
    alert('수강 신청에 실패했습니다. 다시 시도해주세요.')
  }
}

// 수업 참여 모달 열기
const handleJoinClass = (classData) => {
  selectedClass.value = classData
  isJoinModalOpen.value = true
}

// 수업 참여 모달 닫기
const closeJoinModal = () => {
  isJoinModalOpen.value = false
  selectedClass.value = null
}

// 수업 참여 확인 처리
const handleJoinClassConfirm = async (joinData) => {
  console.log('수업 참여 데이터:', joinData)
  
  try {
    // 백엔드에서 토큰 요청
    const accessToken = localStorage.getItem('accessToken')
    if (!accessToken) {
      alert('로그인이 필요합니다.')
      return
    }
    
    console.log('🔍 토큰 요청 시작')
    console.log('🔍 요청 URL:', `https://api.studywithtymee.com/api/v1/meetingroom/token`)
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
    
    const response = await fetch(`https://api.studywithtymee.com/api/v1/meetingroom/token`, {
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

// 수업 생성 모달 열기
const handleCreateClass = (classData) => {
  selectedClassForCreate.value = classData
  isCreateModalOpen.value = true
}

// 수업 생성 모달 닫기
const closeCreateModal = () => {
  isCreateModalOpen.value = false
  selectedClassForCreate.value = null
}

// 수업 생성 확인 처리
const handleCreateClassConfirm = (createData) => {
  console.log('수업 생성 데이터:', createData)
  
  // 화상 수업 페이지로 이동
  const queryParams = {
    roomName: createData.roomName,
    className: createData.className,
    creatorName: createData.creatorName,
    isCreator: 'true' // 생성자임
  }
  
  // URL 쿼리 파라미터로 데이터 전달
  const queryString = new URLSearchParams(queryParams).toString()
  router.push(`/class/${createData.classId}/video?${queryString}`)
  
  // 모달 닫기
  closeCreateModal()
}

// 클래스 삭제 처리
const handleDeleteClass = async (classId) => {
  console.log('🔍 HomeView - 삭제할 classId:', classId)
  
  if (!classId) {
    alert('클래스 ID가 없습니다. 다시 시도해주세요.')
    return
  }
  
  try {
    await classStore.deleteClass(classId)
    
    // 삭제 성공 후 목록 새로고침
    await loadClasses()
    
    alert('클래스가 성공적으로 삭제되었습니다.')
  } catch (error) {
    console.error('클래스 삭제 실패:', error)
    alert('클래스 삭제에 실패했습니다. 다시 시도해주세요.')
  }
}

// 클래스 상세 보기
const handleViewDetail = (classData) => {
  console.log('클래스 상세 보기:', classData)
  // 여기에 상세 보기 로직 추가
}

// 학생 목록 보기
const handleViewMembers = (classData) => {
  console.log('학생 목록 보기:', classData)
  // 여기에 학생 목록 모달 로직 추가
}



// 로그아웃 처리
const handleLogout = async () => {
  try {
    await authStore.logout()
  } catch (error) {
    console.error("로그아웃 오류:", error)
  } finally {
    user.value = null
    router.push("/")
  }
}

onMounted(async () => {
  if (isLoggedIn.value) {
    user.value = authStore.currentUser
    // 로그인한 사용자는 실제 클래스 데이터 로드
    await loadClasses()
  } else {
    // 로그인하지 않은 사용자는 기본 데이터 사용
    draggableCards.value = defaultClasses.map(cls => ({ ...cls, isMyCreatedClass: false }))
  }
  
  await nextTick()
})

// 로그인 상태 변경 감지 (CreateClassView와 동일한 방식)
watch(isLoggedIn, async (newValue) => {
  if (newValue) {
    // 로그인 시 실제 데이터 로드
    user.value = authStore.currentUser
    await loadClasses()
  } else {
    // 로그아웃 시 기본 데이터 사용
    user.value = null
    draggableCards.value = defaultClasses.map(cls => ({ ...cls, isMyCreatedClass: false }))
  }
})

// 탭 변경 시 드래그 위치 초기화
watch(activeTab, () => {
  translateX.value = 0
})
</script>


