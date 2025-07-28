<template>
  <div class="class-view">
    <h1>📚 {{ classInfo.name }}</h1>
    <p>{{ classInfo.description }}</p>

    <h2>🧑‍💻 화상채팅 방 목록</h2>
    <ul>
      <li v-for="room in roomList" :key="room.id">
        <router-link :to="`/class/${classId}/room/${room.id}`">{{ room.name }}</router-link>
      </li>
    </ul>

    <div class="create-room">
      <h3>➕ 새로운 화상채팅 방 만들기</h3>
      <input v-model="newRoomName" placeholder="방 이름 입력" />
      <input type="number" v-model.number="maxParticipants" min="2" max="10" placeholder="최대 인원 수" />

      <div class="video-preview">
        <video ref="previewVideo" autoplay playsinline muted></video>
      </div>

      <button @click="createRoom">방 생성</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const classId = route.params.classId;

const classInfo = ref({ name: '', description: '' });
const roomList = ref([]);
const newRoomName = ref('');
const maxParticipants = ref(4); // 기본 최대 인원 수
const previewVideo = ref(null);

onMounted(() => {
  loadClassInfo();
  loadRoomList();
  startCameraPreview();
});

async function loadClassInfo() {
  try {
    const response = await axios.get(`http://localhost:8080/api/v1/class/${classId}`);
    classInfo.value = response.data;
  } catch (error) {
    console.error('반 정보 불러오기 실패', error);
    classInfo.value = { name: '알 수 없는 반', description: '' };
  }
}

async function loadRoomList() {
  try {
    const response = await axios.get(`http://localhost:8080/api/v1/metting?classId=${classId}`);
    roomList.value = response.data; // 방 목록 배열이 와야 함
  } catch (error) {
    console.error('방 목록 불러오기 실패', error);
    roomList.value = [];
  }
}

async function startCameraPreview() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    previewVideo.value.srcObject = stream;
  } catch (err) {
    console.error('카메라 접근 실패:', err);
    alert('카메라 사용 권한이 필요합니다.');
  }
}

function createRoom() {
  if (!newRoomName.value.trim()) {
    alert('방 이름을 입력해주세요!');
    return;
  }

  const newRoomId = generateId();

  roomList.value.push({
    id: newRoomId,
    name: newRoomName.value,
    max: maxParticipants.value
  });

  newRoomName.value = '';
  router.push(`/class/${classId}/room/${newRoomId}`); // 생성 후 바로 입장
}

function generateId(length = 8) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  return Array.from({ length }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}
</script>

<style scoped>
.class-view {
  padding: 2rem;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  margin: 8px 0;
}
.create-room {
  margin-top: 2rem;
}
input {
  padding: 8px;
  margin: 0 8px 8px 0;
}
button {
  padding: 8px 12px;
}
.video-preview {
  margin: 1rem 0;
}
video {
  width: 320px;
  height: 240px;
  border: 1px solid #ccc;
  border-radius: 10px;
}
</style>
