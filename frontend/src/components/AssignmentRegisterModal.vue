<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">📝 과제 등록</h3>
        <button class="close-btn" @click="closeModal">X</button>
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
          <label>첨부 파일 (선택)</label>
          <input type="file" @change="onFileChange" class="form-input" />
          <ul>
            <li v-for="(file, index) in form.files" :key="index">
              {{ file.name }}
              <button type="button" @click="removeFile(index)">삭제</button>
            </li>
          </ul>
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

const props = defineProps({ isVisible: Boolean });
const emit = defineEmits(['close', 'register']);

const form = ref({
  title: '',
  description: '',
  files: []
});

const onFileChange = (e) => {
  const file = e.target.files && e.target.files[0];
  if (file) {
    form.value.files.push(file);
  }
  e.target.value = null;  // 같은 파일 다시 선택 가능하게 초기화
};

const removeFile = (index) => {
  form.value.files.splice(index, 1);
};

const submitForm = () => {
  if (!form.value.title) {
    alert('제목을 입력해주세요.');
    return;
  }
  emit('register', {
    title: form.value.title,
    description: form.value.description,
    files: form.value.files,
    done: false
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
    description: '',
    files: []
  };
};
</script>


<style >
  @import '@/styles/classinfo.css';
</style>