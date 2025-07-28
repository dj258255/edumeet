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
            <router-link v-else to="/class" class="btn btn-primary">
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
        <div class="header-badge">추천 콘텐츠</div>
        <h2 class="section-title">인기 강의를 만나보세요</h2>
        <p class="section-subtitle">마우스로 드래그하여 더 많은 콘텐츠를 확인하세요</p>
      </div>
      <div class="cards-container-wrapper">
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
              v-for="(card, index) in draggableCards" 
              :key="card.id"
              :card="card"
              :animation-delay="index * 0.1"
              @enroll="handleEnroll"
            />
          </div>
        </div>
        <button class="nav-button next-button" @click="goToNext" :disabled="translateX <= minTranslate">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9,18 15,12 9,6"></polyline>
          </svg>
        </button>
      </div>
      <div class="cards-indicator">
        <div class="indicator-dots">
          <div 
            v-for="(_, index) in Math.ceil(draggableCards.length / visibleCards)" 
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
              <div class="member-photo-container">
                <img class="member-photo animate-photo" :src="selectedMember.image" :alt="selectedMember.name" />
                <div class="photo-overlay"></div>
              </div>
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
import { ref, computed, onMounted, nextTick } from "vue"
import { useRouter } from "vue-router"
import { userManager, tokenManager, authAPI } from "../stores/auth.js"
import gsap from "gsap"
import ScrollTrigger from "gsap/ScrollTrigger"
import ClassCard from "../components/ClassCard.vue"
import MainSection from "../components/MainSection.vue"
import "../styles/HomeView.css"
gsap.registerPlugin(ScrollTrigger)

const router = useRouter()

// 사용자 상태
const user = ref(null)
const isLoggedIn = computed(() => userManager.isLoggedIn())

// 팀 멤버 정보
const members = [
  {
    name: "권시온",
    role: "백엔드 개발",
    quote: "코드를 통해 세상을 연결합니다",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-code",
    description: "Vue.js와 React를 활용한 사용자 친화적인 웹 인터페이스를 개발합니다. 사용자 경험을 최우선으로 생각하며, 깔끔하고 직관적인 UI/UX를 구현하는 것을 전문으로 합니다."
  },
  {
    name: "이승민",
    role: "프론트엔드 개발",
    quote: "데이터 흐름을 설계합니다",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-server",
    description: "Node.js와 Python을 기반으로 안정적이고 확장 가능한 서버 아키텍처를 구축합니다. 데이터베이스 설계와 API 개발을 담당하며, 시스템의 성능과 보안을 최적화합니다."
  },
  {
    name: "박시은",
    role: "백앤드개발",
    quote: "감각을 담은 UI/UX",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-palette",
    description: "사용자 중심의 디자인 철학을 바탕으로 직관적이고 아름다운 인터페이스를 설계합니다. Figma와 Adobe Creative Suite를 활용하여 브랜드 아이덴티티와 일관된 디자인 시스템을 구축합니다."
  },
  {
    name: "전준영",
    role: "백엔드 개발",
    quote: "학습하는 알고리즘에 생명을",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-brain",
    description: "머신러닝과 딥러닝 기술을 활용하여 개인화된 학습 경험을 제공하는 AI 시스템을 개발합니다. 자연어 처리와 추천 시스템을 전문으로 하며, 교육 분야에 특화된 AI 솔루션을 연구합니다."
  },
  {
    name: "권민환",
    role: "프론트엔드 개발",
    quote: "데이터로 인사이트를 발견합니다",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-chart-bar",
    description: "교육 데이터를 분석하여 학습 패턴과 효과를 측정합니다. 통계적 분석과 시각화를 통해 교육 과정의 개선점을 발견하고, 데이터 기반의 의사결정을 지원합니다."
  },
  {
    name: "범수",
    role: "백엔드 개발",
    quote: "안정적인 서비스를 보장합니다",
    image: "https://via.placeholder.com/300x400",
    icon: "fas fa-cloud",
    description: "AWS와 Docker를 활용한 클라우드 인프라를 구축하고 관리합니다. CI/CD 파이프라인을 구축하여 개발과 배포 과정을 자동화하고, 시스템의 안정성과 확장성을 보장합니다."
  },
]

// 선택된 팀원 상태
const selectedMember = ref(null)

// 드래그 가능한 카드 데이터
const draggableCards = ref([])
const isLoading = ref(false)
const error = ref(null)

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
  
  // 백엔드가 없으므로 기본 데이터 사용
  console.log('백엔드 없음: 기본 데이터 사용')
  draggableCards.value = defaultClasses
  isLoading.value = false
  
  // 백엔드가 준비되면 아래 주석을 해제하고 사용
  /*
  try {
    // 백엔드에서 인기 클래스 데이터 가져오기
    const classes = await classService.getPopularClasses(8)
    draggableCards.value = classes
  } catch (err) {
    console.error('클래스 데이터 로드 실패:', err)
    error.value = '클래스 데이터를 불러오는데 실패했습니다.'
    
    // 에러 시 기본 데이터 사용
    draggableCards.value = defaultClasses
  } finally {
    isLoading.value = false
  }
  */
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
  return -(draggableCards.value.length - visibleCards) * cardWidth
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
  // 백엔드가 없으므로 시뮬레이션
  console.log('수강 신청 시뮬레이션:', classId)
  alert('수강 신청이 완료되었습니다! (시뮬레이션)')
  
  // 백엔드가 준비되면 아래 주석을 해제하고 사용
  /*
  try {
    await classService.enrollClass(classId)
    alert('수강 신청이 완료되었습니다!')
  } catch (error) {
    console.error('수강 신청 실패:', error)
    alert('수강 신청에 실패했습니다. 다시 시도해주세요.')
  }
  */
}



// 로그아웃 처리
const handleLogout = async () => {
  try {
    await authAPI.logout()
  } catch (error) {
    console.error("로그아웃 오류:", error)
  } finally {
    tokenManager.removeToken()
    userManager.removeUser()
    user.value = null
    router.push("/")
  }
}

onMounted(async () => {
  if (isLoggedIn.value) {
    user.value = userManager.getUser()
  }
  
  // 클래스 데이터 로드
  await loadClasses()
  
  await nextTick()
 
})
</script>


