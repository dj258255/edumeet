<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-container" @click.stop>
      <div class="modal-header">
        <h2 class="modal-title">📡 라이브 정보 & 요약본</h2>
        <button class="close-btn" @click="closeModal">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <div class="modal-body">

        <!-- 라이브 정보 목록 -->
        <div class="live-section">
          <h3 class="section-title">📄 문서 요약본</h3>
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

              <!-- ✅ s3url이 있을 때만 다운로드 섹션 표시 -->
              <div v-if="info.hasRecordingFile" class="info-files">
                <h4 class="files-title">🎥 녹화 파일</h4>
                <div class="file-list">
                  <div class="file-item">
                    <div class="file-info">
                      <span class="file-icon">🎥</span>
                      <span class="file-name">{{ info.recordingFileName }}</span>
                      <span v-if="info.fileSize" class="file-size">{{ formatFileSize(info.fileSize) }}</span>
                    </div>
                    <button 
                      class="download-btn"
                      @click="downloadMeetingFile(info)"
                      :disabled="info.downloading"
                    >
                      <span v-if="info.downloading">다운로드 중...</span>
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

              <!-- ✅ s3url이 없을 때 표시 -->
              <div v-else class="no-files">
                <p class="no-files-text">파일이 아직 준비되지 않았습니다.</p>
              </div>
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

/** API Base URL (.env: VITE_BASE_URL) */
const API_BASE_URL = import.meta.env.VITE_BASE_URL || 'https://api.studywithtymee.com'

const props = defineProps({
  isVisible: { type: Boolean, default: false },
  classId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['close'])

/** 화면 상태 */
const liveInfoList = ref([])
const loading = ref(false)
const errorMsg = ref('')
const summaryInfo = ref(null)
const summaryLoading = ref(false)

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

/** ✅ 커스텀 파일명 생성: {날짜}_{화상강의 제목} */
const generateCustomFileName = (meetingInfo, backendFileName = '', contentType = '') => {
  // 1. 날짜 추출 (startTime에서 yyyy-MM-dd 형식)
  let date = 'unknown-date'
  if (meetingInfo.startTime) {
    try {
      const dateObj = new Date(meetingInfo.startTime)
      date = dateObj.toISOString().split('T')[0] // yyyy-MM-dd
    } catch (e) {
      console.warn('날짜 파싱 실패:', e)
    }
  }

  // 2. 화상강의 제목 정리 (파일시스템 안전한 문자로)
  const safeTitle = meetingInfo.title
    .replace(/[<>:"/\\|?*]/g, '') // 파일시스템 금지 문자 제거
    .replace(/\s+/g, '_') // 공백을 언더스코어로
    .substring(0, 50) // 최대 50자
    .trim()

  // 3. 최종 파일명: {날짜}_{화상강의 제목} (확장자 없음)
  const finalFileName = `${date}_${safeTitle}`

  console.log("finalFileName: ", finalFileName)
  
  console.log('🎯 파일명 생성:', {
    date,
    originalTitle: meetingInfo.title,
    safeTitle,
    backendFileName,
    contentType,
    finalFileName
  })
  
  return finalFileName
}

/** ✅ 미팅 녹화 파일 다운로드 - MeetingDownloadController 사용 */
const downloadMeetingFile = async (meetingInfo) => {
  console.log('🎯 다운로드 시작:', meetingInfo)

  if (!meetingInfo?.id) {
    console.error('❌ 미팅 ID 없음:', meetingInfo)
    alert('미팅 ID가 없어 다운로드할 수 없습니다.')
    return
  }

  if (!meetingInfo.hasRecordingFile) {
    alert('다운로드할 녹화 파일이 없습니다.')
    return
  }

  try {
    meetingInfo.downloading = true

    const accessToken = localStorage.getItem('accessToken')
    const url = `${API_BASE_URL}/api/v1/meeting/files/download/${meetingInfo.id}`
    
    console.log('📡 요청 URL:', url)
    console.log('🔑 토큰 존재:', !!accessToken)

    // 실제 다운로드 요청
    console.log('⬇️ 파일 다운로드 시작...')
    const response = await axios.get(url, {
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      responseType: 'blob',
      timeout: 120000, // 2분 타임아웃
      onDownloadProgress: (progressEvent) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        console.log(`📥 다운로드 진행률: ${percentCompleted}%`)
      }
    })

    console.log('✅ 응답 수신:', {
      status: response.status,
      headers: response.headers,
      dataSize: response.data?.size || 'unknown'
    })

    // 응답 데이터 크기 확인
    if (!response.data || response.data.size === 0) {
      console.error('❌ 빈 파일 응답')
      alert('다운로드된 파일이 비어있습니다.')
      return
    }

    // 파일명 추출 및 새로운 형식으로 변경
    const contentDisposition = response.headers?.['content-disposition'] || ''
    let backendFileName = ''
    
    console.log('📝 Content-Disposition:', contentDisposition)
    
    // 백엔드에서 제공한 파일명 추출 (참고용)
    const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)|filename="([^"]+)"/i)
    if (filenameMatch) {
      const encodedFilename = filenameMatch[1] || filenameMatch[2]
      try {
        backendFileName = decodeURIComponent(encodedFilename)
        console.log('🏷️ 백엔드 파일명:', backendFileName)
      } catch (e) {
        console.warn('파일명 디코딩 실패:', e)
      }
    }

    // ✅ 프론트엔드에서 새로운 형식으로 파일명 생성: {날짜}_{화상강의 제목}
    const filename = generateCustomFileName(meetingInfo, backendFileName, response.headers['content-type'] || '')
    console.log('📄 최종 파일명:', filename)

    // Blob을 이용한 파일 다운로드
    const blob = new Blob([response.data], { 
      type: response.headers['content-type'] || 'application/octet-stream' 
    })
    
    console.log('📦 Blob 생성:', {
      size: blob.size,
      type: blob.type
    })

    if (blob.size === 0) {
      console.error('❌ Blob 크기가 0')
      alert('다운로드된 파일 크기가 0입니다.')
      return
    }

    // 다운로드 실행
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = filename
    link.style.display = 'none'
    document.body.appendChild(link)
    
    console.log('🖱️ 다운로드 링크 클릭')
    link.click()
    
    // 정리
    setTimeout(() => {
      document.body.removeChild(link)
      URL.revokeObjectURL(downloadUrl)
      console.log('🧹 리소스 정리 완료')
    }, 100)

    console.log(`🎉 미팅 파일 다운로드 완료: ${filename}`)
    
    // ✅ 커스텀 파일명으로 성공 메시지 표시
    alert(`파일이 다운로드되었습니다!\n파일명: ${filename}`)

  } catch (error) {
    console.error('💥 미팅 파일 다운로드 실패:', {
      message: error.message,
      status: error?.response?.status,
      statusText: error?.response?.statusText,
      data: error?.response?.data,
      config: error?.config
    })

    const status = error?.response?.status
    if (status === 401) {
      alert('로그인이 필요합니다.')
    } else if (status === 403) {
      alert('파일 다운로드 권한이 없습니다.')
    } else if (status === 404) {
      alert('녹화 파일을 찾을 수 없습니다.')
    } else if (status === 500) {
      alert('서버에서 파일 처리 중 오류가 발생했습니다.')
    } else if (error.code === 'ECONNABORTED') {
      alert('다운로드 시간이 초과되었습니다. 파일이 너무 클 수 있습니다.')
    } else {
      alert(`파일 다운로드에 실패했습니다: ${error.message}`)
    }
  } finally {
    meetingInfo.downloading = false
    console.log('🏁 다운로드 프로세스 종료')
  }
}

const closeModal = () => emit('close')

/** ✅ 녹화 파일명 생성 함수: {날짜}_{화상강의 제목} */
const generateRecordingFileName = (item, startTime) => {
  // 1. 날짜 추출
  let date = 'unknown-date'
  if (startTime) {
    try {
      const dateObj = new Date(startTime)
      date = dateObj.toISOString().split('T')[0] // yyyy-MM-dd
    } catch (e) {
      console.warn('날짜 파싱 실패:', e)
    }
  }

  // 2. 제목 정리
  const title = item.title ?? item.meetingTitle ?? '제목없음'
  const safeTitle = title
    .replace(/[<>:"/\\|?*]/g, '') // 파일시스템 금지 문자 제거
    .replace(/\s+/g, '_') // 공백을 언더스코어로
    .substring(0, 30) // 최대 30자 (화면 표시용)
    .trim()

  return `${date}_${safeTitle}`
}

/** ✅ 백엔드 → 프런트 매핑 (s3url 체크 로직 중심) */
function mapToViewModel(items = []) {
  // items가 배열이 아니면 빈 배열 반환
  if (!Array.isArray(items)) {
    console.warn('🔍 mapToViewModel - items가 배열이 아님:', items)
    return []
  }
  
  const now = new Date()
  return items.map((item) => {
    const id = item.id ?? item.meetingId ?? item.roomId
    const title = item.title ?? item.meetingTitle ?? '제목 없음'
    const description = item.description ?? item.meetingDescription ?? ''
    const createdAt = item.createdAt ?? item.createTime ?? item.startTime ?? new Date().toISOString()
    const startTime = item.startTime ?? item.beginTime ?? null
    const endTime = item.endTime ?? item.finishTime ?? null

    // 상태 계산
    let status = 'scheduled'
    if (endTime) status = 'ended'
    else if (startTime && new Date(startTime) <= now) status = 'live'

    // ✅ s3url 체크 - Meeting 엔티티의 s3url 필드 확인
    const s3Url = item.s3url || item.s3Url || item.recordingUrl || item.fileUrl
    const hasRecordingFile = Boolean(s3Url && s3Url.trim() !== '' && s3Url !== 'null')

    // 녹화 파일 정보
    const recordingFileName = generateRecordingFileName(item, startTime)
    const fileSize = item.fileSize || item.contentLength || 0

    console.log(`🔍 미팅 ${id} s3url 체크:`, {
      original_s3url: item.s3url,
      s3Url: s3Url,
      hasRecordingFile: hasRecordingFile,
      title: title
    })

    return {
      id,
      title,
      description,
      status,
      createdAt,
      startTime,
      hasRecordingFile,
      recordingFileName,
      fileSize,
      s3Url,
      downloading: false
    }
  })
}

/** 라이브 정보 조회 */
async function fetchLiveInfos(classId) {
  if (!classId) {
    console.warn('❌ classId가 없어서 요청을 보내지 않습니다.')
    return
  }
  
  loading.value = true
  errorMsg.value = ''
  
  try {
    console.log(`📡 라이브 정보 조회 시작 - classId: ${classId}`)
    console.log(`🌐 API_BASE_URL: ${API_BASE_URL}`)
    
    const accessToken = localStorage.getItem('accessToken')
    const url = `${API_BASE_URL}/api/v1/meetingroom/${classId}`
    
    console.log(`🔗 요청 URL: ${url}`)
    console.log(`🔑 토큰 존재: ${!!accessToken}`)
    
    const response = await axios.get(url, {
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
    })
    
    console.log('📋 전체 응답:', response)
    console.log('📋 원본 데이터:', response.data)
    console.log('📋 원본 데이터 타입:', typeof response.data)
    console.log('📋 원본 데이터가 배열인가:', Array.isArray(response.data))
    
    const data = response.data
    
    // API 응답이 단일 객체인 경우 배열로 변환
    const items = Array.isArray(data) ? data : (data ? [data] : [])
    liveInfoList.value = mapToViewModel(items)
    
    console.log('🎯 매핑된 데이터:', liveInfoList.value)
    
  } catch (e) {
    console.error('💥 라이브 정보 조회 실패:', {
      message: e.message,
      status: e?.response?.status,
      statusText: e?.response?.statusText,
      data: e?.response?.data,
      config: e?.config
    })
    errorMsg.value = '라이브 정보를 불러오지 못했습니다.'
    liveInfoList.value = []
  } finally {
    loading.value = false
  }
}

/** 요약본 조회 */
async function fetchSummary(classId) {
  if (!classId) {
    console.warn('❌ classId가 없어서 요약본 요청을 보내지 않습니다.')
    return
  }
  
  summaryLoading.value = true
  
  try {
    console.log(`📖 요약본 조회 시작 - classId: ${classId}`)
    
    const accessToken = localStorage.getItem('accessToken')
    const url = `${API_BASE_URL}/api/v1/meeting/summary/${classId}`
    
    console.log(`🔗 요약본 요청 URL: ${url}`)
    console.log(`🔑 토큰 존재: ${!!accessToken}`)
    
    const response = await axios.get(url, {
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
    })
    
    console.log('📋 요약본 응답:', response)
    console.log('📋 요약본 데이터:', response.data)
    
    if (response.data.success) {
      summaryInfo.value = response.data.data
      console.log('✅ 요약본 조회 성공:', summaryInfo.value)
    } else {
      console.warn('⚠️ 요약본 조회 실패:', response.data.message)
      summaryInfo.value = null
    }
    
  } catch (e) {
    console.error('💥 요약본 조회 실패:', {
      message: e.message,
      status: e?.response?.status,
      statusText: e?.response?.statusText,
      data: e?.response?.data
    })
    summaryInfo.value = null
  } finally {
    summaryLoading.value = false
  }
}

/** 열릴 때 & classId 바뀔 때 로드 */
watch(
  () => [props.isVisible, props.classId],
  ([visible, classId]) => {
    if (visible && classId) {
      console.log(`👁️ 모달 열림 - classId: ${classId}`)
      fetchLiveInfos(classId)
      fetchSummary(classId)
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (props.isVisible && props.classId) {
    console.log(`🚀 컴포넌트 마운트 - classId: ${props.classId}`)
    fetchLiveInfos(props.classId)
  }
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
  z-index: 9999;
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

/* 섹션 제목 */
.section-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 1rem 0;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid var(--border-color);
}

/* 요약본 섹션 */
.summary-section {
  margin-bottom: 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.loading-state {
  text-align: center;
  padding: 2rem 1rem;
  color: var(--text-secondary);
}

.loading-icon {
  font-size: 2rem;
  margin-bottom: 1rem;
  animation: spin 1s linear infinite;
}

.loading-text {
  font-size: 1rem;
  margin: 0;
}

.summary-content {
  background: var(--bg-secondary);
  border-radius: 8px;
  padding: 1rem;
}

.summary-info {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: var(--bg-primary);
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

.summary-icon {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.summary-label {
  font-weight: 500;
  color: var(--text-primary);
  flex: 1;
}

.summary-link {
  text-decoration: none;
}

.view-btn {
  padding: 0.5rem 1rem;
  background: var(--brand-main);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
}

.view-btn:hover {
  background: var(--brand-accent);
}

.no-summary {
  text-align: center;
  padding: 2rem 1rem;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.no-summary-icon {
  font-size: 2rem;
  margin-bottom: 1rem;
}

.no-summary-text {
  color: var(--text-secondary);
  font-style: italic;
  margin: 0;
}

/* 라이브 섹션 */
.live-section {
  margin-top: 1rem;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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