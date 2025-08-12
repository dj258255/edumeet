<template>
  <section class="main-section">
    <div class="layout-container">
      <!-- 왼쪽 네비게이션 패널 -->
      <div class="left-panel">
        <div class="nav-buttons">
          <button type="button" @click="slideTransition(0)" :class="{ active: currentSlide === 0 }" class="nav-btn">
            <span class="nav-icon">🎓</span>
            <span class="nav-text">온라인 클래스</span>
          </button>
          <button type="button" @click="slideTransition(1)" :class="{ active: currentSlide === 1 }" class="nav-btn">
            <span class="nav-icon">🤖</span>
            <span class="nav-text">AI 수업 요약</span>
          </button>
          <button type="button" @click="slideTransition(2)" :class="{ active: currentSlide === 2 }" class="nav-btn">
            <span class="nav-icon">📝</span>
            <span class="nav-text">실시간 자막</span>
          </button>
          <button type="button" @click="slideTransition(3)" :class="{ active: currentSlide === 3 }" class="nav-btn">
            <span class="nav-icon">🎯</span>
            <span class="nav-text">올인원 플랫폼</span>
          </button>
          <button type="button" @click="slideTransition(4)" :class="{ active: currentSlide === 4 }" class="nav-btn">
            <span class="nav-icon">💬</span>
            <span class="nav-text">실시간 게시판</span>
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
                  <button class="learn-more-btn" @click="openModal">
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
    
    <!-- 기능 상세 모달 -->
    <FeatureDetailModal 
      :isVisible="isModalVisible" 
      :currentFeature="currentSlide"
      @close="closeModal"
    />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import '../styles/HomeView.css'
import FeatureDetailModal from './FeatureDetailModal.vue'

const assetPath = import.meta.env.VITE_ASSET_PATH;

const currentSlide = ref(0)
const isModalVisible = ref(false)

const slideTransition = (slideIndex) => {
  currentSlide.value = slideIndex
}

const openModal = () => {
  isModalVisible.value = true
}

const closeModal = () => {
  isModalVisible.value = false
}

const getCurrentImage = () => {

      const images = [
      `${assetPath}/mainim.png`,
      `${assetPath}/mainim1.png`,
      `${assetPath}/mainim2.png`,
      `${assetPath}/mainim3.png`,
      `${assetPath}/mainim4.png`
    ];
  return images[currentSlide.value]
}

const getCurrentHeading = () => {
  const headings = [
    '온라인 클래스',
    'AI 수업 요약',
    '실시간 자막',
    '올인원 플랫폼',
    '실시간 게시판'
  ]
  return headings[currentSlide.value]
}

const getCurrentSubheading = () => {
  const subheadings = [
    '언제 어디서나 편리한 수업',
    'AI가 자동으로 요약해드려요',
    '언어 장벽 없는 교육',
    '모든 교육 도구를 한 곳에서',
    '즉시 소통하는 학습 공간'
  ]
  return subheadings[currentSlide.value]
}

const getCurrentBody = () => {
  const bodies = [
    '고품질 화상회의 시스템으로 온라인 수업을 경험해보세요. 여러 명이 동시에 참여할 수 있으며 안정적인 연결을 제공합니다.',
    '수업 내용을 AI가 자동으로 요약하여 학습 효율성을 높여줍니다. 수업 후 자동 업로드 되며 모두가 확인 할 수 있습니다.',
    '언어 장벽 없는 교육 환경을 위한 실시간 자막 서비스를 제공합니다. 누구든 공평한 교육 환경을 제공 받습니다.',
    '교육에 필요한 모든 기능을 하나의 플랫폼에서 제공합니다. 화상회의, 자료 공유, 과제 관리까지 통합된 교육 환경을 경험하세요.',
    '실시간으로 소통할 수 있는 게시판으로 수업 관련 공지사항과 질문을 빠르게 확인하고 답변받을 수 있습니다.'
  ]
  return bodies[currentSlide.value]
}
</script> 