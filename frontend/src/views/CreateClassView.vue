<template>
  <div>
    <h1>새 반 만들기</h1>
    <input v-model="className" placeholder="반 이름을 입력하세요" />
    <textarea v-model="classDescription" placeholder="반 설명"></textarea>
    <button @click="createClass">반 생성</button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const className = ref('');
const classDescription = ref('');
const router = useRouter();

async function createClass() {
  if (!className.value.trim()) {
    alert('반 이름을 입력하세요');
    return;
  }

  try {
    // 서버에 반 생성 요청
    const response = await axios.post('http://localhost:8080/api/v1/class', {
      name: className.value,
      description: classDescription.value,
    });

    const newClassId = response.data.id;  // 서버가 생성해준 반 ID 받기
    alert(`반 "${className.value}" 이(가) 생성되었습니다!`);

    router.push(`/class/${newClassId}`); // 새로 만든 반 페이지로 이동
  } catch (error) {
    alert('반 생성 실패: ' + (error.response?.data?.message || error.message));
  }
}
</script>

<style scoped>
input, textarea {
  display: block;
  margin-bottom: 10px;
  width: 300px;
  padding: 8px;
}
button {
  padding: 8px 16px;
}
</style>
