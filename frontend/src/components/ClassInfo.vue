<template>
  <div class="class-info">
    <div class="info-header">
      <h3 class="class-title">{{ classData.title }}</h3>
      <div class="class-status" :class="classData.status">
        {{ getStatusText(classData.status) }}
      </div>
    </div>

    <div class="info-content">
      <button @click="classww">정보보기</button>
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
        <h4 class="section-title">게시판</h4>

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

        <div v-if="activeTab === 'notice'" class="tab-content">
          <div class="filter-section">
            <select v-model="noticeFilter" class="filter-select">
              <option value="all">전체</option>
              <option value="required">필수</option>
              <option value="general">일반</option>
            </select>
          </div>
          
          <div v-if="isMyCreatedClass" class="action-bar">
            <button class="add-btn" @click="openNoticeRegisterModal">📢 공지 등록</button>
          </div>

          <div class="notice-board">
            <div
              v-for="notice in filteredNotices"
              :key="notice.id"
              class="notice-item"
              :class="{ required: notice.required }"
            >
              <div @click="openNoticeDetailModal(notice)" class="notice-item-content">
                <span class="badge">{{ notice.required ? '필수' : '일반' }}</span>
                <span class="text">{{ notice.title }}</span>
              </div>
              <button
                v-if="isMyCreatedClass"
                @click.stop="deleteNotice(notice.id)"
                class="delete-btn small-btn"
              >
                삭제
              </button>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'assignment'" class="tab-content">
          <div class="filter-section">
            <select v-model="assignmentFilter" class="filter-select">
              <option value="all">전체</option>
              <option value="incomplete">미완료</option>
              <option value="complete">완료</option>
            </select>
          </div>
          
          <div v-if="isMyCreatedClass" class="action-bar">
            <button class="add-btn" @click="openAssignmentRegisterModal">📝 과제 등록</button>
          </div>

          <div class="assignment-board">
            <div
              v-for="task in filteredAssignments"
              :key="task.id"
              class="task-item"
              :class="{ done: task.done }"
            >
              <div @click="openAssignmentDetailModal(task)" class="task-info-left">
                <span class="status">{{ task.done ? '완료' : '미완료' }}</span>
                <span class="text">{{ task.title }}</span>
              </div>
              <button
                v-if="isMyCreatedClass"
                @click.stop="deleteAssignment(task.id)"
                class="delete-btn small-btn"
              >
                삭제
              </button>
              <button
                v-else-if="!task.done"
                @click.stop="submitAssignment(task.id)"
                class="submit-btn small-btn"
              >
                제출
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="info-actions">
      <button class="action-btn primary" @click="$emit('enter-class', classData.id)">
        수업 참여
      </button>
      <button v-if="isMyCreatedClass" class="action-btn secondary" @click="openInviteModal">
        초대 하기
      </button>
    </div>

    <InviteModal
      :open="inviteModalOpen"
      :class-id="String(classData.id || classData.classId || '')"
      @close="closeInviteModal"
      @invite="handleInvite"
    />
    <NoticeDetailModal
      :isVisible="showNoticeModal"
      :noticeData="selectedNotice"
      :isMyCreatedClass="isMyCreatedClass"
      @close="closeNoticeModal"
      @delete="deleteNotice"
    />
    <AssignmentDetailModal
      :isVisible="showAssignmentModal"
      :assignmentData="selectedAssignment"
      :isMyCreatedClass="isMyCreatedClass"
      @close="closeAssignmentModal"
      @submit="submitAssignment"
    />
    <NoticeRegisterModal
      :isVisible="showNoticeRegisterModal"
      @close="closeNoticeRegisterModal"
      @register="registerNotice"
    />
    <AssignmentRegisterModal
      :isVisible="showAssignmentRegisterModal"
      @close="closeAssignmentRegisterModal"
      @register="registerAssignment"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import InviteModal from './InviteModal.vue'
import NoticeDetailModal from './NoticeDetailModal.vue'
import AssignmentDetailModal from './AssignmentDetailModal.vue'
import NoticeRegisterModal from './NoticeRegisterModal.vue'
import AssignmentRegisterModal from './AssignmentRegisterModal.vue'
import { useAuthStore } from '@/stores/auth.js'
import apiClient from '@/stores/auth.js';
import { watch } from 'vue' // watch를 import
const authStore = useAuthStore()

const props = defineProps({
  classData: Object,
  isMyCreatedClass: {
    type: Boolean,
    default: false
  }
});

// props.classData가 변경될 때마다 로그를 출력
watch(() => props.classData, (newVal) => {
  console.log("Class data updated:", newVal);
}, { deep: true });

const classww = () => {
  console.log(props.classData)
}

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

const assignmentRate = computed(() => {
  // classData가 없으면 기본값 0 반환
  if (!props.classData || props.classData.totalAssignments === 0) {
    return 0
  }
  return Math.round((props.classData.submittedAssignments / props.classData.totalAssignments) * 100)
})
const submittedAssignments = computed(() => {
  return props.classData?.submittedAssignments || 0
})
const totalAssignments = computed(() => {
  return props.classData?.totalAssignments || 0
})

const notices = ref([
  { id: 1, title: '중간고사 일정 안내', required: true, content: '안녕하세요, 중간고사 일정을 안내드립니다. 시험 범위는 1단원부터 5단원까지이며, 자세한 내용은 첨부파일을 확인해주세요.', date: '2025.08.01' },
  { id: 2, title: 'Zoom 접속 링크 변경', required: false, content: '다음 수업부터 사용될 Zoom 접속 링크가 변경되었습니다. 기존 링크는 사용 불가하니, 변경된 링크를 통해 접속해 주시기 바랍니다.', date: '2025.07.28' }
])

const assignments = ref([
  { id: 1, title: '1주차 과제', description: '1주차 수업 내용을 바탕으로 주어진 문제를 해결하세요. 마감일은 다음주 금요일입니다.', done: true, dueDate: '2025.08.08' },
  { id: 2, title: '2주차 과제', description: '2주차 과제는 실습 위주의 프로젝트입니다. 자세한 요구사항은 공지사항을 확인하세요.', done: false, dueDate: '2025.08.15' },
  { id: 3, title: '3주차 과제', description: '3주차 과제는 심화 학습 내용입니다. 궁금한 점은 게시판에 질문해주세요.', done: true, dueDate: '2025.08.22' }
])

const activeTab = ref('notice')
const noticeFilter = ref('all')
const assignmentFilter = ref('all')

const filteredNotices = computed(() => {
  if (noticeFilter.value === 'all') {
    return notices.value
  } else if (noticeFilter.value === 'required') {
    return notices.value.filter(notice => notice.required)
  } else {
    return notices.value.filter(notice => !notice.required)
  }
})

const filteredAssignments = computed(() => {
  if (assignmentFilter.value === 'all') {
    return assignments.value
  } else if (assignmentFilter.value === 'complete') {
    return assignments.value.filter(task => task.done)
  } else {
    return assignments.value.filter(task => !task.done)
  }
})

const submitAssignment = (assignmentId) => {
  const task = assignments.value.find(t => t.id === assignmentId);
  if (task) {
    task.done = true;
    alert(`${task.title}이(가) 성공적으로 제출되었습니다!`);
  }
};

const getStatusText = (status) => {
  const map = {
    active: '진행중',
    completed: '완료',
    upcoming: '예정'
  }
  return map[status] || '진행중'
}

const showNoticeModal = ref(false);
const selectedNotice = ref(null);
const openNoticeDetailModal = (notice) => {
  selectedNotice.value = notice;
  showNoticeModal.value = true;
};
const closeNoticeModal = () => {
  showNoticeModal.value = false;
  selectedNotice.value = null;
};

const showAssignmentModal = ref(false);
const selectedAssignment = ref(null);
const openAssignmentDetailModal = (assignment) => {
  selectedAssignment.value = assignment;
  showAssignmentModal.value = true;
};
const closeAssignmentModal = () => {
  showAssignmentModal.value = false;
  selectedAssignment.value = null;
};

const showNoticeRegisterModal = ref(false);
const openNoticeRegisterModal = () => {
  showNoticeRegisterModal.value = true;
};
const closeNoticeRegisterModal = () => {
  showNoticeRegisterModal.value = false;
};

const registerNotice = async (newNoticeData) => {
  try {
    let uploadedFileNames = [];

    if (newNoticeData.files && newNoticeData.files.length > 0) {
      const formData = new FormData();
      newNoticeData.files.forEach(file => {
        formData.append('files', file);
      });

      const fileUploadResponse = await apiClient.post('/boards/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });

      const uploadResult = fileUploadResponse.data;
      console.log('📢 파일 업로드 성공:', uploadResult);

      // 문자열 배열로 변환
      uploadedFileNames = uploadResult.map(file => `${file.uuid}_${file.fileName}`);
    }

    const classId = BigInt(props.classData.classId);
    const noticePayload = {
      title: newNoticeData.title,
      content: newNoticeData.content,
      categoryId: null,
      boardType: 'NORMAL',
      fileNames: uploadedFileNames  // ✅ 문자열만 담긴 배열
    };

    console.log(`📢 공지사항 등록 API 요청: /class/${classId}/boards`, noticePayload);

    await apiClient.post(`/class/${classId}/boards`, noticePayload);

    showNoticeRegisterModal.value = false;
    alert('공지사항이 성공적으로 등록되었습니다!');
  } catch (error) {
    console.error('📢 공지사항 등록 실패:', error);
    alert('공지사항 등록에 실패했습니다. 다시 시도해주세요.');
  }
};


const showAssignmentRegisterModal = ref(false);
const openAssignmentRegisterModal = () => {
  showAssignmentRegisterModal.value = true;
};
const closeAssignmentRegisterModal = () => {
  showAssignmentRegisterModal.value = false;
};
const registerAssignment = (newAssignment) => {
  assignments.value.push({
    ...newAssignment,
    id: assignments.value.length + 1,
    done: false
  });
  showAssignmentRegisterModal.value = false;
  alert('과제가 성공적으로 등록되었습니다!');
};

const deleteNotice = (noticeId) => {
  if (confirm('정말 이 공지사항을 삭제하시겠습니까?')) {
    notices.value = notices.value.filter(notice => notice.id !== noticeId);
    closeNoticeModal();
    alert('공지사항이 삭제되었습니다.');
  }
};

const deleteAssignment = (assignmentId) => {
  if (confirm('정말 이 과제를 삭제하시겠습니까?')) {
    assignments.value = assignments.value.filter(task => task.id !== assignmentId);
    closeAssignmentModal();
    alert('과제가 삭제되었습니다.');
  }
};
</script>

<style>
@import '@/styles/classinfo.css';
</style>