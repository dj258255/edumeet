<template>
  <div class="login-view">
    <!-- 왼쪽 브랜딩 섹션 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-header">
          <div class="brand-logo">
            <img alt="EduMeet Logo" class="logo" src="@/assets/edumeet_logo.png" />
            <h2 class="brand-name">EduMeet</h2>
          </div>
          <p class="brand-slogan">Education At Home</p>
        </div>
        
        <div class="brand-main">
          <h1 class="brand-title">Knowledge From Home</h1>
          <p class="brand-description">
            It is a long established fact that a reader will be distracted by the readable content of a page when looking at its layout.
          </p>
        </div>
        
        <div class="brand-footer">
          <span class="region">KOREA</span>
          <span class="region">ASIA</span>
          <span class="region">GLOBAL</span>
        </div>
      </div>
    </div>

    <!-- 오른쪽 로그인 폼 섹션 -->
    <div class="form-section">
      <div class="form-container">
        <h2 class="form-title">Login</h2>

        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label for="email">EMAIL</label>
            <div class="input-wrapper">
              <span class="input-icon">✉️</span>
              <input
                id="email"
                v-model="email"
                type="email"
                :class="{ error: errors.email }"
                placeholder="Type your Email"
              />
            </div>
            <div v-if="errors.email" class="error-message">{{ errors.email }}</div>
          </div>
          
          <div class="form-group">
            <label for="password">PASSWORD</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                id="password"
                v-model="password"
                type="password"
                :class="{ error: errors.password }"
                placeholder="Type your password"
              />
            </div>
            <div v-if="errors.password" class="error-message">{{ errors.password }}</div>
          </div>
          
          <button type="submit" class="login-btn" :disabled="!email || !password">
            Login
          </button>
        </form>
        
        <div v-if="message" :class="['message', message.includes('성공') ? 'success' : 'error']">
          {{ message }}
        </div>
        
        <div class="forgot-password">
          <a href="#" class="forgot-link">Forgot your password?</a>
        </div>
        
        <div class="signup-link">
          Don't have an account? <RouterLink to="/signup">Sign Up</RouterLink>
        </div>
        
        <!-- 카카오 로그인 섹션 -->
        <div class="social-login-section">
          <p class="social-login-label">소셜 로그인</p>
          <div class="social-login-buttons">
            <button 
              class="kakao-login-btn"
              @click="handleKakaoLogin"
              :disabled="isKakaoLoading"
            >
              <div class="kakao-btn-content">
                👥 {{ isKakaoLoading ? '로그인 중...' : '카카오로 로그인' }}
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useClassStore } from '@/stores/class'
import '../styles/LoginView.css'

const router = useRouter()
const authStore = useAuthStore()
const classStore = useClassStore()

const email = ref('')
const password = ref('')
const errors = ref({})
const message = ref('')
const isKakaoLoading = ref(false)

const validateForm = () => {
  errors.value = {}
  if (!email.value) errors.value.email = '이메일을 입력해주세요.'
  else if (!/\S+@\S+\.\S+/.test(email.value)) errors.value.email = '올바른 이메일 형식을 입력해주세요.'
  if (!password.value) errors.value.password = '비밀번호를 입력해주세요.'
  else if (password.value.length < 6) errors.value.password = '비밀번호는 최소 6자 이상이어야 합니다.'
  return Object.keys(errors.value).length === 0
}

const handleLogin = async () => {
  if (!validateForm()) return
  try {
    console.log('로그인 시도:', email.value)
    
    // 실제 백엔드 API를 통한 로그인
    const result = await authStore.login(email.value, password.value)
    console.log('로그인 결과:', result)

    // 먼저 이동 → 가드 통과 안정화 (데이터는 백그라운드 로드)
    await router.push('/class/create')

    message.value = '로그인 성공!'
    console.log('로그인 후 상태:', authStore.isLoggedIn)
    console.log('로그인 후 사용자:', authStore.currentUser)

    // 로그인 직후 반 목록 선로딩 (실패해도 무시) - 백그라운드
    Promise.allSettled([
      classStore.fetchMyCreatedClasses(),
      classStore.fetchMyJoinedClasses(),
    ])
  } catch (error) {
    message.value = error.message || '로그인에 실패했습니다.'
    console.error('로그인 에러:', error)
  }
}

// 카카오 로그인 - Spring Security OAuth2 방식
const handleKakaoLogin = () => {
  console.log('카카오 로그인 버튼 클릭됨')
  
  // Spring Security OAuth2 방식으로 리다이렉트
  console.log('Spring Security OAuth2로 카카오 로그인 시작...')
  isKakaoLoading.value = true
  
  try {
    // 백엔드 OAuth2 엔드포인트로 이동
    window.location.href = `${BASE_URL}/oauth2/authorization/kakao`
  } catch (error) {
    console.error('카카오 로그인 에러:', error)
    alert('카카오 로그인 중 오류가 발생했습니다.')
    isKakaoLoading.value = false
  }
}

// 컴포넌트 마운트 - OAuth2 토큰 처리는 App.vue에서 수행
onMounted(() => {
  // Spring Security OAuth2가 전체적으로 처리함
  console.log('LoginView 마운트 완료')
})
</script>

<style scoped>
/* 기존 CSS는 유지하고 카카오 버튼만 업데이트 */
.kakao-login-btn {
  width: 100%;
  background: #fee500;
  color: #3c1e1e;
  border: none;
  padding: 12px;
  border-radius: 8px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 10px;
}

.kakao-login-btn:hover {
  background: #ffd900;
  transform: translateY(-1px);
}

.kakao-login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.kakao-btn-content {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.social-login-section {
  margin-top: 20px;
  text-align: center;
}

.social-login-label {
  margin-bottom: 10px;
  color: #666;
  font-size: 14px;
}
</style>
