<template>
  <div class="draggable-card" :class="viewTypeClass" :style="{ animationDelay: `${animationDelay}s` }">
    <div class="card-image">
      <img :src="cardImage" :alt="card.title" />
      <div class="card-overlay">
        <div class="card-hover-content">
          <span class="view-more" @click.stop="handleViewDetail">자세히 보기</span>
        </div>
      </div>
      <div class="card-badge">{{ firstTag }}</div>
    </div>
         <div class="card-content">
       <div class="content-main">
         <div class="content-left">
           <h3 class="card-title">{{ card.title }}</h3>
           <p class="card-description">{{ card.description }}</p>
           <div class="card-tags">
             <span class="tag" v-for="tag in safeTags" :key="tag">{{ tag }}</span>
           </div>
         </div>
         <div class="content-right">
           <div class="card-stats">
             <span class="stat">👤 {{ creatorDisplayName }}</span>
             <span class="stat">👥 {{ memberCountDisplay }}</span>
           </div>
         </div>
       </div>
               <div class="card-actions">
          <div class="left-actions">
            <button 
              class="enroll-btn" 
              :class="{ 'create-btn': isMyCreatedClass }"
              @click="handleButtonClick"
            >
              {{ isMyCreatedClass ? '수업 생성' : '입장하기' }}
            </button>
          </div>
          <div class="right-actions">
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
  },
  viewType: {
    type: String,
    default: 'default', // 'default', 'create-class', 'home'
    validator: (value) => ['default', 'create-class', 'home'].includes(value)
  }
})

const emit = defineEmits(['enroll', 'createClass', 'deleteClass', 'joinClass', 'viewDetail', 'viewMembers'])

// 안전한 태그 배열 계산
const safeTags = computed(() => {
  return Array.isArray(props.card.tags) ? props.card.tags : []
})

// 첫 번째 태그 (안전하게)
const firstTag = computed(() => {
  return safeTags.value.length > 0 ? safeTags.value[0] : '새 반'
})

// viewType에 따른 CSS 클래스 계산
const viewTypeClass = computed(() => {
  switch (props.viewType) {
    case 'create-class':
      return 'create-class-view'
    case 'home':
      return 'home-view'
    default:
      return ''
  }
})

const cardImage = computed(() => {
  console.log('🔍 ClassCard - cardImage computed 호출됨')
  console.log('🔍 ClassCard - card 데이터:', props.card)
  console.log('🔍 ClassCard - thumbnailUrl:', props.card.thumbnailUrl)
  console.log('🔍 ClassCard - image:', props.card.image)
  
  // API 응답에서 thumbnailUrl 우선 확인
  if (props.card.thumbnailUrl && props.card.thumbnailUrl !== '' && props.card.thumbnailUrl !== 'null' && props.card.thumbnailUrl !== 'undefined' && props.card.thumbnailUrl !== null) {
    console.log('🔍 ClassCard - thumbnailUrl 사용:', props.card.thumbnailUrl)
    return props.card.thumbnailUrl
  }
  
  // 기존 image 필드 확인
  if (props.card.image && props.card.image !== '' && props.card.image !== 'null' && props.card.image !== 'undefined' && props.card.image !== null) {
    console.log('🔍 ClassCard - 기존 이미지 사용:', props.card.image)
    return props.card.image
  }
  
  // 기본 이미지 사용
  console.log('🔍 ClassCard - 기본 이미지 사용')
  return defaultImage
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
.draggable-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition: var(--transition-normal);
  cursor: pointer;
  position: relative;
  border: 1px solid var(--border-color);
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
  transform: translateY(20px);
  /* 기본 크기 - HomeView용 */
  width: 600px;
  min-height: 420px;
}

.draggable-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(34, 122, 83, 0.15);
  border-color: var(--brand-main);
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
  background: var(--bg-tertiary);
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-normal);
}

.draggable-card:hover .card-image img {
  transform: scale(1.05);
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-overlay);
  opacity: 0;
  transition: var(--transition-normal);
  display: flex;
  align-items: center;
  justify-content: center;
}

.draggable-card:hover .card-overlay {
  opacity: 1;
}

.card-hover-content {
  text-align: center;
}

.view-more {
  color: var(--text-inverse);
  font-weight: 600;
  font-size: var(--font-size-sm);
  padding: 10px 20px;
  border: 2px solid var(--text-inverse);
  border-radius: var(--radius-2xl);
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  transition: var(--transition-normal);
  cursor: pointer;
}

.view-more:hover {
  background: var(--text-inverse);
  color: var(--brand-main);
  transform: scale(1.05);
}

.card-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  background: var(--brand-main);
  color: var(--text-inverse);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-content {
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  height: 220px;
  overflow: hidden;
}

.content-main {
  display: flex;
  gap: var(--spacing-md);
  flex: 1;
  min-height: 0;
}

.content-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.content-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: flex-end;
  min-width: 120px;
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 6px 0;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex-shrink: 0;
}

.card-description {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.3;
  margin: 0 0 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex-shrink: 0;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.tag {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  padding: 3px 6px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 500;
  border: 1px solid var(--border-color);
  transition: var(--transition-fast);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag:hover {
  background: var(--brand-main);
  color: var(--text-inverse);
}

.card-stats {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.stat {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
  text-align: right;
}

.card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-md);
  flex-shrink: 0;
  min-height: 50px;
}

.left-actions {
  display: flex;
  align-items: center;
}

.right-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.enroll-btn {
  background: rgba(34, 197, 94, 0.9);
  color: #ffffff !important;
  border: 2px solid rgba(34, 197, 94, 0.3);
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.2);
}

.enroll-btn:hover {
  background: rgba(34, 197, 94, 1);
  border-color: rgba(34, 197, 94, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(34, 197, 94, 0.3);
}

.enroll-btn:active {
  transform: translateY(0);
}

.enroll-btn.create-btn {
  background: rgba(22, 163, 74, 0.9);
  color: #ffffff !important;
  border-color: rgba(22, 163, 74, 0.3);
}

.enroll-btn.create-btn:hover {
  background: rgba(22, 163, 74, 1);
  border-color: rgba(22, 163, 74, 0.5);
}

.delete-btn {
  background: rgba(239, 68, 68, 0.9);
  color: #ffffff !important;
  border: 2px solid rgba(239, 68, 68, 0.3);
  padding: 6px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--font-size-xs);
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.2);
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 1);
  border-color: rgba(239, 68, 68, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.3);
}

.delete-btn:active {
  transform: translateY(0);
}

.members-btn {
  background: rgba(34, 197, 94, 0.8);
  color: #ffffff !important;
  border: 2px solid rgba(34, 197, 94, 0.3);
  padding: 6px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--font-size-xs);
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.2);
}

.members-btn:hover {
  background: rgba(34, 197, 94, 1);
  border-color: rgba(34, 197, 94, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(34, 197, 94, 0.3);
}

.members-btn:active {
  transform: translateY(0);
}

/* CreateClassView에서 더 큰 카드 스타일 */
.draggable-card.create-class-view {
  width: 100%;
  min-height: 600px;
  max-width: 100%;
  margin: 0;
  border-radius: 0;
}

.draggable-card.create-class-view .card-image {
  height: 250px;
}

.draggable-card.create-class-view .card-content {
  height: 350px;
  padding: var(--spacing-xl);
  border-radius: 0;
}

.draggable-card.create-class-view .card-title {
  font-size: var(--font-size-lg);
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.draggable-card.create-class-view .card-description {
  font-size: var(--font-size-base);
  margin: 0 0 12px 0;
  line-height: 1.4;
}

.draggable-card.create-class-view .card-tags {
  gap: 6px;
  margin-bottom: 12px;
}

.draggable-card.create-class-view .enroll-btn {
  padding: 12px 24px;
  font-size: var(--font-size-base);
}

.draggable-card.create-class-view .delete-btn {
  padding: 10px 16px;
  font-size: var(--font-size-sm);
}

.draggable-card.create-class-view .members-btn {
  padding: 10px 16px;
  font-size: var(--font-size-sm);
}

.draggable-card.create-class-view .content-main {
  gap: var(--spacing-lg);
}

.draggable-card.create-class-view .content-right {
  min-width: 140px;
}

.draggable-card.create-class-view .card-actions {
  min-height: 60px;
}

.draggable-card.create-class-view .right-actions {
  gap: 12px;
}

/* HomeView에서 더 작은 카드 스타일 */
.draggable-card.home-view {
  width: 300px;
  min-height: 400px;
}

.draggable-card.home-view .card-image {
  height: 180px;
}

.draggable-card.home-view .card-content {
  height: 220px;
  padding: var(--spacing-lg);
}

.draggable-card.home-view .card-title {
  font-size: var(--font-size-base);
  margin: 0 0 6px 0;
  line-height: 1.3;
}

.draggable-card.home-view .card-description {
  font-size: var(--font-size-sm);
  margin: 0 0 8px 0;
  line-height: 1.3;
}

.draggable-card.home-view .card-tags {
  gap: 4px;
  margin-bottom: 8px;
}

.draggable-card.home-view .enroll-btn {
  padding: 10px 20px;
  font-size: var(--font-size-sm);
}

.draggable-card.home-view .delete-btn {
  padding: 8px 14px;
  font-size: var(--font-size-xs);
}

.draggable-card.home-view .members-btn {
  padding: 8px 14px;
  font-size: var(--font-size-xs);
}

.draggable-card.home-view .content-main {
  gap: var(--spacing-sm);
}

.draggable-card.home-view .content-right {
  min-width: 110px;
}

.draggable-card.home-view .card-actions {
  min-height: 45px;
}

.draggable-card.home-view .right-actions {
  gap: 6px;
}

/* 다크 모드 대응 */
@media (prefers-color-scheme: dark) {
  .draggable-card {
    background: var(--bg-card);
    border-color: var(--border-dark);
  }
  
  .card-title {
    color: var(--text-primary);
  }
  
  .card-description {
    color: var(--text-secondary);
  }
  
  .tag {
    background: var(--bg-tertiary);
    color: var(--text-secondary);
    border-color: var(--border-dark);
  }
  
  .tag:hover {
    background: var(--brand-main);
    color: var(--text-inverse);
  }
  
  .stat {
    color: var(--text-tertiary);
  }
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .draggable-card {
    width: 100%;
    max-width: 300px;
  }
  
  .content-main {
    flex-direction: column;
    gap: var(--spacing-sm);
  }
  
  .content-right {
    align-items: flex-start;
    min-width: auto;
  }
  
  .card-stats {
    align-items: flex-start;
  }
  
  .stat {
    text-align: left;
  }
  
  .card-actions {
    flex-direction: column;
    gap: var(--spacing-sm);
  }
  
  .left-actions,
  .right-actions {
    justify-content: center;
    width: 100%;
  }
  
  .enroll-btn {
    flex: 1;
    max-width: 200px;
  }
}
</style> 