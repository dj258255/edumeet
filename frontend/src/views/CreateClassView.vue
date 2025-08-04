<template>
  <div class="class-layout">
    <div class="create-class-section">
      <h1>새 반 만들기</h1>
      <input v-model="className" placeholder="반 이름을 입력하세요" type="text" />
      <textarea v-model="classDescription" placeholder="반 설명"></textarea>
      
      <div class="file-upload-container">
        <input 
          type="file" 
          id="classImageInput" 
          @change="handleImageUpload" 
          accept="image/*" 
          style="display: none;"
        />
        <label for="classImageInput" class="file-upload-label">파일 선택</label>
        <span class="file-name">{{ imageFileName || '선택된 파일 없음' }}</span>
      </div>

      <input v-model="classTags" placeholder="태그를 입력하세요 (쉼표로 구분)" type="text" />
      
      <button @click="handleCreateClass" :disabled="isCreating">
        {{ isCreating ? '생성 중...' : '반 생성' }}
      </button>
      <p v-if="createError" class="error">{{ createError }}</p>
    </div>

    <div class="my-classes-section">
      <h1>내가 속한 클래스 목록</h1>

      <p v-if="classStore.isLoading">클래스 목록을 불러오는 중...</p>

      <p v-else-if="!classStore.getMyClasses.length && !listError">
        아직 속한 클래스가 없습니다.
      </p>

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
const classImageFile = ref(null) // 파일 객체를 저장할 ref 추가
const imageFileName = ref('') // 선택된 파일 이름을 표시할 ref 추가
const classTags = ref('') // 태그를 저장할 ref 추가

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

// 파일이 선택되었을 때 실행되는 함수
function handleImageUpload(event) {
  const file = event.target.files[0];
  if (file) {
    classImageFile.value = file;
    imageFileName.value = file.name;
  } else {
    classImageFile.value = null;
    imageFileName.value = '';
  }
}

// 반 생성
async function handleCreateClass() {
  if (!className.value.trim()) {
    alert('반 이름을 입력하세요')
    return
  }

  try {
    createError.value = ''
    isCreating.value = true

    // FormData 객체를 사용하여 파일과 텍스트 데이터 모두 전송
    const formData = new FormData();
    formData.append('name', className.value);
    formData.append('description', classDescription.value);
    if (classImageFile.value) {
      formData.append('image', classImageFile.value);
    }
    // 쉼표로 구분된 태그 문자열을 배열로 변환 후 전송
    const tagsArray = classTags.value.split(',').map(tag => tag.trim()).filter(tag => tag.length > 0);
    formData.append('tags', tagsArray.join(','));

    const newClass = await classStore.createClass(formData);

    // 목록 다시 갱신
    await classStore.fetchMyClasses();

    alert(`반 "${newClass.title}" 이(가) 생성되었습니다!`);
    
    // // 새 반 페이지로 이동
    // router.push(`/class/${newClass.id}`);
  } catch (error) {
    console.error('클래스 생성 에러:', error);
    createError.value = '반 생성에 실패했습니다. 다시 시도해주세요.';
  } finally {
    isCreating.value = false;
  }
}

// ClassCard의 enroll 이벤트로 호출됨
function goToVideoRoom(classId) {
  router.push(`/class/${classId}/video`);
}
</script>

<style scoped>
/* 파일 업로드 버튼을 위한 CSS */
.file-upload-container {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  margin-bottom: 20px;
}

.file-upload-label {
  display: inline-block;
  padding: 8px 16px;
  background-color: #f0f0f0;
  border: 1px solid #ccc;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.file-upload-label:hover {
  background-color: #e0e0e0;
}

.file-name {
  font-size: 14px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 기존 CSS */
.error {
  color: #ff5a5a;
  font-weight: 600;
  text-align: center;
  margin-top: 0.5rem;
}
</style>