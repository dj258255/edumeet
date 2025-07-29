<template>
  <div class="room-view">
    <h2>🟢 {{ roomName }} ({{ roomId }})</h2>

    <div class="videos">
      <div class="main-video-box">
        <h4>{{ isTeacher ? '🧑‍🏫 나 (선생님)' : '🧑‍🏫 선생님' }}</h4>
        <video ref="mainVideo" autoplay playsinline :muted="isTeacher"></video>
      </div>

      <div class="thumbnail-videos-container">
        <div class="video-box" v-if="!isTeacher">
          <h4>👤 나 (학생)</h4>
          <video ref="localVideo" autoplay playsinline muted></video>
        </div>

        <div
          class="video-box"
          v-for="(stream, id) in remoteStreams"
          :key="id"
          :class="{ 'hidden-teacher-stream': !isTeacher && teacherSocketId === id }"
        >
          <h4>
            <template v-if="teacherSocketId === id">🧑‍🏫 선생님</template>
            <template v-else>👥 학생 ({{ id.substring(0, 8) }}...)</template>
          </h4>
          <video
            :ref="el => setRemoteVideo(el, id)"
            autoplay
            playsinline
            :muted="false"
          ></video>
        </div>
      </div>
    </div>

    <button @click="leaveRoom">🚪 나가기</button>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { io } from 'socket.io-client';
import { useAuthStore } from '@/stores/auth'; // 사용자 역할 정보를 가져오기 위함 (가정)

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore(); // Auth 스토어 사용 (사용자 역할 정보)

const classId = route.params.classId;
const roomId = route.params.roomId;

const roomName = ref('화상채팅방');
const localVideo = ref(null); // 학생일 경우 내 비디오 (썸네일)
const mainVideo = ref(null); // 메인 비디오 (선생님 스트림)
const localStream = ref(null);

// 역할 정보 (실제로는 authStore.currentUser.role 등으로 가져와야 함)
const isTeacher = ref(false); // ★★★ 여기에 현재 사용자의 역할 정보를 설정해야 합니다 ★★★
const teacherSocketId = ref(null); // 선생님의 소켓 ID를 저장하여 메인 스트림 구분

// 여러 상대방 스트림을 id(key)별로 저장
const remoteStreams = reactive({}); // { socketId: MediaStream }

// 여러 PeerConnection을 id별로 저장
const peers = {}; // { socketId: RTCPeerConnection }

const socket = io("http://localhost:3000");

// 컴포넌트 마운트 시 초기화
onMounted(async () => {
  // 실제 앱에서는 로그인한 사용자의 역할을 여기에서 가져와야 합니다.
  // 예시: isTeacher.value = authStore.currentUser?.role === 'TEACHER';
  // 현재는 임시로 true/false로 설정하여 테스트 가능
  isTeacher.value = true; // ★★★ 테스트용: true면 선생님, false면 학생 ★★★
  console.log(`현재 사용자 역할: ${isTeacher.value ? '선생님' : '학생'}`);

  await setupMedia(); // 미디어(카메라/마이크) 설정
  setupSocket();      // Socket.IO 통신 설정
});

// 컴포넌트 언마운트 전에 방 나가기 처리
onBeforeUnmount(() => {
  leaveRoom();
});

// mainVideo ref에 스트림이 설정될 때 즉시 업데이트
watch(mainVideo, (newVal) => {
  if (newVal && (isTeacher.value ? localStream.value : remoteStreams[teacherSocketId.value])) {
    newVal.srcObject = isTeacher.value ? localStream.value : remoteStreams[teacherSocketId.value];
  }
});

// localVideo ref (학생일 경우)에 스트림이 설정될 때 즉시 업데이트
watch(localVideo, (newVal) => {
  if (newVal && !isTeacher.value && localStream.value) {
    newVal.srcObject = localStream.value;
  }
});


// 로컬 미디어 스트림 (카메라/마이크) 설정
async function setupMedia() {
  try {
    localStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true }); 
    // 선생님인 경우, 내 로컬 스트림을 메인 비디오에 바로 할당
    if (isTeacher.value) {
      mainVideo.value.srcObject = localStream.value;
    } 
    // 학생인 경우, 내 로컬 스트림은 localVideo (썸네일)에 할당
    else {
      localVideo.value.srcObject = localStream.value;
    }
  } catch (err) {
    alert("카메라 또는 마이크 접근 권한이 필요합니다. 권한을 허용해주세요.");
    console.error("미디어 스트림 설정 실패:", err);
    router.push(`/class/${classId}`); 
  }
}

// Socket.IO 이벤트 리스너 설정
function setupSocket() {
  socket.emit('join-room', roomId);

  socket.on('room-users', async (existingUserIds) => {
    console.log('방에 이미 있는 사용자들:', existingUserIds);
    
    // 선생님 소켓 ID를 찾습니다. (가장 먼저 들어온 사람을 선생님으로 가정)
    // 실제로는 서버에서 역할을 함께 보내주는 것이 좋습니다.
    if (existingUserIds.length > 0 && !teacherSocketId.value) {
        teacherSocketId.value = existingUserIds[0]; 
        console.log(`선생님 소켓 ID 초기 설정: ${teacherSocketId.value}`);
    }

    for (const userId of existingUserIds) {
      if (userId === socket.id) continue; // 자기 자신 제외

      // ★ 선생님 역할: 모든 기존 학생들에게 Offer를 보냄
      if (isTeacher.value) {
        console.log(`(선생님) 기존 학생 ${userId}에게 offer 생성 및 전송`);
        await createOffer(userId);
      } 
      // ★ 학생 역할: 모든 기존 사용자(선생님 포함)에게 Answer를 보낼 준비
      // 또는 선생님의 Offer를 기다립니다.
      // 여기서는 학생이 offer를 보내지 않고, 선생님의 offer를 기다리는 방식으로 구현
    }
  });

  socket.on('user-joined', async (newSocketId) => {
    console.log('새 사용자 입장:', newSocketId);
    if (newSocketId === socket.id) return; // 자기 자신 제외

    // 선생님 소켓 ID가 아직 설정되지 않았다면, 가장 먼저 들어온 사람을 선생님으로 가정
    if (!teacherSocketId.value) {
        teacherSocketId.value = newSocketId;
        console.log(`선생님 소켓 ID 설정: ${teacherSocketId.value}`);
    }

    // ★ 선생님 역할: 새로 들어온 학생에게 Offer를 보냄
    if (isTeacher.value) {
      console.log(`(선생님) 새 학생 ${newSocketId}에게 offer 생성 및 전송`);
      await createOffer(newSocketId);
    } 
    // ★ 학생 역할: 새로 들어온 사람이 선생님인지 확인 후 Offer를 기다림 (또는 보냄)
    // 여기서는 offer를 기다림
  });

  socket.on('offer', async ({ from, offer }) => {
    console.log(`Offer 받음 from ${from}`);
    // 만약 학생이고, offer가 선생님으로부터 온 것이라면, 메인 비디오에 할당
    if (!isTeacher.value && teacherSocketId.value === from) {
        // 선생님의 스트림을 메인 비디오로 설정
        // 이 로직은 ontrack에서 처리되므로 여기서는 단순히 offer 처리만 합니다.
    }
    await handleOffer(from, offer);
  });

  socket.on('answer', async ({ from, answer }) => {
    console.log(`Answer 받음 from ${from}`);
    await handleAnswer(from, answer);
  });

  socket.on('ice-candidate', ({ from, candidate }) => {
    handleNewICECandidate(from, candidate);
  });

  socket.on('user-left', (leavingSocketId) => {
    console.log('사용자 퇴장:', leavingSocketId);
    if (peers[leavingSocketId]) {
      peers[leavingSocketId].close();
      delete peers[leavingSocketId];
    }
    if (remoteStreams[leavingSocketId]) {
      delete remoteStreams[leavingSocketId]; 
    }
    // 만약 퇴장한 유저가 선생님이었다면 teacherSocketId 초기화 또는 다음 선생님 지정 로직 필요
    if (teacherSocketId.value === leavingSocketId) {
        teacherSocketId.value = null; // 선생님 퇴장 처리
        console.warn('선생님 스트림이 종료되었습니다.');
        // 필요에 따라 다음 선생님을 지정하거나, 방을 닫는 로직 추가
    }
  });

  socket.on('disconnect', (reason) => {
      console.log('Socket disconnected:', reason);
  });

  socket.on('connect_error', (err) => {
      console.error('Socket connection error:', err);
  });
}

function createPeerConnection(socketId) {
  if (peers[socketId]) {
      console.log(`PeerConnection for ${socketId} 이미 존재, 재사용.`);
      return peers[socketId];
  }

  console.log(`PeerConnection 생성 for ${socketId}`);
  const pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
  });

  // 자신의 로컬 스트림 트랙을 PeerConnection에 추가
  if (localStream.value) {
    localStream.value.getTracks().forEach(track => {
      pc.addTrack(track, localStream.value);
    });
    console.log(`자신의 로컬 스트림 트랙을 ${socketId} PeerConnection에 추가.`);
  } else {
    console.warn(`Local stream not available when creating PeerConnection for ${socketId}.`);
  }

  pc.onicecandidate = (event) => {
    if (event.candidate) {
      socket.emit('ice-candidate', { to: socketId, candidate: event.candidate });
    }
  };

  pc.ontrack = (event) => {
    console.log(`원격 스트림 트랙 수신 from ${socketId}:`, event.streams[0]);
    // 원격 스트림을 remoteStreams에 저장
    remoteStreams[socketId] = event.streams[0];

    // ★ 학생 역할: 선생님의 스트림을 받으면 메인 비디오에 할당
    if (!isTeacher.value && teacherSocketId.value === socketId) {
        if (mainVideo.value) {
            mainVideo.value.srcObject = event.streams[0];
            console.log(`선생님 스트림 (${socketId})을 메인 비디오에 할당.`);
        } else {
            // mainVideo.value가 아직 null일 경우를 대비 (watch로 처리)
            console.warn('mainVideo ref is null when trying to assign teacher stream.');
        }
    }
  };

  pc.onconnectionstatechange = () => {
    console.log(`PeerConnection with ${socketId} connection state: ${pc.connectionState}`);
    if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
        console.warn(`Connection with ${socketId} disconnected or failed.`);
        if (peers[socketId]) {
            peers[socketId].close();
            delete peers[socketId];
        }
        if (remoteStreams[socketId]) {
            delete remoteStreams[socketId];
        }
    }
  };

  peers[socketId] = pc;
  return pc;
}

async function createOffer(socketId) {
  const pc = createPeerConnection(socketId);

  try {
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    console.log(`Offer 전송 to ${socketId}:`, offer);
    socket.emit('offer', { to: socketId, offer });
  } catch (error) {
    console.error(`Offer 생성/전송 실패 for ${socketId}:`, error);
  }
}

async function handleOffer(from, offer) {
  const pc = createPeerConnection(from);

  try {
    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    console.log(`Answer 전송 to ${from}:`, answer);
    socket.emit('answer', { to: from, answer });
  } catch (error) {
    console.error(`Offer 처리 실패 from ${from}:`, error);
  }
}

async function handleAnswer(from, answer) {
  const pc = peers[from];
  if (!pc) {
    console.warn(`Answer 수신했는데 해당하는 peerConnection이 없음 for ${from}.`);
    return;
  }
  try {
    await pc.setRemoteDescription(new RTCSessionDescription(answer));
    console.log(`Answer 설정 완료 from ${from}.`);
  } catch (error) {
    console.error(`Answer 처리 실패 from ${from}:`, error);
  }
}

function handleNewICECandidate(from, candidate) {
  const pc = peers[from];
  if (!pc) {
    console.warn(`ICE candidate 수신했는데 해당하는 peerConnection이 없음 for ${from}.`);
    return;
  }
  try {
    if (candidate) { 
      pc.addIceCandidate(new RTCIceCandidate(candidate));
    }
  } catch (error) {
    console.error(`ICE candidate 추가 실패 from ${from}:`, error);
  }
}

// 원격 비디오 요소에 스트림 바인딩
const setRemoteVideo = (el, id) => {
  if (el && remoteStreams[id] && el.srcObject !== remoteStreams[id]) {
    el.srcObject = remoteStreams[id];
    console.log(`비디오 요소에 스트림 바인딩 완료 for ${id}.`);
  }
};

function leaveRoom() {
  socket.disconnect();
  console.log('Socket.IO 연결 끊김.');

  for (const socketId in peers) {
    if (peers[socketId]) {
      peers[socketId].close();
      delete peers[socketId];
      console.log(`PeerConnection for ${socketId} 종료.`);
    }
  }
  for (const key in remoteStreams) {
    delete remoteStreams[key];
  }
  console.log('모든 원격 스트림 및 PeerConnection 정리 완료.');

  if (localStream.value) {
    localStream.value.getTracks().forEach(track => track.stop());
    localStream.value = null; 
    console.log('로컬 미디어 스트림 종료.');
  }
  if (localVideo.value) {
      localVideo.value.srcObject = null;
  }
  if (mainVideo.value) {
      mainVideo.value.srcObject = null;
  }
  teacherSocketId.value = null; // 선생님 소켓 ID 초기화

  console.log('방에서 나갑니다.');
  router.push(`/class/${classId}`);
}
</script>

<style scoped>
.room-view {
  text-align: center;
  padding: 2rem;
  background-color: #f0f2f5;
  min-height: calc(100vh - 80px); 
  display: flex;
  flex-direction: column;
  align-items: center;
}
h2 {
  color: #2c3e50;
  margin-bottom: 2rem;
  font-size: 2.5rem;
}
.videos {
  display: flex;
  flex-wrap: wrap; 
  justify-content: center; /* 가운데 정렬 */
  gap: 20px;
  width: 100%;
  max-width: 1200px; 
}

/* 메인 비디오 (선생님) 스타일 */
.main-video-box {
  background-color: #ffffff;
  border: 2px solid #3498db; /* 선생님 스트림 강조 */
  padding: 1.5rem;
  border-radius: 15px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px; /* 아래쪽 썸네일과의 간격 */
  width: 100%; /* 너비 최대로 확장 */
  max-width: 800px; /* 큰 화면에서의 최대 너비 */
  box-sizing: border-box;
}
.main-video-box h4 {
  margin-bottom: 1.2rem;
  color: #2980b9;
  font-size: 1.8rem;
}
.main-video-box video {
  width: 100%;
  height: auto;
  min-height: 450px; /* 메인 비디오 최소 높이 */
  background: #2c3e50;
  border-radius: 10px;
  object-fit: cover;
  border: 3px solid #5faee3;
}

/* 썸네일 비디오들 컨테이너 */
.thumbnail-videos-container {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 15px;
  width: 100%;
  max-width: 1200px; /* videos와 동일하게 설정 */
}

/* 일반 비디오 (썸네일) 스타일 */
.video-box {
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
  padding: 1rem;
  border-radius: 12px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-basis: calc(33.333% - 20px); /* 한 줄에 3개 (gap 고려) */
  max-width: calc(33.333% - 20px); /* 최대 너비 */
  box-sizing: border-box;
}
.video-box h4 {
  margin-bottom: 0.8rem;
  color: #34495e;
  font-size: 1rem;
}
.video-box video {
  width: 100%; 
  height: auto; 
  min-height: 180px; /* 썸네일 비디오 최소 높이 */
  background: #2c3e50; 
  border-radius: 8px; 
  object-fit: cover; 
  border: 2px solid #a4b0be; 
}

/* 학생일 때 선생님의 썸네일 비디오 숨기기 (선생님 스트림이 메인에 표시되므로) */
.hidden-teacher-stream {
    display: none;
}

button {
  margin-top: 3rem;
  padding: 15px 30px;
  font-size: 1.2rem;
  background-color: #e74c3c; 
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s ease, transform 0.2s ease;
  font-weight: bold;
  letter-spacing: 0.5px;
}
button:hover {
  background-color: #c0392b;
  transform: translateY(-2px);
}
button:active {
  transform: translateY(0);
}

@media (max-width: 1024px) {
    .video-box {
        flex-basis: calc(50% - 15px); /* 태블릿에서는 한 줄에 2개 */
        max-width: calc(50% - 15px);
    }
}

@media (max-width: 768px) {
  .main-video-box {
    padding: 1rem;
    max-width: 100%;
  }
  .main-video-box video {
    min-height: 300px;
  }
  .main-video-box h4 {
    font-size: 1.5rem;
  }
  .video-box {
    flex-basis: 100%; /* 모바일에서는 한 줄에 1개 */
    max-width: 100%;
  }
}
</style>