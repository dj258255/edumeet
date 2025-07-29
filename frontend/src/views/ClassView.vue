<template>
  <div class="class-view">
    <template v-if="classStore.getCurrentClassInfo">
      <h1>📚 {{ classStore.getCurrentClassInfo.name }}</h1>
      <p>{{ classStore.getCurrentClassInfo.description }}</p>
    </template>
    <template v-else-if="classStore.isLoading">
      <p>반 정보를 불러오는 중...</p>
    </template>
    <template v-else>
      <p>반 정보를 불러올 수 없습니다.</p>
    </template>


    <h2>🧑‍💻 화상채팅 방 목록</h2>
    <p v-if="classStore.isLoading && !classStore.getRoomList.length">방 목록 불러오는 중...</p>
    <ul v-else-if="classStore.getRoomList.length">
      <li v-for="room in classStore.getRoomList" :key="room.id">
        <router-link :to="`/class/${classId}/room/${room.id}`">{{ room.name }} (최대 인원: {{ room.maxParticipants || 'N/A' }})</router-link>
      </li>
    </ul>
    <p v-else>생성된 화상채팅 방이 없습니다.</p>

    <div class="create-room">
      <h3>➕ 새로운 화상채팅 방 만들기</h3>
      <input v-model="newRoomName" placeholder="방 이름 입력" />
      <input type="number" v-model.number="maxParticipants" min="2" max="10" placeholder="최대 인원 수" />

      <div class="video-preview">
        <video ref="previewVideo" autoplay playsinline muted></video>
      </div>

      <button @click="handleCreateRoom" :disabled="classStore.isLoading">
        {{ classStore.isLoading ? '생성 중...' : '방 생성' }}
      </button>
      <p v-if="classStore.hasError" style="color: red;">{{ classStore.error }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useClassStore } from '@/stores/class';
import '../styles/ClassRelated.css'; // **여기만 수정합니다.**

const route = useRoute();
const router = useRouter();
const classId = route.params.classId;

const classStore = useClassStore();

const newRoomName = ref('');
const maxParticipants = ref(4);
const previewVideo = ref(null);

onMounted(async () => {
  await classStore.fetchClassInfo(classId);
  await classStore.fetchRoomList(classId);
  startCameraPreview();
});

async function startCameraPreview() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    if (previewVideo.value) {
      previewVideo.value.srcObject = stream;
    }
  } catch (err) {
    console.error('카메라 접근 실패:', err);
    alert('카메라 사용 권한이 필요합니다. (권한 거부 시 미리보기가 표시되지 않습니다.)');
  }
}

async function handleCreateRoom() {
  if (!newRoomName.value.trim()) {
    alert('방 이름을 입력해주세요!');
    return;
  }

  try {
    const createdRoom = await classStore.createMeetingRoom(classId, {
      name: newRoomName.value,
      maxParticipants: maxParticipants.value
    });

    alert(`화상채팅 방 "${newRoomName.value}" 이(가) 생성되었습니다!`);
    newRoomName.value = '';
    
    router.push(`/class/${classId}/room/${createdRoom.id}`);
  } catch (error) {
    console.error('컴포넌트에서 방 생성 에러 처리:', error);
  }
}
</script>