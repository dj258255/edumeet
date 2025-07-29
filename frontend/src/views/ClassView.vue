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
import { useClassStore } from '@/stores/class'; // class 스토어 임포트

const route = useRoute();
const router = useRouter();
const classId = route.params.classId;

// 스토어 인스턴스 가져오기
const classStore = useClassStore();

const newRoomName = ref('');
const maxParticipants = ref(4); // 기본 최대 인원 수
const previewVideo = ref(null);

onMounted(async () => {
  // 스토어 액션 호출하여 데이터 로드
  await classStore.fetchClassInfo(classId);
  await classStore.fetchRoomList(classId);
  startCameraPreview();
});

async function startCameraPreview() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    if (previewVideo.value) { // ref가 마운트된 후에만 srcObject 설정
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
    // 스토어 액션 호출
    const createdRoom = await classStore.createMeetingRoom(classId, {
      name: newRoomName.value,
      maxParticipants: maxParticipants.value // 서버 API 필드명에 맞게 조정하세요.
    });

    alert(`화상채팅 방 "${newRoomName.value}" 이(가) 생성되었습니다!`);
    newRoomName.value = ''; // 입력 필드 초기화
    
    // 생성된 방으로 바로 이동
    router.push(`/class/${classId}/room/${createdRoom.id}`); 
  } catch (error) {
    // 스토어에서 이미 에러를 처리했으므로, 여기서는 추가 로깅만 합니다.
    console.error('컴포넌트에서 방 생성 에러 처리:', error);
  }
}

// generateId 함수는 서버에서 ID를 받으므로 이제 필요 없습니다.
// function generateId(length = 8) {
//   const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
//   return Array.from({ length }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
// }
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