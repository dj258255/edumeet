<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h2 class="modal-title">📡 라이브 정보</h2>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <!-- 라이브 정보 목록 -->
        <div v-if="liveInfoList.length === 0" class="empty-state">
          <div class="empty-icon">📺</div>
          <p class="empty-text">등록된 라이브 정보가 없습니다.</p>
        </div>

        <div v-else class="live-info-list">
          <div 
            v-for="info in liveInfoList" 
            :key="info.id" 
            class="live-info-item"
          >
            <div class="info-header">
              <h3 class="info-title">{{ info.title }}</h3>
              <span class="info-status" :class="info.status">
                {{ getStatusText(info.status) }}
              </span>
            </div>
            
            <div class="info-description">
              <p>{{ info.description }}</p>
            </div>

            <div class="info-meta">
              <div class="meta-item">
                <span class="meta-label">생성일:</span>
                <span class="meta-value">{{ formatDate(info.createdAt) }}</span>
              </div>
              <div v-if="info.startTime" class="meta-item">
                <span class="meta-label">시작 시간:</span>
                <span class="meta-value">{{ formatTime(info.startTime) }}</span>
              </div>
            </div>

            <div v-if="info.files && info.files.length > 0" class="info-files">
              <h4 class="files-title">📎 첨부 파일</h4>
              <div class="file-list">
                <div 
                  v-for="file in info.files" 
                  :key="file.id"
                  class="file-item"
                >
                  <div class="file-info">
                    <span class="file-icon">📄</span>
                    <span class="file-name">{{ file.fileName }}</span>
                    <span class="file-size">{{ formatFileSize(file.size) }}</span>
                  </div>
                  <button 
                    class="download-btn"
                    @click="downloadFile(file)"
                    :disabled="file.downloading"
                  >
                    <span v-if="file.downloading">다운로드 중...</span>
                    <span v-else>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                        <polyline points="7,10 12,15 17,10"></polyline>
                        <line x1="12" y1="15" x2="12" y2="3"></line>
                      </svg>
                      다운로드
                    </span>
                  </button>
                </div>
              </div>
            </div>

            <div v-else class="no-files">
              <p class="no-files-text">첨부된 파일이 없습니다.</p>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="close-footer-btn" @click="closeModal">
          닫기
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '' // 예: http://localhost:8080

const props = defineProps({
  isVisible: { type: Boolean, default: false },
  classId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['close'])

/** UI에서 쓰는 데이터 */
const liveInfoList = ref([])
const loading = ref(false)
const errorMsg = ref('')

/** 상태 텍스트 */
const getStatusText = (status) => {
  const statusMap = { live: '진행중', scheduled: '예정', ended: '종료' }
  return statusMap[status] || '알 수 없음'
}

/** 날짜/시간 포맷 */
const formatDate = (s) => {
  const d = new Date(s)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}
const formatTime = (s) => {
  const d = new Date(s)
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}
const formatFileSize = (bytes) => {
  if (!bytes) return '0 Bytes'
  const k = 1024, sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/** 파일 다운로드(예시) */
const downloadFile = async (file) => {
  try {
    file.downloading = true
    // 실제 구현 시:
    // const { data } = await apiClient.get(`/api/v1/files/${file.id}`, { responseType: 'blob' })
    // file-saver 등을 사용하여 저장
    setTimeout(() => {
      alert(`${file.fileName} 파일이 다운로드됩니다.`)
      file.downloading = false
    }, 800)
  } catch (e) {
    console.error(e)
    alert('파일 다운로드에 실패했습니다.')
    file.downloading = false
  }
}

const closeModal = () => emit('close')

/** 백엔드 → 프런트 매핑 함수
 * ClassMeetingInfoResponseDto[] → liveInfoList(화면 모델)
 * 백엔드 DTO 필드명이 다르면 아래에서 맞춰주세요.
 */
function mapToViewModel(items = []) {
  const now = new Date()
  return items.map((it) => {
    const id = it.id ?? it.meetingId ?? it.roomId
    const title = it.title ?? it.meetingTitle ?? '제목 없음'
    const description = it.description ?? it.meetingDescription ?? ''
    const createdAt = it.createdAt ?? it.createTime ?? it.startTime ?? new Date().toISOString()
    const startTime = it.startTime ?? it.beginTime ?? null
    const endTime = it.endTime ?? it.finishTime ?? null

    // 상태 계산: start/end 기준으로 scheduled/live/ended
    let status = 'scheduled'
    if (endTime) status = 'ended'
    else if (startTime && new Date(startTime) <= now) status = 'live'

    // 첨부파일(백엔드가 제공하면 매핑)
    const files = Array.isArray(it.files)
      ? it.files.map(f => ({
          id: f.id ?? f.fileId ?? `${id}-${f.fileName}`,
          fileName: f.fileName ?? f.name ?? '파일',
          size: f.size ?? 0,
          downloadUrl: f.downloadUrl ?? '#',
          downloading: false
        }))
      : []

    return { id, title, description, status, createdAt, startTime, files }
  })
}

/** API 호출 */
async function fetchLiveInfos(classId) {
  if (!classId) return
  loading.value = true
  errorMsg.value = ''
  try {
    const accessToken = localStorage.getItem('accessToken')
    const { data } = await axios.get(`${import.meta.env.VITE_BASE_URL}/api/v1/meetingroom/${classId}`, {
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
    })
    // data: ClassMeetingInfoResponseDto[]
    liveInfoList.value = mapToViewModel(data || [])
  } catch (e) {
    console.error('라이브 정보 조회 실패:', e)
    errorMsg.value = '라이브 정보를 불러오지 못했습니다.'
    liveInfoList.value = []
  } finally {
    loading.value = false
  }
}

/** 열릴 때 & classId 바뀔 때 로드 */
watch(
  () => [props.isVisible, props.classId],
  ([visible, classId]) => {
    if (visible && classId) fetchLiveInfos(classId)
  },
  { immediate: true }
)

onMounted(() => {
  if (props.isVisible && props.classId) fetchLiveInfos(props.classId)
})
</script>


<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  transition: opacity 0.3s ease;
}

.modal-container {
  background: var(--bg-primary);
  border-radius: 12px;
  width: 90%;
  max-width: 800px;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  overflow: hidden;
  animation: slide-in 0.3s ease-out;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.2rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.modal-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  color: var(--text-primary);
  background: var(--bg-tertiary);
}

.modal-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex-grow: 1;
}

.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.empty-text {
  font-size: 1.1rem;
  margin: 0;
}

.live-info-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.live-info-item {
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 1.5rem;
  border: 1px solid var(--border-color);
  transition: box-shadow 0.2s ease;
}

.live-info-item:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  gap: 1rem;
}

.info-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  flex: 1;
}

.info-status {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 500;
  white-space: nowrap;
}

.info-status.live {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.info-status.scheduled {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.info-status.ended {
  background: rgba(156, 163, 175, 0.1);
  color: #6b7280;
}

.info-description {
  margin-bottom: 1rem;
}

.info-description p {
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.info-meta {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.meta-label {
  color: var(--text-secondary);
  font-weight: 500;
}

.meta-value {
  color: var(--text-primary);
  font-weight: 600;
}

.info-files {
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 1rem;
}

.files-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 1rem 0;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
  background: var(--bg-primary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 0;
}

.file-icon {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.file-name {
  font-weight: 500;
  color: var(--text-primary);
  word-break: break-word;
  flex: 1;
}

.file-size {
  font-size: 0.8rem;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--brand-main);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.download-btn:hover:not(:disabled) {
  background: var(--brand-accent);
}

.download-btn:disabled {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  cursor: not-allowed;
}

.no-files {
  text-align: center;
  padding: 2rem 1rem;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.no-files-text {
  color: var(--text-secondary);
  font-style: italic;
  margin: 0;
}

.modal-footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
  background: var(--bg-secondary);
}

.close-footer-btn {
  padding: 0.75rem 1.5rem;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.close-footer-btn:hover {
  background: var(--bg-card);
  border-color: var(--border-dark);
}

@keyframes slide-in {
  from { 
    transform: translateY(-50px); 
    opacity: 0; 
  }
  to { 
    transform: translateY(0); 
    opacity: 1; 
  }
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .modal-container {
    width: 95%;
    max-height: 95vh;
  }
  
  .info-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .info-meta {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .file-item {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }
  
  .file-info {
    justify-content: space-between;
  }
}
</style>