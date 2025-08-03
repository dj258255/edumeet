<script setup lang="ts">
import {
  LocalVideoTrack,
  Room,
  RoomEvent,
} from 'livekit-client';
import { onMounted, onUnmounted, ref, shallowRef, type Ref } from 'vue';
import { useRoute } from 'vue-router';
import VideoComponent from '@/components/VideoComponent.vue';
import AudioComponent from '@/components/AudioComponent.vue';

const route = useRoute();
const classId = route.params.classId as string;

const room = shallowRef<Room>();
const localTrack = ref<LocalVideoTrack>();
const remoteTracksMap: Ref<Map<string, any>> = ref(new Map());

const participantName = ref('Participant' + Math.floor(Math.random() * 100));
const roomName = ref('');
const isJoining = ref(false);

const activeRooms = ref<Array<{ name: string; participants: number }>>([]);

// 메인화면 관리
const mainTrack = ref<any>(null);
const mainIdentity = ref<string>('');

// 카메라/마이크 상태
const isCameraOn = ref(true);
const isMicOn = ref(true);

let APPLICATION_SERVER_URL = '';
let LIVEKIT_URL = '';

function configureUrls() {
  APPLICATION_SERVER_URL =
    window.location.hostname === 'localhost'
      ? 'http://localhost:6080/'
      : 'https://' + window.location.hostname + ':6443/';

  LIVEKIT_URL =
    window.location.hostname === 'localhost'
      ? 'ws://localhost:7880/'
      : 'wss://' + window.location.hostname + ':7443/';
}
configureUrls();

onMounted(() => {
  fetchActiveRooms();
});

function fetchActiveRooms() {
  activeRooms.value = [
    { name: `${classId}-main`, participants: 3 },
    { name: `${classId}-study`, participants: 5 }
  ];
}

async function joinRoom(targetRoom?: string) {
  isJoining.value = true;
  const target = targetRoom || roomName.value;
  if (!target) {
    isJoining.value = false;
    return;
  }

  room.value = new Room();

  room.value.on(RoomEvent.TrackSubscribed, (_track, publication, participant) => {
    remoteTracksMap.value.set(publication.trackSid, {
      trackPublication: publication,
      participantIdentity: participant.identity
    });
  });

  room.value.on(RoomEvent.TrackUnsubscribed, (_track, publication) => {
    remoteTracksMap.value.delete(publication.trackSid);
  });

  try {
    const token = await getToken(target, participantName.value);
    await room.value.connect(LIVEKIT_URL, token);
    await room.value.localParticipant.enableCameraAndMicrophone();

    const firstVideoPub = room.value.localParticipant.videoTrackPublications.values().next().value;
    if (firstVideoPub) {
      localTrack.value = firstVideoPub.videoTrack;
      mainTrack.value = firstVideoPub.videoTrack;
      mainIdentity.value = participantName.value;
    }

    roomName.value = target;
  } catch (error: any) {
    console.error('영상방 연결 실패:', error.message);
    await leaveRoom();
  } finally {
    isJoining.value = false;
  }

  window.addEventListener('beforeunload', leaveRoom);
}

async function leaveRoom() {
  if (room.value) {
    await room.value.disconnect();
  }
  room.value = undefined;
  localTrack.value = undefined;
  mainTrack.value = null;
  remoteTracksMap.value.clear();
}

onUnmounted(leaveRoom);

async function getToken(roomName: string, participantName: string) {
  const response = await fetch(APPLICATION_SERVER_URL + 'token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      roomName,
      participantName
    })
  });
  const data = await response.json();
  return data.token;
}

// 메인화면 교체
function setMainTrack(track: any, identity: string) {
  mainTrack.value = track;
  mainIdentity.value = identity;
}

// 카메라 ON/OFF
function toggleCamera() {
  isCameraOn.value = !isCameraOn.value;
  room.value?.localParticipant.setCameraEnabled(isCameraOn.value);
}

// 마이크 ON/OFF
function toggleMic() {
  isMicOn.value = !isMicOn.value;
  room.value?.localParticipant.setMicrophoneEnabled(isMicOn.value);
}
</script>

<template>
  <div id="class-video-room">
    <!-- 방에 입장하지 않은 경우 -->
    <div v-if="!room" class="room-layout">
      <div class="join-section">
        <h2>🎥 화상채팅 방 참가</h2>
        <form @submit.prevent="joinRoom()">
          <label>참가자 이름</label>
          <input v-model="participantName" type="text" />

          <label>방 이름</label>
          <input v-model="roomName" type="text" placeholder="방 이름 입력" />

          <button type="submit" :disabled="!roomName || isJoining">
            {{ isJoining ? '참가 중...' : '참가하기' }}
          </button>
        </form>
      </div>

      <div class="active-rooms-section">
        <h2>📡 현재 진행 중인 화상채팅방</h2>
        <ul v-if="activeRooms.length">
          <li v-for="r in activeRooms" :key="r.name">
            <div class="room-card">
              <div>
                <strong>{{ r.name }}</strong>
                <p>{{ r.participants }}명 참여 중</p>
              </div>
              <button @click="joinRoom(r.name)">참가</button>
            </div>
          </li>
        </ul>
        <p v-else class="empty">진행 중인 방이 없습니다.</p>
      </div>
    </div>

    <!-- 방에 입장한 경우 -->
    <div v-else class="video-room">
      <div class="video-room-header">
        <h2>{{ roomName }}</h2>
        <div class="controls">
          <button :class="{ off: !isCameraOn }" @click="toggleCamera">
            {{ isCameraOn ? '📷 카메라 끄기' : '📷 카메라 켜기' }}
          </button>
          <button :class="{ off: !isMicOn }" @click="toggleMic">
            {{ isMicOn ? '🎤 마이크 끄기' : '🎤 마이크 켜기' }}
          </button>
          <button class="leave" @click="leaveRoom">🚪 퇴장하기</button>
        </div>
      </div>

      <!-- 메인화면 -->
      <div class="main-video">
        <VideoComponent
          v-if="mainTrack"
          :track="mainTrack"
          :participantIdentity="mainIdentity"
          class="main-tile"
        />
      </div>

      <!-- 썸네일 리스트 -->
      <div class="thumbnail-grid">
        <!-- 내 화면 썸네일 -->
        <VideoComponent
          v-if="localTrack"
          :track="localTrack"
          :participantIdentity="participantName"
          class="thumbnail"
          :local="true"
          @click="setMainTrack(localTrack, participantName)"
        />

        <!-- 원격 썸네일 -->
        <template v-for="remoteTrack of remoteTracksMap.values()" :key="remoteTrack.trackPublication.trackSid">
          <VideoComponent
            v-if="remoteTrack.trackPublication.kind === 'video'"
            :track="remoteTrack.trackPublication.videoTrack!"
            :participantIdentity="remoteTrack.participantIdentity"
            class="thumbnail"
            @click="setMainTrack(remoteTrack.trackPublication.videoTrack!, remoteTrack.participantIdentity)"
          />
          <AudioComponent
            v-else
            :track="remoteTrack.trackPublication.audioTrack!"
            hidden
          />
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 참가화면 레이아웃 */
.room-layout {
  display: flex;
  gap: 2rem;
  padding: 2rem;
  background: #f8fafc;
  min-height: 100vh;
}

.join-section,
.active-rooms-section {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.join-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.join-section input {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 0.6rem;
}

.join-section button {
  border: none;
  background: #2563eb;
  color: white;
  padding: 0.8rem;
  border-radius: 8px;
  font-weight: bold;
  cursor: pointer;
  transition: 0.2s;
}

.join-section button:hover {
  background: #1e3a8a;
}

.active-rooms-section {
  flex: 2;
}

.active-rooms-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.room-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 0.8rem;
  margin-bottom: 0.8rem;
  background: #f9fafb;
}

.room-card button {
  background: #10b981;
  color: white;
  border: none;
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  cursor: pointer;
}
.room-card button:hover {
  background: #047857;
}
.empty {
  color: #999;
  text-align: center;
  margin-top: 1rem;
}

/* 방 입장 후 레이아웃 */
.video-room {
  padding: 1.5rem;
  background: #f8fafc;
  min-height: 100vh;
}

.video-room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.controls button {
  margin-left: 10px;
  padding: 0.5rem 0.8rem;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-weight: bold;
  background: #2563eb;
  color: white;
}
.controls button.off {
  background: #9ca3af;
}
.controls button.leave {
  background: #ef4444;
}

.main-video {
  width: 100%;
  height: 60vh;
  margin-bottom: 1rem;
}
.main-tile {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  background: black;
}

.thumbnail-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.5rem;
}
.thumbnail {
  width: 100%;
  aspect-ratio: 16/9;
  border-radius: 6px;
  background: black;
  cursor: pointer;
  border: 2px solid transparent;
  transition: 0.2s;
}
.thumbnail:hover {
  border-color: #2563eb;
}
</style>
