<template>
  <div class="screen-share-container">
    <div class="screen-share-controls">
      <button 
        v-if="!isScreenSharing" 
        @click="startScreenShare" 
        class="screen-share-btn"
        :disabled="isLoading"
      >
        <span class="btn-icon">🖥️</span>
        <span class="btn-text">{{ isLoading ? '공유 중...' : '화면 공유 시작' }}</span>
      </button>
      
      <button 
        v-else 
        @click="stopScreenShare" 
        class="screen-share-btn stop"
      >
        <span class="btn-icon">⏹️</span>
        <span class="btn-text">화면 공유 중지</span>
      </button>
    </div>

    <div v-if="isScreenSharing" class="screen-share-info">
      <div class="info-badge">
        <span class="info-icon">📺</span>
        <span class="info-text">화면 공유 중</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  room: {
    type: Object,
    required: true
  }
})

const emit = defineEmits([
  'screen-share-started',
  'screen-share-stopped',
  'screen-share-error',
  'camera-restored'
])

// 상태 관리
const isScreenSharing = ref(false)
const isLoading = ref(false)
const screenStream = ref(null)
const screenTrack = ref(null)

// 화면 공유 시작
async function startScreenShare() {
  try {
    isLoading.value = true
    console.log('🖥️ 화면 공유 시작...')

    // 화면 공유 스트림 가져오기
    const stream = await navigator.mediaDevices.getDisplayMedia({
      video: {
        cursor: 'always',
        displaySurface: 'monitor'
      },
      audio: false
    })

    screenStream.value = stream

    // LiveKit LocalVideoTrack 생성
    const { LocalVideoTrack } = await import('livekit-client')
    const videoTrack = new LocalVideoTrack(stream.getVideoTracks()[0], {
      name: 'screen-share',
      source: 'screen'
    })
    screenTrack.value = videoTrack

    // 화면 공유 트랙을 추가로 퍼블리시 (기존 카메라 트랙은 유지)
    if (props.room && props.room.localParticipant) {
      // 화면 공유 트랙 퍼블리시 (다른 참여자들에게 전송)
      await props.room.localParticipant.publishTrack(videoTrack, {
        priority: 'high',
        dtx: false,
        adaptiveStream: true
      })
      
      // 기존 카메라 트랙은 그대로 유지하여 썸네일에 표시
      console.log('🖥️ 화면 공유 트랙 추가, 카메라 트랙 유지')
    }

    isScreenSharing.value = true

    // 스트림 종료 감지
    stream.getVideoTracks()[0].onended = () => {
      stopScreenShare()
    }

    console.log('🖥️ 화면 공유 시작 완료')
    emit('screen-share-started', stream)

  } catch (error) {
    console.error('🖥️ 화면 공유 시작 실패:', error)
    emit('screen-share-error', error)
  } finally {
    isLoading.value = false
  }
}

// 화면 공유 중지
async function stopScreenShare() {
  try {
    console.log('🖥️ 화면 공유 중지...')

    // 화면 공유 트랙 언퍼블리시
    if (props.room && props.room.localParticipant && screenTrack.value) {
      await props.room.localParticipant.unpublishTrack(screenTrack.value)
    }

    // 스트림 정리
    if (screenStream.value) {
      screenStream.value.getTracks().forEach(track => track.stop())
      screenStream.value = null
    }

    // 트랙 정리
    if (screenTrack.value) {
      screenTrack.value.stop()
      screenTrack.value = null
    }

    // 화면 공유 중지 완료 (기존 카메라 트랙은 그대로 유지)
    console.log('🖥️ 화면 공유 중지 완료, 기존 카메라 트랙 유지')

    isScreenSharing.value = false
    console.log('🖥️ 화면 공유 중지 완료')
    emit('screen-share-stopped')

  } catch (error) {
    console.error('🖥️ 화면 공유 중지 실패:', error)
    emit('screen-share-error', error)
  }
}

onUnmounted(() => {
  if (isScreenSharing.value) {
    stopScreenShare()
  }
})

// 외부에서 화면 공유 중지 호출 가능하도록 expose
defineExpose({
  startScreenShare,
  stopScreenShare
})
</script>

<style scoped>
.screen-share-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 8px;
  color: white;
}

.screen-share-controls {
  display: flex;
  justify-content: center;
}

.screen-share-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 6px;
  background: #007bff;
  color: white;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s ease;
}

.screen-share-btn:hover:not(:disabled) {
  background: #0056b3;
  transform: translateY(-1px);
}

.screen-share-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.screen-share-btn.stop {
  background: #dc3545;
}

.screen-share-btn.stop:hover {
  background: #c82333;
}

.btn-icon {
  font-size: 1.2rem;
}

.screen-share-info {
  display: flex;
  justify-content: center;
}

.info-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  font-size: 0.85rem;
}

.info-icon {
  font-size: 1rem;
}
</style>
