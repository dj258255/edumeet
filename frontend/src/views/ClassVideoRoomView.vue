<script setup lang="ts">
import {
  LocalVideoTrack,
  Room,
  RoomEvent,
  DataPacket_Kind,
} from 'livekit-client';
import { onMounted, onUnmounted, ref, type Ref, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import VideoComponent from '@/components/VideoComponent.vue';
import AudioComponent from '@/components/AudioComponent.vue';
import LiveCaption from '@/components/LiveCaption.vue';
import '@/styles/ClassRelated.css';

const route = useRoute();
const classId = route.params.classId as string;

const room = ref<Room | null>(null);
const localTrack = ref<LocalVideoTrack>();
const remoteTracksMap: Ref<Map<string, any>> = ref(new Map());

const participantName = ref('Participant' + Math.floor(Math.random() * 100));
const roomName = ref('');
const isJoining = ref(false);
const isUserCreator = ref(false); // 생성자 여부

const activeRooms = ref<Array<{ name: string; participants: number }>>([]);

const mainTrack = ref<any>(null);
const mainIdentity = ref<string>('');
const className = ref(''); // 모달에서 입력한 className을 제목으로 사용
const isCameraOn = ref(true);
const isMicOn = ref(true);

const chatMessagesList = ref<Array<{ sender: string; message: string }>>([]);
const chatInput = ref('');
const chatBoxRef = ref<HTMLElement | null>(null);

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
  
  // URL 쿼리 파라미터에서 방 이름, 제목, 생성자 여부 확인
  const queryRoomName = route.query.roomName as string;
  const queryClassName = route.query.className as string;
  const isCreator = route.query.isCreator === 'true';
  const creatorName = route.query.creatorName as string;
  const participantNameParam = route.query.participantName as string;
  
  console.log('🔍 ClassVideoRoomView - URL 파라미터:')
  console.log('🔍 roomName:', queryRoomName)
  console.log('🔍 className:', queryClassName)
  console.log('🔍 isCreator:', isCreator)
  console.log('🔍 creatorName:', creatorName)
  console.log('🔍 participantName:', participantNameParam)
  
  if (queryRoomName) {
    roomName.value = queryRoomName;
    // 모달에서 입력한 className을 제목으로 사용
    if (queryClassName) {
      className.value = queryClassName;
    }
    
    // 생성자 여부 설정
    isUserCreator.value = isCreator;
    
    // 참여자 이름이 있으면 설정
    if (participantNameParam) {
      participantName.value = participantNameParam;
    }
    
    // 생성자인 경우 자동으로 방에 참가
    if (isCreator) {
      // 모달에서 입력받은 생성자 이름을 사용
      if (creatorName) {
        participantName.value = creatorName;
      }
      joinRoom(queryRoomName);
    } else {
      // 참여자인 경우도 자동으로 방에 참가
      joinRoom(queryRoomName);
    }
  }
});

function fetchActiveRooms() {
  activeRooms.value = [
    { name: `${classId}-main`, participants: 3 },
    { name: `${classId}-study`, participants: 5 },
  ];
}

async function joinRoom(targetRoom?: string) {
  isJoining.value = true;
  const target = targetRoom || roomName.value;
  if (!target) {
    isJoining.value = false;
    return;
  }

  const currentRoom = new Room();
  room.value = currentRoom;

  currentRoom.on(RoomEvent.TrackSubscribed, (_track, publication, participant) => {
    remoteTracksMap.value.set(publication.trackSid, {
      trackPublication: publication,
      participantIdentity: participant.identity,
    });
  });

  currentRoom.on(RoomEvent.TrackUnsubscribed, (_track, publication) => {
    remoteTracksMap.value.delete(publication.trackSid);
  });

  currentRoom.on(RoomEvent.DataReceived, (payload, participant) => {
    try {
      const decoded = new TextDecoder().decode(payload);
      console.log('📩 수신된 원시 문자열:', decoded);

      if (!decoded || decoded.trim() === '') return;
      const data = JSON.parse(decoded);
      if (data.message && data.sender) {
        chatMessagesList.value.push({
          sender: data.sender || participant?.identity || '익명',
          message: data.message,
        });
        
        // 새 메시지 수신 시 자동 스크롤
        nextTick(() => {
          scrollToBottom();
        });
      }
    } catch (e) {
      console.error('채팅 메시지 해석 실패:', e);
    }
  });

  try {
    const token = await getToken(target, participantName.value);
    await currentRoom.connect(LIVEKIT_URL, token);
    await currentRoom.localParticipant.enableCameraAndMicrophone();

    const firstVideoPub = currentRoom.localParticipant.videoTrackPublications.values().next().value;
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
  room.value = null;
  localTrack.value = undefined;
  mainTrack.value = null;
  remoteTracksMap.value.clear();
  chatMessagesList.value = [];
}

onUnmounted(leaveRoom);

async function getToken(roomName: string, participantName: string) {
  const response = await fetch(APPLICATION_SERVER_URL + 'token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomName, participantName }),
  });
  const data = await response.json();
  return data.token;
}

function setMainTrack(track: any, identity: string) {
  mainTrack.value = track;
  mainIdentity.value = identity;
}

function toggleCamera() {
  isCameraOn.value = !isCameraOn.value;
  room.value?.localParticipant.setCameraEnabled(isCameraOn.value);
}

function toggleMic() {
  isMicOn.value = !isMicOn.value;
  room.value?.localParticipant.setMicrophoneEnabled(isMicOn.value);
}

function sendChatMessage() {
  const msg = chatInput.value.trim();
  if (!msg || !room.value) return;

  const encoder = new TextEncoder();
  const payload = encoder.encode(JSON.stringify({
    sender: participantName.value,
    message: msg,
  }));

  console.log('📤 채팅 전송:', new TextDecoder().decode(payload));
  room.value.localParticipant.publishData(payload, DataPacket_Kind.RELIABLE);
  chatMessagesList.value.push({ sender: '나', message: msg });
  chatInput.value = '';
  
  // 채팅 전송 후 자동 스크롤
  nextTick(() => {
    scrollToBottom();
  });
}

function scrollToBottom() {
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight;
  }
}

// LiveCaption 이벤트 핸들러들
function handleLiveCaption(data) {
  console.log('🎤 실시간 자막:', data.text);
  console.log('🎤 신뢰도:', data.confidence);
  console.log('🎤 최종 결과 여부:', data.isFinal);
  
  // 실시간 자막은 자막창에만 표시하고 채팅창에는 입력하지 않음
}

function handleCaptionError(error) {
  console.error('🎤 자막 오류:', error);
}

function handleCaptionStatus(status) {
  console.log('🎤 자막 상태:', status);
}

// 첫 번째 원격 비디오 트랙 가져오기
function getFirstRemoteVideoTrack() {
  if (!room.value) return null;
  
  const remoteParticipants = Array.from(room.value.remoteParticipants.values());
  for (const participant of remoteParticipants) {
    if (participant.videoTrackPublications.size > 0) {
      const videoTrack = participant.videoTrackPublications.values().next().value;
      if (videoTrack && videoTrack.videoTrack) {
        return videoTrack.videoTrack;
      }
    }
  }
  return null;
}

// 첫 번째 원격 참가자 identity 가져오기
function getFirstRemoteParticipantIdentity() {
  if (!room.value) return '';
  
  const remoteParticipants = Array.from(room.value.remoteParticipants.values());
  if (remoteParticipants.length > 0) {
    return remoteParticipants[0].identity;
  }
  return '';
}
</script>

<!-- 나머지 template 부분은 동일하므로 생략 가능. 필요시 다시 제공 가능. -->


<template>
  <div id="class-video-room">
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

    <div v-else class="video-room">
              <div class="video-room-header">
          <div class="header-info">
            <h2>{{ className || roomName }}</h2>
            <div class="user-role">
              <span v-if="isUserCreator" class="creator-badge">👑 생성자</span>
              <span v-else class="participant-badge">👤 참여자</span>
            </div>
          </div>
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

      <div class="video-body">
        <div class="main-content">
          <div class="video-section">
            <div class="main-video">
              <!-- 참여자인 경우 원격 참가자 화면을 메인에 표시 -->
              <VideoComponent
                v-if="!isUserCreator && getFirstRemoteVideoTrack()"
                :track="getFirstRemoteVideoTrack()"
                :participantIdentity="getFirstRemoteParticipantIdentity()"
                class="main-tile"
              />
              <!-- 생성자인 경우 기존 로직 유지 -->
              <VideoComponent
                v-else-if="mainTrack"
                :track="mainTrack"
                :participantIdentity="mainIdentity"
                class="main-tile"
              />
            </div>

            <div class="live-caption-section">
              <LiveCaption 
                @transcript="handleLiveCaption"
                @error="handleCaptionError"
                @status="handleCaptionStatus"
              />
            </div>

            <div class="thumbnail-grid">
              <!-- 참여자인 경우 로컬 화면을 썸네일에 표시 -->
              <VideoComponent
                v-if="!isUserCreator && localTrack"
                :track="localTrack"
                :participantIdentity="participantName"
                class="thumbnail"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />
              
              <!-- 생성자인 경우 기존 로직 유지 -->
              <VideoComponent
                v-else-if="localTrack && localTrack !== mainTrack"
                :track="localTrack"
                :participantIdentity="participantName"
                class="thumbnail"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />

              <template v-for="remoteTrack of remoteTracksMap.values()" :key="remoteTrack.trackPublication.trackSid">
                <!-- 참여자인 경우 첫 번째 원격 참가자는 메인에 표시되므로 썸네일에서 제외 -->
                <VideoComponent
                  v-if="remoteTrack.trackPublication.kind === 'video' && 
                         remoteTrack.trackPublication.videoTrack !== mainTrack &&
                         !(getFirstRemoteVideoTrack() === remoteTrack.trackPublication.videoTrack && !isUserCreator)"
                  :track="remoteTrack.trackPublication.videoTrack!"
                  :participantIdentity="remoteTrack.participantIdentity"
                  class="thumbnail"
                  @click="setMainTrack(remoteTrack.trackPublication.videoTrack!, remoteTrack.participantIdentity)"
                />
                <AudioComponent
                  v-else-if="remoteTrack.trackPublication.kind === 'audio'"
                  :track="remoteTrack.trackPublication.audioTrack!"
                  hidden
                />
              </template>
            </div>
          </div>
        </div>
        
        <div class="chat-section">
          <h3>💬 채팅</h3>
          <div class="chat-box" ref="chatBoxRef">
            <div v-for="(msg, idx) in chatMessagesList" :key="idx" class="chat-message">
              <strong>{{ msg.sender }}:</strong> {{ msg.message }}
            </div>
          </div>
          <form class="chat-input" @submit.prevent="sendChatMessage">
            <input v-model="chatInput" type="text" placeholder="메시지를 입력하세요" />
            <button type="submit">전송</button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
