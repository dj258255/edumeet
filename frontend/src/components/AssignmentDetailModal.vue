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
        <div class="assignment-meta">
          <span class="status" :class="{ done: assignmentData.done }">
            {{ assignmentData.done ? '완료' : '미완료' }}
          </span>
          <span v-if="assignmentData.dueDate" class="due-date">마감일: {{ assignmentData.dueDate }}</span>
        </div>
        <div class="assignment-content">
          <p>{{ assignmentData.description }}</p>
        </div>
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
    default: () => ({ title: '', description: '', done: false, dueDate: '' }),
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

const submitAssignment = () => {
  emit('submit', { id: props.assignmentData.id, file: selectedFile.value });
  closeModal();
};

const selectedFile = ref(null)
const selectedFileName = ref('')
const onFileChange = (e) => {
  const file = e.target.files && e.target.files[0]
  selectedFile.value = file || null
  selectedFileName.value = file ? file.name : ''
}
</script>

<style scoped>
  @import '@/styles/classinfo.css';
</style>