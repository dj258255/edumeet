<template>
  <div class="draggable-card" :style="{ animationDelay: `${animationDelay}s` }">
    <div class="card-image">
      <img :src="card.image" :alt="card.title" />
      <div class="card-overlay">
        <div class="card-hover-content">
          <span class="view-more">자세히 보기</span>
        </div>
      </div>
      <div class="card-badge">{{ card.tags[0] }}</div>
    </div>
    <div class="card-content">
      <h3 class="card-title">{{ card.title }}</h3>
      <p class="card-description">{{ card.description }}</p>
      <div class="card-tags">
        <span class="tag" v-for="tag in card.tags" :key="tag">{{ tag }}</span>
      </div>
      <div class="card-footer">
        <div class="card-stats">
          <span class="stat">⭐ 4.8</span>
          <span class="stat">👥 1.2k</span>
        </div>
        <button class="enroll-btn" @click="handleEnroll">수강하기</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'

const props = defineProps({
  card: {
    type: Object,
    required: true
  },
  animationDelay: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['enroll'])

const handleEnroll = () => {
  emit('enroll', props.card.id)
}
</script>

<style scoped>
/* HomeView.css의 카드 관련 스타일을 그대로 사용하기 위해 scoped를 제거하고 
   부모 컴포넌트에서 CSS를 import하도록 설정 */
</style> 