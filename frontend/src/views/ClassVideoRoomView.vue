<script setup lang="ts">
import {
  LocalVideoTrack,
  Room,
  RoomEvent,
  DataPacket_Kind,
} from 'livekit-client';
import { onMounted, onUnmounted, ref, type Ref, nextTick, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import VideoComponent from '@/components/VideoComponent.vue';
import AudioComponent from '@/components/AudioComponent.vue';
import LiveCaption from '@/components/LiveCaption.vue';
import SharedLiveCaption from '@/components/SharedLiveCaption.vue';
import AudioRecorder from '@/components/AudioRecorder.vue';
import '@/styles/ClassRelated.css';

const route = useRoute();
const router = useRouter();
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
// 녹화 제어 상태
const recordingState = ref<'idle' | 'recording' | 'paused'>('idle');
const isRecorderOpen = ref(false);
const audioRecorderRef = ref<any | null>(null);

// 퇴장 모달
const showExitModal = ref(false);

const recordButtonLabel = computed(() => {
  if (recordingState.value === 'idle') return '⏺ 수업 녹화 시작';
  if (recordingState.value === 'recording') return '⏸ 일시정지';
  return '▶ 재개';
});

const chatMessagesList = ref<Array<{ sender: string; message: string }>>([]);
const chatInput = ref('');
const chatBoxRef = ref<HTMLElement | null>(null);

// 공유 자막 관련 상태
const sharedCaption = ref('');
const sharedCaptionConfidence = ref(0);
const isSharedCaptionActive = ref(false);
const isCaptionVisible = ref(true); // 자막 표시/숨김 상태
const isChatVisible = ref(true); // 채팅 표시/숨김 상태
const isControlPanelOpen = ref(false); // 컨트롤 패널 열림/닫힘 상태
const hamburgerPosition = ref({ x: 20, y: 20 }); // 햄버거 버튼 위치
const isDragging = ref(false); // 드래그 상태

let APPLICATION_SERVER_URL = '';
let LIVEKIT_URL = '';

function configureUrls() {
  APPLICATION_SERVER_URL = 'http://localhost:8080/api/v1/meetingroom/'
      
  LIVEKIT_URL = 'wss://edumeet-1jz93drq.livekit.cloud'
}
configureUrls();

onMounted(() => {
  fetchActiveRooms();
  
  // 전역 마우스 이벤트 리스너 추가
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDrag);
  
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
      
      // 자막 데이터 처리
      if (data.type === 'caption') {
        handleCaptionData(decoded);
        return;
      }
      
      // 채팅 메시지 처리
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
      console.error('데이터 해석 실패:', e);
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
  
  // /class/create 페이지로 이동
  router.push('/class/create');
}

onUnmounted(() => {
  // 전역 마우스 이벤트 리스너 제거
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDrag);
  leaveRoom();
});

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

function toggleCaption() {
  isCaptionVisible.value = !isCaptionVisible.value;
}

function toggleChat() {
  isChatVisible.value = !isChatVisible.value;
}

function toggleControlPanel() {
  isControlPanelOpen.value = !isControlPanelOpen.value;
}

// 햄버거 버튼 드래그 관련 함수들
let dragOffset = { x: 0, y: 0 };

function startDrag(event) {
  isDragging.value = true;
  const rect = event.currentTarget.getBoundingClientRect();
  dragOffset.x = event.clientX - rect.left;
  dragOffset.y = event.clientY - rect.top;
  event.preventDefault();
}

function onDrag(event) {
  if (!isDragging.value) return;
  
  const x = event.clientX - dragOffset.x;
  const y = event.clientY - dragOffset.y;
  
  // 화면 경계 내에서만 이동
  const maxX = window.innerWidth - 50;
  const maxY = window.innerHeight - 50;
  
  hamburgerPosition.value.x = Math.max(0, Math.min(x, maxX));
  hamburgerPosition.value.y = Math.max(0, Math.min(y, maxY));
}

function stopDrag() {
  isDragging.value = false;
}

// 녹화 토글 버튼 동작
async function handleRecordToggle() {
  if (!isUserCreator.value) return; // 생성자만 녹화 조작
  if (!audioRecorderRef.value) return;

  if (recordingState.value === 'idle') {
    await audioRecorderRef.value.startRecording?.();
    recordingState.value = 'recording';
    return;
  }
  if (recordingState.value === 'recording') {
    await audioRecorderRef.value.pauseRecording?.();
    recordingState.value = 'paused';
    return;
  }
  if (recordingState.value === 'paused') {
    await audioRecorderRef.value.resumeRecording?.();
    recordingState.value = 'recording';
  }
}

// 문서 요약 버튼 동작
async function handleGenerateSummary() {
  if (!isUserCreator.value) return;
  if (!audioRecorderRef.value) return;
  try {
    await audioRecorderRef.value.generateSummary?.();
  } catch (e) {
    console.error('문서 요약 실행 중 오류:', e);
  }
}

// 퇴장 모달
function handleLeaveClick() {
  showExitModal.value = true;
}

async function confirmLeaveWithoutSummary() {
  showExitModal.value = false;
  await leaveRoom();
}

async function confirmLeaveWithSummary() {
  showExitModal.value = false;
  await leaveRoom();
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
  room.value.localParticipant.publishData(payload, { reliable: true });
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

// 공유 실시간 자막 이벤트 핸들러
function handleSharedCaption(data) {
  console.log('🎤 공유 실시간 자막:', data.text);
  console.log('🎤 신뢰도:', data.confidence);
  console.log('🎤 최종 결과 여부:', data.isFinal);
  
  // 생성자의 음성만 전체 학생들이 볼 수 있도록 처리
  if (isUserCreator.value) {
    // 실시간 자막을 모든 참여자에게 공유 (중간 결과 포함)
    shareCaptionToAll(data.text, data.confidence, data.isFinal);
  }
}

// 자막을 모든 참여자에게 공유
function shareCaptionToAll(text, confidence, isFinal) {
  if (!room.value) return;
  
  const captionData = {
    type: 'caption',
    text: text,
    confidence: confidence,
    isFinal: isFinal,
    sender: participantName.value,
    timestamp: Date.now()
  };
  
  const encoder = new TextEncoder();
  const payload = encoder.encode(JSON.stringify(captionData));
  
  console.log('📤 자막 공유:', captionData);
  room.value.localParticipant.publishData(payload, { reliable: true });
}

// 다른 참여자로부터 자막 데이터 수신
function handleCaptionData(data) {
  try {
    const captionData = JSON.parse(data);
    
    if (captionData.type === 'caption') {
      console.log('📥 자막 수신:', captionData);
      
      // 생성자의 자막만 표시
      if (captionData.sender !== participantName.value) {
        sharedCaption.value = captionData.text;
        sharedCaptionConfidence.value = captionData.confidence;
        isSharedCaptionActive.value = true;
        
        // 최종 결과가 아닌 경우에만 자동 숨김 (실시간 유지)
        if (captionData.isFinal) {
          // 최종 결과는 3초 후 숨김
          setTimeout(() => {
            if (sharedCaption.value === captionData.text) {
              isSharedCaptionActive.value = false;
            }
          }, 3000);
        }
      }
    }
  } catch (error) {
    console.error('자막 데이터 파싱 오류:', error);
  }
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

// 음성 녹음 관련 이벤트 핸들러
function handleRecordingStarted() {
  console.log('🎤 음성 녹음이 시작되었습니다.')
  // 여기에 녹음 시작 시 필요한 로직 추가
}

function handleRecordingStopped() {
  console.log('⏹️ 음성 녹음이 종료되었습니다.')
  // 여기에 녹음 종료 시 필요한 로직 추가
}

function handleChunkUploaded(chunkData: { chunkNumber: number; timestamp: number }) {
  console.log('📤 청크 업로드 완료:', chunkData)
  // 여기에 청크 업로드 완료 시 필요한 로직 추가
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
          <!-- 드래그 가능한 햄버거 버튼 -->
          <button 
            class="hamburger-btn" 
            :style="{ left: hamburgerPosition.x + 'px', top: hamburgerPosition.y + 'px' }"
            @click="!isDragging && toggleControlPanel()" 
            @mousedown="startDrag"
            title="컨트롤 패널 (드래그하여 이동 가능)"
          >
            ☰
          </button>
          
          <!-- 컨트롤 패널 -->
          <div 
            v-if="isControlPanelOpen" 
            class="control-panel"
            :style="{ 
              left: (hamburgerPosition.x + 60) + 'px', 
              top: (hamburgerPosition.y - 200) + 'px' 
            }"
          >
            <div class="control-panel-header">
              <h3>컨트롤</h3>
              <button class="close-btn" @click="toggleControlPanel">✕</button>
            </div>
            <div class="control-buttons">
              <button v-if="isUserCreator" @click="handleRecordToggle" :title="recordButtonLabel">
                {{ recordingState === 'idle' ? '⏺' : recordingState === 'recording' ? '⏸' : '▶' }}
              </button>
              <button :class="{ off: !isCameraOn }" @click="toggleCamera" title="카메라 끄기/켜기">
                📷
              </button>
              <button :class="{ off: !isMicOn }" @click="toggleMic" title="마이크 끄기/켜기">
                🎤
              </button>
              <button :class="{ off: !isCaptionVisible }" @click="toggleCaption" title="자막 숨기기/보기">
                📝
              </button>
              <button :class="{ off: !isChatVisible }" @click="toggleChat" title="채팅 숨기기/보기">
                💬
              </button>
              <button class="leave" @click="handleLeaveClick" title="퇴장하기">
                ✕
              </button>
            </div>
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



            <div class="thumbnail-grid">
              <!-- 참여자인 경우 로컬 화면을 썸네일에 표시 -->
              <VideoComponent
                v-if="!isUserCreator && localTrack"
                :track="localTrack"
                :participantIdentity="participantName"
                class="thumbnail participant"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />
              <div v-if="!isUserCreator && localTrack" class="thumbnail-label">참여자</div>
              
              <!-- 생성자인 경우 기존 로직 유지 -->
              <VideoComponent
                v-else-if="localTrack && localTrack !== mainTrack"
                :track="localTrack"
                :participantIdentity="participantName"
                class="thumbnail creator"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />
              <div v-else-if="localTrack && localTrack !== mainTrack" class="thumbnail-label">생성자</div>

              <template v-for="remoteTrack of remoteTracksMap.values()" :key="remoteTrack.trackPublication.trackSid">
                <!-- 참여자인 경우 첫 번째 원격 참가자는 메인에 표시되므로 썸네일에서 제외 -->
                <VideoComponent
                  v-if="remoteTrack.trackPublication.kind === 'video' && 
                         remoteTrack.trackPublication.videoTrack !== mainTrack &&
                         !(getFirstRemoteVideoTrack() === remoteTrack.trackPublication.videoTrack && !isUserCreator)"
                  :track="remoteTrack.trackPublication.videoTrack!"
                  :participantIdentity="remoteTrack.participantIdentity"
                  :class="['thumbnail', isUserCreator ? 'participant' : 'creator']"
                  @click="setMainTrack(remoteTrack.trackPublication.videoTrack!, remoteTrack.participantIdentity)"
                />
                <div v-if="remoteTrack.trackPublication.kind === 'video' && 
                           remoteTrack.trackPublication.videoTrack !== mainTrack &&
                           !(getFirstRemoteVideoTrack() === remoteTrack.trackPublication.videoTrack && !isUserCreator)" 
                     class="thumbnail-label">
                  {{ isUserCreator ? '참여자' : '생성자' }}
                </div>
                <AudioComponent
                  v-else-if="remoteTrack.trackPublication.kind === 'audio'"
                  :track="remoteTrack.trackPublication.audioTrack!"
                  hidden
                />
              </template>
            </div>
          </div>
        </div>
        
        <div v-if="isChatVisible" class="chat-section">
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
    
    <!-- 공유 실시간 자막 (화면 하단 오버레이) -->
    <SharedLiveCaption
      :isCreator="isUserCreator"
      :isVisible="isCaptionVisible"
      :sharedCaption="sharedCaption"
      :sharedCaptionConfidence="sharedCaptionConfidence"
      :isSharedCaptionActive="isSharedCaptionActive"
      @transcript="handleSharedCaption"
      @error="handleCaptionError"
      @status="handleCaptionStatus"
    />
    
    <!-- 음성 녹음 컴포넌트 (생성자에게만 표시, 모달은 항상 숨김 상태로 동작) -->
    <div v-if="isUserCreator" class="audio-recorder-container" style="display:none;">
      <AudioRecorder
        ref="audioRecorderRef"
        :isOpen="false"
        :classId="classId"
        :className="className"
        :creatorName="participantName"
        @recording-started="handleRecordingStarted"
        @recording-stopped="handleRecordingStopped"
        @chunk-uploaded="handleChunkUploaded"
      />
    </div>

    <!-- 퇴장 확인 모달 -->
    <div v-if="showExitModal" class="exit-modal" @click.self="showExitModal = false">
      <div class="exit-modal-content">
        <h3>수업에서 퇴장하시겠습니까?</h3>
        <p>문서 요약을 생성하고 싶으시면 아래 버튼을 클릭하세요.</p>
        <div class="exit-modal-buttons">
          <button @click="confirmLeaveWithSummary" class="summary-btn">
            📝 문서 요약 생성 후 퇴장
          </button>
          <button @click="confirmLeaveWithoutSummary" class="leave-btn">
            지금 퇴장
          </button>
          <button @click="showExitModal = false" class="cancel-btn">
            취소
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.audio-recorder-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  max-width: 400px;
}

</style>
