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
          <button class="tab-btn" :class="{ active: activeTab === 'notice' }" @click="activeTab = 'notice'">
            📢 공지사항
          </button>
          <button class="tab-btn" :class="{ active: activeTab === 'assignment' }" @click="activeTab = 'assignment'">
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
            <button class="add-btn" @click="openNoticeRegisterModal" :disabled="!canRegisterNotice">📢 공지 등록</button>
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
                <!-- 학생인 경우에만 제출 상태 표시 -->
                <span v-if="!isMyCreatedClass" class="status">{{ task.done ? '완료' : '미완료' }}</span>
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
                @click.stop="openAssignmentDetailModal(task)"
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
      v-if="selectedNotice"
      :isVisible="showNoticeModal"
      :noticeData="selectedNotice"
      :isMyCreatedClass="isMyCreatedClass"
      @close="closeNoticeModal"
      @delete="deleteNotice"
    />
    <AssignmentDetailModal
      v-if="selectedAssignment"
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
import { ref, computed, watch, onMounted } from 'vue'
import InviteModal from './InviteModal.vue'
import NoticeDetailModal from './NoticeDetailModal.vue'
import AssignmentDetailModal from './AssignmentDetailModal.vue'
import NoticeRegisterModal from './NoticeRegisterModal.vue'
import AssignmentRegisterModal from './AssignmentRegisterModal.vue'
import { useAuthStore } from '@/stores/auth.js'
import apiClient from '@/utils/apiClient'

const authStore = useAuthStore()

const props = defineProps({
  classData: Object,
  isMyCreatedClass: {
    type: Boolean,
    default: false
  }
})

watch(() => props.classData, (newVal) => {
  console.log("Class data updated:", newVal)
}, { deep: true })

const classww = () => {
  console.log(props.classData)
}

const emit = defineEmits(['enter-class', 'invite'])

const inviteModalOpen = ref(false)
const openInviteModal = () => inviteModalOpen.value = true
const closeInviteModal = () => inviteModalOpen.value = false
const handleInvite = (data) => {
  alert('초대가 성공적으로 전송되었습니다!')
  emit('invite', data)
}

const assignmentRate = computed(() => {
  if (!props.classData || props.classData.totalAssignments === 0) {
    return 0
  }
  return Math.round((props.classData.submittedAssignments / props.classData.totalAssignments) * 100)
})
const submittedAssignments = computed(() => props.classData?.submittedAssignments || 0)
const totalAssignments = computed(() => props.classData?.totalAssignments || 0)

const notices = ref([]) 
const assignments = ref([]) 

const activeTab = ref('notice')
const noticeFilter = ref('all')
const assignmentFilter = ref('all')

const filteredNotices = computed(() => {
  if (noticeFilter.value === 'all') return notices.value
  // notice 객체에 required 속성이 없으므로 boardType으로 필터링
  if (noticeFilter.value === 'required') return notices.value.filter(n => n.boardType === 'NOTICE')
  return notices.value.filter(n => n.boardType === 'NORMAL')
})

const filteredAssignments = computed(() => {
    // props.classData?.assignments 대신 로컬 상태인 assignments.value를 사용
    const assignmentList = assignments.value || [];
    const userEmail = authStore.currentUser.email; // 현재 로그인된 사용자 이메일

    const processedAssignments = assignmentList.map(task => {
        const submissionStatus = task.studentSubmissionStatuses.find(
            status => status.studentEmail === userEmail
        );
        const isDone = submissionStatus && submissionStatus.status === 'SUBMITTED';

        return {
            ...task,
            done: isDone
        };
    });

    if (assignmentFilter.value === 'all') return processedAssignments;
    if (assignmentFilter.value === 'complete') return processedAssignments.filter(t => t.done);
    return processedAssignments.filter(t => !t.done);
});


const getStatusText = (status) => {
  const map = {
    active: '진행중',
    completed: '완료',
    upcoming: '예정'
  }
  return map[status] || '진행중'
}

// 공지사항 모달
const showNoticeModal = ref(false)
const selectedNotice = ref(null)
const isFetchingNotice = ref(false)

// 현재 클래스 ID 계산 (다양한 키 지원)
const currentClassId = computed(() => {
  const data = props.classData || {}
  return data.classId || data.id || data.classroomId || data._id || ''
})

// 공지 등록 가능 여부 (유효한 클래스 ID가 있어야 함)
const canRegisterNotice = computed(() => !!currentClassId.value)

const openNoticeDetailModal = async (notice) => {
  if (isFetchingNotice.value) return
  const classId = currentClassId.value
  if (!classId) {
    alert('유효한 클래스 ID가 없습니다.')
    return
  }

  // 모달 즉시 오픈 후 상세는 백그라운드로 로드
  selectedNotice.value = notice
  showNoticeModal.value = true
  isFetchingNotice.value = true

  try {
    const res = await apiClient.get(`/class/${classId}/boards/${notice.id}`, { timeout: 10000 })
    if (res?.data) {
      selectedNotice.value = res.data
      console.log('selectedNotice:', selectedNotice.value);
    }
  } catch (err) {
    console.error('공지사항 상세 불러오기 실패:', err)
    // 모달은 유지하되, 필요시 사용자에게 재시도 안내만 표시
  } finally {
    isFetchingNotice.value = false
  }
}
const closeNoticeModal = () => {
  showNoticeModal.value = false
  selectedNotice.value = null
}

// 과제 모달
const showAssignmentModal = ref(false)
const selectedAssignment = ref(null)
const isFetchingAssignment = ref(false); 

const openAssignmentDetailModal = async (assignment) => {
  if (isFetchingAssignment.value) return; // 이미 로딩 중이면 중복 호출 방지
  const classId = currentClassId.value;

  if (!classId) {
    alert('유효한 클래스 ID가 없습니다.');
    return;
  }

  selectedAssignment.value = assignment; // 모달을 미리 띄우기 위해 초기 데이터 설정
  showAssignmentModal.value = true;
  isFetchingAssignment.value = true;

  try {
    const res = await apiClient.get(`/class/${classId}/assignments/${assignment.id}`, { timeout: 10000 });
    if (res?.data) {
      selectedAssignment.value = res.data; // 상세 정보로 업데이트
      console.log('selectedAssignment:', res.data);
    }
  } catch (err) {
    console.error('과제 상세 불러오기 실패:', err);
    // 모달을 닫거나, 에러 메시지를 표시할 수 있습니다.
  } finally {
    isFetchingAssignment.value = false;
  }
};
const closeAssignmentModal = () => {
  showAssignmentModal.value = false
  selectedAssignment.value = null
}

const fetchNoticesAndAssignments = async () => {
  try {
    const classId = currentClassId.value
    notices.value = []

    if (!classId) {
      console.warn('클래스 ID가 없어 공지/과제를 불러올 수 없습니다.')
      return
    }

    // 게시글 목록 조회 API 호출 (게시판 타입별 필터링은 백엔드에서 처리)
    const noticeRes = await apiClient.get(`/class/${classId}/boards`);
    // API 응답 구조에 따라 dtoList를 사용
    notices.value = noticeRes.data.dtoList;
    console.log('notices:', notices.value);
    
    // 과제 목록 조회 (있으면 가져오고, 없으면 빈 배열 유지)
    try {
      const assignmentRes = await apiClient.get(`/class/${classId}/assignments`)
      console.log('aaaaaaaaaaaaaaaaaaaaaaaaa',assignmentRes)
      // 응답이 배열일 수도, dtoList 형태일 수도 있어 유연 처리
      const list = Array.isArray(assignmentRes.data)
        ? assignmentRes.data
        : (assignmentRes.data?.dtoList || [])
      assignments.value = list
      console.log('asssssssss',assignments)
    } catch (e) {
      console.warn('과제 목록 API가 없거나 실패하여 빈 목록을 유지합니다.', e)
      assignments.value = []
    }

  } catch (err) {
    console.error('목록 불러오기 실패:', err);
  }
};

// 공지 등록
const showNoticeRegisterModal = ref(false)
const openNoticeRegisterModal = () => {
  if (!currentClassId.value) {
    alert('클래스가 선택되지 않아 공지 등록을 할 수 없습니다.')
    return
  }
  showNoticeRegisterModal.value = true
}
const closeNoticeRegisterModal = () => showNoticeRegisterModal.value = false

const registerNotice = async (newNoticeData) => {
  try {
    if (!currentClassId.value) {
      alert('클래스가 선택되지 않아 공지 등록을 할 수 없습니다.')
      return
    }
    let boardImages = []
    if (Array.isArray(newNoticeData.files) && newNoticeData.files.length > 0) {
      const formData = new FormData()
      newNoticeData.files.forEach(file => formData.append('files', file))
      formData.append('domain', 'board')
      
      try {
        const res = await apiClient.post('/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        

        boardImages = res.data.map(file => ({
          uuid: file.uuid,
          fileName: file.fileName,
          ord: file.ord,
          img: file.isImage
        }))
      } catch (error) {
        console.error('파일 업로드 실패:', error)
        return
      }
    }

    console.log('file',boardImages)
    const classId = currentClassId.value
    const payload = {
      title: newNoticeData.title,
      content: newNoticeData.content,
      writer: authStore.currentUser.email,
      boardType: newNoticeData.required ? 'NOTICE' : 'NORMAL',
      boardImages: boardImages // 위에서 생성한 파일 정보 배열 추가
    }
    await apiClient.post(`/class/${classId}/boards`, payload)
    fetchNoticesAndAssignments()
    showNoticeRegisterModal.value = false
    alert('공지사항이 성공적으로 등록되었습니다!')
  } catch (err) {
    console.error(err)
    alert('공지사항 등록에 실패했습니다.')
  }
}

// 과제 등록
const showAssignmentRegisterModal = ref(false)
const openAssignmentRegisterModal = () => {
  if (!currentClassId.value) {
    alert('클래스가 선택되지 않아 과제제 등록을 할 수 없습니다.')
    return
  }
  showAssignmentRegisterModal.value = true
}
const closeAssignmentRegisterModal = () => {
  showAssignmentRegisterModal.value = false
}

const registerAssignment = async (newAssignment) => {
  try {
    const classId = currentClassId.value
    if (!classId) {
      alert('클래스가 선택되지 않아 과제를 등록할 수 없습니다.')
      return
    }

    const creatorName = authStore.currentUser.email
    let attachments = []

    // 파일 업로드 처리
    if (Array.isArray(newAssignment.files) && newAssignment.files.length > 0) {
      const formData = new FormData()
      newAssignment.files.forEach(file => formData.append('files', file))
      formData.append('domain', 'assignment')

      try {
        const res = await apiClient.post('/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        attachments = res.data.map(file => ({
          uuid: file.uuid,
          fileName: file.fileName,
          ord: file.ord,
          img: file.isImage
        }))
      } catch (error) {
        console.error('파일 업로드 실패:', error)
        return
      }
    }

    const payload = {
      title: newAssignment.title,
      description: newAssignment.description,
      createdByName: creatorName,
      attachmentFiles: attachments,
    }

    const res = await apiClient.post(`/class/${classId}/assignments`, payload)
    const created = res?.data

    // ✅ UI 즉시 반영
    assignments.value.unshift({
      id: created?.id || created?.assignmentId || Date.now(),
      title: created?.title || payload.title,
      description: created?.description || payload.description,
      done: created?.done ?? false,
    })

    // 서버 데이터 싱크
    await fetchNoticesAndAssignments()

    showAssignmentRegisterModal.value = false
    alert('과제가 성공적으로 등록되었습니다!')
  } catch (err) {
    console.error('과제 등록 실패:', err)
    alert('과제 등록에 실패했습니다.')
  }
}


const submitAssignment = async (payload) => {
  try {
    const classId = currentClassId.value
    if (!classId) {
      alert('클래스가 선택되지 않아 과제를 제출할 수 없습니다.')
      return
    }

    const assignmentId = typeof payload === 'object' ? payload.id : payload
    let attachments = []

    // 파일 업로드 처리
    if (Array.isArray(payload.files) && payload.files.length > 0) {
      const formData = new FormData()
      payload.files.forEach(file => formData.append('files', file))
      formData.append('domain', 'submission')

      try {
        const res = await apiClient.post('/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        attachments = res.data.map(file => ({
          uuid: file.uuid,
          fileName: file.fileName,
          ord: file.ord,
          img: file.isImage
        }))
      } catch (error) {
        console.error('파일 업로드 실패:', error)
        return
      }
    }

    const submissionData = {
      classMemberEmail: authStore.currentUser.email,
      classMemberName: authStore.currentUser.email,
      content: payload.content || '',
      attachmentFiles: attachments
    }

    await apiClient.post(
      `/class/${classId}/submissions/assignment/${assignmentId}`,
      submissionData
    )

    // ✅ UI 즉시 반영
    assignments.value = assignments.value.map(t =>
      t.id === assignmentId ? { ...t, done: true } : t
    )

    // 서버 데이터 싱크
    await fetchNoticesAndAssignments()

    const task = assignments.value.find(t => t.id === assignmentId)
    if (task) {
      alert(`${task.title}이(가) 성공적으로 제출되었습니다!`)
    } else {
      alert('과제가 성공적으로 제출되었습니다!')
    }
  } catch (err) {
    console.error('과제 제출 실패:', err)
    alert('과제 제출에 실패했습니다.')
  }
}





const deleteNotice = async (noticeId) => {
  if (!confirm('정말 이 공지사항을 삭제하시겠습니까?')) return;

  try {
    const classId = currentClassId.value
    await apiClient.delete(`/class/${classId}/boards/${noticeId}`);
    // 삭제 성공 시 로컬 상태 반영
    notices.value = notices.value.filter(n => n.id !== noticeId);
    closeNoticeModal();
    alert('공지사항이 삭제되었습니다.');
  } catch (err) {
    console.error('공지사항 삭제 실패:', err);
    alert('공지사항 삭제에 실패했습니다.');
  }
}

const deleteAssignment = async (assignmentId) => {
  if (!confirm('정말 이 과제를 삭제하시겠습니까?')) return;

  try {
    const classId = currentClassId.value
    await apiClient.delete(`/class/${classId}/assignments/${assignmentId}`)
    // 삭제 성공 시 로컬 상태 반영
    assignments.value = assignments.value.filter(t => t.id !== assignmentId);
    closeAssignmentModal();
    alert('과제가 삭제되었습니다.');
  } catch (err) {
    console.error('과제 삭제 실패:', err);
    alert('과제 삭제에 실패했습니다.');
  }
}

// 클래스가 바뀔 때마다 공지/과제 재조회
watch(() => currentClassId.value, (newId, oldId) => {
  if (newId && newId !== oldId) {
    fetchNoticesAndAssignments()
  }
}, { immediate: true })

</script>

<style>
@import '@/styles/classinfo.css';
</style>
