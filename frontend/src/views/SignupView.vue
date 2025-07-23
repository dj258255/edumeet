<template>
  <div class="signup-view">
    <!-- 왼쪽 브랜딩 섹션 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-header">
          <div class="brand-logo">
            <span class="logo-icon">📚</span>
            <h2 class="brand-name">EduMeet</h2>
          </div>
          <p class="brand-slogan">Education At Home</p>
        </div>
        
        <div class="brand-main">
          <h1 class="brand-title">Join Our Community</h1>
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

    <!-- 오른쪽 회원가입 폼 섹션 -->
    <div class="form-section">
      <div class="form-container">
        <h2 class="form-title">Sign Up</h2>
        
        <!-- 역할 선택 -->
        <div class="role-selection">
          <p class="role-label">Please select your role</p>
          <div class="role-buttons">
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'student' }"
              @click="selectedRole = 'student'"
            >
              <span class="role-icon">🎓</span>
              <span class="role-text">STUDENT</span>
            </button>
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'tutor' }"
              @click="selectedRole = 'tutor'"
            >
              <span class="role-icon">📊</span>
              <span class="role-text">TUTOR</span>
            </button>
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'parent' }"
              @click="selectedRole = 'parent'"
            >
              <span class="role-icon">👨‍👩‍👧‍👦</span>
              <span class="role-text">PARENT</span>
            </button>
          </div>
        </div>

        <form class="signup-form" @submit.prevent="handleSignup">
          <div class="form-group">
            <label for="name">NAME</label>
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input
                id="name"
                v-model="name"
                type="text"
                :class="{ error: errors.name }"
                placeholder="Type your Name"
              />
            </div>
            <div v-if="errors.name" class="error-message">{{ errors.name }}</div>
          </div>
          
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
                :disabled="isEmailVerified"
              />
            </div>
            <div v-if="errors.email" class="error-message">{{ errors.email }}</div>
          </div>
          
          <!-- 이메일 인증 섹션 -->
          <div v-if="email && !isEmailVerified" class="email-verification-section">
            <div class="verification-header">
              <p class="verification-label">이메일 인증</p>
              <button 
                v-if="!verificationSent"
                @click="sendVerificationCode" 
                type="button"
                class="send-verification-btn"
                :disabled="!email || isLoading"
              >
                인증 코드 발송
              </button>
            </div>
            
            <div v-if="verificationSent" class="verification-content">
              <p class="verification-description">
                <strong>{{ email }}</strong>로 발송된 6자리 인증 코드를 입력해주세요.
              </p>
              
              <div class="code-input-section">
                <label>VERIFICATION CODE</label>
                <div class="code-input-wrapper">
                  <input
                    v-for="(digit, index) in 6"
                    :key="index"
                    :id="`code-${index}`"
                    v-model="codeDigits[index]"
                    type="text"
                    maxlength="1"
                    class="code-input"
                    :class="{ error: errors.code }"
                    @input="handleCodeInput(index, $event)"
                    @keydown="handleCodeKeydown(index, $event)"
                    @paste="handleCodePaste"
                    :disabled="isLoading"
                  />
                </div>
                <div v-if="errors.code" class="error-message">{{ errors.code }}</div>
              </div>
              
              <div class="timer-section">
                <p class="timer-text">
                  인증 코드 유효시간 : 
                  <span class="timer-countdown">{{ formatTime(countdown) }}</span>
                </p>
                <button 
                  @click="resendCode" 
                  type="button"
                  class="resend-btn"
                  :disabled="countdown > 0 || isLoading"
                >
                  재전송
                </button>
              </div>
              
              <button 
                @click="verifyCode" 
                type="button"
                class="verify-btn"
                :disabled="!isCodeComplete || isLoading"
              >
                {{ isLoading ? '인증 중...' : '인증 완료' }}
              </button>
            </div>
          </div>
          
          <!-- 이메일 인증 완료 표시 -->
          <div v-if="isEmailVerified" class="email-verified">
            <div class="verified-icon">✅</div>
            <p class="verified-text">이메일 인증 완료</p>
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
          
          <div class="form-group">
            <label for="confirmPassword">CONFIRM PASSWORD</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                id="confirmPassword"
                v-model="confirmPassword"
                type="password"
                :class="{ error: errors.confirmPassword }"
                placeholder="Confirm your password"
              />
            </div>
            <div v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</div>
          </div>
          
          <button type="submit" class="signup-btn" :disabled="isLoading || !name || !email || !password || !confirmPassword || !selectedRole || !isEmailVerified">
            {{ isLoading ? 'Signing up...' : 'Sign Up' }}
          </button>
        </form>
        
        <div v-if="message" :class="['message', message.includes('완료') ? 'success' : 'error']">
          {{ message }}
        </div>
        
        <div class="login-link">
          Already have an account? <RouterLink to="/login">Sign In</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI, userManager, tokenManager } from '../stores/auth.js'
import '../styles/SignupView.css'

const router = useRouter()

const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const name = ref('')
const selectedRole = ref('tutor') // 기본값으로 tutor 선택
const errors = ref({})
const message = ref('')
const isLoading = ref(false)

// 이메일 인증 관련 상태
const verificationSent = ref(false)
const isEmailVerified = ref(false)
const codeDigits = ref(['', '', '', '', '', ''])
const countdown = ref(0)
const timer = ref(null)

// Computed
const isCodeComplete = computed(() => {
  return codeDigits.value.every(digit => digit !== '')
})

const validateForm = () => {
  errors.value = {}
  
  if (!name.value) {
    errors.value.name = '이름을 입력해주세요.'
  }
  
  if (!email.value) {
    errors.value.email = '이메일을 입력해주세요.'
  } else if (!/\S+@\S+\.\S+/.test(email.value)) {
    errors.value.email = '올바른 이메일 형식을 입력해주세요.'
  }
  
  if (!isEmailVerified.value) {
    errors.value.email = '이메일 인증을 완료해주세요.'
  }
  
  if (!password.value) {
    errors.value.password = '비밀번호를 입력해주세요.'
  } else if (password.value.length < 6) {
    errors.value.password = '비밀번호는 최소 6자 이상이어야 합니다.'
  }
  
  if (!confirmPassword.value) {
    errors.value.confirmPassword = '비밀번호 확인을 입력해주세요.'
  } else if (password.value !== confirmPassword.value) {
    errors.value.confirmPassword = '비밀번호가 일치하지 않습니다.'
  }
  
  return Object.keys(errors.value).length === 0
}

// 이메일 인증 관련 메서드
const sendVerificationCode = async () => {
  if (!email.value || !/\S+@\S+\.\S+/.test(email.value)) {
    errors.value.email = '올바른 이메일 형식을 입력해주세요.'
    return
  }
  
  isLoading.value = true
  errors.value = {}
  message.value = ''
  
  try {
    // TODO: 실제 API 호출로 변경
    // await authAPI.sendVerificationCode(email.value)
    
    // 임시로 성공 처리
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    verificationSent.value = true
    startCountdown()
    message.value = '인증 코드가 발송되었습니다.'
  } catch (error) {
    message.value = '인증 코드 발송에 실패했습니다. 다시 시도해주세요.'
  } finally {
    isLoading.value = false
  }
}

const startCountdown = () => {
  countdown.value = 180 // 3분
  timer.value = setInterval(() => {
    if (countdown.value > 0) {
      countdown.value--
    } else {
      clearInterval(timer.value)
    }
  }, 1000)
}

const formatTime = (seconds) => {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

const handleCodeInput = (index, event) => {
  const value = event.target.value
  
  // 숫자만 허용
  if (!/^\d*$/.test(value)) {
    event.target.value = ''
    return
  }
  
  codeDigits.value[index] = value
  
  // 다음 입력 필드로 이동
  if (value && index < 5) {
    const nextInput = document.getElementById(`code-${index + 1}`)
    if (nextInput) {
      nextInput.focus()
    }
  }
}

const handleCodeKeydown = (index, event) => {
  // 백스페이스로 이전 필드로 이동
  if (event.key === 'Backspace' && !codeDigits.value[index] && index > 0) {
    const prevInput = document.getElementById(`code-${index - 1}`)
    if (prevInput) {
      prevInput.focus()
    }
  }
}

const handleCodePaste = (event) => {
  event.preventDefault()
  const pastedData = event.clipboardData.getData('text')
  const numbers = pastedData.replace(/\D/g, '').slice(0, 6)
  
  if (numbers.length === 6) {
    codeDigits.value = numbers.split('')
  }
}

const verifyCode = async () => {
  if (!isCodeComplete.value) {
    errors.value.code = '6자리 인증 코드를 모두 입력해주세요.'
    return
  }
  
  isLoading.value = true
  errors.value = {}
  message.value = ''
  
  try {
    const code = codeDigits.value.join('')
    
    // TODO: 실제 API 호출로 변경
    await authAPI.verifyCode(email.value, code)
    
    // 임시로 성공 처리
    // await new Promise(resolve => setTimeout(resolve, 1000))
    
    isEmailVerified.value = true
    message.value = '이메일 인증이 완료되었습니다.'
    
    if (timer.value) {
      clearInterval(timer.value)
      countdown.value = 0
    }
  } catch (error) {
    errors.value.code = '인증 코드가 올바르지 않습니다. 다시 확인해주세요.'
    message.value = '인증에 실패했습니다.'
  } finally {
    isLoading.value = false
  }
}

const resendCode = async () => {
  if (countdown.value > 0) return
  
  isLoading.value = true
  message.value = ''
  
  try {
    // TODO: 실제 API 호출로 변경
    // await authAPI.sendVerificationCode(email.value)
    
    // 임시로 성공 처리
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    startCountdown()
    message.value = '인증 코드가 재발송되었습니다.'
    
    // 코드 입력 필드 초기화
    codeDigits.value = ['', '', '', '', '', '']
    errors.value = {}
  } catch (error) {
    message.value = '인증 코드 재발송에 실패했습니다.'
  } finally {
    isLoading.value = false
  }
}

const handleSignup = async () => {
  if (!validateForm()) return
  
  isLoading.value = true
  message.value = ''
  
  try {
    const response = await authAPI.signup({
      name: name.value,
      email: email.value,
      password: password.value,
      role: selectedRole.value
    })
    
    message.value = '회원가입이 완료되었습니다!'
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error) {
    console.error('회원가입 오류:', error)
    message.value = error.response?.data?.message || '회원가입에 실패했습니다.'
  } finally {
    isLoading.value = false
  }
}

// Cleanup
onUnmounted(() => {
  if (timer.value) {
    clearInterval(timer.value)
  }
})
</script>