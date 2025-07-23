<template>
  <div class="about-view">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <div class="hero-badge">EduMeet 소개</div>
        <h1 class="hero-title">혁신적인 교육 플랫폼</h1>
        <p class="hero-subtitle">
          EduMeet는 온라인 교육의 새로운 패러다임을 제시합니다. 
          AI 기술과 실시간 상호작용을 통해 더욱 효과적이고 몰입감 있는 학습 경험을 제공합니다.
        </p>
      </div>
      <div class="hero-image">
        <div class="floating-elements">
          <div class="element element-1">🎓</div>
          <div class="element element-2">🤖</div>
          <div class="element element-3">📝</div>
          <div class="element element-4">💡</div>
        </div>
      </div>
    </section>

    <!-- 서비스 소개 Section -->
    <section class="service-intro">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">왜 EduMeet인가요?</h2>
          <p class="section-subtitle">
            기존 온라인 교육의 한계를 뛰어넘는 EduMeet만의 특별한 기능들을 만나보세요
          </p>
        </div>
        
        <div class="features-grid">
          <div class="feature-card" v-for="feature in features" :key="feature.id">
            <div class="feature-icon">{{ feature.icon }}</div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-description">{{ feature.description }}</p>
            <ul class="feature-list">
              <li v-for="benefit in feature.benefits" :key="benefit">{{ benefit }}</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 주요 기능 상세 Section -->
    <section class="detailed-features">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">주요 기능</h2>
          <p class="section-subtitle">EduMeet의 핵심 기능들을 자세히 살펴보세요</p>
        </div>

        <div class="feature-details">
          <div class="feature-detail" v-for="detail in featureDetails" :key="detail.id">
            <div class="detail-content">
              <div class="detail-header">
                <div class="detail-icon">{{ detail.icon }}</div>
                <h3 class="detail-title">{{ detail.title }}</h3>
              </div>
              <p class="detail-description">{{ detail.description }}</p>
              <div class="detail-highlights">
                <div class="highlight" v-for="highlight in detail.highlights" :key="highlight">
                  <span class="highlight-icon">✓</span>
                  <span class="highlight-text">{{ highlight }}</span>
                </div>
              </div>
            </div>
            <div class="detail-image">
              <div class="image-placeholder">
                <div class="placeholder-icon">{{ detail.imageIcon }}</div>
                <p>{{ detail.imageText }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 사용법 Section -->
    <section class="how-to-use">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">사용법</h2>
          <p class="section-subtitle">EduMeet를 시작하는 방법을 단계별로 안내해드립니다</p>
        </div>

        <div class="steps-container">
          <div class="step" v-for="(step, index) in steps" :key="step.id">
            <div class="step-number">{{ index + 1 }}</div>
            <div class="step-content">
              <h3 class="step-title">{{ step.title }}</h3>
              <p class="step-description">{{ step.description }}</p>
              <div class="step-actions">
                <router-link :to="step.link" class="step-btn">
                  {{ step.buttonText }}
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 기술 스택 Section -->
    <section class="tech-stack">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">기술 스택</h2>
          <p class="section-subtitle">EduMeet를 구축한 최신 기술들을 소개합니다</p>
        </div>

        <div class="tech-grid">
          <div class="tech-category" v-for="category in techStack" :key="category.name">
            <h3 class="category-title">{{ category.name }}</h3>
            <div class="tech-items">
              <div class="tech-item" v-for="tech in category.items" :key="tech.name">
                <div class="tech-icon">{{ tech.icon }}</div>
                <span class="tech-name">{{ tech.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="container">
        <div class="cta-content">
          <h2 class="cta-title">지금 바로 시작해보세요</h2>
          <p class="cta-subtitle">
            EduMeet와 함께 새로운 교육 경험을 만들어보세요
          </p>
          <div class="cta-buttons">
            <router-link to="/" class="cta-btn primary">
              홈으로 돌아가기
            </router-link>
            <router-link to="/create-room" class="cta-btn secondary">
              방 만들기
            </router-link>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const features = ref([
  {
    id: 1,
    icon: '🎓',
    title: '실시간 온라인 수업',
    description: '언제 어디서나 편리하게 참여하는 온라인 강의',
    benefits: [
      '고품질 비디오/오디오 스트리밍',
      '실시간 화면 공유 및 협업',
      '다양한 참가자 관리 기능'
    ]
  },
  {
    id: 2,
    icon: '🤖',
    title: 'AI 수업 요약 서비스',
    description: '수업 내용을 자동으로 요약하여 학습 효율성 극대화',
    benefits: [
      '실시간 음성-텍스트 변환',
      '핵심 내용 자동 추출',
      '개인화된 학습 노트 생성'
    ]
  },
  {
    id: 3,
    icon: '📝',
    title: '실시간 자막 서비스',
    description: '언어 장벽 없는 교육 환경 제공',
    benefits: [
      '다국어 실시간 자막',
      '청각 장애인 접근성 향상',
      '정확한 발음 및 억양 표시'
    ]
  }
])

const featureDetails = ref([
  {
    id: 1,
    icon: '🎥',
    title: '고품질 화상회의',
    description: '4K 해상도와 고음질을 지원하는 최고 수준의 화상회의 시스템을 제공합니다. 네트워크 상태에 관계없이 안정적인 연결을 보장하며, 최대 100명까지 동시 참여가 가능합니다.',
    highlights: [
      '4K 고화질 비디오 스트리밍',
      '노이즈 캔슬링 오디오',
      '자동 대역폭 최적화',
      '최대 100명 동시 참여'
    ],
    imageIcon: '📹',
    imageText: '고품질 화상회의'
  },
  {
    id: 2,
    icon: '🧠',
    title: 'AI 기반 학습 분석',
    description: '머신러닝 기술을 활용하여 학습자의 참여도와 이해도를 실시간으로 분석합니다. 개인별 맞춤 학습 경로를 제안하고, 학습 효과를 극대화할 수 있도록 도와줍니다.',
    highlights: [
      '실시간 참여도 분석',
      '개인별 학습 패턴 분석',
      '맞춤형 학습 콘텐츠 추천',
      '학습 성과 리포트 생성'
    ],
    imageIcon: '📊',
    imageText: 'AI 학습 분석'
  },
  {
    id: 3,
    icon: '🔒',
    title: '보안 및 개인정보 보호',
    description: '엔터프라이즈급 보안 시스템으로 모든 통신을 암호화하여 안전한 교육 환경을 제공합니다. GDPR 및 개인정보보호법을 준수하여 사용자의 개인정보를 철저히 보호합니다.',
    highlights: [
      '엔드투엔드 암호화',
      'GDPR 준수',
      '다중 인증 시스템',
      '정기 보안 감사'
    ],
    imageIcon: '🛡️',
    imageText: '보안 시스템'
  }
])

const steps = ref([
  {
    id: 1,
    title: '계정 생성',
    description: 'EduMeet에 가입하여 개인 계정을 만드세요. 간단한 이메일 인증만으로 시작할 수 있습니다.',
    buttonText: '회원가입',
    link: '/signup'
  },
  {
    id: 2,
    title: '방 생성',
    description: '새로운 수업 방을 만들고 제목, 설명, 설정을 구성하세요.',
    buttonText: '방 만들기',
    link: '/create-room'
  },
  {
    id: 3,
    title: '참가자 초대',
    description: '생성된 방 링크를 공유하여 학생들을 초대하세요.',
    buttonText: '링크 복사',
    link: '/'
  },
  {
    id: 4,
    title: '수업 시작',
    description: '모든 준비가 완료되면 수업을 시작하고 EduMeet의 모든 기능을 활용하세요.',
    buttonText: '수업 시작',
    link: '/'
  }
])

const techStack = ref([
  {
    name: '프론트엔드',
    items: [
      { name: 'Vue.js', icon: '⚡' },
      { name: 'JavaScript', icon: '🟨' },
      { name: 'HTML5', icon: '🌐' },
      { name: 'CSS3', icon: '🎨' }
    ]
  },
  {
    name: '백엔드',
    items: [
      { name: 'Node.js', icon: '🟢' },
      { name: 'Express.js', icon: '🚀' },
      { name: 'Python', icon: '🐍' },
      { name: 'Django', icon: '⚙️' }
    ]
  },
  {
    name: 'AI/ML',
    items: [
      { name: 'TensorFlow', icon: '🧠' },
      { name: 'OpenAI API', icon: '🤖' },
      { name: 'NLP', icon: '📝' },
      { name: 'Speech Recognition', icon: '🎤' }
    ]
  },
  {
    name: '인프라',
    items: [
      { name: 'AWS', icon: '☁️' },
      { name: 'Docker', icon: '🐳' },
      { name: 'WebRTC', icon: '📹' },
      { name: 'WebSocket', icon: '🔌' }
    ]
  }
])
</script>

<style scoped>
.about-view {
  min-height: 100vh;
  background: var(--bg-secondary);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-lg);
}

/* Hero Section */
.hero-section {
  display: flex;
  align-items: center;
  min-height: 80vh;
  padding: var(--spacing-2xl) 0;
  background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
  position: relative;
  overflow: hidden;
}

.hero-content {
  flex: 1;
  z-index: 2;
}

.hero-badge {
  display: inline-block;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  font-size: var(--font-size-sm);
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 20px;
  margin-bottom: var(--spacing-md);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.hero-title {
  font-size: var(--font-size-5xl);
  font-weight: 800;
  color: var(--brand-main);
  margin-bottom: var(--spacing-lg);
  line-height: 1.1;
}

.hero-subtitle {
  font-size: var(--font-size-xl);
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 600px;
}

.hero-image {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.floating-elements {
  position: relative;
  width: 400px;
  height: 400px;
}

.element {
  position: absolute;
  font-size: 4rem;
  animation: float 6s ease-in-out infinite;
}

.element-1 {
  top: 20%;
  left: 20%;
  animation-delay: 0s;
}

.element-2 {
  top: 60%;
  right: 20%;
  animation-delay: 2s;
}

.element-3 {
  bottom: 20%;
  left: 30%;
  animation-delay: 4s;
}

.element-4 {
  top: 40%;
  right: 40%;
  animation-delay: 1s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

/* Section Styles */
.section-header {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
}

.section-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--brand-main);
  margin-bottom: var(--spacing-md);
}

.section-subtitle {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 600px;
  margin: 0 auto;
}

/* Service Intro Section */
.service-intro {
  padding: var(--spacing-2xl) 0;
  background: var(--bg-primary);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: var(--spacing-xl);
}

.feature-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
  transition: all var(--transition-normal);
}

.feature-card:hover {
  transform: translateY(-8px);
  box-shadow: var(--shadow-lg);
}

.feature-icon {
  font-size: 3rem;
  margin-bottom: var(--spacing-md);
}

.feature-title {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--brand-main);
  margin-bottom: var(--spacing-md);
}

.feature-description {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--spacing-lg);
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feature-list li {
  padding: var(--spacing-sm) 0;
  color: var(--text-primary);
  position: relative;
  padding-left: var(--spacing-lg);
}

.feature-list li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: var(--brand-main);
  font-weight: bold;
}

/* Detailed Features Section */
.detailed-features {
  padding: var(--spacing-2xl) 0;
  background: var(--bg-secondary);
}

.feature-details {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xl);
}

.feature-detail {
  display: flex;
  align-items: center;
  gap: var(--spacing-xl);
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
}

.feature-detail:nth-child(even) {
  flex-direction: row-reverse;
}

.detail-content {
  flex: 1;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.detail-icon {
  font-size: 2rem;
}

.detail-title {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  color: var(--brand-main);
  margin: 0;
}

.detail-description {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: var(--spacing-lg);
}

.detail-highlights {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.highlight {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.highlight-icon {
  color: var(--brand-main);
  font-weight: bold;
}

.highlight-text {
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.detail-image {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.image-placeholder {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl);
}

.placeholder-icon {
  font-size: 4rem;
  margin-bottom: var(--spacing-md);
}

/* How to Use Section */
.how-to-use {
  padding: var(--spacing-2xl) 0;
  background: var(--bg-primary);
}

.steps-container {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.step {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
}

.step-number {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xl);
  font-weight: 700;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
}

.step-title {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--brand-main);
  margin-bottom: var(--spacing-sm);
}

.step-description {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--spacing-md);
}

.step-btn {
  display: inline-block;
  background: var(--brand-main);
  color: var(--text-inverse);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-md);
  text-decoration: none;
  font-size: var(--font-size-sm);
  font-weight: 600;
  transition: all var(--transition-normal);
}

.step-btn:hover {
  background: var(--brand-sub);
  transform: translateY(-2px);
}

/* Tech Stack Section */
.tech-stack {
  padding: var(--spacing-2xl) 0;
  background: var(--bg-secondary);
}

.tech-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--spacing-xl);
}

.tech-category {
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
}

.category-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--brand-main);
  margin-bottom: var(--spacing-lg);
  text-align: center;
}

.tech-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  justify-content: center;
}

.tech-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  min-width: 80px;
}

.tech-icon {
  font-size: var(--font-size-xl);
}

.tech-name {
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  font-weight: 500;
  text-align: center;
}

/* CTA Section */
.cta-section {
  padding: var(--spacing-2xl) 0;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
}

.cta-content {
  text-align: center;
}

.cta-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  margin-bottom: var(--spacing-md);
}

.cta-subtitle {
  font-size: var(--font-size-lg);
  margin-bottom: var(--spacing-xl);
  opacity: 0.9;
}

.cta-buttons {
  display: flex;
  gap: var(--spacing-md);
  justify-content: center;
}

.cta-btn {
  padding: var(--spacing-md) var(--spacing-xl);
  border-radius: var(--radius-md);
  font-size: var(--font-size-base);
  font-weight: 600;
  text-decoration: none;
  transition: all var(--transition-normal);
}

.cta-btn.primary {
  background: var(--text-inverse);
  color: var(--brand-main);
}

.cta-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.cta-btn.secondary {
  background: transparent;
  color: var(--text-inverse);
  border: 2px solid var(--text-inverse);
}

.cta-btn.secondary:hover {
  background: var(--text-inverse);
  color: var(--brand-main);
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
    gap: var(--spacing-xl);
  }
  
  .hero-title {
    font-size: var(--font-size-3xl);
  }
  
  .hero-subtitle {
    font-size: var(--font-size-lg);
  }
  
  .floating-elements {
    width: 300px;
    height: 300px;
  }
  
  .element {
    font-size: 3rem;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
  }
  
  .feature-detail {
    flex-direction: column;
    text-align: center;
  }
  
  .feature-detail:nth-child(even) {
    flex-direction: column;
  }
  
  .step {
    flex-direction: column;
    text-align: center;
  }
  
  .tech-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .cta-buttons {
    flex-direction: column;
    align-items: center;
  }
  
  .cta-btn {
    width: 100%;
    max-width: 300px;
  }
}
</style> 