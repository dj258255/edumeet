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
            <li v-for="file in assignmentData.attachmentFiles" :key="file.uuid">
              <template v-if="isImage(file.fileName)">
                <button class="file-btn image-btn" @click="openImageModal(file.s3Url)">
                  {{ file.fileName }}
                </button>
              </template>
              <template v-else>
                <a :href="file.s3Url" target="_blank" download class="file-link">{{ file.fileName }}</a>
              </template>
            </li>
          </ul>
        </div>
      </div>

      <div v-if="isMyCreatedClass && assignmentData.studentSubmissionStatuses?.length" class="student-submission-status section">
        <div class="section-title-box">
          <h4 class="section-title">학생 제출 현황</h4>
        </div>
        <div class="section-content">
          <ul>
            <li v-for="student in assignmentData.studentSubmissionStatuses" :key="student.studentId">
              <span class="student-name">{{ student.studentName }}</span>:
              <span :class="{'submitted': student.submitted, 'not-submitted': !student.submitted}">
                {{ student.status === 'NOT_SUBMITTED' ? '미제출' : '제출' }}
              </span>
              <span v-if="student.submittedAt" class="submitted-at">({{ new Date(student.submittedAt).toLocaleString() }})</span>
            </li>
          </ul>
        </div>
      </div>

      <div v-if="!isMyCreatedClass && !assignmentData.done" class="form-group section">
        <div class="section-title-box">
          <h4 class="section-title">제출 파일</h4>
        </div>
        <div class="section-content">
          <label for="assignment-submit-file" class="file-label">제출 파일 (선택)</label>
          <input id="assignment-submit-file" type="file" @change="onFileChange" class="form-input" />
          <small v-if="selectedFileName">선택된 파일: {{ selectedFileName }}</small>
        </div>
      </div>

      <div class="modal-footer section" v-if="!isMyCreatedClass && !assignmentData.done">
        <button @click="submitAssignment" class="submit-btn">제출하기</button>
      </div>
    </div>

    <ImagePreviewModal v-if="imageModalVisible" :src="previewImageUrl" @close="closeImageModal" />
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits } from 'vue';
import ImagePreviewModal from './ImagePreviewModal.vue';

const props = defineProps({
  isVisible: Boolean,
  assignmentData: Object,
  isMyCreatedClass: Boolean
});
const emit = defineEmits(['close','submit']);

const selectedFile = ref(null);
const selectedFileName = ref('');

const imageModalVisible = ref(false);
const previewImageUrl = ref('');

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
</script>

<style scoped>
@import '@/styles/classinfo.css';

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
  background: #1a202c;
  border-radius: 8px;
  width: 600px;
  max-width: 95%;
  max-height: 90%;
  overflow-y: auto;
  box-shadow: 0 8px 16px rgba(0,0,0,0.5);
  color: #e2e8f0;
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
  border-bottom: 1px solid #334155;
}
.close-btn {
  background: none;
  border: none;
  font-size: 1.25rem;
  cursor: pointer;
  color: #e2e8f0;
}
.modal-title {
  margin: 0;
  padding: 0;
}

/* 섹션 스타일 */
.section {
  padding: 1.5rem;
  background-color: #1a202c;
  border-bottom: 1px solid #334155;
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
  background-color: #2d3748;
  padding: 0.75rem 1.5rem; /* 패딩을 늘려 크기 키우기 */
  border-radius: 8px; /* 모서리를 더 둥글게 */
  margin-bottom: 1rem;
  width: fit-content; /* 내용물에 맞춰 가로 폭 조절 */
}

/* 섹션 제목 */
.section-title {
  margin: 0;
  font-weight: bold;
  color: #f1f5f9;
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
.attachment-files li {
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}
.file-btn, .file-link {
  color: #cbd5e1;
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
  color: #90cdf4;
}
.image-btn::before {
  content: '🖼️';
  color: #f6ad55;
}

/* 학생 제출 상태 */
.student-submission-status ul {
  list-style: none;
  padding-left: 0;
}
.student-submission-status li {
  margin-bottom: 0.5rem;
}
.submitted { color: #22c55e; font-weight: bold; }
.not-submitted { color: #ef4444; font-weight: bold; }

/* 폼 입력 */
.form-input { 
  width: 100%; 
  padding: 0.5rem; 
  margin-top: 0.25rem; 
  background-color: #4a5568; 
  border: 1px solid #4a5568;
  color: #e2e8f0;
}

/* 제출 버튼 */
.submit-btn {
  background-color: #3b82f6;
  color: #fff;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 4px;
  cursor: pointer;
  width: 100%;
  font-weight: bold;
  margin-top: 1rem;
}
.submit-btn:hover {
  background-color: #2563eb;
}

/* 스크롤바 스타일링 (웹킷 기반 브라우저) */
.section::-webkit-scrollbar {
  width: 8px;
}
.section::-webkit-scrollbar-thumb {
  background-color: #4a5568;
  border-radius: 4px;
}
.section::-webkit-scrollbar-track {
  background-color: #2d3748;
}
</style>