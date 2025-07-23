<template>
  <div class="home-view">
    <!-- 인트로(인트로 텍스트/버튼) -->
    <section class="hero">
      <div class="hero-background">
        <div class="gradient-overlay"></div>
        <div class="floating-shapes">
          <div class="shape shape-1"></div>
          <div class="shape shape-2"></div>
          <div class="shape shape-3"></div>
        </div>
      </div>
      <div class="intro-text">
        <h1 class="hero-title">
          <span class="title-line">EduMeet에 오신 것을</span>
          <span class="title-line highlight">환영합니다</span>
        </h1>
        <p class="hero-subtitle">혁신적인 교육 플랫폼으로 새로운 학습 경험을 시작하세요</p>
      </div>
      <div class="hero-actions">
        <router-link :to="isLoggedIn ? '/create-room' : '/login'" class="hero-btn primary-btn">
          <span class="btn-text">시작하기</span>
          <span class="btn-icon">→</span>
        </router-link>
        <router-link to="/about" class="hero-btn secondary-btn">
          <span class="btn-text">더 알아보기</span>
        </router-link>
      </div>
    </section>
    
    <!-- 설명 Section -->
    <section class="main-section">
      <div class="layout-container">
        <!-- 왼쪽 네비게이션 패널 -->
        <div class="left-panel">
          <div class="nav-buttons">
            <button type="button" @click="slideTransition(0)" :class="{ active: currentSlide === 0 }" class="nav-btn">
              <span class="nav-icon">🎓</span>
              <span class="nav-text">online class</span>
            </button>
            <button type="button" @click="slideTransition(1)" :class="{ active: currentSlide === 1 }" class="nav-btn">
              <span class="nav-icon">🤖</span>
              <span class="nav-text">AI 수업 요약 서비스</span>
            </button>
            <button type="button" @click="slideTransition(2)" :class="{ active: currentSlide === 2 }" class="nav-btn">
              <span class="nav-icon">📝</span>
              <span class="nav-text">실시간 자막 서비스</span>
            </button>
          </div>
        </div>
        
        <!-- 오른쪽 콘텐츠 영역 -->
        <div class="content-area">
          <div id="mainCarousel" class="carousel slide" data-bs-ride="carousel">
            <transition name="slide-fade" mode="out-in">
              <div :key="currentSlide" class="carousel-item active">
                <div class="content-wrapper">
                  <div class="image-container">
                    <img :src="getCurrentImage()" class="main-image" :alt="`mainim${currentSlide + 1}`" />
                    <div class="image-overlay"></div>
                  </div>
                  <div class="main-text">
                    <div class="text-badge">{{ getCurrentHeading() }}</div>
                    <h3 class="main-heading">{{ getCurrentSubheading() }}</h3>
                    <p class="main-body">
                      {{ getCurrentBody() }}
                    </p>
                    <button class="learn-more-btn">
                      자세히 알아보기
                      <span class="arrow">→</span>
                    </button>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </section>
    
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
               :style="{ transform: `translateX(${translateX}px)` }"
               ref="cardsWrapper">
            <div class="draggable-card" 
                 v-for="(card, index) in draggableCards" 
                 :key="card.id"
                 :style="{ animationDelay: `${index * 0.1}s` }">
              <div class="card-image">
                <img :src="card.image" :alt="card.title" />
                <div class="card-overlay">
                  <div class="card-hover-content">
                    <span class="view-more">자세히 보기</span>
                  </div>
                </div>
                <div class="card-badge">{{ card.tags[0] }}</div>
              </div>
              <div class="card-content">
                <h3 class="card-title">{{ card.title }}</h3>
                <p class="card-description">{{ card.description }}</p>
                <div class="card-tags">
                  <span class="tag" v-for="tag in card.tags" :key="tag">{{ tag }}</span>
                </div>
                <div class="card-footer">
                  <div class="card-stats">
                    <span class="stat">⭐ 4.8</span>
                    <span class="stat">👥 1.2k</span>
                  </div>
                  <button class="enroll-btn">수강하기</button>
                </div>
              </div>
            </div>
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
const draggableCards = ref([
  {
    id: 1,
    title: "Vue.js 마스터 클래스",
    description: "Vue.js의 핵심 개념부터 고급 기능까지 체계적으로 학습하세요. 실무에서 바로 활용할 수 있는 실습 중심의 강의입니다.",
    image: "https://via.placeholder.com/300x200/227a53/ffffff?text=Vue.js",
    tags: ["프론트엔드", "Vue.js", "JavaScript"]
  },
  {
    id: 2,
    title: "React 완전 정복",
    description: "React의 기본부터 고급 패턴까지. Hooks, Context API, 상태 관리 등 현대적인 React 개발을 배워보세요.",
    image: "https://via.placeholder.com/300x200/667eea/ffffff?text=React",
    tags: ["프론트엔드", "React", "JavaScript"]
  },
  {
    id: 3,
    title: "Node.js 백엔드 개발",
    description: "Express.js와 MongoDB를 활용한 실전 백엔드 개발. RESTful API 설계부터 배포까지 완벽 가이드.",
    image: "https://via.placeholder.com/300x200/27ae60/ffffff?text=Node.js",
    tags: ["백엔드", "Node.js", "Express"]
  },
  {
    id: 4,
    title: "Python 데이터 분석",
    description: "Pandas, NumPy, Matplotlib을 활용한 데이터 분석과 시각화. 실무 데이터로 배우는 데이터 사이언스.",
    image: "https://via.placeholder.com/300x200/3498db/ffffff?text=Python",
    tags: ["데이터분석", "Python", "Pandas"]
  },
  {
    id: 5,
    title: "AWS 클라우드 아키텍처",
    description: "AWS 서비스를 활용한 확장 가능한 클라우드 인프라 구축. 실무 중심의 클라우드 아키텍처 설계.",
    image: "https://via.placeholder.com/300x200/ff6b35/ffffff?text=AWS",
    tags: ["클라우드", "AWS", "인프라"]
  },
  {
    id: 6,
    title: "Docker 컨테이너 기술",
    description: "Docker와 Kubernetes를 활용한 컨테이너 기반 애플리케이션 배포. DevOps 실무 스킬을 익혀보세요.",
    image: "https://via.placeholder.com/300x200/0db7ed/ffffff?text=Docker",
    tags: ["DevOps", "Docker", "Kubernetes"]
  },
  {
    id: 7,
    title: "UI/UX 디자인 기초",
    description: "사용자 중심의 디자인 원칙과 Figma를 활용한 프로토타이핑. 실제 프로젝트로 배우는 디자인 워크플로우.",
    image: "https://via.placeholder.com/300x200/e74c3c/ffffff?text=Design",
    tags: ["디자인", "UI/UX", "Figma"]
  },
  {
    id: 8,
    title: "머신러닝 입문",
    description: "Scikit-learn과 TensorFlow를 활용한 머신러닝 기초. 실제 데이터로 배우는 AI 모델 개발.",
    image: "https://via.placeholder.com/300x200/9b59b6/ffffff?text=ML",
    tags: ["AI", "머신러닝", "TensorFlow"]
  }
])

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
const minTranslate = -(draggableCards.value.length - visibleCards) * cardWidth

// 드래그 시작
const startDrag = (e) => {
  isDragging.value = true
  startX.value = e.clientX - translateX.value
  e.preventDefault()
}

// 드래그 중
const onDrag = (e) => {
  if (!isDragging.value) return
  
  const currentX = e.clientX - startX.value
  translateX.value = Math.max(minTranslate, Math.min(maxTranslate, currentX))
}

// 드래그 종료 - 스냅 기능 추가
const stopDrag = () => {
  if (!isDragging.value) return
  
  isDragging.value = false
  
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

const imageList = ["/src/assets/mainim.png", "/src/assets/mainim1.png", "/src/assets/mainim2.png"]
const mainImageIndex = ref(0)
const mainImageSrc = computed(() => imageList[mainImageIndex.value])
const descriptList = []
const currentSlide = ref(0)

// 슬라이드 데이터
const slideData = [
  {
    image: "/src/assets/mainim.png",
    heading: "online class",
    subheading: "교육을 연결하는 새로운 방법",
    body: "언제 어디서든 편리하게 참여하는 온라인 강의를 통해 학습의 가능성을 넓혀보세요. 다양한 분야의 전문가와 함께 실시간으로 소통하며 깊이 있는 지식을 습득하고, 동료 학습자들과 협력하여 학습 효과를 극대화할 수 있습니다. EduMeet의 온라인 클래스는 시간과 공간의 제약 없이 원하는 교육을 받을 수 있도록 지원합니다."},
  {
    image: "/src/assets/mainim1.png",
    heading: "AI 수업 요약 서비스",
    subheading: "자동화된 수업 요약 서비스",
    body: "자동화된 수업 요약 서비스\n\n수업 내용을 놓치셨나요? EduMeet의 AI 수업 요약 서비스는 실시간 강의 내용을 텍스트로 변환하고 핵심 내용을 자동으로 요약하여 제공합니다. 복습 시간을 절약하고 학습 효율성을 높여보세요. 중요한 정보를 빠르고 정확하게 파악하여 학습 효과를 극대화할 수 있습니다."
   },
  {
    image: "/src/assets/mainim2.png",
    heading: "실시간 자막 서비스",
    subheading: "차별 없는 교육 환경 제공",
    body: "차별 없는 교육 환경 제공\n\n언어 장벽 없이 모두가 평등하게 교육에 참여할 수 있도록 EduMeet는 실시간 자막 서비스를 제공합니다. 다양한 언어를 지원하여 국제적인 학습 환경을 구축하고, 청각에 어려움을 겪는 학습자들에게도 편리한 학습 환경을 제공합니다. EduMeet는 모든 학습자의 교육 접근성을 향상시키기 위해 노력합니다."
  }
]

// 현재 슬라이드 데이터 가져오기 함수들
const getCurrentImage = () => slideData[currentSlide.value].image
const getCurrentHeading = () => slideData[currentSlide.value].heading
const getCurrentSubheading = () => slideData[currentSlide.value].subheading
const getCurrentBody = () => slideData[currentSlide.value].body

const goToSlide = (slideIndex) => {
  currentSlide.value = slideIndex
}

// 슬라이드 전환 애니메이션을 위한 함수
const slideTransition = (slideIndex) => {
  currentSlide.value = slideIndex
}

const handleMainImageClick = (e) => {
  const el = e.currentTarget
  const rect = el.getBoundingClientRect()
  const x = e.clientX - rect.left
  const edge = rect.width * 0.15
  if (x < edge || x > rect.width - edge) {
    // 가장자리 클릭 시 다음 이미지로
    mainImageIndex.value = (mainImageIndex.value + 1) % imageList.length
  }
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
  await nextTick()
  // 설명 섹션 이미지/텍스트
  gsap.from(".main-image", {
    scrollTrigger: {
      trigger: ".main-section",
      start: "top 80%",
      toggleActions: "play none none reverse",
    },
    x: 100,
    opacity: 0,
    duration: 1,
    ease: "power2.out"
  })
  gsap.from(".main-text", {
    scrollTrigger: {
      trigger: ".main-section",
      start: "top 80%",
      toggleActions: "play none none reverse",
    },
    x: -100,
    opacity: 0,
    duration: 1,
    ease: "power2.out",
    delay: 0.1
  })
 
})
</script>


