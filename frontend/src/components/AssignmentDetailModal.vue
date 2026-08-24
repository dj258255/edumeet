<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">{{ assignmentData.title }}</h3>
        <button class="close-btn" @click="closeModal">✕</button>
      </div>

      <div class="modal-body section">
        <div class="section-title-box">
          <h4 class="section-title">설명</h4>
        </div>
        <div class="section-content">
          <p>{{ assignmentData.description }}</p>
        </div>
      </div>

      <div v-if="assignmentData.attachmentFiles?.length" class="attachment-files section">
        <div class="section-title-box">
          <h4 class="section-title">첨부 파일</h4>
        </div>
        <div class="section-content">
          <ul>
            <li v-for="file in assignmentData.attachmentFiles" :key="file.uuid" class="file-item-with-actions">
              <div class="file-info">
                <template v-if="isImage(file.fileName)">
                  <button class="file-btn image-btn" @click="openImageModal(file.s3Url)">
                    {{ file.fileName }}
                  </button>
                </template>
                <template v-else>
                  <span class="file-name">
                    📄 {{ file.fileName }}
                  </span>
                </template>
              </div>
              <div class="file-actions">
                <button @click="forceDownload(file.s3Url, file.fileName)" class="download-link-btn">
                  📥 다운로드
                </button>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <div v-if="isMyCreatedClass && assignmentData.studentSubmissionStatuses?.length" class="student-submission-status section">
        <div class="section-title-box">
          <h4 class="section-title">학생 제출 현황</h4>
        </div>
        <div class="section-content">
          <div v-for="student in assignmentData.studentSubmissionStatuses" :key="student.studentId" class="student-submission-item" @click="debugStudentSubmission(student)">
            <div class="student-header">
              <span class="student-name">{{ student.studentName }}</span>
              <span :class="{'submitted': student.status === 'SUBMITTED', 'not-submitted': student.status === 'NOT_SUBMITTED'}" class="status-badge">
                {{ student.status === 'NOT_SUBMITTED' ? '미제출' : '제출' }}
              </span>
              <span v-if="student.submittedAt" class="submitted-at">
                {{ formatDate(student.submittedAt) }}
              </span>
            </div>
            
            <div v-if="student.status === 'SUBMITTED'" class="submission-details">
              <div v-if="student.submissionFiles?.length" class="submission-files">
                <h5 class="files-title">제출 파일:</h5>
                <ul class="files-list">
                  <li v-for="file in student.submissionFiles" :key="file.uuid" class="file-item-with-actions">
                    <div class="file-info">
                      <template v-if="isImage(file.fileName)">
                        <button class="file-btn image-btn" @click="openImageModal(file.s3Url)">
                          🖼️ {{ file.fileName }}
                        </button>
                      </template>
                      <template v-else>
                        <span class="file-name">
                          📄 {{ file.fileName }}
                        </span>
                      </template>
                    </div>
                    <div class="file-actions">
                      <a :href="file.s3Url" :download="file.fileName" class="download-link-btn">
                        📥 다운로드
                      </a>
                    </div>
                  </li>
                </ul>
              </div>
              
              <div v-if="!student.submissionFiles?.length" class="no-content">
                <span class="no-content-text">제출된 파일이 없습니다.</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!isMyCreatedClass && isSubmitted && submittedFiles?.length" class="submitted-files section">
        <div class="section-title-box">
          <h4 class="section-title">제출한 파일</h4>
        </div>
        <div class="section-content">
          <ul>
            <li v-for="file in submittedFiles" :key="file.uuid" class="file-item-with-actions">
              <div class="file-info">
                <template v-if="isImage(file.fileName)">
                  <button class="file-btn image-btn" @click="openImageModal(file.s3Url)">
                    {{ file.fileName }}
                  </button>
                </template>
                <template v-else>
                  <span class="file-name">
                    📄 {{ file.fileName }}
                  </span>
                </template>
              </div>
              <div class="file-actions">
                <button @click="forceDownload(file.s3Url, file.fileName)" class="download-link-btn">
                  📥 다운로드
                </button>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <div v-if="!isMyCreatedClass && isSubmitted" class="submission-status section">
        <div class="section-title-box">
          <h4 class="section-title">제출 상태</h4>
        </div>
        <div class="section-content">
          <div class="status-message submitted">
            ✅ 과제가 성공적으로 제출되었습니다.
            <span v-if="submissionDate" class="submission-date">
              (제출일: {{ formatDate(submissionDate) }})
            </span>
          </div>
        </div>
      </div>

      <div v-if="!isMyCreatedClass && !isSubmitted" class="form-group section">
        <div class="section-title-box">
          <h4 class="section-title">제출 파일</h4>
        </div>
        <div class="section-content">
          <label for="assignment-submit-file" class="file-label">제출 파일 (선택)</label>
          <input id="assignment-submit-file" type="file" @change="onFileChange" class="form-input" />
          <small v-if="selectedFileName">선택된 파일: {{ selectedFileName }}</small>
        </div>
      </div>

      <div class="modal-footer section" v-if="!isMyCreatedClass && !isSubmitted">
        <button @click="submitAssignment" class="submit-btn">제출하기</button>
      </div>
    </div>

    <ImagePreviewModal v-if="imageModalVisible" :src="previewImageUrl" @close="closeImageModal" />
  </div>
</template>

<script setup>
import { ref, computed, defineProps, defineEmits } from 'vue';
import { useAuthStore } from '@/stores/auth.js';
import ImagePreviewModal from './ImagePreviewModal.vue';

const props = defineProps({
  isVisible: Boolean,
  assignmentData: Object,
  isMyCreatedClass: Boolean
});
const emit = defineEmits(['close','submit']);

const authStore = useAuthStore();

const selectedFile = ref(null);
const selectedFileName = ref('');

const imageModalVisible = ref(false);
const previewImageUrl = ref('');

// 현재 사용자의 제출 상태 확인
const currentUserSubmission = computed(() => {
  if (!props.assignmentData?.studentSubmissionStatuses || !authStore.currentUser?.email) {
    return null;
  }
  
  const submission = props.assignmentData.studentSubmissionStatuses.find(
    status => status.studentEmail === authStore.currentUser.email
  );
  
  // 디버깅을 위한 로그
  console.log('Current user submission:', submission);
  console.log('Assignment data:', props.assignmentData);
  console.log('All student submissions:', props.assignmentData.studentSubmissionStatuses);
  
  return submission;
});

// 제출 완료 여부
const isSubmitted = computed(() => {
  return currentUserSubmission.value?.status === 'SUBMITTED';
});

// 제출한 파일들
const submittedFiles = computed(() => {
  return currentUserSubmission.value?.submissionFiles || [];
});

// 제출일
const submissionDate = computed(() => {
  return currentUserSubmission.value?.submittedAt;
});

const closeModal = () => emit('close');

const onFileChange = (e) => {
  const file = e.target.files?.[0] || null;
  selectedFile.value = file;
  selectedFileName.value = file?.name || '';
};

const submitAssignment = () => {
  emit('submit', { id: props.assignmentData.id, files: selectedFile.value ? [selectedFile.value] : [] });
  closeModal();
};

const isImage = (fileName) => /\.(jpg|jpeg|png|gif|bmp|webp)$/i.test(fileName);

const openImageModal = (url) => {
  previewImageUrl.value = url;
  imageModalVisible.value = true;
};

const closeImageModal = () => {
  imageModalVisible.value = false;
};

// 파일 다운로드 함수 (기존)
const downloadFile = (url, fileName) => {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.target = '_blank';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

// 강제 다운로드 함수 (새로운 방식)
const forceDownload = async (url, fileName) => {
  try {
    // fetch를 사용해서 파일을 blob으로 다운로드
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error('파일 다운로드에 실패했습니다.');
    }
    
    const blob = await response.blob();
    
    // blob URL 생성
    const blobUrl = window.URL.createObjectURL(blob);
    
    // 다운로드 링크 생성
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = fileName || 'download';
    link.style.display = 'none';
    
    // 링크를 DOM에 추가하고 클릭
    document.body.appendChild(link);
    link.click();
    
    // 정리
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
    
    console.log('파일 다운로드 완료:', fileName);
  } catch (error) {
    console.error('다운로드 중 오류 발생:', error);
    // 에러 발생시 기존 방식으로 fallback
    downloadFile(url, fileName);
  }
};

// 날짜 포맷팅 함수
const formatDate = (dateString) => {
  if (!dateString) return '';
  
  const date = new Date(dateString);
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 디버깅 함수
const debugStudentSubmission = (student) => {
  console.log('=== 학생 제출 정보 디버깅 ===');
  console.log('학생 이름:', student.studentName);
  console.log('학생 이메일:', student.studentEmail);
  console.log('제출 상태:', student.status);
  console.log('제출 시간:', student.submittedAt);
  console.log('제출 파일들 (submissionFiles):', student.submissionFiles);
  console.log('첨부 파일들 (attachmentFiles):', student.attachmentFiles);
  console.log('전체 학생 객체:', student);
  console.log('==============================');
};
</script>

<style scoped>
/*
  classinfo.css에서 분리된 모달 관련 스타일을 이곳에 직접 삽입하여
  다크/라이트 모드 변수가 올바르게 적용되도록 함.
*/

:root {
  --bg-color: #f8fafc;
  --card-bg: #ffffff;
  --text-color: #1f2937;
  --text-secondary: #6b7280;
  --border-color: #e5e7eb;
  --input-bg: #ffffff;
  --primary-color: #2563eb;
  --primary-hover: #1e3a8a;
  --success-color: #10b981;
  --success-hover: #047857;
  --danger-color: #ef4444;
  --danger-hover: #dc2626;
  --disabled-color: #9ca3af;
  --video-bg: #000000;
  --shadow-color: rgba(0, 0, 0, 0.08);
  --brand-main: #2563eb;
  --brand-sub: #eff6ff;
  --text-inverse: #ffffff;
}

.dark-mode {
  --bg-color: #1f2937;
  --card-bg: #374151;
  --text-color: #f9fafb;
  --text-secondary: #d1d5db;
  --border-color: #4b5563;
  --input-bg: #374151;
  --primary-color: #3b82f6;
  --primary-hover: #1d4ed8;
  --success-color: #10b981;
  --success-hover: #047857;
  --danger-color: #ef4444;
  --danger-hover: #dc2626;
  --video-bg: #111827;
  --shadow-color: rgba(0, 0, 0, 0.3);
  --brand-main: #3b82f6;
  --brand-sub: #1e3a8a;
  --text-inverse: #ffffff;
}

/* 모달 오버레이 */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,0.5);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000;
}

/* 모달 컨테이너 */
.modal-container {
  background: var(--card-bg); /* 변수 적용 */
  color: var(--text-color); /* 변수 적용 */
  border-radius: 8px;
  width: 600px;
  max-width: 95%;
  max-height: 90%;
  overflow-y: auto;
  box-shadow: 0 8px 16px rgba(0,0,0,0.5);
  padding: 0;
}

/* 헤더 */
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  font-size: 1.5rem;
  font-weight: 700;
  border-bottom: 1px solid var(--border-color); /* 변수 적용 */
  background: var(--bg-color); /* 변수 적용 */
}
.close-btn {
  background: none;
  border: none;
  font-size: 1.25rem;
  cursor: pointer;
  color: var(--text-color); /* 변수 적용 */
}
.modal-title {
  margin: 0;
  padding: 0;
}

/* 섹션 스타일 */
.section {
  padding: 1.5rem;
  background-color: var(--card-bg); /* 변수 적용 */
  border-bottom: 1px solid var(--border-color); /* 변수 적용 */
  max-height: 200px;
  overflow-y: hidden;
  transition: all 0.3s ease;
}
.section:hover {
  overflow-y: auto;
}
.section:last-of-type {
  border-bottom: none;
}

/* 섹션 제목 컨테이너 */
.section-title-box {
  background-color: var(--input-bg); /* 변수 적용 */
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  margin-bottom: 1rem;
  width: fit-content;
}

/* 섹션 제목 */
.section-title {
  margin: 0;
  font-weight: bold;
  color: var(--text-color); /* 변수 적용 */
  font-size: 1.2rem;
}

/* 섹션 내용 */
.section-content p {
  margin: 0;
}

/* 첨부파일 스타일 */
.attachment-files ul {
  list-style: none;
  padding-left: 0;
}
.file-btn, .file-link {
  color: var(--text-secondary); /* 변수 적용 */
  text-decoration: underline;
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
}
.file-btn::before, .file-link::before {
  content: '📄';
  margin-right: 8px;
  color: var(--primary-color); /* 변수 적용 */
}
.image-btn::before {
  content: '🖼️';
  color: var(--success-color); /* 변수 적용 */
}

/* 학생 제출 상태 */
.student-submission-status .section-content {
  max-height: 400px;
  overflow-y: auto;
}

.student-submission-item {
  margin-bottom: 1.5rem;
  padding: 1rem;
  border: 1px solid var(--border-color); /* 변수 적용 */
  border-radius: 8px;
  background-color: var(--bg-color); /* 변수 적용 */
}

.student-submission-item:last-child {
  margin-bottom: 0;
}

.student-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color); /* 변수 적용 */
}

.student-name {
  font-weight: 600;
  color: var(--text-color); /* 변수 적용 */
  font-size: 1rem;
}

.status-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.status-badge.submitted {
  background-color: var(--success-color); /* 변수 적용 */
  color: var(--text-inverse); /* 변수 적용 */
  border: 1px solid var(--success-color); /* 변수 적용 */
}

.status-badge.not-submitted {
  background-color: var(--danger-color); /* 변수 적용 */
  color: var(--text-inverse); /* 변수 적용 */
  border: 1px solid var(--danger-color); /* 변수 적용 */
}

.submitted-at {
  font-size: 0.8rem;
  color: var(--text-secondary); /* 변수 적용 */
  margin-left: auto;
}

.submission-details {
  padding-top: 0.75rem;
}

.submission-content {
  margin-bottom: 1rem;
}

.content-title, .files-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-secondary); /* 변수 적용 */
  margin: 0 0 0.5rem 0;
}

.content-text {
  background-color: var(--input-bg); /* 변수 적용 */
  padding: 0.75rem;
  border-radius: 6px;
  border-left: 3px solid var(--primary-color); /* 변수 적용 */
  color: var(--text-color); /* 변수 적용 */
  font-size: 0.9rem;
  line-height: 1.5;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.submission-files {
  margin-bottom: 1rem;
}

.files-list {
  list-style: none;
  padding-left: 0;
  margin: 0;
}

.file-item {
  margin-bottom: 0.5rem;
}

.no-content {
  padding: 0.75rem;
  text-align: center;
  color: var(--text-secondary); /* 변수 적용 */
  font-style: italic;
  background-color: var(--input-bg); /* 변수 적용 */
  border-radius: 6px;
}

.no-content-text {
  font-size: 0.9rem;
}

/* 폼 입력 */
.form-input { 
  width: 100%; 
  padding: 0.5rem; 
  margin-top: 0.25rem; 
  background-color: var(--input-bg); /* 변수 적용 */
  border: 1px solid var(--border-color); /* 변수 적용 */
  color: var(--text-color); /* 변수 적용 */
}

/* 제출 버튼 */
.submit-btn {
  background-color: var(--primary-color); /* 변수 적용 */
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 4px;
  cursor: pointer;
  width: 100%;
  font-weight: bold;
  margin-top: 1rem;
}
.submit-btn:hover {
  background-color: var(--primary-hover); /* 변수 적용 */
}

/* 제출 상태 메시지 */
.submission-status .status-message {
  padding: 1rem;
  border-radius: 8px;
  background-color: var(--success-color); /* 변수 적용 */
  border: 1px solid var(--success-color); /* 변수 적용 */
  color: var(--text-inverse); /* 변수 적용 */
  font-weight: bold;
  text-align: center;
}

.submission-date {
  display: block;
  font-size: 0.9rem;
  color: var(--text-inverse); /* 변수 적용 */
  font-weight: normal;
  margin-top: 0.5rem;
}

/* 다운로드 버튼 스타일 */
.download-btn {
  color: var(--primary-color) !important; /* 변수 적용 */
  text-decoration: underline;
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.download-btn::before {
  content: '📥';
  margin-right: 8px;
  color: var(--primary-color); /* 변수 적용 */
}

.download-btn:hover {
  color: var(--primary-hover) !important; /* 변수 적용 */
}

/* 파일 항목 레이아웃 */
.file-item-with-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
  background-color: var(--input-bg); /* 변수 적용 */
  border-radius: 6px;
  border: 1px solid var(--border-color); /* 변수 적용 */
}

.file-item-with-actions:hover {
  background-color: var(--bg-color); /* 변수 적용 */
}

.file-info {
  flex: 1;
  display: flex;
  align-items: center;
}

.file-name {
  color: var(--text-color); /* 변수 적용 */
  font-size: 0.9rem;
  display: flex;
  align-items: center;
}

.file-actions {
  margin-left: 1rem;
}

.download-link-btn {
  display: inline-flex;
  align-items: center;
  padding: 0.5rem 1rem;
  background-color: var(--primary-color); /* 변수 적용 */
  color: var(--text-inverse); /* 변수 적용 */
  text-decoration: none;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  transition: background-color 0.2s ease;
  border: 1px solid var(--primary-color); /* 변수 적용 */
  cursor: pointer;
}

.download-link-btn:hover {
  background-color: var(--primary-hover); /* 변수 적용 */
  color: var(--text-inverse); /* 변수 적용 */
  text-decoration: none;
}

.download-link-btn:visited {
  color: var(--text-inverse); /* 변수 적용 */
}

.download-link-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px var(--primary-hover); /* 변수 적용 */
}

/* 파일 목록 공통 스타일 */
.attachment-files ul,
.submission-files ul,
.submitted-files ul {
  list-style: none;
  padding-left: 0;
  margin: 0;
}

.files-list {
  margin: 0;
}

/* 스크롤바 스타일링 (웹킷 기반 브라우저) */
.section::-webkit-scrollbar {
  width: 8px;
}
.section::-webkit-scrollbar-thumb {
  background-color: var(--border-color); /* 변수 적용 */
  border-radius: 4px;
}
.section::-webkit-scrollbar-track {
  background-color: var(--bg-color); /* 변수 적용 */
}

</style>