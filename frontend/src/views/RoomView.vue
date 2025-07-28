<template>
  <div class="room-view">
    <h2>🟢 {{ roomName }} ({{ roomId }})</h2>

    <div class="videos">
      <div class="video-box">
        <h4>👤 나</h4>
        <video ref="localVideo" autoplay playsinline muted></video>
      </div>

      <div
        class="video-box"
        v-for="(stream, id) in remoteStreams"
        :key="id"
      >
        <h4>👥 상대방 ({{ id }})</h4>
        <video
          :ref="el => setRemoteVideo(el, id)"
          autoplay
          playsinline
          :muted="false"
        ></video>
      </div>
    </div>

    <button @click="leaveRoom">🚪 나가기</button>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { io } from 'socket.io-client';

const route = useRoute();
const router = useRouter();

const classId = route.params.classId;
const roomId = route.params.roomId;

const roomName = ref('화상채팅방');
const localVideo = ref(null);
const localStream = ref(null);

// 여러 상대방 스트림을 id(key)별로 저장
const remoteStreams = reactive({});  // { socketId: MediaStream }

// 여러 PeerConnection을 id별로 저장
const peers = {};

const socket = io("http://localhost:3000");

onMounted(async () => {
  await setupMedia();
  setupSocket();
});

onBeforeUnmount(() => {
  leaveRoom();
});

async function setupMedia() {
  try {
    localStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    localVideo.value.srcObject = localStream.value;
  } catch (err) {
    alert("카메라/마이크 접근 권한이 필요합니다.");
    console.error(err);
  }
}

function setupSocket() {
  socket.emit('join-room', roomId);

  // 누군가 방에 들어왔다는 알림 받음 → offer 생성해서 보내기
  socket.on('user-joined', async (socketId) => {
    console.log('새 사용자 입장:', socketId);
    await createOffer(socketId);
  });

  // offer 받음
  socket.on('offer', async ({ from, offer }) => {
    console.log('offer 받음 from', from);
    await handleOffer(from, offer);
  });

  // answer 받음
  socket.on('answer', async ({ from, answer }) => {
    console.log('answer 받음 from', from);
    await handleAnswer(from, answer);
  });

  // ICE candidate 받음
  socket.on('ice-candidate', ({ from, candidate }) => {
    handleNewICECandidate(from, candidate);
  });
}

async function createOffer(socketId) {
  const pc = createPeerConnection(socketId);

  localStream.value.getTracks().forEach(track => pc.addTrack(track, localStream.value));

  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);

  socket.emit('offer', { to: socketId, offer });
}

async function handleOffer(from, offer) {
  const pc = createPeerConnection(from);

  localStream.value.getTracks().forEach(track => pc.addTrack(track, localStream.value));

  await pc.setRemoteDescription(new RTCSessionDescription(offer));
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);

  socket.emit('answer', { to: from, answer });
}

async function handleAnswer(from, answer) {
  const pc = peers[from];
  if (!pc) return console.warn('answer 수신했는데 peerConnection 없음:', from);

  await pc.setRemoteDescription(new RTCSessionDescription(answer));
}

function handleNewICECandidate(from, candidate) {
  const pc = peers[from];
  if (!pc) return console.warn('ICE candidate 수신했는데 peerConnection 없음:', from);

  pc.addIceCandidate(new RTCIceCandidate(candidate));
}

function createPeerConnection(socketId) {
  if (peers[socketId]) return peers[socketId];

  const pc = new RTCPeerConnection();

  pc.onicecandidate = (event) => {
    if (event.candidate) {
      socket.emit('ice-candidate', { to: socketId, candidate: event.candidate });
    }
  };

  pc.ontrack = (event) => {
    // 스트림을 remoteStreams에 저장
    remoteStreams[socketId] = event.streams[0];
  };

  peers[socketId] = pc;
  return pc;
}

// video ref에 스트림 바인딩
const remoteVideoRefs = reactive({});

function setRemoteVideo(el, id) {
  if (el) {
    el.srcObject = remoteStreams[id];
    remoteVideoRefs[id] = el;
  }
}

function leaveRoom() {
  socket.disconnect();

  // 모든 peerConnection 종료
  Object.values(peers).forEach(pc => pc.close());

  // 로컬 스트림 종료
  if (localStream.value) {
    localStream.value.getTracks().forEach(track => track.stop());
  }

  router.push(`/class/${classId}`);
}
</script>

<style scoped>
.room-view {
  text-align: center;
  padding: 2rem;
}
.videos {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 2rem;
  flex-wrap: wrap;
}
.video-box {
  border: 1px solid #ccc;
  padding: 1rem;
  border-radius: 10px;
}
video {
  width: 320px;
  height: 240px;
  background: black;
}
button {
  margin-top: 2rem;
  padding: 10px 20px;
  font-size: 16px;
}
</style>
