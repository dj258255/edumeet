<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">📝 과제 등록</h3>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label for="assignment-title">제목</label>
          <input type="text" id="assignment-title" v-model="form.title" placeholder="과제 제목을 입력하세요" class="form-input" />
        </div>
        <div class="form-group">
          <label for="assignment-description">설명</label>
          <textarea id="assignment-description" v-model="form.description" placeholder="과제 설명을 입력하세요" class="form-textarea"></textarea>
        </div>
        <div class="form-group">
          <label for="assignment-due-date">마감일</label>
          <input type="date" id="assignment-due-date" v-model="form.dueDate" class="form-input" />
        </div>
      </div>
      <div class="modal-footer">
        <button class="submit-btn" @click="submitForm">등록</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits } from 'vue';

const props = defineProps({
  isVisible: Boolean
});

const emit = defineEmits(['close', 'register']);

const form = ref({
  title: '',
  description: '',
  dueDate: ''
});

const submitForm = () => {
  if (!form.value.title || !form.value.description || !form.value.dueDate) {
    alert('제목, 설명, 마감일을 모두 입력해주세요.');
    return;
  }
  emit('register', { ...form.value, done: false });
  resetForm();
};

const closeModal = () => {
  emit('close');
  resetForm();
};

const resetForm = () => {
  form.value = {
    title: '',
    description: '',
    dueDate: ''
  };
};
</script>

<style >
  @import '@/styles/classinfo.css';
</style>