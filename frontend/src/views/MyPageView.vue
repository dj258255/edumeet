<template>
  <div class="mypage-view">
    <!-- 헤더 섹션 -->
    <section class="mypage-header">
      <div class="header-container">
        <div class="header-content">
          <div class="header-badge">마이페이지</div>
          <h1 class="header-title">
            안녕하세요, <span class="highlight">{{ userInfo.nickname }}</span>님!
          </h1>
          <p class="header-description">
            EduMeet에서의 학습 활동을 확인하고 관리하세요.
          </p>
        </div>
        <div class="header-visual">
          <div class="profile-card">
            <div class="profile-avatar">
              <img 
                src="@/assets/member/1.png" 
                alt="프로필 이미지" 
                class="avatar-image"
              />
              <div class="avatar-overlay">
                <span class="edit-icon">✏️</span>
              </div>
            </div>
            <div class="profile-info">
              <h3 class="profile-name">{{ userInfo.nickname }}</h3>
              <p class="profile-email">{{ userInfo.email }}</p>

            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 초대받은 수업 섹션 -->
    <section class="my-classes-section">
        <div class="section-container">
          <div class="section-header">
            <h2 class="section-title">초대 받은 수업</h2>
            <p class="section-subtitle">초대 받은 수업 목록을 확인하고 관리하세요.</p>
          </div>
        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </div>
          <p>초대 목록을 불러오는 중...</p>
        </div>
        <div v-else-if="inviteClasses && inviteClasses.length > 0" class="activity-list">
             <div 
               v-for="cls in inviteClasses" 
               :key="cls.classId || cls.id" 
            class="activity-card"
             >
            <div class="activity-icon">
              <img :src="cls.thumbnailUrl || '@/assets/class_default_image.png'" :alt="cls.title + ' 썸네일'" class="class-thumbnail" />
              </div>
            <div class="activity-content">
              <h3 class="activity-title">{{ cls.title }}</h3>
              <p class="activity-description">{{ cls.description }}</p>
              <div class="activity-meta">
                <span class="activity-time">방금 전</span>
                <span class="activity-status pending">대기중</span>
                </div>
              <div class="activity-actions">
                <div class="invite-buttons">
                   <button 
                    class="accept-invite-btn"
                     @click="handleAcceptInvite(cls.classId || cls.id)"
                     :disabled="isResponding"
                   >
                    <span class="btn-icon">✓</span>
                    <span class="btn-text">수락</span>
                   </button>
                   <button 
                    class="reject-invite-btn"
                     @click="handleRejectInvite(cls.classId || cls.id)"
                     :disabled="isResponding"
                   >
                    <span class="btn-icon">✕</span>
                    <span class="btn-text">거절</span>
                   </button>
                </div>
                 </div>
              </div>
            </div>
          </div>
          <div v-else class="no-classes">
            <p>아직 초대 받은 수업이 없습니다.</p>
          </div>
        </div>
      </section>

    <!-- 내가 속한 수업 섹션 -->
    <section class="my-joined-classes-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">내가 속한 수업</h2>
          <p class="section-subtitle">현재 참여하고 있는 수업 목록입니다</p>
        </div>
        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
            </div>
          <p>수업 목록을 불러오는 중...</p>
          </div>
        <div v-else-if="classStore.getMyJoinedClasses && classStore.getMyJoinedClasses.length > 0" class="horizontal-cards">
          <div 
            v-for="cls in classStore.getMyJoinedClasses" 
            :key="cls.classId || cls.id" 
            class="horizontal-card"
          >
            <div class="card-left">
              <div class="card-icon">
                <img :src="cls.thumbnailUrl || '@/assets/class_default_image.png'" :alt="cls.title + ' 썸네일'" class="class-thumbnail" />
            </div>
              <div class="card-content">
                <h3 class="card-title">{{ cls.title }}</h3>
                <p class="card-description">{{ cls.description }}</p>
                <div class="card-meta">
                  <span class="card-time">2시간 전</span>
                  <span class="card-status completed">완료</span>
          </div>
                <div class="card-actions">
                  <button class="join-class-btn" @click.stop="joinClass(cls)">
                    <span class="btn-icon">🎥</span>
                    <span class="btn-text">수업 참여</span>
                  </button>
            </div>
          </div>
            </div>
            <div class="card-right">
              <div class="live-info-section">
                <h4 class="live-info-title">📄 문서 요약본</h4>
                <div v-if="cls.liveInfoList && cls.liveInfoList.length > 0" class="live-info-list" :class="{ 'scrollable': cls.liveInfoList.length > 2 }">
                  <div 
                    v-for="info in cls.liveInfoList" 
                    :key="info.id" 
                    class="live-info-item"
                  >
                    <div class="info-header">
                      <h5 class="info-title">{{ info.title }}</h5>
                      <span class="info-status" :class="info.status">
                        {{ getStatusText(info.status) }}
                      </span>
          </div>
                    <div class="info-description">
                      <p>{{ info.description }}</p>
                    </div>
                    <div class="info-meta">
                      <span class="meta-date">{{ formatDate(info.createdAt) }}</span>
                      <span v-if="info.startTime" class="meta-time">{{ formatTime(info.startTime) }}</span>
                    </div>
                    <div v-if="info.hasRecordingFile" class="info-files">
                      <button class="download-btn" @click.stop="downloadMeetingFile(info)">
                        <span class="btn-icon">📥</span>
                        <span class="btn-text">녹화파일 다운로드</span>
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="no-live-info">
                  <p>등록된 라이브 정보가 없습니다.</p>
                  <p class="debug-info">Debug: liveInfoList = {{ cls.liveInfoList ? cls.liveInfoList.length : 'undefined' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="no-classes">
          <p>아직 참여하고 있는 수업이 없습니다.</p>
          <RouterLink to="/class/create" class="create-class-link">새로운 수업 만들기</RouterLink>
        </div>
      </div>
    </section>

    <!-- 내가 만든 수업 섹션 -->
    <section class="my-created-classes-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">내가 만든 수업</h2>
          <p class="section-subtitle">내가 개설한 수업 목록입니다</p>
        </div>
        <div v-if="classStore.isLoading" class="loading-state">
          <div class="loading-spinner">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2V6M12 18V22M4.93 4.93L7.76 7.76M16.24 16.24L19.07 19.07M2 12H6M18 12H22M4.93 19.07L7.76 16.24M16.24 7.76L19.07 4.93"/>
            </svg>
          </div>
          <p>수업 목록을 불러오는 중...</p>
        </div>
        <div v-else-if="classStore.getMyCreatedClasses && classStore.getMyCreatedClasses.length > 0" class="horizontal-cards">
          <div 
            v-for="cls in classStore.getMyCreatedClasses" 
            :key="cls.classId || cls.id" 
            class="horizontal-card"
          >
            <div class="card-left">
              <div class="card-icon">
                <img :src="cls.thumbnailUrl || '@/assets/class_default_image.png'" :alt="cls.title + ' 썸네일'" class="class-thumbnail" />
            </div>
              <div class="card-content">
                <h3 class="card-title">{{ cls.title }}</h3>
                <p class="card-description">{{ cls.description }}</p>
                <div class="card-meta">
                  <span class="card-time">1일 전</span>
                  <span class="card-status completed">완료</span>
                </div>
                <div class="card-actions">
                  <button class="create-class-btn" @click.stop="createClass(cls)">
                    <span class="btn-icon">🎬</span>
                    <span class="btn-text">수업 시작</span>
                  </button>
                </div>
              </div>
            </div>
            <div class="card-right">
              <div class="live-info-section">
                <h4 class="live-info-title">📄 문서 요약본</h4>
                <div v-if="cls.liveInfoList && cls.liveInfoList.length > 0" class="live-info-list" :class="{ 'scrollable': cls.liveInfoList.length > 2 }">
                  <div 
                    v-for="info in cls.liveInfoList" 
                    :key="info.id" 
                    class="live-info-item"
                  >
                    <div class="info-header">
                      <h5 class="info-title">{{ info.title }}</h5>
                      <span class="info-status" :class="info.status">
                        {{ getStatusText(info.status) }}
                </span>
              </div>
                    <div class="info-description">
                      <p>{{ info.description }}</p>
            </div>
                    <div class="info-meta">
                      <span class="meta-date">{{ formatDate(info.createdAt) }}</span>
                      <span v-if="info.startTime" class="meta-time">{{ formatTime(info.startTime) }}</span>
          </div>
                    <div v-if="info.hasRecordingFile" class="info-files">
                      <button class="download-btn" @click.stop="downloadMeetingFile(info)">
                        <span class="btn-icon">📥</span>
                        <span class="btn-text">녹화파일 다운로드</span>
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="no-live-info">
                  <p>등록된 라이브 정보가 없습니다.</p>
                  <p class="debug-info">Debug: liveInfoList = {{ cls.liveInfoList ? cls.liveInfoList.length : 'undefined' }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="no-classes">
          <p>아직 만든 수업이 없습니다.</p>
          <RouterLink to="/class/create" class="create-class-link">새로운 수업 만들기</RouterLink>
        </div>
      </div>
    </section>

    <!-- 학습 통계 섹션 -->
    <section class="learning-stats-section">
      <div class="section-container">
        <div class="section-header">
          <h2 class="section-title">학습 통계</h2>
          <p class="section-subtitle">이번 달 학습 활동을 확인해보세요</p>
        </div>
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon">📚</div>
            <div class="stat-content">
              <div class="stat-number">{{ learningStats.totalClasses }}</div>
              <div class="stat-label">참여한 수업</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">🎬</div>
            <div class="stat-content">
              <div class="stat-number">{{ learningStats.createdClasses }}</div>
              <div class="stat-label">개설한 수업</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">👥</div>
            <div class="stat-content">
              <div class="stat-number">{{ learningStats.joinedClasses }}</div>
              <div class="stat-label">참여 중인 수업</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 수업 참여 모달 -->
    <JoinClassModal
      :isOpen="isJoinModalOpen"
      :className="selectedClassForJoin?.className || ''"
      :classDescription="selectedClassForJoin?.classDescription || ''"
      :classId="selectedClassForJoin?.classId || ''"
      @close="closeJoinModal"
      @join="handleJoinClassConfirm"
    />

    <!-- 수업 시작 모달 -->
    <CreateClassModal
      :isOpen="showCreateClassModal"
      :defaultClassName="pendingClassData?.className || ''"
      :classId="pendingClassData?.classId || ''"
      @close="handleCreateClassModalClose"
      @create="handleCreateClassConfirm"
    />
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useClassStore } from '@/stores/class'
import JoinClassModal from '@/components/JoinClassModal.vue'
import CreateClassModal from '@/components/CreateClassModal.vue'

export default {
  name: 'MyPageView',
  components: {
    JoinClassModal,
    CreateClassModal
  },
  setup() {
    const router = useRouter()
    const authStore = useAuthStore()
    const classStore = useClassStore()
    
    // 사용자 정보
    const userInfo = computed(() => {
      return authStore.currentUser || {
        nickname: '사용자',
        email: 'user@example.com',
        role: 'student'
      }
    })

    // 응답 중 상태
    const isResponding = ref(false)

    // 초대받은 수업 목록
    const inviteClasses = ref([])

    // 학습 통계
    const learningStats = ref({
      totalClasses: 0,
      createdClasses: 0,
      joinedClasses: 0,
      totalStudyHours: 0
    })

    // 수업 참여 모달 관련 상태
    const isJoinModalOpen = ref(false)
    const selectedClassForJoin = ref(null)

         // 수업 시작 모달 관련 상태
     const showCreateClassModal = ref(false)
     const pendingClassData = ref(null)

     // LiveInfo 관련 함수들
    const getStatusText = (status) => {
       const statusMap = { live: '진행중', scheduled: '예정', ended: '종료' }
       return statusMap[status] || '알 수 없음'
     }

     const formatDate = (s) => {
       const d = new Date(s)
       return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
     }

     const formatTime = (s) => {
       const d = new Date(s)
       return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
     }

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
         const url = `https://api.studywithtymee.com/api/v1/meeting/files/download/${meetingInfo.id}`
         
         console.log('📡 요청 URL:', url)
         console.log('🔑 토큰 존재:', !!accessToken)

         const response = await fetch(url, {
           method: 'GET',
           headers: {
             'Authorization': `Bearer ${accessToken}`
           }
         })

         if (!response.ok) {
           throw new Error(`HTTP error! status: ${response.status}`)
         }

         const blob = await response.blob()
         const downloadUrl = window.URL.createObjectURL(blob)
         const link = document.createElement('a')
         link.href = downloadUrl
         
         // 파일명 생성
         const date = meetingInfo.startTime ? new Date(meetingInfo.startTime).toISOString().split('T')[0] : 'unknown-date'
         const safeTitle = meetingInfo.title.replace(/[<>:"/\\|?*]/g, '').replace(/\s+/g, '_').substring(0, 50).trim()
         const fileName = `${date}_${safeTitle}.mp4`
         
         link.download = fileName
         document.body.appendChild(link)
         link.click()
         document.body.removeChild(link)
         window.URL.revokeObjectURL(downloadUrl)

         console.log('✅ 다운로드 완료:', fileName)
         alert('다운로드가 완료되었습니다.')

       } catch (error) {
         console.error('❌ 다운로드 실패:', error)
         alert('다운로드에 실패했습니다. 다시 시도해주세요.')
       } finally {
         meetingInfo.downloading = false
       }
     }

    // API에서 초대 목록을 가져오는 함수
  const fetchMyClasses = async () => {
    console.log('🔍 초대 목록 로드 시작...');
    console.log('🔍 현재 토큰:', localStorage.getItem('token') ? '있음' : '없음');
    
    try {
      console.log('🔍 classStore.fetchInviteList() 호출...');
      const data = await classStore.fetchInviteList();
      console.log('🔍 받은 데이터:', data);
      
      inviteClasses.value = data;
      
      if (inviteClasses.value && !Array.isArray(inviteClasses.value)) {
        console.log('🔍 배열이 아닌 데이터 수신, 빈 배열로 설정');
        inviteClasses.value = [];
      }
    } catch (error) {
      console.error('🔍 초대 목록 로드 실패:', error);
      inviteClasses.value = [];
    }
  };

         // LiveInfo 데이터를 매핑하는 함수
     const mapLiveInfoToViewModel = (items = []) => {
       if (!Array.isArray(items)) {
         console.warn('🔍 mapLiveInfoToViewModel - items가 배열이 아님:', items)
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

         // s3url 체크
         const s3Url = item.s3url || item.s3Url || item.recordingUrl || item.fileUrl
         const hasRecordingFile = Boolean(s3Url && s3Url.trim() !== '' && s3Url !== 'null')

         // 녹화 파일 정보
         const recordingFileName = `${new Date(startTime || createdAt).toISOString().split('T')[0]}_${title.replace(/[<>:"/\\|?*]/g, '').replace(/\s+/g, '_').substring(0, 50)}.mp4`
         const fileSize = item.fileSize || item.contentLength || 0

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

     // LiveInfo 데이터를 가져오는 함수
     const fetchLiveInfoForClass = async (classId) => {
       try {
         const accessToken = localStorage.getItem('accessToken')
         const url = `https://api.studywithtymee.com/api/v1/meetingroom/${classId}`
         
         console.log(`📡 LiveInfo 조회 시작 - classId: ${classId}`)
         console.log(`🔗 요청 URL: ${url}`)
         
         const response = await fetch(url, {
           headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}
         })
         
         if (!response.ok) {
           console.warn(`⚠️ LiveInfo 조회 실패 - classId: ${classId}, status: ${response.status}`)
           return []
         }
         
         const data = await response.json()
         const items = Array.isArray(data) ? data : (data ? [data] : [])
         const mappedData = mapLiveInfoToViewModel(items)
         
         console.log(`✅ LiveInfo 조회 성공 - classId: ${classId}`, mappedData)
         return mappedData
         
       } catch (error) {
         console.error(`💥 LiveInfo 조회 실패 - classId: ${classId}`, error)
         return []
       }
     }

     // 클래스 목록 새로고침 함수
     const loadClasses = async () => {
       try {
         await classStore.fetchMyCreatedClasses()
         await classStore.fetchMyJoinedClasses()
         await fetchMyClasses()
         
         // LiveInfo 데이터 추가
         const createdClasses = classStore.getMyCreatedClasses || []
         const joinedClasses = classStore.getMyJoinedClasses || []
         
         // 생성한 클래스에 LiveInfo 추가
         for (const cls of createdClasses) {
           const liveInfoList = await fetchLiveInfoForClass(cls.classId || cls.id)
           cls.liveInfoList = liveInfoList
         }
         
         // 참여한 클래스에 LiveInfo 추가
         for (const cls of joinedClasses) {
           const liveInfoList = await fetchLiveInfoForClass(cls.classId || cls.id)
           cls.liveInfoList = liveInfoList
         }
         
         // 통계 계산
         calculateLearningStats()
         
         console.log('🔍 Created Classes with LiveInfo:', classStore.getMyCreatedClasses)
         console.log('🔍 Joined Classes with LiveInfo:', classStore.getMyJoinedClasses)
       } catch (error) {
         console.error('클래스 목록 로드 에러:', error)
       }
     }

    // 학습 통계 계산
    const calculateLearningStats = () => {
      learningStats.value = {
        totalClasses: (classStore.getMyCreatedClasses?.length || 0) + (classStore.getMyJoinedClasses?.length || 0),
        createdClasses: classStore.getMyCreatedClasses?.length || 0,
        joinedClasses: classStore.getMyJoinedClasses?.length || 0,
        totalStudyHours: Math.floor(Math.random() * 50) + 10 // 임시 데이터
      };
  };

  // 초대 수락 함수
  const handleAcceptInvite = async (classId) => {
    if (isResponding.value) return;
    
    const token = localStorage.getItem('token');
    if (!token) {
      alert('로그인이 필요합니다.');
        router.push('/login');
      return;
    }
    
         try {
       isResponding.value = true;
       
       const requestData = {
         classId: classId,
         status: 'ACCEPTED'
       };
       
       await classStore.respondToInvite(requestData);
       alert('초대를 수락했습니다.');
       
       await fetchMyClasses();
        await loadClasses(); // 전체 목록 새로고침
     } catch (error) {
       alert('초대 수락에 실패했습니다. 다시 시도해주세요.');
     } finally {
       isResponding.value = false;
     }
  };

  // 초대 거절 함수
  const handleRejectInvite = async (classId) => {
    if (isResponding.value) return;
    
    const token = localStorage.getItem('token');
    if (!token) {
      alert('로그인이 필요합니다.');
        router.push('/login');
      return;
    }
    
    if (!confirm('정말로 이 초대를 거절하시겠습니까?')) {
      return;
    }
    
         try {
       isResponding.value = true;
       
       const requestData = {
         classId: classId,
         status: 'DENIED'
       };
       
       await classStore.respondToInvite(requestData);
       alert('초대를 거절했습니다.');
       
       await fetchMyClasses();
     } catch (error) {
       console.error('초대 거절 실패:', error);
       alert('초대 거절에 실패했습니다. 다시 시도해주세요.');
     } finally {
       isResponding.value = false;
     }
  };

    // 수업 참여하기 (내가 속한 반의 수업 참여)
    const joinClass = (cls) => {
      const classData = {
        classId: cls.classId || cls.id,
        className: cls.title,
        classDescription: cls.description
      };
      
      selectedClassForJoin.value = classData;
      isJoinModalOpen.value = true;
    };

    // 수업 시작하기 (내가 만든 반의 수업 생성)
    const createClass = (cls) => {
      const classData = {
        classId: cls.classId || cls.id,
        className: cls.title,
        classDescription: cls.description
      };
      
      pendingClassData.value = classData;
      showCreateClassModal.value = true;
    };

    // 수업 상세 페이지로 이동
    const goToClass = (classId) => {
      router.push(`/class/${classId}/info`);
    };

    // 수업 참여 모달 닫기
    const closeJoinModal = () => {
      isJoinModalOpen.value = false;
      selectedClassForJoin.value = null;
    };

    // 수업 시작 모달 닫기
    const handleCreateClassModalClose = () => {
      showCreateClassModal.value = false;
      pendingClassData.value = null;
    };

    // 수업 참여 확인 처리 (CreateClassView.vue와 동일한 로직)
    const handleJoinClassConfirm = async (joinData) => {
      console.log('수업 참여 데이터:', joinData);
      
      try {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
          alert('로그인이 필요합니다.');
          return;
        }
        
        console.log('🔍 토큰 요청 시작');
        console.log('🔍 요청 URL:', `https://api.studywithtymee.com/api/v1/meetingroom/token`);
        
        const response = await fetch(`https://api.studywithtymee.com/api/v1/meetingroom/token`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`
          },
          body: JSON.stringify({
            title: joinData.roomName,
            participantName: joinData.participantName,
            classId: joinData.classId
          })
        });
        
        console.log('🔍 응답 상태:', response.status);
        
        if (!response.ok) {
          const errorText = await response.text();
          console.log('🔍 오류 응답 본문:', errorText);
          
          let errorData;
          try {
            errorData = JSON.parse(errorText);
          } catch (e) {
            errorData = { error: errorText };
          }
          
          const errorMessage = errorData.error || `토큰 요청 실패: ${response.status}`;
          throw new Error(errorMessage);
        }
        
        const responseText = await response.text();
        console.log('🔍 백엔드 응답 본문:', responseText);
        
        if (!responseText || responseText.trim() === '') {
          throw new Error('서버에서 빈 응답을 받았습니다.');
        }
        
        let data;
        try {
          data = JSON.parse(responseText);
        } catch (parseError) {
          throw new Error('서버 응답을 파싱할 수 없습니다.');
        }
        
        if (!data.token) {
          throw new Error('토큰이 없습니다.');
        }
        
        // 화상 수업 페이지로 이동
        const queryParams = {
          roomName: joinData.roomName,
          className: joinData.className,
          participantName: joinData.participantName,
          isCreator: 'false',
          token: data.token
        };
        
        const queryString = new URLSearchParams(queryParams).toString();
        router.push(`/class/${joinData.classId}/video?${queryString}`);
        
        closeJoinModal();
        
      } catch (error) {
        console.error('토큰 요청 실패:', error);
        alert('수업 참여에 실패했습니다. 다시 시도해주세요.');
      }
    };

    // 수업 시작 확인 처리 (CreateClassView.vue와 동일한 로직)
    const handleCreateClassConfirm = (modalData) => {
      console.log('🔍 handleCreateClassConfirm - modalData:', modalData);
      
      router.push({
        path: `/class/${modalData.classId}/video`,
        query: {
          meetingId: modalData.meetingId,
          title: modalData.title,
          email: modalData.email,
          isCreator: 'true',
          creatorName: modalData.creatorName,
          description: modalData.description,
          token: modalData.token
        }
      });
      
      showCreateClassModal.value = false;
      pendingClassData.value = null;
    };

    // 로그인 상태 감시
    const isLoggedIn = computed(() => authStore.isLoggedIn);

    // 페이지 진입 시 목록 로드
    onMounted(async () => {
      if (isLoggedIn.value) {
        await loadClasses();
      } else {
        const stop = watch(isLoggedIn, async (v) => {
          if (v) {
            stop();
            await loadClasses();
          }
        });
      }
  });

    return {
      userInfo,
      inviteClasses,
       learningStats,
      isResponding,
       isJoinModalOpen,
       selectedClassForJoin,
       showCreateClassModal,
       pendingClassData,
       classStore,
      handleAcceptInvite,
       handleRejectInvite,
       joinClass,
       createClass,
       goToClass,
       closeJoinModal,
       handleCreateClassModalClose,
       handleJoinClassConfirm,
       handleCreateClassConfirm,
       getStatusText,
       formatDate,
       formatTime,
       downloadMeetingFile,
       mapLiveInfoToViewModel,
       fetchLiveInfoForClass
    }
  }
}
</script>

<style scoped>
.mypage-view {
  min-height: 100vh;
  background-color: var(--bg-secondary);
  transition: background-color var(--transition-normal);
}

/* 헤더 섹션 */
.mypage-header {
  padding: var(--spacing-2xl) 0;
  background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
  transition: background var(--transition-normal);
}

.header-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-xl);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-2xl);
  align-items: center;
}

.header-content {
  text-align: left;
}

.header-badge {
  display: inline-block;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  font-size: var(--font-size-sm);
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 20px;
  margin-bottom: var(--spacing-md);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.header-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
  line-height: 1.2;
  transition: color var(--transition-normal);
}

.header-title .highlight {
  color: var(--brand-main);
}

.header-description {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
  line-height: 1.6;
  transition: color var(--transition-normal);
}

.header-visual {
  display: flex;
  justify-content: center;
}

.profile-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  transition: all var(--transition-normal);
  border: 1px solid var(--border-color);
}

.profile-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.profile-avatar {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto var(--spacing-lg);
  border-radius: 50%;
  overflow: hidden;
  border: 4px solid var(--brand-main);
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-normal);
}

.profile-avatar:hover .avatar-overlay {
  opacity: 1;
}

.edit-icon {
  font-size: 24px;
  color: white;
}

.profile-info {
  text-align: center;
}

.profile-name {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  transition: color var(--transition-normal);
}

.profile-email {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
  transition: color var(--transition-normal);
}

.profile-role {
  display: inline-block;
}

.role-badge {
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: var(--text-inverse);
  font-size: var(--font-size-sm);
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 12px;
}

/* 섹션 공통 스타일 */
.my-classes-section,
.my-joined-classes-section,
.my-created-classes-section,
.learning-stats-section {
  padding: var(--spacing-2xl) 0;
}

.my-classes-section,
.my-joined-classes-section,
.learning-stats-section {
  background-color: var(--bg-secondary);
}

.my-created-classes-section {
  background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
}

.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-xl);
}

.section-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.section-title {
  font-size: var(--font-size-3xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
  transition: color var(--transition-normal);
}

.section-subtitle {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
  line-height: 1.6;
  transition: color var(--transition-normal);
}

/* 로딩 상태 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.loading-spinner {
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 활동 목록 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 가로 카드 레이아웃 */
.horizontal-cards {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.horizontal-card {
  background: var(--bg-card);
  border-radius: 16px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid var(--border-color);
  display: flex;
  gap: 2rem;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
}

.horizontal-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--brand-main), var(--brand-sub));
  opacity: 0;
  transition: opacity 0.3s ease;
}

.horizontal-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.horizontal-card:hover::before {
  opacity: 1;
}

.card-left {
  display: flex;
  gap: 1rem;
  flex: 1;
  min-width: 0;
}

.card-icon {
  flex-shrink: 0;
  width: 60px;
  height: 60px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
  justify-content: space-between;
}

.card-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.4;
  letter-spacing: -0.025em;
}

.card-description {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  margin-bottom: 0.5rem;
}

.card-time {
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.card-status {
  font-size: 0.625rem;
  font-weight: 700;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.card-status.completed {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.3);
}

.card-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0;
}

.card-right {
  flex: 1;
  min-width: 300px;
  max-width: 400px;
}

.live-info-section {
  background: var(--bg-tertiary);
  border-radius: 12px;
  padding: 1rem;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.live-info-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.75rem 0;
  flex-shrink: 0;
}

.live-info-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  flex: 1;
  padding-right: 0.5rem;
}

.live-info-list.scrollable {
  overflow-y: auto;
  max-height: 200px;
}

.live-info-list.scrollable::-webkit-scrollbar {
  width: 4px;
}

.live-info-list.scrollable::-webkit-scrollbar-track {
  background: var(--bg-secondary);
  border-radius: 2px;
}

.live-info-list.scrollable::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 2px;
}

.live-info-list.scrollable::-webkit-scrollbar-thumb:hover {
  background: var(--text-secondary);
}

.live-info-item {
  background: var(--bg-card);
  border-radius: 8px;
  padding: 0.75rem;
  border: 1px solid var(--border-color);
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.5rem;
}

.info-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.4;
}

.info-status {
  font-size: 0.625rem;
  font-weight: 700;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.info-status.live {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
}

.info-status.scheduled {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
}

.info-status.ended {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.info-description {
  margin-bottom: 0.5rem;
}

.info-description p {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.info-meta {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.meta-date,
.meta-time {
  font-size: 0.625rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.info-files {
  margin-top: 0.5rem;
}

.download-btn {
  width: 100%;
  background: linear-gradient(135deg, var(--brand-main), var(--brand-sub));
  color: white;
  border: none;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
    justify-content: center;
  gap: 0.25rem;
}

.download-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.no-live-info {
  text-align: center;
  padding: 1rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.debug-info {
  font-size: 0.75rem;
  color: var(--text-tertiary);
  margin-top: 0.5rem;
  font-style: italic;
}

.view-details-btn {
  background: transparent;
  color: var(--text-primary);
  border: 2px solid var(--border-color);
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.view-details-btn:hover {
  background: var(--bg-tertiary);
  transform: translateY(-1px);
}

.activity-card {
background: var(--bg-card);
  border-radius: 16px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
border: 1px solid var(--border-color);
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  gap: 1.25rem;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
}

.activity-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--brand-main), var(--brand-sub));
  opacity: 0;
  transition: opacity 0.3s ease;
}

.activity-card:hover {
transform: translateY(-4px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.activity-card:hover::before {
  opacity: 1;
}

.activity-icon {
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  border-radius: 12px;
overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  position: relative;
}

.class-thumbnail {
width: 100%;
height: 100%;
object-fit: cover;
  transition: transform 0.3s ease;
}

.activity-card:hover .class-thumbnail {
transform: scale(1.05);
}

.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.activity-title {
  font-size: 1.125rem;
  font-weight: 700;
color: var(--text-primary);
  margin: 0;
  line-height: 1.4;
  letter-spacing: -0.025em;
}

.activity-description {
  font-size: 0.875rem;
color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
display: -webkit-box;
-webkit-line-clamp: 2;
-webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-meta {
display: flex;
  justify-content: space-between;
align-items: center;
  margin-top: 0.5rem;
}

.activity-time {
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.activity-status {
  font-size: 0.625rem;
  font-weight: 700;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.activity-status.pending {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
  box-shadow: 0 2px 4px rgba(245, 158, 11, 0.3);
}

.activity-status.completed {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.3);
}

.activity-status.in_progress {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.3);
}

.no-classes {
text-align: center;
padding: var(--spacing-xl);
color: var(--text-tertiary);
font-style: italic;
}

/* 버튼 스타일 */
.activity-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}

.invite-buttons {
  display: flex;
  gap: 0.5rem;
  width: 100%;
}

.accept-invite-btn,
.reject-invite-btn {
  flex: 1;
  background: transparent;
  color: var(--text-primary);
  border: 2px solid;
  padding: 0.75rem 1rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  position: relative;
  overflow: hidden;
}

.accept-invite-btn {
  border-color: #10b981;
  color: #10b981;
}

.accept-invite-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(16, 185, 129, 0.1), transparent);
  transition: left 0.5s ease;
}

.accept-invite-btn:hover::before {
  left: 100%;
}

.accept-invite-btn:hover {
  background: #10b981;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(16, 185, 129, 0.3);
}

.reject-invite-btn {
  border-color: #ef4444;
  color: #ef4444;
}

.reject-invite-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(239, 68, 68, 0.1), transparent);
  transition: left 0.5s ease;
}

.reject-invite-btn:hover::before {
  left: 100%;
}

.reject-invite-btn:hover {
  background: #ef4444;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(239, 68, 68, 0.3);
}

.accept-invite-btn:disabled,
.reject-invite-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-icon {
  font-size: 1rem;
  font-weight: bold;
}

.btn-text {
  font-weight: 600;
}

.join-class-btn,
.create-class-btn {
  width: 100%;
  background: linear-gradient(135deg, var(--brand-main), var(--brand-sub));
  color: white;
  border: none;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.join-class-btn::before,
.create-class-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.join-class-btn:hover::before,
.create-class-btn:hover::before {
  left: 100%;
}

.join-class-btn:hover,
.create-class-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
}

.create-class-link {
  display: inline-block;
  background: var(--brand-main);
  color: var(--text-inverse);
  text-decoration: none;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: 600;
  margin-top: var(--spacing-md);
  transition: all var(--transition-normal);
}

.create-class-link:hover {
  background: var(--brand-sub);
  transform: translateY(-1px);
}

/* 통계 섹션 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--spacing-lg);
}

.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  transition: all var(--transition-normal);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.stat-icon {
  font-size: 2.5rem;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
  color: white;
}

.stat-content {
  flex: 1;
}

.stat-number {
  font-size: var(--font-size-3xl);
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
  margin-bottom: var(--spacing-xs);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .header-container {
    grid-template-columns: 1fr;
    gap: var(--spacing-lg);
    text-align: center;
  }

  .header-title {
    font-size: var(--font-size-3xl);
  }

  .activity-list {
    gap: 0.75rem;
  }
  
  .activity-card {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
    padding: 1.25rem;
  }
  
  .activity-icon {
    width: 60px;
    height: 60px;
    align-self: center;
  }
  
  .invite-buttons {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .accept-invite-btn,
  .reject-invite-btn {
    width: 100%;
  }

  /* 가로 카드 반응형 */
  .horizontal-card {
    flex-direction: column;
    gap: 1rem;
    padding: 1.25rem;
  }

  .card-left {
    flex-direction: column;
    text-align: center;
    gap: 0.75rem;
  }

  .card-icon {
    align-self: center;
  }

  .card-actions {
    flex-direction: column;
    gap: 0.5rem;
  }

  .card-right {
    min-width: auto;
    max-width: none;
  }

  .live-info-section {
    padding: 0.75rem;
    max-height: 250px;
  }
  
  .live-info-list {
    max-height: 150px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-card {
    flex-direction: column;
    text-align: center;
    gap: var(--spacing-md);
  }

  .stat-icon {
    width: 50px;
    height: 50px;
    font-size: 2rem;
  }
}

@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .header-title {
    font-size: var(--font-size-2xl);
  }

  .section-title {
    font-size: var(--font-size-2xl);
  }
}
</style>