<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <span class="badge" :class="{ required: noticeData.required }">{{ noticeData.required ? '필수' : '일반' }}</span>
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
          <span class="date">작성자 : {{ props.noticeData.writer }}</span>
        </div>
        <div class="notice-content">
          <p>{{ noticeData.content }}</p>
        </div>
        <!-- 첨부 이미지가 있을 때만 렌더링 -->
        <div class="notice-content" v-if="file">
          <img :src="file" alt="attachment image">
        </div>
      </div>
      
      <div class="modal-footer" v-if="isMyCreatedClass">
        <div class="notice-views">
          <span class="badge view-label">조회수</span>
          <span class="view-count">{{ noticeData.view }}</span>
        </div>
        
        <button class="delete-btn" @click="deleteItem">삭제</button>
      </div>
      </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue';
import { ref, watch, onBeforeUnmount } from 'vue';
import apiClient from '@/stores/auth.js';

const file=ref('')
let objectUrl = null

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
  isMyCreatedClass: {
    type: Boolean,
    default: false
  }
});
const localNoticeData = ref(props.noticeData);
// noticeData prop의 변화를 감시하고, 변화가 있을 때마다 localNoticeData를 업데이트합니다.
watch(() => props.noticeData, (newVal) => {
  console.log('NoticeDetailModal → watch newVal:', newVal)
  if (newVal) {
    localNoticeData.value = newVal;
  }
}, { immediate: true });

const emit = defineEmits(['close', 'delete']);

const closeModal = () => {
  emit('close');
};

const deleteItem = () => {
  emit('delete', props.noticeData.id);
};
// 모달이 열리고 유효한 데이터가 있을 때만 이미지 로드
async function loadNoticeImage() {
  const nd = props.noticeData;
  if (!props.isVisible || !nd || !nd.boardImages || nd.boardImages.length === 0) {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
      objectUrl = null
    }
    file.value = '';
    return;
  }

  // 첫 번째 이미지 정보 추출
  console.log('NoticeDetailModal → nd:', nd);
  const boardImage = nd.boardImages[0];
  console.log('NoticeDetailModal → boardImage:', boardImage);
  const combinedName = `${boardImage.uuid}_${boardImage.fileName}`;
  console.log('NoticeDetailModal → combinedName:', combinedName);
  try {
    const res = await apiClient.get(`/boards/upload/${combinedName}`);
    console.log('NoticeDetailModal → res data:', res.data);

    file.value = res.data.url;
    console.log('NoticeDetailModal → file.value:', file.value);
  } catch (e) {
    console.warn('첨부 이미지 요청 실패', e);
    file.value = '';
  }
}

watch(
  () => [props.isVisible, props.noticeData],
  () => {
    loadNoticeImage();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})
</script>

<style>
  /*
    classinfo.css 파일을 불러옵니다.
    NoticeDetailModal의 스타일은 모두 classinfo.css에 포함되어야 합니다.
  */
  @import '@/styles/classinfo.css';
</style>