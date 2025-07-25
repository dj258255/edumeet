<template>
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
            <span class="nav-text">AI 수업 요약</span>
          </button>
          <button type="button" @click="slideTransition(2)" :class="{ active: currentSlide === 2 }" class="nav-btn">
            <span class="nav-icon">📝</span>
            <span class="nav-text">실시간 자막</span>
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
</template>

<script setup>
import { ref } from 'vue'
import '../styles/HomeView.css'

const currentSlide = ref(0)

const slideTransition = (slideIndex) => {
  currentSlide.value = slideIndex
}

const getCurrentImage = () => {
  const images = [
    '/src/assets/mainim.png',
    '/src/assets/mainim1.png',
    '/src/assets/mainim2.png'
  ]
  return images[currentSlide.value]
}

const getCurrentHeading = () => {
  const headings = [
    '온라인 클래스',
    'AI 수업 요약',
    '실시간 자막'
  ]
  return headings[currentSlide.value]
}

const getCurrentSubheading = () => {
  const subheadings = [
    '언제 어디서나 편리한 수업',
    'AI가 자동으로 요약해드려요',
    '언어 장벽 없는 교육'
  ]
  return subheadings[currentSlide.value]
}

const getCurrentBody = () => {
  const bodies = [
    '고품질 화상회의 시스템으로 온라인 수업을 경험해보세요. 여러 명이 동시에 참여할 수 있으며 안정적인 연결을 제공합니다.',
    '수업 내용을 AI가 자동으로 요약하여 학습 효율성을 높여줍니다. 수업 후 자동 업로드 되며 모두가 확인 할 수 있습니다.',
    '언어 장벽 없는 교육 환경을 위한 실시간 자막 서비스를 제공합니다. 누구든 공평한 교육 환경을 제공 받습니다.'
  ]
  return bodies[currentSlide.value]
}
</script> 