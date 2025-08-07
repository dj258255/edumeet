<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">📢 공지 등록</h3>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label for="notice-title">제목</label>
          <input type="text" id="notice-title" v-model="form.title" placeholder="공지사항 제목을 입력하세요" class="form-input" />
        </div>
        <div class="form-group">
          <label for="notice-content">내용</label>
          <textarea id="notice-content" v-model="form.content" placeholder="공지사항 내용을 입력하세요" class="form-textarea"></textarea>
        </div>
        <div class="form-group">
          <label for="notice-files">파일 첨부</label>
          <input type="file" id="notice-files" multiple @change="handleFileChange" class="form-file-input" />
        </div>
        <div class="form-group form-check">
          <input type="checkbox" id="notice-required" v-model="form.required" class="form-checkbox" />
          <label for="notice-required">필수 공지사항으로 등록</label>
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
  content: '',
  required: false
});

const files = ref([]);

const handleFileChange = (event) => {
  files.value = [...event.target.files];
};

const submitForm = () => {
  if (!form.value.title || !form.value.content) {
    alert('제목과 내용을 모두 입력해주세요.');
    return;
  }
  emit('register', {
    ...form.value,
    files: files.value,
    date: new Date().toISOString().split('T')[0]
  });
  resetForm();
};

const closeModal = () => {
  emit('close');
  resetForm();
};

const resetForm = () => {
  form.value = {
    title: '',
    content: '',
    required: false
  };
  files.value = [];
  const fileInput = document.getElementById('notice-files');
  if (fileInput) {
    fileInput.value = '';
  }
};
</script>

<style>
  @import '@/styles/classinfo.css';

</style>