<template>
  <div class="class-info">
    <div class="info-header">
      <h3 class="class-title">{{ classData.title }}</h3>
      <div class="class-status" :class="classData.status">
        {{ getStatusText(classData.status) }}
      </div>
    </div>
    
    <div class="info-content">
      <div class="info-section">
        <h4 class="section-title">출석률</h4>
        <div class="attendance-info">
          <div class="attendance-circle">
            <svg width="60" height="60" viewBox="0 0 60 60">
              <circle cx="30" cy="30" r="25" fill="none" stroke="#e5e7eb" stroke-width="4"/>
              <circle 
                cx="30" cy="30" r="25" 
                fill="none" 
                stroke="#10b981" 
                stroke-width="4"
                stroke-dasharray="157"
                :stroke-dashoffset="157 - (157 * attendanceRate) / 100"
                transform="rotate(-90 30 30)"
              />
            </svg>
            <span class="attendance-text">{{ attendanceRate }}%</span>
          </div>
          <div class="attendance-details">
            <div class="detail-item">
              <span class="label">출석</span>
              <span class="value">{{ attendanceCount }}회</span>
            </div>
            <div class="detail-item">
              <span class="label">총 수업</span>
              <span class="value">{{ totalClasses }}회</span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="info-section">
        <h4 class="section-title">과제 제출률</h4>
        <div class="assignment-info">
          <div class="assignment-circle">
            <svg width="60" height="60" viewBox="0 0 60 60">
              <circle cx="30" cy="30" r="25" fill="none" stroke="#e5e7eb" stroke-width="4"/>
              <circle 
                cx="30" cy="30" r="25" 
                fill="none" 
                stroke="#3b82f6" 
                stroke-width="4"
                stroke-dasharray="157"
                :stroke-dashoffset="157 - (157 * assignmentRate) / 100"
                transform="rotate(-90 30 30)"
              />
            </svg>
            <span class="assignment-text">{{ assignmentRate }}%</span>
          </div>
          <div class="assignment-details">
            <div class="detail-item">
              <span class="label">제출</span>
              <span class="value">{{ submittedAssignments }}개</span>
            </div>
            <div class="detail-item">
              <span class="label">총 과제</span>
              <span class="value">{{ totalAssignments }}개</span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="info-section">
        <h4 class="section-title">학습 진행률</h4>
        <div class="progress-info">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: learningProgress + '%' }"></div>
          </div>
          <span class="progress-text">{{ learningProgress }}% 완료</span>
        </div>
      </div>
      
      <div class="info-section">
        <h4 class="section-title">최근 활동</h4>
        <div class="recent-activities">
          <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
            <div class="activity-icon" :class="activity.type">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path v-if="activity.type === 'attendance'" d="M12 2L2 7L12 12L22 7L12 2Z"/>
                <path v-else-if="activity.type === 'assignment'" d="M14 2H6A2 2 0 0 0 4 4V20A2 2 0 0 0 6 22H18A2 2 0 0 0 20 20V8L14 2Z"/>
                <path v-else-if="activity.type === 'comment'" d="M21 15A2 2 0 0 1 19 17H7L3 21V5A2 2 0 0 1 5 3H19A2 2 0 0 1 21 5Z"/>
              </svg>
            </div>
            <div class="activity-content">
              <span class="activity-text">{{ activity.text }}</span>
              <span class="activity-time">{{ formatTime(activity.time) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="info-actions">
      <button class="action-btn primary" @click="$emit('enter-class', classData.id)">
        수업 참여
      </button>
      <button class="action-btn secondary" @click="openInviteModal">
        초대 하기
      </button>
    </div>
  </div>
  
  <!-- 초대하기 모달 -->
  <InviteModal 
    :open="inviteModalOpen"
    :class-id="classData.id"
    @close="closeInviteModal"
    @invite="handleInvite"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import InviteModal from './InviteModal.vue'

const props = defineProps({
  classData: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['enter-class', 'view-details', 'invite'])

const inviteModalOpen = ref(false)

const openInviteModal = () => {
  inviteModalOpen.value = true
}

const closeInviteModal = () => {
  inviteModalOpen.value = false
}

const handleInvite = (inviteData) => {
  console.log('초대 데이터:', inviteData)
  
  // 백엔드로 전송할 데이터 형식 확인
  // inviteData = { users: ['user1@example.com', 'user2@example.com'], classId: "class_123" }
  
  // 여기에 실제 초대 API 호출 로직을 추가할 수 있습니다
  // 예시: 백엔드 API 호출
  /*
  fetch('/api/classes/invite', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${authToken}`
    },
    body: JSON.stringify(inviteData)
  })
  */
  
  emit('invite', inviteData)
}

// 출석률 계산 (임시 데이터)
const attendanceRate = computed(() => {
  return props.classData.attendanceRate || Math.floor(Math.random() * 30) + 70
})

const attendanceCount = computed(() => {
  return props.classData.attendanceCount || Math.floor(Math.random() * 10) + 15
})

const totalClasses = computed(() => {
  return props.classData.totalClasses || 20
})

// 과제 제출률 계산 (임시 데이터)
const assignmentRate = computed(() => {
  return props.classData.assignmentRate || Math.floor(Math.random() * 40) + 60
})

const submittedAssignments = computed(() => {
  return props.classData.submittedAssignments || Math.floor(Math.random() * 8) + 12
})

const totalAssignments = computed(() => {
  return props.classData.totalAssignments || 20
})

// 학습 진행률 (임시 데이터)
const learningProgress = computed(() => {
  return props.classData.learningProgress || Math.floor(Math.random() * 30) + 70
})

// 최근 활동 (임시 데이터)
const recentActivities = computed(() => {
  return props.classData.recentActivities || [
    { id: 1, type: 'attendance', text: '오늘 수업에 참석했습니다', time: new Date(Date.now() - 2 * 60 * 60 * 1000) },
    { id: 2, type: 'assignment', text: '과제를 제출했습니다', time: new Date(Date.now() - 24 * 60 * 60 * 1000) },
    { id: 3, type: 'comment', text: '질문을 남겼습니다', time: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000) }
  ]
})

function getStatusText(status) {
  const statusMap = {
    'active': '진행중',
    'completed': '완료',
    'upcoming': '예정'
  }
  return statusMap[status] || '진행중'
}

function formatTime(time) {
  const now = new Date()
  const diff = now - time
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(hours / 24)
  
  if (days > 0) {
    return `${days}일 전`
  } else if (hours > 0) {
    return `${hours}시간 전`
  } else {
    return '방금 전'
  }
}
</script>

<style scoped>
.class-info {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}

.class-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.class-status {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 500;
}

.class-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.class-status.completed {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.class-status.upcoming {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.info-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.section-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin: 0;
}

.attendance-info,
.assignment-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.attendance-circle,
.assignment-circle {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attendance-text,
.assignment-text {
  position: absolute;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.attendance-details,
.assignment-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-item .label {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.detail-item .value {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-info {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--brand-main), var(--brand-accent));
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.8rem;
  color: var(--text-secondary);
  text-align: center;
}

.recent-activities {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem;
  border-radius: 8px;
  background: var(--bg-secondary);
}

.activity-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.activity-icon.attendance {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.activity-icon.assignment {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.activity-icon.comment {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.activity-text {
  font-size: 0.8rem;
  color: var(--text-primary);
  font-weight: 500;
}

.activity-time {
  font-size: 0.7rem;
  color: var(--text-tertiary);
}

.info-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}

.action-btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;
}

.action-btn.primary {
  background: var(--brand-main);
  color: white;
}

.action-btn.primary:hover {
  background: var(--brand-accent);
  transform: translateY(-1px);
}

.action-btn.secondary {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.action-btn.secondary:hover {
  background: var(--bg-card);
  border-color: var(--border-dark);
}

/* 다크모드 대응 */
:global(.dark-mode) .class-info {
  background: var(--bg-primary);
  border-color: var(--border-color);
}

:global(.dark-mode) .activity-item {
  background: var(--bg-secondary);
}
</style>