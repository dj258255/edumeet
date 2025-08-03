<template>
  <div class="class-layout">
    <!-- 좌측: 반 생성 -->
    <div class="create-class-section">
      <h1>새 반 만들기</h1>
      <input v-model="className" placeholder="반 이름을 입력하세요" type="text" />
      <textarea v-model="classDescription" placeholder="반 설명"></textarea>
      <button @click="handleCreateClass" :disabled="isCreating">
        {{ isCreating ? '생성 중...' : '반 생성' }}
      </button>
      <p v-if="createError" class="error">{{ createError }}</p>
    </div>

    <!-- 우측: 내가 속한 클래스 목록 -->
    <div class="my-classes-section">
      <h1>내가 속한 클래스 목록</h1>

      <!-- 로딩 중 -->
      <p v-if="classStore.isLoading">클래스 목록을 불러오는 중...</p>

      <!-- 데이터 없음 -->
      <p v-else-if="!classStore.getMyClasses.length && !listError">
        아직 속한 클래스가 없습니다.
      </p>

      <!-- 클래스 카드 -->
      <div v-else class="class-card-list">
        <ClassCard
          v-for="(classItem, idx) in classStore.getMyClasses"
          :key="`${classItem.id}-${classItem.title}`"
          :card="classItem"
          :animationDelay="idx * 0.1"
          @enroll="goToVideoRoom"
        />
      </div>

      <p v-if="listError" class="error">{{ listError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useClassStore } from '@/stores/class'
import ClassCard from '../components/ClassCard.vue'
import '../styles/ClassRelated.css'

const className = ref('')
const classDescription = ref('')
const createError = ref('')
const listError = ref('')
const isCreating = ref(false)

const router = useRouter()
const classStore = useClassStore()

// 페이지 진입 시 목록 로드
onMounted(async () => {
  try {
    listError.value = ''
    await classStore.fetchMyClasses()
  } catch (error) {
    console.error('클래스 목록 로드 에러:', error)
    listError.value = '클래스 목록을 불러오는 데 실패했습니다.'
  }
})

// 반 생성
async function handleCreateClass() {
  if (!className.value.trim()) {
    alert('반 이름을 입력하세요')
    return
  }

  try {
    createError.value = ''
    isCreating.value = true

    const newClass = await classStore.createClass({
      name: className.value,
      description: classDescription.value,
    })

    // **목록 다시 갱신**
    await classStore.fetchMyClasses()

    alert(`반 "${newClass.title}" 이(가) 생성되었습니다!`)
    // 새 반 페이지로 이동
    router.push(`/class/${newClass.id}`)
  } catch (error) {
    console.error('클래스 생성 에러:', error)
    createError.value = '반 생성에 실패했습니다. 다시 시도해주세요.'
  } finally {
    isCreating.value = false
  }
}

// ClassCard의 enroll 이벤트로 호출됨
function goToVideoRoom(classId) {
  router.push(`/class/${classId}/video`)
}
</script>

<style scoped>
.error {
  color: #ff5a5a;
  font-weight: 600;
  text-align: center;
  margin-top: 0.5rem;
}
</style>
