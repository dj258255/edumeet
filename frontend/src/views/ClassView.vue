<template>
    <div class="home">
      <h1>화상 수업</h1>
  
      <button @click="createRoom">새 반 만들기</button>
  
      <div class="join-form">
        <input v-model="roomCode" placeholder="반 코드 입력" />
        <button @click="joinRoom">입장</button>
      </div>
    </div>
  </template>
  
  <script setup>
  import { useRouter } from 'vue-router';
  import { ref } from 'vue';
  
  const router = useRouter();
  const roomCode = ref('');
  
  function generateRoomId(length = 10) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    return Array.from({ length }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  }
  
  function createRoom() {
    const roomId = generateRoomId();
    router.push(`/room/${roomId}`);
  }
  
  function joinRoom() {
    if (!roomCode.value) return alert('반 코드를 입력하세요!');
    router.push(`/room/${roomCode.value}`);
  }
  </script>
  
  <style scoped>
  .home {
    text-align: center;
    padding: 50px;
  }
  .join-form {
    margin-top: 30px;
  }
  input {
    padding: 8px;
    font-size: 16px;
  }
  button {
    margin-left: 10px;
    padding: 8px 16px;
    font-size: 16px;
  }
  </style>
  