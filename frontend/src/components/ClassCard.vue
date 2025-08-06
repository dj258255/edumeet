<template>
  <div class="draggable-card" :style="{ animationDelay: `${animationDelay}s` }">
    <div class="card-image">
      <img :src="cardImage" :alt="card.title" />
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
        <div class="card-actions">
          <button 
            class="enroll-btn" 
            :class="{ 'create-btn': isMyCreatedClass }"
            @click="handleButtonClick"
          >
            {{ isMyCreatedClass ? '수업 생성' : '입장하기' }}
          </button>
          <button 
            v-if="isMyCreatedClass"
            class="delete-btn" 
            @click="handleDeleteClick"
            title="클래스 삭제"
          >
            🗑️
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, computed } from 'vue'
import defaultImage from '@/assets/class_default_image.png'

const props = defineProps({
  card: {
    type: Object,
    required: true
  },
  animationDelay: {
    type: Number,
    default: 0
  },
  isMyCreatedClass: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['enroll', 'createClass', 'deleteClass', 'joinClass'])

const cardImage = computed(() => {
  // card.image가 없거나 빈 문자열이거나 유효하지 않은 경우 기본 이미지 사용
  if (!props.card.image || props.card.image === '' || props.card.image === 'null' || props.card.image === 'undefined') {
    return defaultImage
  }
  return props.card.image
})

const handleButtonClick = () => {
  console.log('🔍 ClassCard - props.card:', props.card)
  console.log('🔍 ClassCard - props.card.id:', props.card.id)
  console.log('🔍 ClassCard - props.card.classId:', props.card.classId)
  console.log('🔍 ClassCard - 모든 키:', Object.keys(props.card))
  console.log('🔍 ClassCard - props.isMyCreatedClass:', props.isMyCreatedClass)
  
  // 백엔드 데이터에서 실제 ID 키를 찾기
  const classId = props.card.id || props.card.classId || props.card.classroomId || props.card._id
  
  if (props.isMyCreatedClass) {
    // 내가 만든 반이면 수업 생성 이벤트 발생
    emit('createClass', {
      classId: classId,
      className: props.card.title
    })
  } else {
    // 내가 속한 반이면 수업 참여 이벤트 발생
    emit('joinClass', {
      classId: classId,
      className: props.card.title,
      classDescription: props.card.description
    })
  }
}

const handleDeleteClick = () => {
  console.log('🔍 ClassCard - props.card:', props.card)
  console.log('🔍 ClassCard - props.card.id:', props.card.id)
  console.log('🔍 ClassCard - props.card.classId:', props.card.classId)
  console.log('🔍 ClassCard - 모든 키:', Object.keys(props.card))
  
  if (confirm(`"${props.card.title}" 클래스를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.`)) {
    // id가 없으면 classId를 사용
    const classId = props.card.id || props.card.classId
    console.log('🔍 ClassCard - 삭제할 classId:', classId)
    emit('deleteClass', classId)
  }
}
</script>

<style scoped>
/* HomeView.css의 카드 관련 스타일을 그대로 사용하기 위해 scoped를 제거하고 
   부모 컴포넌트에서 CSS를 import하도록 설정 */
</style> 