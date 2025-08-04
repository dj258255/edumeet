<template>
  <div class="signup-view">
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
          <h1 class="brand-title">Join Our Community</h1>
          <p class="brand-description">
            It is a long established fact that a reader will be distracted by the readable content
            of a page when looking at its layout.
          </p>
        </div>

        <div class="brand-footer">
          <span class="region">KOREA</span>
          <span class="region">ASIA</span>
          <span class="region">GLOBAL</span>
        </div>
      </div>
    </div>

    <div class="form-section">
      <div class="form-container">
        <h2 class="form-title">Sign Up</h2>

        <form class="signup-form" @submit.prevent="handleSignup">
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
                :disabled="isEmailVerified || verificationSent"
              />
            </div>
            <div v-if="errors.email" class="error-message">{{ errors.email }}</div>
          </div>

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
                <strong>{{ email }}</strong>로 발송된 8자리 인증 코드를 입력해주세요.
              </p>

              <div class="code-input-section">
                <label>VERIFICATION CODE</label>
                <div class="code-input-wrapper">
                  <input
                    v-for="(digit, index) in 8"
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

          <div v-if="isEmailVerified" class="email-verified">
            <div class="verified-icon">✅</div>
            <p class="verified-text">이메일 인증 완료</p>
          </div>

          <div class="form-group">
            <label for="name">NAME</label>
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input
                :disabled="!isEmailVerified"
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
            <label for="password">PASSWORD</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                :disabled="!isEmailVerified"
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
                :disabled="!isEmailVerified"
                id="confirmPassword"
                v-model="confirmPassword"
                type="password"
                :class="{ error: errors.confirmPassword }"
                placeholder="Confirm your password"
              />
            </div>
            <div v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</div>
          </div>

          <button
            type="submit"
            class="signup-btn"
            :disabled="
              isLoading ||
              !name ||
              !email ||
              !password ||
              !confirmPassword ||
              !selectedRole ||
              !isEmailVerified
            "
          >
            {{ isLoading ? 'Signing up...' : 'Sign Up' }}
          </button>
        </form>

        <div v-if="message" :class="['message', message.includes('완료') ? 'success' : 'error']">
          {{ message }}
        </div>

        <div class="login-link">
          Already have an account? <RouterLink to="/login">Sign In</RouterLink>
        </div>
        <div class="role-selection">
          <p class="role-label">또는 다음으로 로그인</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import '../styles/SignupView.css'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const name = ref('')
const selectedRole = ref('tutor')
const errors = ref({})
const message = ref('')
const isLoading = computed(() => authStore.loading)

// 이메일 인증 관련 상태
const verificationSent = ref(false)
const isEmailVerified = ref(false)
const codeDigits = ref(['', '', '', '', '', '', '', ''])
const countdown = ref(0)
const timer = ref(null)

const isCodeComplete = computed(() => codeDigits.value.every((digit) => digit !== ''))

const validateForm = () => {
  errors.value = {}
  // 이메일이 인증되었을 때만 이름, 비밀번호 유효성 검사
  if (isEmailVerified.value) {
    if (!name.value) errors.value.name = '이름을 입력해주세요.'
    if (!password.value) errors.value.password = '비밀번호를 입력해주세요.'
    else if (password.value.length < 6) errors.value.password = '비밀번호는 최소 6자 이상이어야 합니다.'
    if (!confirmPassword.value) errors.value.confirmPassword = '비밀번호 확인을 입력해주세요.'
    else if (password.value !== confirmPassword.value)
      errors.value.confirmPassword = '비밀번호가 일치하지 않습니다.'
  }

  // 이메일은 인증 여부와 관계없이 항상 유효성 검사, 하지만 인증되지 않았다면 에러 표시
  if (!email.value) errors.value.email = '이메일을 입력해주세요.'
  else if (!/\S+@\S+\.\S+/.test(email.value))
    errors.value.email = '올바른 이메일 형식을 입력해주세요.'
  if (!isEmailVerified.value) errors.value.email = '이메일 인증을 완료해주세요.'

  return Object.keys(errors.value).length === 0
}

const startCountdown = () => {
  countdown.value = 180
  timer.value = setInterval(() => {
    if (countdown.value > 0) countdown.value--
    else clearInterval(timer.value)
  }, 1000)
}

const formatTime = (seconds) => {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

const handleCodeInput = (index, event) => {
  const value = event.target.value
  if (!/^\d*$/.test(value)) {
    event.target.value = ''
    return
  }
  codeDigits.value[index] = value
  if (value && index < 7) {
    const nextInput = document.getElementById(`code-${index + 1}`)
    if (nextInput) nextInput.focus()
  }
}
const handleCodeKeydown = (index, event) => {
  if (event.key === 'Backspace' && !codeDigits.value[index] && index > 0) {
    const prevInput = document.getElementById(`code-${index - 1}`)
    if (prevInput) prevInput.focus()
  }
}
const handleCodePaste = (event) => {
  event.preventDefault()
  const pastedData = event.clipboardData.getData('text')
  const numbers = pastedData.replace(/\D/g, '').slice(0, 8)
  if (numbers.length === 8) codeDigits.value = numbers.split('')
}

const sendVerificationCode = async () => {
  if (!email.value || !/\S+@\S+\.\S+/.test(email.value)) {
    errors.value.email = '올바른 이메일 형식을 입력해주세요.'
    return
  }
  errors.value = {}
  message.value = ''
  try {
    await authStore.sendVerificationCode(email.value)
    verificationSent.value = true
    startCountdown()
    message.value = '인증 코드가 발송되었습니다.'
  } catch (error) {
    message.value = authStore.error || '인증 코드 발송에 실패했습니다.'
  }
}
const verifyCode = async () => {
  if (!isCodeComplete.value) {
    errors.value.code = '8자리 인증 코드를 모두 입력해주세요.'
    return
  }
  errors.value = {}
  message.value = ''
  try {
    const code = codeDigits.value.join('')
    const verifyInfo=ref([email.value,code])
    await authStore.verifyCode(verifyInfo)
    isEmailVerified.value = true
    message.value = '이메일 인증이 완료되었습니다.'
    if (timer.value) {
      clearInterval(timer.value)
      countdown.value = 0
    }
  } catch (error) {
    errors.value.code = authStore.error || '인증 코드가 올바르지 않습니다.'
    message.value = '인증에 실패했습니다.'
  }
}
const resendCode = async () => {
  if (countdown.value > 0) return
  message.value = ''
  try {
    await authStore.resendCode(email.value)
    startCountdown()
    message.value = '인증 코드가 재발송되었습니다.'
    codeDigits.value = ['', '', '', '', '', '', '', '']
    errors.value = {}
  } catch (error) {
    message.value = authStore.error || '인증 코드 재발송에 실패했습니다.'
  }
}
const handleSignup = async () => {
  if (!validateForm()) return
  message.value = ''
  try {
    await authStore.signup({
      nickname: name.value,
      email: email.value,
      password: password.value,
    })
    message.value = '회원가입이 완료되었습니다!'
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error) {
    message.value = authStore.error || '회원가입에 실패했습니다.'
  }
}
onUnmounted(() => {
  if (timer.value) clearInterval(timer.value)
})
</script>