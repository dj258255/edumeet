<template>
  <div class="oauth-success-view">
    <!-- 상태에 따른 콘텐츠 표시 -->
    <div class="status-container">
      <!-- 로딩 상태 -->
      <div v-if="loading" class="status-content loading">
        <div class="status-icon">
          <div class="spinner"></div>
        </div>
        <h2 class="status-title">로그인 처리 중</h2>
        <p class="status-message">토큰을 확인하고 있습니다...</p>
        
        <!-- 진행률 바 -->
        <div class="progress-container">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${progress}%` }"></div>
          </div>
          <span class="progress-text">{{ progress }}%</span>
        </div>
      </div>
      
      <!-- 에러 상태 -->
      <div v-else-if="error" class="status-content error">
        <div class="status-icon">
          <span class="icon-error">⚠️</span>
        </div>
        <h2 class="status-title">로그인 실패</h2>
        <p class="status-message">{{ error }}</p>
        <p class="status-submessage">{{ countdown }}초 후 로그인 페이지로 이동합니다</p>
        
        <button @click="goToLogin" class="retry-btn">
          지금 이동하기
        </button>
      </div>
      
      <!-- 성공 상태 -->
      <div v-else class="status-content success">
        <div class="status-icon">
          <span class="icon-success">✅</span>
        </div>
        <h2 class="status-title">로그인 완료</h2>
        <p class="status-message">{{ userInfo }}</p>
        <p class="status-submessage">메인 페이지로 이동합니다...</p>
      </div>
    </div>
    
    <!-- 브랜드 정보 (하단) -->
    <div class="brand-footer">
      <div class="brand-info">
        <img alt="EduMeet Logo" class="brand-logo" src="@/assets/edumeet_logo.png" />
        <span class="brand-name">EduMeet</span>
      </div>
      <span class="brand-slogan">Education At Home</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.js'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const error = ref(null)
const countdown = ref(5)
const progress = ref(0)
const userInfo = ref('')

const API_BASE_URL = `${import.meta.env.VITE_BASE_URL}`

// 개발 환경 확인
const isDevelopment = computed(() => {
  return import.meta.env.MODE === 'development'
})

onMounted(() => {
  fetchOAuth2Tokens()
})

const fetchOAuth2Tokens = async () => {
  try {
    console.log('🔄 OAuth2 토큰 조회 시작...')
    progress.value = 20
    
    // 사용자에게 진행 상황 표시
    await new Promise(resolve => setTimeout(resolve, 300))
    progress.value = 40
    
    const response = await fetch(`${API_BASE_URL}/api/oauth2/token`, {
      method: 'GET',
      credentials: 'include', // 세션 쿠키 포함
      headers: {
        'Content-Type': 'application/json',
      },
    })

    console.log('📡 OAuth2 토큰 API 응답 상태:', response.status)
    progress.value = 60

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      if (response.status === 401) {
        throw new Error(errorData.error || 'OAuth2 인증 정보가 없습니다. 다시 로그인해주세요.')
      } else {
        throw new Error(errorData.error || `서버 오류가 발생했습니다. (${response.status})`)
      }
    }

    const data = await response.json()
    console.log('✅ OAuth2 토큰 조회 성공:', {
      hasAccessToken: !!data.accessToken,
      hasRefreshToken: !!data.refreshToken,
      userEmail: data.userEmail,
      userNickname: data.userNickname
    })
    
    progress.value = 80

    // 백엔드 응답 구조에 맞춰 사용자 정보 구성
    const user = {
      email: data.userEmail,
      nickname: data.userNickname || data.userEmail?.split('@')[0] || '사용자'
    }
    
    userInfo.value = `${user.nickname}님, 환영합니다!`

    // 토큰을 localStorage에 저장
    if (data.accessToken && data.refreshToken) {
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('token', data.accessToken) // auth store 호환성
      localStorage.setItem('user', JSON.stringify(user))
      
      progress.value = 90
      
      // Pinia 스토어에 사용자 정보 저장
      await authStore.loginWithOAuth2({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: user
      })

      console.log('💾 토큰 및 사용자 정보 저장 완료')
      progress.value = 100
      
      // 성공 상태 표시 후 홈페이지로 이동
      await new Promise(resolve => setTimeout(resolve, 800))
      loading.value = false
      
      await new Promise(resolve => setTimeout(resolve, 1500))
      router.push('/')
    } else {
      throw new Error('유효하지 않은 토큰 정보입니다.')
    }

  } catch (err) {
    console.error('❌ OAuth2 토큰 조회 실패:', err)
    error.value = err.message
    loading.value = false
    
    // 카운트다운 시작
    startCountdown()
  }
}

const startCountdown = () => {
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      goToLogin()
    }
  }, 1000)
}

const goToLogin = () => {
  router.push('/login?error=oauth_failed')
}
</script>

<style scoped>
.oauth-success-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  padding: var(--spacing-2xl);
  transition: background-color var(--transition-normal);
}

.status-container {
  max-width: 480px;
  width: 100%;
  margin-bottom: var(--spacing-2xl);
}

.status-content {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-2xl);
  text-align: center;
  box-shadow: var(--shadow-card);
  border: 1px solid var(--border-color);
  transition: all var(--transition-normal);
}

.status-icon {
  margin-bottom: var(--spacing-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 스피너 애니메이션 */
.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top: 3px solid var(--brand-main);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.icon-error,
.icon-success {
  font-size: 2.5rem;
  line-height: 1;
}

.status-title {
  font-size: var(--font-size-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
  transition: color var(--transition-normal);
}

.status-message {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
  line-height: 1.5;
  transition: color var(--transition-normal);
}

.status-submessage {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-lg);
  transition: color var(--transition-normal);
}

/* 진행률 바 */
.progress-container {
  margin-top: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.progress-bar {
  width: 100%;
  height: 6px;
  background: var(--border-color);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--brand-main);
  border-radius: var(--radius-full);
  transition: width var(--transition-slow);
}

.progress-text {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  font-weight: 500;
  align-self: center;
}

/* 버튼 스타일 */
.retry-btn {
  background: var(--brand-main);
  color: var(--text-inverse);
  border: none;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.retry-btn:hover {
  background: var(--brand-accent);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* 상태별 스타일 */
.loading .status-title {
  color: var(--brand-main);
}

.error .status-title {
  color: var(--danger-color);
}

.error .status-message {
  color: var(--danger-color);
}

.success .status-title {
  color: var(--success-color);
}

.success .status-message {
  color: var(--success-color);
}

/* 브랜드 정보 */
.brand-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: auto;
}

.brand-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.brand-logo {
  height: 32px;
  width: auto;
}

.brand-name {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--brand-main);
}

.brand-slogan {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  font-weight: 500;
}

/* 디버그 정보 */
.debug-info {
  position: fixed;
  bottom: var(--spacing-md);
  right: var(--spacing-md);
  background: var(--text-primary);
  color: var(--text-inverse);
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  max-width: 250px;
  z-index: 1000;
}

.debug-title {
  font-weight: 600;
  margin-bottom: var(--spacing-xs);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: var(--spacing-xs);
}

.debug-item {
  margin-bottom: 2px;
}

.debug-item.error {
  color: #ff6b6b;
}

.debug-item.success {
  color: #51cf66;
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .oauth-success-view {
    padding: var(--spacing-lg);
  }
  
  .status-content {
    padding: var(--spacing-xl);
  }
  
  .status-title {
    font-size: var(--font-size-xl);
  }
  
  .status-message {
    font-size: var(--font-size-sm);
  }
  
  .brand-logo {
    height: 28px;
  }
  
  .brand-name {
    font-size: var(--font-size-base);
  }
  
  .debug-info {
    bottom: var(--spacing-sm);
    right: var(--spacing-sm);
    max-width: 200px;
  }
}

@media (max-width: 480px) {
  .oauth-success-view {
    padding: var(--spacing-md);
  }
  
  .status-content {
    padding: var(--spacing-lg);
  }
  
  .status-title {
    font-size: var(--font-size-lg);
  }
  
  .spinner {
    width: 32px;
    height: 32px;
    border-width: 2px;
  }
  
  .icon-error,
  .icon-success {
    font-size: 2rem;
  }
}
</style>
