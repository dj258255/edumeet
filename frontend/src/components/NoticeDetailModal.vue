<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h3 class="modal-title">{{ noticeData.title }}</h3>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="notice-meta">
          <span class="badge" :class="{ required: noticeData.required }">{{ noticeData.required ? '필수' : '일반' }}</span>
          <span class="date">{{ noticeData.date }}</span>
        </div>
        <div class="notice-content">
          <p>{{ noticeData.content }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue';

const props = defineProps({
  isVisible: {
    type: Boolean,
    default: false,
  },
  noticeData: {
    type: Object,
    required: true,
    default: () => ({ title: '', content: '', required: false, date: '' }),
  },
});

const emit = defineEmits(['close']);

const closeModal = () => {
  emit('close');
};
</script>

<style scoped>
  @import '@/styles/classinfo.css';
</style>