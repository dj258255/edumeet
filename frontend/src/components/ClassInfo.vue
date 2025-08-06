<template>
  <div class="class-info">
    <div class="info-header">
      <h3 class="class-title">{{ classData.title }}</h3>
      <div class="class-status" :class="classData.status">
        {{ getStatusText(classData.status) }}
      </div>
    </div>

    <div class="info-content">
      <!-- 과제 제출률 -->
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

      <!-- 공지사항 게시판 -->
      <div class="info-section">
        <h4 class="section-title">게시판</h4>
        
        <!-- 탭 버튼 -->
        <div class="tab-buttons">
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'notice' }"
            @click="activeTab = 'notice'"
          >
            📢 공지사항
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'assignment' }"
            @click="activeTab = 'assignment'"
          >
            📝 과제
          </button>
        </div>

        <!-- 공지사항 탭 -->
        <div v-if="activeTab === 'notice'" class="tab-content">
          <!-- 공지사항 필터 -->
          <div class="filter-section">
            <select v-model="noticeFilter" class="filter-select">
              <option value="all">전체</option>
              <option value="required">필수</option>
              <option value="general">일반</option>
            </select>
          </div>
          
          <div class="notice-board">
            <div
              v-for="notice in filteredNotices"
              :key="notice.id"
              class="notice-item"
              :class="{ required: notice.required }"
            >
              <span class="badge">{{ notice.required ? '필수' : '일반' }}</span>
              <span class="text">{{ notice.title }}</span>
            </div>
          </div>
        </div>

        <!-- 과제 탭 -->
        <div v-if="activeTab === 'assignment'" class="tab-content">
          <!-- 과제 필터 -->
          <div class="filter-section">
            <select v-model="assignmentFilter" class="filter-select">
              <option value="all">전체</option>
              <option value="incomplete">미완료</option>
              <option value="complete">완료</option>
            </select>
          </div>
          
          <div class="assignment-board">
            <div
              v-for="task in filteredAssignments"
              :key="task.id"
              class="task-item"
              :class="{ done: task.done }"
            >
              <span class="status">{{ task.done ? '완료' : '미완료' }}</span>
              <span class="text">{{ task.title }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 하단 버튼 -->
    <div class="info-actions">
      <button class="action-btn primary" @click="$emit('enter-class', classData.id)">
        수업 참여
      </button>
      <button class="action-btn secondary" @click="openInviteModal">
        초대 하기
      </button>
    </div>

    <!-- 초대 모달 -->
    <InviteModal 
      :open="inviteModalOpen"
      :class-id="String(classData.id || classData.classId || '')"
      @close="closeInviteModal"
      @invite="handleInvite"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import InviteModal from './InviteModal.vue'

const props = defineProps({
  classData: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['enter-class', 'invite'])

const inviteModalOpen = ref(false)

const openInviteModal = () => {
  inviteModalOpen.value = true
}

const closeInviteModal = () => {
  inviteModalOpen.value = false
}

const handleInvite = (data) => {
  alert('초대가 성공적으로 전송되었습니다!')
  emit('invite', data)
}

// 과제 제출률
const assignmentRate = computed(() => {
  return props.classData.assignmentRate || 78
})
const submittedAssignments = computed(() => {
  return props.classData.submittedAssignments || 14
})
const totalAssignments = computed(() => {
  return props.classData.totalAssignments || 18
})

// 공지사항
const notices = ref([
  { id: 1, title: '중간고사 일정 안내', required: true },
  { id: 2, title: 'Zoom 접속 링크 변경', required: false }
])

// 과제 게시판
const assignments = ref([
  { id: 1, title: '1주차 과제', done: true },
  { id: 2, title: '2주차 과제', done: false },
  { id: 3, title: '3주차 과제', done: true }
])

const activeTab = ref('notice')
const noticeFilter = ref('all')
const assignmentFilter = ref('all')

// 필터링된 공지사항
const filteredNotices = computed(() => {
  if (noticeFilter.value === 'all') {
    return notices.value
  } else if (noticeFilter.value === 'required') {
    return notices.value.filter(notice => notice.required)
  } else {
    return notices.value.filter(notice => !notice.required)
  }
})

// 필터링된 과제
const filteredAssignments = computed(() => {
  if (assignmentFilter.value === 'all') {
    return assignments.value
  } else if (assignmentFilter.value === 'complete') {
    return assignments.value.filter(task => task.done)
  } else {
    return assignments.value.filter(task => !task.done)
  }
})

const getStatusText = (status) => {
  const map = {
    active: '진행중',
    completed: '완료',
    upcoming: '예정'
  }
  return map[status] || '진행중'
}
</script>

<style scoped>
.class-info {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 1.5rem;
  border: 1px solid var(--border-color);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.class-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
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
  margin-bottom: 1rem;
}

/* 과제 제출률 스타일 */
.assignment-info {
  display: flex;
  gap: 1rem;
  align-items: center;
}
.assignment-circle {
  position: relative;
  width: 60px;
  height: 60px;
}
.assignment-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 0.9rem;
  font-weight: 600;
}
.assignment-details {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
}
.detail-item .label {
  color: var(--text-secondary);
}
.detail-item .value {
  font-weight: 600;
}

/* 공지사항 */
.notice-board {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.notice-item {
  padding: 0.5rem;
  border-radius: 6px;
  background: var(--bg-secondary);
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.notice-item.required {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  margin-right: 0.5rem;
  background: var(--bg-tertiary);
  color: var(--text-color);
}

/* 과제 게시판 */
.assignment-board {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.task-item {
  padding: 0.5rem;
  border-radius: 6px;
  background: var(--bg-secondary);
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.task-item .status {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}
.task-item.done .status {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.task-item:not(.done) .status {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

/* 탭 스타일 */
.tab-buttons {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.5rem;
}
.tab-btn {
  padding: 0.5rem 1rem;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  color: var(--text-secondary);
  background: none;
  transition: 0.3s ease;
}
.tab-btn.active {
  color: var(--brand-main);
  border-bottom-color: var(--brand-main);
}
.tab-btn:hover {
  color: var(--brand-main);
}
.tab-content {
  /* No specific styles needed here, content will be hidden/shown */
}

/* 필터 스타일 */
.filter-section {
  margin-bottom: 1rem;
}

.filter-select {
  padding: 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: 0.375rem;
  background: var(--bg-color);
  color: var(--text-color);
  font-size: 0.875rem;
  cursor: pointer;
  transition: border-color 0.2s;
}

.filter-select:focus {
  outline: none;
  border-color: var(--brand-main);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.filter-select:hover {
  border-color: var(--border-dark);
}

/* 버튼 */
.info-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: auto;
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
  border: none;
  transition: 0.3s ease;
}
.action-btn.primary {
  background: var(--brand-main);
  color: white;
}
.action-btn.primary:hover {
  background: var(--brand-accent);
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
</style>
