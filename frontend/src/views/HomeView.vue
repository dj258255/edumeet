<template>
  <div class="home-view">
    <!-- 인트로(인트로 텍스트/버튼) -->
    <section class="hero">
      <img src="@/assets/image.png" alt="Hero Main" class="hero-image parallax-bg" />
      <div class="intro-text">
        <h1 class="hero-title">EduMeet에 오신 것을 환영합니다</h1>
      </div>
      <button class="hero-btn intro-btn">시작하기</button>
    </section>
    <!-- 설명 Section -->
    <section class="main-section">
      <div class="layout-container">
        <!-- 왼쪽 네비게이션 패널 -->
        <div class="left-panel">
          <div class="nav-buttons">
            <button type="button" @click="slideTransition(0)" :class="{ active: currentSlide === 0 }" class="nav-btn">
              mainim1
            </button>
            <button type="button" @click="slideTransition(1)" :class="{ active: currentSlide === 1 }" class="nav-btn">
              mainim2
            </button>
            <button type="button" @click="slideTransition(2)" :class="{ active: currentSlide === 2 }" class="nav-btn">
              mainim3
            </button>
          </div>
        </div>
        
        <!-- 오른쪽 콘텐츠 영역 -->
        <div class="content-area">
          <div id="mainCarousel" class="carousel slide" data-bs-ride="carousel">
            <transition name="slide-fade" mode="out-in">
              <div :key="currentSlide" class="carousel-item active">
                <div class="content-wrapper">
                  <img :src="getCurrentImage()" class="main-image" :alt="`mainim${currentSlide + 1}`" />
                  <div class="main-text">
                    <h3 class="main-heading">{{ getCurrentHeading() }}</h3>
                    <div class="main-subheading">{{ getCurrentSubheading() }}</div>
                    <p class="main-body">
                      {{ getCurrentBody() }}
                    </p>
                  </div>
                </div>
              </div>
            </transition>
          </div>
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
              <img class="member-photo animate-photo" :src="selectedMember.image" :alt="selectedMember.name" />
              <div class="member-info">
                <h3 class="member-name animate-text">{{ selectedMember.name }}</h3>
                <p class="member-role animate-text">{{ selectedMember.role }}</p>
                <p class="member-quote animate-text">"{{ selectedMember.quote }}"</p>
                <p class="member-description animate-text">{{ selectedMember.description }}</p>
              </div>
            </div>
            <div class="team-overview" v-else key="overview">
              <h2 class="team-title">우리 팀을 소개합니다</h2>
              <p class="team-subtitle">함께 만들어가는 멤버들입니다</p>
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
    heading: "EduMeet 플랫폼",
    subheading: "교육을 연결하는 새로운 방법",
    body: "Body text for your whole article or post. We'll put in some lorem ipsum to show how a filled-out page might look:\n\nExceptetur efficient emerging, minim veniam anim aute carefully curated Ginza conversation exquisite perfect nostrud nisi intricate Content. Qui international first-class nulla ut. Punctual adipiscing, essential lovely queen tempor eiusmod irure. Exclusive izakaya charming Scandinavian impeccable aute quality off irid soft power parlour Melbourne occaecat discerning. Qui wardrobe aliquip, et Porter destination Toto remarkable officia Helsinki exceptetur Basset hound. Zürich sleepy perfect consectetur."
  },
  {
    image: "/src/assets/mainim1.png",
    heading: "AI 기반 학습",
    subheading: "개인화된 교육 경험",
    body: "Body text for your whole article or post. We'll put in some lorem ipsum to show how a filled-out page might look:\n\nExceptetur efficient emerging, minim veniam anim aute carefully curated Ginza conversation exquisite perfect nostrud nisi intricate Content. Qui international first-class nulla ut. Punctual adipiscing, essential lovely queen tempor eiusmod irure. Exclusive izakaya charming Scandinavian impeccable aute quality off irid soft power parlour Melbourne occaecat discerning. Qui wardrobe aliquip, et Porter destination Toto remarkable officia Helsinki exceptetur Basset hound. Zürich sleepy perfect consectetur."
  },
  {
    image: "/src/assets/mainim2.png",
    heading: "실시간 협업",
    subheading: "함께 만들어가는 교육",
    body: "Body text for your whole article or post. We'll put in some lorem ipsum to show how a filled-out page might look:\n\nExceptetur efficient emerging, minim veniam anim aute carefully curated Ginza conversation exquisite perfect nostrud nisi intricate Content. Qui international first-class nulla ut. Punctual adipiscing, essential lovely queen tempor eiusmod irure. Exclusive izakaya charming Scandinavian impeccable aute quality off irid soft power parlour Melbourne occaecat discerning. Qui wardrobe aliquip, et Porter destination Toto remarkable officia Helsinki exceptetur Basset hound. Zürich sleepy perfect consectetur."
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
  // 인트로 텍스트/버튼 애니메이션
  gsap.from(".intro-text", {
    y: 60,
    opacity: 0,
    duration: 1.1,
    ease: "power3.out",
    delay: 0.1
  })
  gsap.from(".intro-btn", {
    y: 40,
    opacity: 0,
    duration: 0.8,
    delay: 0.7,
    ease: "power3.out"
  })
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

<style scoped>
.home-view {
  min-height: 100vh;
  background-color: #fafafd;
}
.logo {
  height: 40px;
  width: auto;
}

.brand-text {
  font-size: 24px;
  font-weight: 700;
  color: #249d6d;
}

.welcome-text {
  color: #666;
  font-size: 14px;
}

/* 메인 콘텐츠 */
.main-content {
  padding-top: 20px;
}

/* 팀 섹션 */
.team-section {
  padding: 60px 0;
  background-color: #fafafd;
}

.team-layout {
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  gap: 40px;
  min-height: 600px;
}

/* 왼쪽 팀원 상세 정보 패널 */
.team-detail-panel {
  flex: 2;
  background: white;
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
}

.team-member-detail {
  display: flex;
  gap: 30px;
  width: 100%;
}

.member-photo {
  width: 300px;
  height: 400px;
  object-fit: cover;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.member-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.member-name {
  font-size: 32px;
  font-weight: 700;
  color: #227a53;
  margin: 0;
}

.member-role {
  font-size: 18px;
  color: #666;
  margin: 0;
  font-weight: 500;
}

.member-quote {
  font-size: 20px;
  color: #227a53;
  font-style: italic;
  margin: 0;
  padding: 20px;
  background: #f0f9f4;
  border-radius: 12px;
  border-left: 4px solid #227a53;
}

.member-description {
  font-size: 16px;
  line-height: 1.7;
  color: #444;
  margin: 0;
}

/* 팀 개요 (기본 화면) */
.team-overview {
  text-align: center;
  width: 100%;
}

.team-title {
  font-size: 36px;
  font-weight: 700;
  color: #227a53;
  margin-bottom: 16px;
}

.team-subtitle {
  font-size: 18px;
  color: #666;
  margin-bottom: 24px;
}

.team-description {
  font-size: 16px;
  line-height: 1.7;
  color: #444;
  margin-bottom: 40px;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.team-stats {
  display: flex;
  justify-content: center;
  gap: 60px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.stat-number {
  font-size: 48px;
  font-weight: 700;
  color: #227a53;
}

.stat-label {
  font-size: 16px;
  color: #666;
}

/* 오른쪽 팀원 카드 패널 */
.team-cards-panel {
  flex: 1;
  display: flex;
  align-items: center;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  width: 100%;
}

.member-card {
  background: white;
  border: 2px solid #e0e0e0;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  position: relative;
  overflow: hidden;
}

.member-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(34, 122, 83, 0.1), transparent);
  transition: left 0.6s ease;
}

.member-card:hover::before {
  left: 100%;
}

.member-card:hover {
  border-color: #227a53;
  transform: translateY(-6px) scale(1.02);
  box-shadow: 0 12px 32px rgba(34, 122, 83, 0.2);
}

.member-card.active {
  border-color: #227a53;
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  color: white;
  transform: translateY(-6px) scale(1.02);
  box-shadow: 0 16px 40px rgba(34, 122, 83, 0.3);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 16px 40px rgba(34, 122, 83, 0.3);
  }
  50% {
    box-shadow: 0 16px 40px rgba(34, 122, 83, 0.5);
  }
}

.card-icon {
  font-size: 32px;
  color: #227a53;
  margin-bottom: 8px;
}

.member-card.active .card-icon {
  color: white;
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.card-role {
  font-size: 14px;
  margin: 0;
  opacity: 0.8;
}

.image-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 50vh;
}

.backImage {
  max-width: 100%;
  height: auto;
  margin-top: 445px;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding: 0;
  margin: 0;
  background: transparent;
}
.hero-title {
  font-size: 56px;
  font-weight: 700;
  margin-bottom: 8px;
  color: #111;
}
.hero-btn {
  background: #f2f2f2;
  color: #222;
  border: none;
  border-radius: 8px;
  padding: 10px 28px;
  font-size: 18px;
  font-weight: 500;
  cursor: pointer;
}

.hero-image {
  width: 100vw;
  max-width: none;
  height: auto;
  display: block;
  margin-bottom: 20px;
  position: relative;
  left: 50%;
  transform: translateX(-50%);
  border-radius: 0;
}

.main-section {
  padding: 40px 0 0 0;
  margin: 0 auto 40px auto;
}

.layout-container {
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 600px;
}
.main-text {
  flex: 1.2;
}
.main-heading {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 8px;
}
.main-subheading {
  font-size: 16px;
  color: #888;
  margin-bottom: 18px;
}
.main-body {
  font-size: 16px;
  color: #444;
  line-height: 1.7;
}
.main-image {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.main-image img {
  margin-top: 60px;
  max-width: 600px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.carousel-item-flex {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 왼쪽 네비게이션 패널 */
.left-panel {
  width: 280px;
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-radius: 0 20px 20px 0;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
}

.nav-buttons {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.nav-btn {
  background: transparent;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  padding: 16px 20px;
  font-size: 16px;
  font-weight: 600;
  color: white;
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: left;
  position: relative;
  overflow: hidden;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateX(8px);
}

.nav-btn.active {
  background: rgba(255, 255, 255, 0.2);
  border-color: white;
  box-shadow: 0 4px 15px rgba(255, 255, 255, 0.2);
}

/* 오른쪽 콘텐츠 영역 */
.content-area {
  flex: 1;
  background: #f8fafc;
  padding: 40px;
  border-radius: 20px 0 0 20px;
  display: flex;
  align-items: center;
}

.content-wrapper {
  display: flex;
  align-items: center;
  gap: 40px;
  width: 100%;
}

/* Vue Transition 애니메이션 */
.slide-fade-enter-active {
  transition: all 0.6s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.4s ease-in;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

/* 팀원 상세 정보 애니메이션 */
.animate-photo {
  animation: slideInFromLeft 0.8s ease-out 0.2s both;
}

.animate-text {
  animation: slideInFromRight 0.8s ease-out both;
}

.animate-text:nth-child(1) {
  animation-delay: 0.3s;
}

.animate-text:nth-child(2) {
  animation-delay: 0.4s;
}

.animate-text:nth-child(3) {
  animation-delay: 0.5s;
}

.animate-text:nth-child(4) {
  animation-delay: 0.6s;
}

/* 팀 개요 애니메이션 */
.team-overview .team-title {
  animation: fadeInUp 0.8s ease-out 0.2s both;
}

.team-overview .team-subtitle {
  animation: fadeInUp 0.8s ease-out 0.3s both;
}

.team-overview .team-description {
  animation: fadeInUp 0.8s ease-out 0.4s both;
}

.team-overview .team-stats {
  animation: fadeInUp 0.8s ease-out 0.5s both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 캐러셀 아이템 기본 스타일 */
.carousel-item {
  width: 100%;
}

/* 이미지와 텍스트 개별 애니메이션 */
.carousel-item .main-image {
  animation: slideInFromLeft 0.8s ease-out 0.3s both;
}

.carousel-item .main-text {
  animation: slideInFromRight 0.8s ease-out 0.5s both;
}

@keyframes slideInFromLeft {
  from {
    opacity: 0;
    transform: translateX(-40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideInFromRight {
  from {
    opacity: 0;
    transform: translateX(40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.main-image {
  flex: 1;
  max-width: 500px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease;
}

.main-image:hover {
  transform: scale(1.02);
}

.main-text {
  flex: 1;
  padding: 0 20px;
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .nav-container {
    padding: 0 15px;
  }
  
  .brand-text {
    font-size: 20px;
  }
  
  .nav-menu {
    gap: 15px;
  }
  
  .welcome-text {
    display: none;
  }
  
  .nav-btn {
    padding: 8px 16px;
    font-size: 13px;
  }
  
  /* 모바일 레이아웃 */
  .layout-container {
    flex-direction: column;
    min-height: auto;
  }
  
  .left-panel {
    width: 100%;
    border-radius: 20px 20px 0 0;
    padding: 20px;
  }
  
  .nav-buttons {
    flex-direction: row;
    justify-content: center;
    gap: 10px;
  }
  
  .nav-btn {
    padding: 12px 16px;
    font-size: 14px;
    text-align: center;
  }
  
  .content-area {
    border-radius: 0 0 20px 20px;
    padding: 20px;
  }
  
  .content-wrapper {
    flex-direction: column;
    gap: 20px;
  }
  
  .main-image {
    max-width: 100%;
  }
  
  .main-text {
    padding: 0;
  }
  
  /* 팀 섹션 모바일 레이아웃 */
  .team-layout {
    flex-direction: column;
    gap: 20px;
  }
  
  .team-detail-panel {
    padding: 20px;
  }
  
  .team-member-detail {
    flex-direction: column;
    text-align: center;
  }
  
  .member-photo {
    width: 200px;
    height: 250px;
    margin: 0 auto;
  }
  
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  
  .member-card {
    padding: 16px;
  }
  
  .card-icon {
    font-size: 24px;
  }
  
  .card-name {
    font-size: 14px;
  }
  
  .card-role {
    font-size: 12px;
  }
}
.intro-text { text-align: center; margin-bottom: 18px; }
.intro-btn { display: block; margin: 0 auto; }
.footer {
  width: 100%;
  background: #f2f2f2;
  padding: 28px 0 18px 0;
  text-align: center;
  font-size: 15px;
  color: #555;
  margin-top: 0px;
}
.footer-content {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 10px;
}
.footer-title {
  font-weight: bold;
  color: #249d6d;
}
.footer-divider {
  color: #bbb;
}
@media (max-width: 600px) {
  .footer-content { flex-direction: column; gap: 4px; }
}
</style>
