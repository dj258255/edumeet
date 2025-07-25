<template>
  <div class="about-view">
    <!-- Hero Section -->
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
            <router-link to="/create-room" class="btn btn-primary">
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

    <!-- Features Section -->
    <section class="features">
      <div class="container">
        <div class="section-header">
          <h2>핵심 기능</h2>
          <p>EduMeet만의 특별한 기능들을 만나보세요</p>
        </div>
        
        <div class="features-grid">
          <div class="feature-card" v-for="feature in features" :key="feature.id">
            <div class="feature-icon">{{ feature.icon }}</div>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.description }}</p>
            <div class="feature-metrics">
              <div class="metric" v-for="metric in feature.metrics" :key="metric.label">
                <span class="metric-value">{{ metric.value }}</span>
                <span class="metric-label">{{ metric.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- How It Works Section -->
    <section class="how-it-works">
      <div class="container">
        <div class="section-header">
          <h2>사용 방법</h2>
          <p>간단한 3단계로 시작하세요</p>
        </div>
        
        <div class="steps">
          <div class="step" v-for="(step, index) in steps" :key="step.id">
            <div class="step-number">{{ index + 1 }}</div>
            <div class="step-content">
              <h3>{{ step.title }}</h3>
              <p>{{ step.description }}</p>
              <router-link :to="step.link" class="step-button">
                {{ step.buttonText }}
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Technology Section -->
    <section class="technology">
      <div class="container">
        <div class="section-header">
          <h2>기술 스택</h2>
          <p>최신 기술로 구축된 안정적인 플랫폼</p>
        </div>
        
        <div class="tech-grid">
          <div class="tech-category" v-for="category in techStack" :key="category.name">
            <h3>{{ category.name }}</h3>
            <div class="tech-items">
              <div class="tech-item" v-for="tech in category.items" :key="tech.name">
                <span class="tech-icon">{{ tech.icon }}</span>
                <span class="tech-name">{{ tech.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta">
      <div class="container">
        <h2>지금 시작해보세요</h2>
        <p>EduMeet와 함께 새로운 교육 경험을 만들어보세요</p>
        <div class="cta-buttons">
          <router-link to="/" class="btn btn-primary">홈으로 돌아가기</router-link>
          <router-link to="/create-room" class="btn btn-secondary">방 만들기</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import '../styles/AboutView.css'

const features = ref([
  {
    id: 1,
    icon: '🎥',
    title: '고품질 화상회의',
    description: '4K 해상도와 고음질을 지원하는 최고 수준의 화상회의 시스템',
    metrics: [
      { value: '4K', label: '해상도' },
      { value: '100명', label: '동시 참여' },
      { value: '99.9%', label: '안정성' }
    ]
  },
  {
    id: 2,
    icon: '🤖',
    title: 'AI 수업 요약',
    description: '수업 내용을 자동으로 요약하여 학습 효율성 극대화',
    metrics: [
      { value: '95%', label: '정확도' },
      { value: '실시간', label: '처리' },
      { value: '10개', label: '언어 지원' }
    ]
  },
  {
    id: 3,
    icon: '📝',
    title: '실시간 자막',
    description: '언어 장벽 없는 교육 환경을 위한 실시간 자막 서비스',
    metrics: [
      { value: '20개', label: '언어 지원' },
      { value: '0.5초', label: '지연시간' },
      { value: '98%', label: '인식률' }
    ]
  }
])

const steps = ref([
  {
    id: 1,
    title: '계정 생성',
    description: '간단한 이메일 인증으로 계정을 만드세요',
    buttonText: '회원가입',
    link: '/signup'
  },
  {
    id: 2,
    title: '방 생성',
    description: '새로운 수업 방을 만들고 설정을 구성하세요',
    buttonText: '방 만들기',
    link: '/create-room'
  },
  {
    id: 3,
    title: '수업 시작',
    description: '모든 준비가 완료되면 수업을 시작하세요',
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