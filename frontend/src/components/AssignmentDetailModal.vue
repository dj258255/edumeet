<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">{{ assignmentData.title }}</h3>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="assignment-content">
          <p>{{ assignmentData.description }}</p>
        </div>
        
        <!-- 첨부파일 리스트 -->
        <div v-if="assignmentData.attachmentFiles && assignmentData.attachmentFiles.length" class="attachment-files">
          <h4>첨부 파일</h4>
          <ul>
            <li v-for="file in assignmentData.attachmentFiles" :key="file.uuid">
              <a :href="file.s3Url" target="_blank" rel="noopener noreferrer">{{ file.fileName }}</a>
            </li>
          </ul>
        </div>

        <!-- 선생님만 보는 학생 제출 현황 -->
        <div v-if="isMyCreatedClass && assignmentData.studentSubmissionStatuses?.length" class="student-submission-status">
          <h4>학생 제출 현황</h4>
          <ul>
            <li v-for="student in assignmentData.studentSubmissionStatuses" :key="student.studentId">
              <span class="student-name">{{ student.studentName }}</span>:
              <span 
                class="submission-status" 
                :class="{'submitted': student.submitted, 'not-submitted': !student.submitted}"
              >
                {{ student.status === 'NOT_SUBMITTED' ? '미제출' : '제출' }}
              </span>
              <span v-if="student.submittedAt" class="submitted-at">({{ new Date(student.submittedAt).toLocaleString() }})</span>
            </li>
          </ul>
        </div>

        <!-- 학생만 제출 파일 선택 UI -->
        <div v-if="!isMyCreatedClass && !assignmentData.done" class="form-group">
          <label for="assignment-submit-file">제출 파일 (선택)</label>
          <input id="assignment-submit-file" type="file" @change="onFileChange" class="form-input" />
          <small v-if="selectedFileName">선택된 파일: {{ selectedFileName }}</small>
        </div>
      </div>
      <div class="modal-footer" v-if="!isMyCreatedClass">
        <button 
          v-if="!assignmentData.done" 
          @click="submitAssignment" 
          class="submit-btn"
        >
          제출하기
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits } from 'vue';

const props = defineProps({
  isVisible: {
    type: Boolean,
    default: false,
  },
  assignmentData: {
    type: Object,
    required: true,
    default: () => ({
      title: '',
      description: '',
      done: false,
      dueDate: '',
      attachmentFiles: [],
      studentSubmissionStatuses: []
    }),
  },
  isMyCreatedClass: {
    type: Boolean,
    default: false,
  }
});

const emit = defineEmits(['close', 'submit']);

const closeModal = () => {
  emit('close');
};

const selectedFile = ref(null);
const selectedFileName = ref('');

const onFileChange = (e) => {
  const file = e.target.files && e.target.files[0];
  selectedFile.value = file || null;
  selectedFileName.value = file ? file.name : '';
}

const submitAssignment = () => {
  const filesArray = selectedFile.value ? [selectedFile.value] : [];
  emit('submit', { id: props.assignmentData.id, files: filesArray });
  closeModal();
};
</script>


<style scoped>
  @import '@/styles/classinfo.css';
</style>
