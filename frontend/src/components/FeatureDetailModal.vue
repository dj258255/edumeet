<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <div class="modal-badge">
          <span class="modal-icon">{{ getFeatureIcon() }}</span>
          {{ getFeatureTitle() }}
        </div>
        <button class="close-btn" @click="closeModal">×</button>
      </div>
      
      <div class="modal-body">
        <h2 class="modal-title">{{ getFeatureSubtitle() }}</h2>
        <p class="modal-description">{{ getFeatureDescription() }}</p>
        
        <div class="feature-details">
          <div class="detail-item" v-for="(detail, index) in getFeatureDetails()" :key="index">
            <div class="detail-icon">{{ detail.icon }}</div>
            <div class="detail-content">
              <h4 class="detail-title">{{ detail.title }}</h4>
              <p class="detail-text">{{ detail.text }}</p>
            </div>
          </div>
        </div>
      </div>
      
             <div class="modal-footer">
       </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  isVisible: Boolean,
  currentFeature: Number
})

const emit = defineEmits(['close'])
const router = useRouter()

const closeModal = () => {
  emit('close')
}

const navigateToCreateClass = () => {
  emit('close')
  router.push('/create-class')
}

const getFeatureIcon = () => {
  const icons = ['🎓', '🤖', '📝', '🎯', '💬']
  return icons[props.currentFeature] || '🎓'
}

const getFeatureTitle = () => {
  const titles = ['온라인 클래스', 'AI 수업 요약', '실시간 자막', '올인원 플랫폼', '실시간 게시판']
  return titles[props.currentFeature] || '온라인 클래스'
}

const getFeatureSubtitle = () => {
  const subtitles = [
    '언제 어디서나 편리한 수업',
    'AI가 자동으로 요약해드려요',
    '언어 장벽 없는 교육',
    '모든 교육 도구를 한 곳에서',
    '즉시 소통하는 학습 공간'
  ]
  return subtitles[props.currentFeature] || '언제 어디서나 편리한 수업'
}

const getFeatureDescription = () => {
  const descriptions = [
    '고품질 화상회의 시스템으로 온라인 수업을 경험해보세요. 여러 명이 동시에 참여할 수 있으며 안정적인 연결을 제공합니다.',
    '수업 내용을 AI가 자동으로 요약하여 학습 효율성을 높여줍니다. 수업 후 자동 업로드 되며 모두가 확인 할 수 있습니다.',
    '청각장애 학습자를 위한 자막 경로를 제공합니다. 수업 중 표시되는 자막과 수업 후 요약 경로를 분리해 운영합니다.',
    '교육에 필요한 모든 기능을 하나의 플랫폼에서 제공합니다. 화상회의, 자료 공유, 과제 관리까지 통합된 교육 환경을 경험하세요.',
    '실시간으로 소통할 수 있는 게시판으로 수업 관련 공지사항과 질문을 빠르게 확인하고 답변받을 수 있습니다.'
  ]
  return descriptions[props.currentFeature] || descriptions[0]
}

const getFeatureDetails = () => {
  const details = [
    [
      { icon: '🎥', title: '고품질 화상회의', text: '안정적인 연결과 선명한 화질로 원활한 온라인 수업을 제공합니다.' },
      { icon: '👥', title: '다중 참여자 지원', text: '여러 명이 동시에 참여할 수 있어 대규모 수업도 문제없습니다.' },
      { icon: '📱', title: '모바일 지원', text: 'PC, 태블릿, 스마트폰 어디서나 접속 가능합니다.' }
    ],
    [
      { icon: '🧠', title: 'AI 자동 요약', text: '수업 내용을 AI가 자동으로 분석하여 핵심 내용을 요약해드립니다.' },
      { icon: '📊', title: '학습 효율성 증대', text: '요약된 내용으로 복습 시간을 단축하고 학습 효과를 높입니다.' },
      { icon: '📝', title: '자동 업로드', text: '수업 후 자동으로 요약본이 업로드되어 언제든 확인할 수 있습니다.' }
    ],
    [
      { icon: '🌍', title: '한국어 중심 자막', text: '한국어 수업을 기준으로 하고, 영어 기술 용어는 사전 기반으로 보정합니다.' },
      { icon: '⚡', title: '자막 전달 경로', text: '자막은 채팅과 다른 경로로 전달해 필요한 사용자가 따로 켜고 끌 수 있습니다.' },
      { icon: '♿', title: '접근성 향상', text: '청각 장애인을 포함한 모든 학습자가 공평한 교육을 받을 수 있습니다.' }
    ],
    [
      { icon: '🔧', title: '통합 플랫폼', text: '화상회의, 자료 공유, 과제 관리 등 모든 기능을 한 곳에서 제공합니다.' },
      { icon: '📚', title: '자료 관리', text: '수업 자료를 체계적으로 관리하고 쉽게 공유할 수 있습니다.' },
      { icon: '📋', title: '과제 관리', text: '과제 제출, 채점, 피드백까지 모든 과정을 효율적으로 관리합니다.' }
    ],
    [
      { icon: '💬', title: '실시간 소통', text: '수업 관련 공지사항과 질문을 실시간으로 주고받을 수 있습니다.' },
      { icon: '📢', title: '공지사항', text: '중요한 공지사항을 빠르게 전달하고 확인할 수 있습니다.' },
      { icon: '❓', title: '질문과 답변', text: '학습 중 궁금한 점을 즉시 질문하고 답변받을 수 있습니다.' }
    ]
  ]
  return details[props.currentFeature] || details[0]
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(8px);
}

.modal-content {
  background: var(--bg-primary);
  border-radius: 20px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--border-color);
  animation: modalSlideIn 0.3s ease-out;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-50px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 16px 24px;
  border-bottom: 1px solid var(--border-color);
}

.modal-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  padding: 8px 16px;
  border-radius: 20px;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.modal-icon {
  font-size: var(--font-size-lg);
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-body {
  padding: 24px;
}

.modal-title {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  color: var(--brand-main);
  margin: 0 0 16px 0;
  line-height: 1.3;
}

.modal-description {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 32px 0;
}

.feature-details {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  transition: all 0.2s ease;
}

.detail-item:hover {
  background: var(--bg-secondary);
  border-color: var(--brand-main);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(34, 122, 83, 0.1);
}

.detail-icon {
  font-size: 24px;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-main);
  color: var(--text-inverse);
  border-radius: 10px;
}

.detail-content {
  flex: 1;
}

.detail-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.detail-text {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
}

.modal-footer {
  padding: 16px 24px 24px 24px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: center;
}

.try-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  border: none;
  padding: 12px 24px;
  border-radius: 25px;
  font-size: var(--font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(34, 122, 83, 0.3);
}

.try-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(34, 122, 83, 0.4);
}

.arrow {
  transition: transform 0.2s ease;
}

.try-btn:hover .arrow {
  transform: translateX(4px);
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .modal-content {
    width: 95%;
    max-height: 90vh;
  }
  
  .modal-header {
    padding: 20px 20px 12px 20px;
  }
  
  .modal-body {
    padding: 20px;
  }
  
  .modal-footer {
    padding: 12px 20px 20px 20px;
  }
  
  .modal-title {
    font-size: var(--font-size-xl);
  }
  
  .detail-item {
    padding: 12px;
  }
  
  .detail-icon {
    width: 36px;
    height: 36px;
    font-size: 20px;
  }
}
</style>
