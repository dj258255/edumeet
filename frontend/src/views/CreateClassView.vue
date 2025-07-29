<template>
  <div class="class-layout">
    <!-- 좌측: 반 생성하기 -->
    <div class="create-class-section">
      <h1>새 반 만들기</h1>
      <input v-model="className" placeholder="반 이름을 입력하세요" type="text" />
      <textarea v-model="classDescription" placeholder="반 설명"></textarea>
      <button @click="handleCreateClass" :disabled="isCreating">
        {{ isCreating ? '생성 중...' : '반 생성' }}
      </button>
      <!-- 방 생성 에러 -->
      <p v-if="createError" class="error">{{ createError }}</p>
    </div>

    <!-- 우측: 내가 속한 클래스 목록 -->
    <div class="my-classes-section">
      <h1>내가 속한 클래스 목록</h1>
      <p v-if="classStore.isLoading && !classStore.getMyClasses.length">
        클래스 목록을 불러오는 중...
      </p>
      <p v-else-if="!classStore.getMyClasses.length && !listError">
        아직 속한 클래스가 없습니다.
      </p>
      <div v-else class="class-card-list">
        <ClassCard
          v-for="classItem in classStore.getMyClasses"
          :key="classItem.id"
          :classInfo="classItem"
          @click="goToClassDetail(classItem.id)"
        />
      </div>
      <!-- 방 목록 에러 -->
      <p v-if="listError" class="error">{{ listError }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useClassStore } from '@/stores/class';
import ClassCard from "../components/ClassCard.vue";
import '../styles/ClassRelated.css';

const className = ref('');
const classDescription = ref('');
const createError = ref('');
const listError = ref('');
const isCreating = ref(false); // 반 생성 로딩 상태 추가

const router = useRouter();
const classStore = useClassStore();

// 페이지 로드 시 목록 불러오기
onMounted(async () => {
  try {
    listError.value = '';
    await classStore.fetchMyClasses();
  } catch (error) {
    console.error('클래스 목록 로드 에러:', error);
    listError.value = '클래스 목록을 불러오는 데 실패했습니다.';
  }
});

// 반 생성 함수
async function handleCreateClass() {
  if (!className.value.trim()) {
    alert('반 이름을 입력하세요');
    return;
  }

  try {
    createError.value = '';
    isCreating.value = true; // 생성 시작
    const newClass = await classStore.createClass({
      name: className.value,
      description: classDescription.value,
    });

    alert(`반 "${newClass.name}" 이(가) 생성되었습니다!`);
    router.push(`/class/${newClass.id}`);
  } catch (error) {
    console.error('클래스 생성 에러:', error);
    createError.value = '반 생성에 실패했습니다. 다시 시도해주세요.';
  } finally {
    isCreating.value = false; // 생성 종료
  }
}

function goToClassDetail(classId) {
  router.push(`/class/${classId}`);
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
