<template>
  <div class="draggable-card" :style="{ animationDelay: `${animationDelay}s` }">
    <div class="card-image">
      <img :src="cardImage" :alt="card.title" />
      <div class="card-overlay">
        <div class="card-hover-content">
          <span class="view-more" @click.stop="handleViewDetail">자세히 보기</span>
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
          <span class="stat">👤 {{ creatorDisplayName }}</span>
          <span class="stat">👥 {{ memberCountDisplay }}</span>
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
          <button 
            class="members-btn" 
            @click="handleViewMembers"
            title="학생 목록 보기"
          >
            👥
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits, computed, ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth.js'
import defaultImage from '@/assets/class_default_image.png'
import apiClient from '@/utils/apiClient'

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

const emit = defineEmits(['enroll', 'createClass', 'deleteClass', 'joinClass', 'viewDetail', 'viewMembers'])

const cardImage = computed(() => {
  // card.image가 없거나 빈 문자열이거나 유효하지 않은 경우 기본 이미지 사용
  if (!props.card.image || props.card.image === '' || props.card.image === 'null' || props.card.image === 'undefined') {
    return defaultImage
  }
  return props.card.image
})

// 클래스 ID 해석
const resolvedClassId = computed(() => props.card.id || props.card.classId || props.card.classroomId || props.card._id || '')

// 서버에서 보완적으로 가져온 값들
const fetchedCreatorName = ref(null)
const fetchedMemberCount = ref(null)

// 반 생성자 이름 계산
const creatorDisplayName = computed(() => {
  if (fetchedCreatorName.value) return fetchedCreatorName.value
  const c = props.card || {}
  return (
    c.creatorName ||
    c.creator ||
    c.teacherName ||
    c.ownerName ||
    (c.owner && c.owner.name) ||
    c.writer ||
    (c.createdBy && c.createdBy.name) ||
    c.createdBy ||
    '알 수 없음'
  )
})

// 반 인원수 계산
const memberCount = computed(() => {
  if (typeof fetchedMemberCount.value === 'number') return fetchedMemberCount.value
  const c = props.card || {}
  const candidates = [
    c.memberCount,
    Array.isArray(c.members) ? c.members.length : undefined,
    c.studentCount,
    Array.isArray(c.students) ? c.students.length : undefined,
    c.enrolledCount,
    c.participantsCount,
  ]
  for (const v of candidates) {
    if (typeof v === 'number' && !Number.isNaN(v)) return v
  }
  return 0
})

const memberCountDisplay = computed(() => {
  const n = memberCount.value
  if (n >= 1000) {
    const k = n / 1000
    const fixed = Number.isInteger(k) ? k.toFixed(0) : k.toFixed(1)
    return `${fixed}k`
  }
  return String(n)
})

// 부족한 정보는 서버에서 보완 조회
onMounted(async () => {
  const authStore = useAuthStore()
  // 비로그인 상태이거나 토큰이 없으면 API 조회 스킵
  const hasToken = !!localStorage.getItem('token')
  if (!hasToken || !authStore.isLoggedIn) return
  try {
    if (creatorDisplayName.value !== '알 수 없음' && memberCount.value > 0) return
    const classId = resolvedClassId.value
    if (!classId) return
    console.log('ClassCard: resolvedClassId =', classId)
    const res = await apiClient.get(`/classroom/${classId}/members`)
    const list = Array.isArray(res.data) ? res.data : []
    // 인원수
    fetchedMemberCount.value = list.length
    // 생성자/선생님 추정: role 필드가 있으면 TEACHER 우선, 없으면 첫 번째의 nickname/name/email
    const teacher = list.find(m => String(m.role || '').toUpperCase().includes('TEACH')) || list[0]
    if (teacher) {
      fetchedCreatorName.value = teacher.nickname || teacher.name || teacher.username || teacher.email || '알 수 없음'
    }

    // 여전히 정보가 부족하면 상세 정보 조회 시도
    if ((!fetchedCreatorName.value || fetchedCreatorName.value === '알 수 없음') || (typeof fetchedMemberCount.value !== 'number' || fetchedMemberCount.value === 0)) {
      try {
        const detailRes = await apiClient.get(`/classroom/${classId}`)
        const detail = detailRes.data || {}
        // 생성자 후보
        const creatorCand = detail.creatorName || detail.ownerName || (detail.owner && detail.owner.name) || detail.teacherName || detail.teacher || detail.writer
        if (creatorCand && !fetchedCreatorName.value) fetchedCreatorName.value = creatorCand
        // 인원수 후보
        const countCand = detail.membersCount || detail.memberCount || (Array.isArray(detail.members) ? detail.members.length : undefined) || detail.studentsCount || detail.studentCount
        if (typeof countCand === 'number' && !Number.isNaN(countCand) && (typeof fetchedMemberCount.value !== 'number' || fetchedMemberCount.value === 0)) {
          fetchedMemberCount.value = countCand
        }
      } catch (e2) {
        console.warn('Failed to fetch classroom detail for card:', e2)
      }
    }
  } catch (e) {
    // 네트워크 실패 시 조용히 무시하고 기본 값 사용
    console.warn('Failed to fetch members for card:', e)
  }
})

const handleViewDetail = () => {
  console.log('🔍 ClassCard - 자세히 보기 클릭:', props.card)
  emit('viewDetail', props.card)
}

const handleButtonClick = () => {
  console.log('�� ClassCard - props.card:', props.card)
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

const handleViewMembers = () => {
  console.log('🔍 ClassCard - 학생 목록 보기 클릭:', props.card)
  const classId = props.card.id || props.card.classId || props.card.classroomId || props.card._id
  emit('viewMembers', {
    classId: classId,
    className: props.card.title
  })
}
</script>

<style scoped>
/* HomeView.css의 카드 관련 스타일을 그대로 사용하기 위해 scoped를 제거하고 
   부모 컴포넌트에서 CSS를 import하도록 설정 */
/* 학생 목록 버튼 스타일 */
.members-btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  margin-left: 8px;
}

.members-btn:hover {
  background: #2563eb;
  transform: translateY(-1px);
}

.members-btn:active {
  transform: translateY(0);
}
</style> 