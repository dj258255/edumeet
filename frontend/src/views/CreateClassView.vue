<template>
  <div class="create-class-view">
    <h1>새 반 만들기</h1>
    <input v-model="className" placeholder="반 이름을 입력하세요" type="text" />
    <textarea v-model="classDescription" placeholder="반 설명"></textarea>
    <button @click="handleCreateClass" :disabled="classStore.isLoading">
      {{ classStore.isLoading ? '생성 중...' : '반 생성' }}
    </button>
    <p v-if="classStore.hasError" style="color: red;">{{ classStore.error }}</p>
  </div>

  <div class="my-classes-section"> <h1>내가 속한 클래스 목록</h1>
    <p v-if="classStore.isLoading && !classStore.getMyClasses.length">클래스 목록을 불러오는 중...</p>
    <p v-else-if="!classStore.getMyClasses.length">아직 속한 클래스가 없습니다.</p>
    <div v-else class="class-card-list"> <ClassCard
        v-for="classItem in classStore.getMyClasses"
        :key="classItem.id"
        :classInfo="classItem"
        @click="goToClassDetail(classItem.id)"
      />
    </div>
    <p v-if="classStore.hasError && classStore.getMyClasses.length === 0" style="color: red;">
      {{ classStore.error }}
    </p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'; // onMounted 임포트
import { useRouter } from 'vue-router';
import { useClassStore } from '@/stores/class';
import ClassCard from "../components/ClassCard.vue";
import '../styles/ClassRelated.css';
import '../styles/HomeView.css'; // HomeView.css가 이 컴포넌트에 필요한 스타일을 포함한다면 유지

const className = ref('');
const classDescription = ref('');
const router = useRouter();
const classStore = useClassStore();
// classList ref는 이제 필요 없습니다. 스토어의 myClasses를 직접 사용합니다.

onMounted(async () => {
  // 컴포넌트 마운트 시 내가 속한 클래스 목록 불러오기
  await classStore.fetchMyClasses();
});

async function handleCreateClass() {
  if (!className.value.trim()) {
    alert('반 이름을 입력하세요');
    return;
  }

  try {
    const newClass = await classStore.createClass({
      name: className.value,
      description: classDescription.value,
    });

    alert(`반 "${newClass.name}" 이(가) 생성되었습니다!`);

    // 생성 후 해당 반 상세 페이지로 이동
    router.push(`/class/${newClass.id}`);
  } catch (error) {
    console.error('컴포넌트에서 잡은 클래스 생성 에러:', error);
  }
}

// ClassCard 클릭 시 해당 클래스 상세 페이지로 이동하는 함수
function goToClassDetail(classId) {
  router.push(`/class/${classId}`);
}

</script>

<style scoped>
/* HomeView.css에 포함되지 않은, 이 컴포넌트의 고유한 스타일이 있다면 여기에 추가하거나,
   class-related.css에 .my-classes-section 같은 이름으로 추가할 수 있습니다. */
.my-classes-section {
  margin-top: 3rem;
  padding: 2rem;
  background-color: #f0f0f0;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
}

.my-classes-section h1 {
  text-align: center;
  margin-bottom: 1.5rem;
  color: #333;
}

.class-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); /* 반응형 그리드 */
  gap: 20px;
  justify-content: center;
}
</style>