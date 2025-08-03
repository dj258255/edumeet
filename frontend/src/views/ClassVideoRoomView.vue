<script setup lang="ts">
import {
  LocalVideoTrack,
  Room,
  RoomEvent
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
</script>

<template>
  <div id="class-video-room">
    <!-- 방에 입장하지 않은 경우 -->
    <div v-if="!room" class="room-layout">
      <!-- 좌측: 참가자 정보 입력 -->
      <div class="join-section">
        <h2>화상채팅 방 참가</h2>
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

      <!-- 우측: 현재 진행 중인 방 목록 -->
      <div class="active-rooms-section">
        <h2>현재 진행 중인 화상채팅방</h2>
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
        <p v-else>진행 중인 방이 없습니다.</p>
      </div>
    </div>

    <!-- 방에 입장한 경우 -->
    <div v-else class="video-room">
      <div class="video-room-header">
        <h2>{{ roomName }}</h2>
        <button @click="leaveRoom">퇴장하기</button>
      </div>

      <div class="video-grid">
        <!-- 로컬 비디오 -->
        <VideoComponent
          v-if="localTrack"
          :track="localTrack"
          :participantIdentity="participantName"
          :local="true"
          class="video-tile"
        />

        <!-- 원격 비디오 -->
        <template v-for="remoteTrack of remoteTracksMap.values()" :key="remoteTrack.trackPublication.trackSid">
          <VideoComponent
            v-if="remoteTrack.trackPublication.kind === 'video'"
            :track="remoteTrack.trackPublication.videoTrack!"
            :participantIdentity="remoteTrack.participantIdentity"
            class="video-tile"
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
/* 전체 레이아웃 */
.room-layout {
  display: flex;
  gap: 2rem;
  padding: 2rem;
}

/* 좌측 참가 섹션 */
.join-section {
  flex: 1;
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.join-section input {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 0.5rem;
}

.join-section button {
  border: none;
  background: #1d3557;
  color: white;
  padding: 0.6rem;
  border-radius: 8px;
  font-weight: bold;
  cursor: pointer;
}

.join-section button:disabled {
  background: #ccc;
}

/* 우측 방 목록 섹션 */
.active-rooms-section {
  flex: 2;
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
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
  background: #f9f9f9;
}

.room-card button {
  background: #457b9d;
  color: white;
  border: none;
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  cursor: pointer;
}

/* 방 입장 후 레이아웃 */
.video-room {
  padding: 1.5rem;
}

.video-room-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr); /* 2x2 그리드 */
  gap: 1rem;
}

.video-tile {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  background: black;
  overflow: hidden;
}
</style>
