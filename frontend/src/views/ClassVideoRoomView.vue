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
import ScreenShareComponent from '@/components/ScreenShareComponent.vue';
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
const screenShareRef = ref<any | null>(null)
const directVideoRef = ref<HTMLVideoElement | null>(null);
const mainVideoRef = ref<HTMLVideoElement | null>(null);

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
const isScreenShareVisible = ref(true); // 화면 공유 패널 표시/숨김 상태
const isScreenSharing = ref(false); // 화면 공유 중인지 상태
const screenShareTrack = ref(null); // 화면 공유 트랙
const isControlPanelOpen = ref(false); // 컨트롤 패널 열림/닫힘 상태

// URL 파라미터 존재 여부를 확인하는 computed 속성
const hasUrlParams = computed(() => {
  return !!(route.query.meetingId || route.query.roomName);
});


let APPLICATION_SERVER_URL = '';
let LIVEKIT_URL = '';

function configureUrls() {
  APPLICATION_SERVER_URL = 'https://i13c205.p.ssafy.io/api/v1/meetingroom/'
      
  LIVEKIT_URL = 'wss://edumeet-1jz93drq.livekit.cloud'
}
configureUrls();

onMounted(async () => {
  fetchActiveRooms();
  
  // ESC 키로 컨트롤 패널 닫기
  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && isControlPanelOpen.value) {
      isControlPanelOpen.value = false;
    }
  };
  
  document.addEventListener('keydown', handleKeyDown);
  
  // 컴포넌트 언마운트 시 이벤트 리스너 제거
  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeyDown);
  });
  
  // URL 쿼리 파라미터에서 화상수업 정보 확인
  const meetingId = route.query.meetingId as string;
  const queryRoomName = route.query.roomName as string;
  const queryTitle = route.query.title as string;
  const queryClassName = route.query.className as string;
  const queryEmail = route.query.email as string;
  const isCreator = route.query.isCreator === 'true';
  const creatorName = route.query.creatorName as string;
  const description = route.query.description as string;
  const participantNameParam = route.query.participantName as string;
  const token = route.query.token as string; // 백엔드에서 받은 토큰
  
  console.log('🔍 ClassVideoRoomView - URL 파라미터:')
  console.log('🔍 meetingId:', meetingId)
  console.log('🔍 roomName:', queryRoomName)
  console.log('🔍 title:', queryTitle)
  console.log('🔍 className:', queryClassName)
  console.log('🔍 email:', queryEmail)
  console.log('🔍 isCreator:', isCreator)
  console.log('🔍 creatorName:', creatorName)
  console.log('🔍 description:', description)
  console.log('🔍 participantName:', participantNameParam)
  console.log('🔍 token:', token ? '있음' : '없음')
  
  // meetingId가 있으면 생성자, roomName이 있으면 참여자
  if (meetingId) {
    // meetingId를 roomName으로 사용 (생성자)
    roomName.value = meetingId;
    
    // API에서 받은 제목을 사용
    if (queryTitle) {
      className.value = queryTitle;
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
      await joinRoom(meetingId, token); // 토큰 전달, await 추가
    } else {
      // 참여자인 경우도 자동으로 방에 참가
      await joinRoom(meetingId, token); // 토큰 전달, await 추가
    }
  } else if (queryRoomName) {
    // roomName을 사용 (참여자)
    roomName.value = queryRoomName;
    
    // className을 제목으로 사용
    if (queryClassName) {
      className.value = queryClassName;
    }
    
    // 생성자 여부 설정
    isUserCreator.value = isCreator;
    
    // 참여자 이름이 있으면 설정
    if (participantNameParam) {
      participantName.value = participantNameParam;
    }
    
    // 참여자로 방에 참가 (참여자는 토큰이 없으므로 null 전달)
    await joinRoom(queryRoomName, null); // await 추가
  }
});

function fetchActiveRooms() {
  activeRooms.value = [
    { name: `${classId}-main`, participants: 3 },
    { name: `${classId}-study`, participants: 5 },
  ];
}

async function joinRoom(targetRoom?: string, existingToken?: string) {
  isJoining.value = true;
  const target = targetRoom || roomName.value;
  if (!target) {
    isJoining.value = false;
    return;
  }

  console.log('🔍 joinRoom 호출 - targetRoom:', targetRoom)
  console.log('🔍 joinRoom 호출 - target:', target)
  console.log('🔍 joinRoom 호출 - existingToken:', existingToken ? '있음' : '없음')
  console.log('🔍 joinRoom 호출 - route.query.meetingId:', route.query.meetingId)
  console.log('🔍 joinRoom 호출 - route.query:', route.query)

  const currentRoom = new Room();
  room.value = currentRoom;

  currentRoom.on(RoomEvent.TrackSubscribed, (_track, publication, participant) => {
    console.log('📹 원격 트랙 구독:', publication.trackSid, publication.kind, '참가자:', participant.identity)
    remoteTracksMap.value.set(publication.trackSid, {
      trackPublication: publication,
      participantIdentity: participant.identity,
    });
    
    // 화면 공유 트랙인지 확인 (트랙의 라벨로 판단)
    if (publication.kind === 'video' && _track.mediaStreamTrack && 
        (_track.mediaStreamTrack.label.includes('screen') || 
         _track.mediaStreamTrack.label.includes('display'))) {
      console.log('🖥️ 원격 화면 공유 트랙 감지:', participant.identity, _track.mediaStreamTrack.label)
      // 화면 공유 트랙을 메인으로 설정
      setMainTrack(publication.videoTrack!, participant.identity + ' (화면 공유)')
    }
    
    // 새로운 카메라 트랙인지 확인
    if (publication.kind === 'video' && _track.mediaStreamTrack && 
        !_track.mediaStreamTrack.label.includes('screen') && 
        !_track.mediaStreamTrack.label.includes('display')) {
      
      // 참가자가 아닌 경우 (다른 참가자의 카메라 트랙)
      if (participant.identity !== participantName.value) {
        console.log('🎯 다른 참가자의 카메라 트랙 감지:', participant.identity)
        
        // 생성자인지 확인하고 우선적으로 메인에 표시
        if (participant.identity.includes('creator') || 
            participant.identity.includes('생성자') ||
            participant.identity.includes('teacher') ||
            participant.identity.includes('강사')) {
          console.log('🎯 생성자 카메라 트랙을 메인에 설정:', participant.identity)
          setMainTrack(publication.videoTrack!, participant.identity)
        } else if (!mainTrack.value) {
          // 메인 트랙이 설정되지 않은 경우 첫 번째 참가자 트랙을 설정
          console.log('🎯 첫 번째 참가자 카메라 트랙을 메인에 설정:', participant.identity)
          setMainTrack(publication.videoTrack!, participant.identity)
        }
      } else {
        // 자신의 카메라 트랙인 경우
        console.log('🖥️ 자신의 새로운 카메라 트랙 감지:', participant.identity, _track.mediaStreamTrack.label)
        setMainTrack(publication.videoTrack!, participant.identity)
      }
    }
  });

  currentRoom.on(RoomEvent.TrackUnsubscribed, (_track, publication) => {
    console.log('📹 원격 트랙 구독 해제:', publication.trackSid, publication.kind)
    remoteTracksMap.value.delete(publication.trackSid);
    
    // 화면 공유 트랙이 언퍼블리시된 경우 메인 화면을 카메라로 복원
    if (publication.kind === 'video' && _track.mediaStreamTrack && 
        (_track.mediaStreamTrack.label.includes('screen') || 
         _track.mediaStreamTrack.label.includes('display'))) {
      console.log('🖥️ 원격 화면 공유 트랙 종료:', publication.trackSid, _track.mediaStreamTrack.label)
      // 첫 번째 사용 가능한 비디오 트랙을 메인으로 설정
      const firstVideoTrack = getFirstRemoteVideoTrack()
      if (firstVideoTrack) {
        setMainTrack(firstVideoTrack, getFirstRemoteParticipantIdentity())
      }
    }
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
    // URL에서 받은 토큰이 있으면 사용, 없으면 새로 요청
    let livekitToken: string;
    if (existingToken) {
      console.log('🔍 URL에서 받은 토큰 사용')
      livekitToken = existingToken;
    } else {
      console.log('🔍 새로운 토큰 요청')
      livekitToken = await getToken(target, participantName.value);
    }
    
    await currentRoom.connect(LIVEKIT_URL, livekitToken);
    await currentRoom.localParticipant.enableCameraAndMicrophone();

    // 카메라 트랙이 준비될 때까지 기다리기
    await new Promise(resolve => setTimeout(resolve, 1000));

    const firstVideoPub = currentRoom.localParticipant.videoTrackPublications.values().next().value;
    if (firstVideoPub) {
      console.log('🖥️ 초기 카메라 트랙 발견:', firstVideoPub.track.mediaStreamTrack?.label)
      localTrack.value = firstVideoPub.videoTrack;
      
      // 참가자인 경우 생성자의 화면을 우선적으로 찾아서 메인에 표시
      if (!isUserCreator.value) {
        console.log('🎯 참가자 입장 - 생성자 화면 찾는 중...')
        // 잠시 기다린 후 원격 참가자들의 트랙을 확인
        setTimeout(() => {
          const creatorTrack = getFirstRemoteVideoTrack();
          const creatorIdentity = getFirstRemoteParticipantIdentity();
          if (creatorTrack) {
            console.log('🎯 생성자 화면을 메인에 설정:', creatorIdentity)
            setMainTrack(creatorTrack, creatorIdentity);
          } else {
            console.log('🎯 생성자 화면을 찾을 수 없어 자신의 화면을 메인에 설정')
            setMainTrack(firstVideoPub.videoTrack, participantName.value);
          }
        }, 2000); // 2초 후에 확인
      } else {
        // 생성자인 경우 자신의 화면을 메인에 표시
        console.log('🎯 생성자 입장 - 자신의 화면을 메인에 설정')
        setMainTrack(firstVideoPub.videoTrack, participantName.value);
      }
    } else {
      console.log('🖥️ 초기 카메라 트랙을 찾을 수 없음')
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
  leaveRoom();
});

async function getToken(roomName: string, participantName: string) {
  console.log('🔍 getToken 호출 - roomName:', roomName)
  console.log('🔍 getToken 호출 - participantName:', participantName)
  console.log('🔍 getToken 호출 - route.query.meetingId:', route.query.meetingId)
  
  // 백엔드 MeetingCreateRequestDto에 맞춰 요청 본문 수정
  const requestBody: { title: string; participantName: string; classId?: number } = { 
    title: roomName, 
    participantName 
  };
  
  // classId가 있으면 요청 본문에 추가 (route.params에서 가져옴)
  const classId = route.params.classId;
  if (classId) {
    requestBody.classId = Number(classId);
    console.log('🔍 getToken - classId를 요청 본문에 추가:', classId)
  }
  
  console.log('🔍 getToken - 최종 요청 본문:', requestBody)
  console.log('🔍 getToken - 요청 URL:', APPLICATION_SERVER_URL + 'token')
  
  // Authorization 헤더 추가
  const accessToken = localStorage.getItem('accessToken');
  const headers: { [key: string]: string } = { 'Content-Type': 'application/json' };
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
    console.log('🔍 getToken - Authorization 헤더 추가됨')
  }
  
  const response = await fetch(APPLICATION_SERVER_URL + 'token', {
    method: 'POST',
    headers,
    body: JSON.stringify(requestBody),
  });
  
  console.log('🔍 getToken - 응답 상태:', response.status)
  
  if (!response.ok) {
    // 오류 응답 본문을 확인
    const errorText = await response.text()
    console.log('🔍 getToken - 오류 응답 본문:', errorText)
    
    // JSON 파싱 시도
    let errorData
    try {
      errorData = JSON.parse(errorText)
    } catch (e) {
      errorData = { error: errorText }
    }
    
    // 백엔드에서 반환하는 구체적인 에러 메시지 사용
    const errorMessage = errorData.error || `토큰 요청 실패: ${response.status}`
    throw new Error(errorMessage)
  }
  
  const data = await response.json();
  console.log('🔍 getToken - 응답 데이터:', data)
  
  if (!data.token) {
    throw new Error('토큰이 응답에 포함되지 않았습니다.')
  }
  
  return data.token;
}

function setMainTrack(track: any, identity: string) {
  console.log('🖥️ setMainTrack 호출:', track, identity)
  mainTrack.value = track;
  mainIdentity.value = identity;
  console.log('🖥️ mainTrack 설정 완료:', mainTrack.value)
  
  // 직접 video 엘리먼트에 스트림 연결
  nextTick(() => {
    if (mainVideoRef.value && track && track.mediaStreamTrack) {
      const stream = new MediaStream([track.mediaStreamTrack])
      mainVideoRef.value.srcObject = stream
      console.log('🖥️ 메인 video 엘리먼트에 스트림 연결 완료')
    }
  })
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

function toggleScreenShare() {
  isScreenShareVisible.value = !isScreenShareVisible.value;
}

function toggleControlPanel() {
  isControlPanelOpen.value = !isControlPanelOpen.value;
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
  try {
    // 문서 요약 생성 후 퇴장
    await handleGenerateSummary();
    console.log('🔍 문서 요약 생성 완료, 퇴장 진행');
  } catch (error) {
    console.error('🔍 문서 요약 생성 실패:', error);
  }
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

// 생성자의 비디오 트랙을 우선적으로 가져오기
function getFirstRemoteVideoTrack() {
  if (!room.value) return null;
  
  const remoteParticipants = Array.from(room.value.remoteParticipants.values());
  
  // 먼저 생성자(creator)를 찾아서 우선적으로 반환
  for (const participant of remoteParticipants) {
    // 생성자 여부를 확인 (identity에 'creator'가 포함되어 있거나 특정 패턴으로 구분)
    if (participant.identity.includes('creator') || 
        participant.identity.includes('생성자') ||
        participant.identity.includes('teacher') ||
        participant.identity.includes('강사')) {
      if (participant.videoTrackPublications.size > 0) {
        const videoTrack = participant.videoTrackPublications.values().next().value;
        if (videoTrack && videoTrack.videoTrack) {
          console.log('🎯 생성자 비디오 트랙 찾음:', participant.identity);
          return videoTrack.videoTrack;
        }
      }
    }
  }
  
  // 생성자를 찾지 못한 경우 첫 번째 비디오 트랙 반환
  for (const participant of remoteParticipants) {
    if (participant.videoTrackPublications.size > 0) {
      const videoTrack = participant.videoTrackPublications.values().next().value;
      if (videoTrack && videoTrack.videoTrack) {
        console.log('🎯 첫 번째 비디오 트랙 반환:', participant.identity);
        return videoTrack.videoTrack;
      }
    }
  }
  return null;
}

// 생성자의 identity를 우선적으로 가져오기
function getFirstRemoteParticipantIdentity() {
  if (!room.value) return '';
  
  const remoteParticipants = Array.from(room.value.remoteParticipants.values());
  
  // 먼저 생성자(creator)를 찾아서 우선적으로 반환
  for (const participant of remoteParticipants) {
    // 생성자 여부를 확인
    if (participant.identity.includes('creator') || 
        participant.identity.includes('생성자') ||
        participant.identity.includes('teacher') ||
        participant.identity.includes('강사')) {
      console.log('🎯 생성자 identity 찾음:', participant.identity);
      return participant.identity;
    }
  }
  
  // 생성자를 찾지 못한 경우 첫 번째 참가자 반환
  if (remoteParticipants.length > 0) {
    console.log('🎯 첫 번째 참가자 identity 반환:', remoteParticipants[0].identity);
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

// 화면 공유 이벤트 핸들러
function handleScreenShareStarted(stream: MediaStream) {
  console.log('🖥️ 화면 공유 시작됨:', stream)
  isScreenSharing.value = true
  
  // 화면 공유 트랙을 메인 트랙으로 설정
  if (room.value && room.value.localParticipant) {
    const videoTracks = room.value.localParticipant.videoTrackPublications
    for (const trackPub of videoTracks.values()) {
      if (trackPub.track && trackPub.track.mediaStreamTrack === stream.getVideoTracks()[0]) {
        screenShareTrack.value = trackPub.track
        setMainTrack(trackPub.track, participantName.value)
        break
      }
    }
  }
}

// 직접 video 엘리먼트에 카메라 스트림 연결
function connectDirectVideo() {
  if (localTrack.value && directVideoRef.value) {
    const stream = new MediaStream([localTrack.value.mediaStreamTrack])
    directVideoRef.value.srcObject = stream
    console.log('🖥️ 직접 video 엘리먼트에 카메라 스트림 연결')
  }
}

function handleScreenShareStopped() {
  console.log('🖥️ 화면 공유 중지됨')
  isScreenSharing.value = false
  screenShareTrack.value = null
  
  // 현재 활성화된 카메라 트랙을 찾아서 메인 화면으로 설정
  if (room.value && room.value.localParticipant) {
    const videoTracks = room.value.localParticipant.videoTrackPublications
    for (const trackPub of videoTracks.values()) {
      // 화면 공유가 아닌 카메라 트랙 찾기
      if (trackPub.track && trackPub.track.mediaStreamTrack && 
          !trackPub.track.mediaStreamTrack.label.includes('screen') && 
          !trackPub.track.mediaStreamTrack.label.includes('display')) {
        console.log('🖥️ 카메라 트랙 발견:', trackPub.track.mediaStreamTrack.label)
        console.log('🖥️ 카메라 트랙 객체:', trackPub.track)
        setMainTrack(trackPub.track, participantName.value)
        console.log('🖥️ setMainTrack 호출 완료')
        
        // DOM 업데이트를 강제로 트리거
        nextTick(() => {
          console.log('🖥️ nextTick 후 mainTrack 상태:', mainTrack.value)
          // 직접 video 엘리먼트에 카메라 스트림 연결
          if (mainVideoRef.value && trackPub.track && trackPub.track.mediaStreamTrack) {
            const stream = new MediaStream([trackPub.track.mediaStreamTrack])
            mainVideoRef.value.srcObject = stream
            console.log('🖥️ 화면 공유 중지 후 메인 video 엘리먼트에 카메라 스트림 연결')
          }
        })
        return
      }
    }
  }
  
  // 카메라 트랙을 찾지 못한 경우 localTrack 사용
  if (localTrack.value) {
    console.log('🖥️ localTrack으로 메인 화면 설정')
    setMainTrack(localTrack.value, participantName.value)
  }
}

function handleScreenShareError(error: any) {
  console.error('🖥️ 화면 공유 오류:', error)
  isScreenSharing.value = false
  screenShareTrack.value = null
}

// 화면 공유 토글 함수
function handleScreenShareToggle() {
  if (isScreenSharing.value) {
    // 화면 공유 중지
    console.log('🖥️ 햄버거 메뉴에서 화면 공유 중지 요청')
    // ScreenShareComponent의 stopScreenShare 메서드 호출
    if (screenShareRef.value) {
      screenShareRef.value.stopScreenShare()
    }
  } else {
    // 화면 공유 시작
    console.log('🖥️ 햄버거 메뉴에서 화면 공유 시작 요청')
    // ScreenShareComponent의 startScreenShare 메서드 호출
    if (screenShareRef.value) {
      screenShareRef.value.startScreenShare()
    }
  }
}

function handleCameraRestored(newCameraTrack: any) {
  console.log('🖥️ 새로운 카메라 트랙 복원됨:', newCameraTrack)
  
  // 새로운 카메라 트랙을 localTrack으로 설정
  localTrack.value = newCameraTrack
  
  // 즉시 메인 화면을 새로운 카메라 트랙으로 설정
  nextTick(() => {
    console.log('🖥️ 새로운 카메라 트랙으로 메인 화면 설정')
    setMainTrack(newCameraTrack, participantName.value)
  })
  
  // 추가로 지연 복원도 시도
  setTimeout(() => {
    console.log('🖥️ 지연 복원 시도')
    setMainTrack(newCameraTrack, participantName.value)
  }, 1000)
}
</script>

<!-- 나머지 template 부분은 동일하므로 생략 가능. 필요시 다시 제공 가능. -->


<template>
  <div id="class-video-room">
    <!-- URL 파라미터가 없고 방에 연결되지 않은 경우에만 방 참가 폼 표시 -->
    <div v-if="!room && !hasUrlParams" class="room-layout">
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

    <!-- URL 파라미터가 있지만 아직 방에 연결되지 않은 경우 로딩 표시 -->
    <div v-else-if="!room && hasUrlParams" class="loading-layout">
      <div class="loading-section">
        <div class="loading-spinner">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
          </svg>
        </div>
        <h2>화상수업에 참여하는 중...</h2>
        <p>잠시만 기다려주세요.</p>
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
          
          <!-- 우측 상단 컨트롤 버튼들 -->
          <div class="header-controls">
            <button :class="{ off: !isCameraOn }" @click="toggleCamera" title="카메라 끄기/켜기">
              <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M23 7a2 2 0 0 0-1-1.9l-4-2.87A2 2 0 0 0 15 3H9a2 2 0 0 0-1.5.69L3.5 5.1A2 2 0 0 0 2 7v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7z"/>
                <circle cx="12" cy="13" r="4"/>
              </svg>
            </button>
            <button :class="{ off: !isMicOn }" @click="toggleMic" title="마이크 끄기/켜기">
              <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
                <line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </button>
            <button class="leave" @click="handleLeaveClick" title="퇴장하기">
              <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          
          <!-- 컨트롤 패널 오버레이 -->
          <div 
            v-if="isControlPanelOpen" 
            class="control-panel-overlay"
            @click="toggleControlPanel"
          >
          </div>
          
          <!-- 컨트롤 패널 -->
          <div 
            v-if="isControlPanelOpen" 
            class="control-panel-fixed"
            @click.stop
          >
            <div class="control-buttons">
              <button v-if="isUserCreator" @click="handleRecordToggle" :title="recordButtonLabel">
                <svg v-if="recordingState === 'idle'" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <circle cx="12" cy="12" r="10"/>
                  <circle cx="12" cy="12" r="3" fill="currentColor"/>
                </svg>
                <svg v-else-if="recordingState === 'recording'" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <rect x="6" y="4" width="4" height="16"/>
                  <rect x="14" y="4" width="4" height="16"/>
                </svg>
                <svg v-else width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <polygon points="5,3 19,12 5,21"/>
                </svg>
              </button>
              <button :class="{ off: !isCaptionVisible }" @click="toggleCaption" title="자막 숨기기/보기">
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14,2 14,8 20,8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10,9 9,9 8,9"/>
                </svg>
              </button>
              <button :class="{ off: !isChatVisible }" @click="toggleChat" title="채팅 숨기기/보기">
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
              <button 
                :class="{ active: isScreenSharing }" 
                @click="handleScreenShareToggle" 
                :title="isScreenSharing ? '화면 공유 중지' : '화면 공유 시작'"
              >
                <svg v-if="isScreenSharing" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                </svg>
                <svg v-else width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                  <line x1="8" y1="21" x2="16" y2="21"/>
                  <line x1="12" y1="17" x2="12" y2="21"/>
                </svg>
              </button>
            </div>
          </div>
      </div>

      <div class="video-body">
        <div class="main-content">
          <div class="video-section">
            <div class="main-video">

              
              <!-- 화면 공유 중인 경우 화면 공유 트랙을 메인에 표시 -->
              <div v-if="isScreenSharing && screenShareTrack" class="main-tile screen-share" style="position: relative;">
                <VideoComponent
                  :track="screenShareTrack"
                  :participantIdentity="participantName + ' (화면 공유)'"
                />
                <!-- 햄버거 버튼 (우측 하단 고정) -->
                <button 
                  class="hamburger-btn-fixed" 
                  @click="toggleControlPanel()" 
                  title="컨트롤 패널"
                >
                  <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <line x1="3" y1="6" x2="21" y2="6"/>
                    <line x1="3" y1="12" x2="21" y2="12"/>
                    <line x1="3" y1="18" x2="21" y2="18"/>
                  </svg>
                </button>
              </div>
              <!-- 생성자인 경우 직접 video 엘리먼트 사용 -->
              <div v-else-if="mainTrack" class="main-tile" style="position: relative;">
                <video 
                  ref="mainVideoRef"
                  autoplay 
                  muted 
                  style="width: 100%; height: 100%; object-fit: cover;"
                />
                <div style="position: absolute; bottom: 10px; left: 10px; background: rgba(0,0,0,0.7); color: white; padding: 5px; border-radius: 4px;">
                  {{ mainIdentity }}
                </div>
                <!-- 햄버거 버튼 (우측 하단 고정) -->
                <button 
                  class="hamburger-btn-fixed" 
                  @click="toggleControlPanel()" 
                  title="컨트롤 패널"
                >
                  <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <line x1="3" y1="6" x2="21" y2="6"/>
                    <line x1="3" y1="12" x2="21" y2="12"/>
                    <line x1="3" y1="18" x2="21" y2="18"/>
                  </svg>
                </button>
              </div>
              <!-- 참여자인 경우 원격 참가자 화면을 메인에 표시 -->
              <div v-else-if="!isUserCreator && getFirstRemoteVideoTrack()" class="main-tile" style="position: relative;">
                <VideoComponent
                  :track="getFirstRemoteVideoTrack()"
                  :participantIdentity="getFirstRemoteParticipantIdentity()"
                />
                <!-- 햄버거 버튼 (우측 하단 고정) -->
                <button 
                  class="hamburger-btn-fixed" 
                  @click="toggleControlPanel()" 
                  title="컨트롤 패널"
                >
                  <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <line x1="3" y1="6" x2="21" y2="6"/>
                    <line x1="3" y1="12" x2="21" y2="12"/>
                    <line x1="3" y1="18" x2="21" y2="18"/>
                  </svg>
                </button>
              </div>
              <!-- 직접 video 엘리먼트로 카메라 표시 (fallback) -->
              <div v-else-if="localTrack" class="main-tile" style="position: relative;">
                <video 
                  ref="directVideoRef"
                  autoplay 
                  muted 
                  style="width: 100%; height: 100%; object-fit: cover;"
                />
                <div style="position: absolute; bottom: 10px; left: 10px; background: rgba(0,0,0,0.7); color: white; padding: 5px; border-radius: 4px;">
                  {{ participantName }} (직접 렌더링)
                </div>
                <!-- 햄버거 버튼 (우측 하단 고정) -->
                <button 
                  class="hamburger-btn-fixed" 
                  @click="toggleControlPanel()" 
                  title="컨트롤 패널"
                >
                  <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <line x1="3" y1="6" x2="21" y2="6"/>
                    <line x1="3" y1="12" x2="21" y2="12"/>
                    <line x1="3" y1="18" x2="21" y2="18"/>
                  </svg>
                </button>
              </div>
              <!-- fallback: 아무것도 표시되지 않을 때 -->
              <div v-else style="display: flex; align-items: center; justify-content: center; height: 100%; background: #000; color: white;">
                <div style="text-align: center;">
                  <div style="font-size: 24px; margin-bottom: 10px;">📹</div>
                  <div>비디오를 불러오는 중...</div>
                  <div style="font-size: 12px; margin-top: 10px; opacity: 0.7;">
                    mainTrack: {{ !!mainTrack }}<br>
                    mainIdentity: {{ mainIdentity }}
                  </div>
                </div>
              </div>
            </div>



            <div class="thumbnail-grid">
              <!-- 화면 공유 중일 때 카메라 화면을 썸네일에 표시 -->
              <VideoComponent
                v-if="isScreenSharing && localTrack && localTrack !== screenShareTrack"
                :track="localTrack"
                :participantIdentity="participantName + ''"
                class="thumbnail camera"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />
              <div v-if="isScreenSharing && localTrack && localTrack !== screenShareTrack" class="thumbnail-label">카메라</div>
              
              <!-- 참여자인 경우 로컬 화면을 썸네일에 표시 -->
              <VideoComponent
                v-else-if="!isUserCreator && localTrack"
                :track="localTrack"
                :participantIdentity="participantName"
                class="thumbnail participant"
                :local="true"
                @click="setMainTrack(localTrack, participantName)"
              />
              <div v-else-if="!isUserCreator && localTrack" class="thumbnail-label">참여자</div>
              
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
        
        <!-- 화면 공유 컴포넌트 (항상 숨겨진 상태로 동작) -->
        <div class="screen-share-section" style="display: none;">
          <ScreenShareComponent
            ref="screenShareRef"
            :room="room"
            @screen-share-started="handleScreenShareStarted"
            @screen-share-stopped="handleScreenShareStopped"
            @screen-share-error="handleScreenShareError"
            @camera-restored="handleCameraRestored"
          />
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
        :meetingId="route.query.meetingId as string"
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

.screen-share-section {
  width: 300px;
  background: rgba(0, 0, 0, 0.8);
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  overflow-y: auto;
}

.control-buttons button.openvidu-btn {
  background: #28a745;
}

.control-buttons button.openvidu-btn:hover {
  background: #218838;
}

.main-tile.screen-share {
  border: 3px solid #28a745;
  box-shadow: 0 0 20px rgba(40, 167, 69, 0.3);
}

.thumbnail.camera {
  border: 2px solid #007bff;
}

/* 헤더 컨트롤 버튼들 스타일 */
.video-room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: #477856fc;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-info h2 {
  margin: 0;
  color: white;
  font-size: 1.5rem;
}

.user-role {
  display: flex;
  gap: 10px;
}

.creator-badge, .participant-badge {
  padding: 5px 10px;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
}

.creator-badge {
  background: rgba(255, 193, 7, 0.2);
  color: #ffc107;
  border: 1px solid rgba(255, 193, 7, 0.3);
}

.participant-badge {
  background: rgba(108, 117, 125, 0.2);
  color: #6c757d;
  border: 1px solid rgba(108, 117, 125, 0.3);
}

.header-controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.header-controls button {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-controls button:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.header-controls button.off {
  background: rgba(255, 255, 255, 0.05);
  opacity: 0.5;
}

.header-controls button.leave {
  background: rgba(220, 53, 69, 0.8);
}

.header-controls button.leave:hover {
  background: rgba(220, 53, 69, 1);
}

/* 컨트롤 패널 오버레이 */
.control-panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.3);
  z-index: 1000;
  cursor: pointer;
}

/* 고정된 햄버거 버튼 스타일 */
.hamburger-btn-fixed {
  position: absolute;
  bottom: 10px;
  right: 10px;
  width: 40px;
  height: 40px;
  background: rgba(0, 0, 0, 0.8);
  border: none;
  border-radius: 50%;
  color: white;
  font-size: 18px;
  cursor: pointer;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.hamburger-btn-fixed:hover {
  background: rgba(0, 0, 0, 0.9);
  transform: scale(1.1);
}

/* 고정된 컨트롤 패널 스타일 */
.control-panel-fixed {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.7);
  border-radius: 12px;
  padding: 15px;
  z-index: 1001;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(15px);
  display: flex;
  flex-direction: row;
  gap: 8px;
  align-items: center;
  animation: slideInFromCenter 0.3s ease-out;
}

.control-panel-fixed .control-panel-header {
  display: none; /* 헤더 숨기기 */
}

.control-panel-fixed .control-buttons {
  display: flex;
  flex-direction: row;
  gap: 8px;
  align-items: center;
}

.control-panel-fixed .control-buttons button {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(5px);
}

.control-panel-fixed .control-buttons button:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(1.1);
  box-shadow: 0 4px 15px rgba(255, 255, 255, 0.2);
}

.control-panel-fixed .control-buttons button.off {
  background: rgba(255, 255, 255, 0.08);
  opacity: 0.6;
}

.control-panel-fixed .control-buttons button.leave {
  background: rgba(220, 53, 69, 0.7);
}

.control-panel-fixed .control-buttons button.leave:hover {
  background: rgba(220, 53, 69, 0.9);
  box-shadow: 0 4px 15px rgba(220, 53, 69, 0.3);
}

/* 햄버거 메뉴 화면 공유 버튼 스타일 */
.control-buttons button.active {
  background: #dc3545 !important;
  color: white;
  animation: pulse 2s infinite;
}

.control-buttons button.active:hover {
  background: #c82333 !important;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(220, 53, 69, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(220, 53, 69, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(220, 53, 69, 0);
  }
}

/* 로딩 화면 스타일 */
.loading-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--bg-color);
}

.loading-section {
  text-align: center;
  padding: 3rem;
  background: var(--bg-primary);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
}

.loading-spinner {
  animation: spin 1s linear infinite;
  margin-bottom: 1.5rem;
  color: var(--brand-main);
}

.loading-section h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.5rem 0;
}

.loading-section p {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes slideInFromCenter {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.8);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}
</style>
