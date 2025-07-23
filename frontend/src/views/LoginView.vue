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
          
          <button type="submit" class="login-btn" :disabled="!email || !password || !selectedRole">
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
        <div class="role-selection">
          <p class="role-label">또는 다음으로 로그인</p>
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import '../styles/LoginView.css'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const selectedRole = ref('tutor') // 기본값으로 tutor 선택
const errors = ref({})
const message = ref('')

const validateForm = () => {
  errors.value = {}
  
  if (!email.value) {
    errors.value.email = '이메일을 입력해주세요.'
  } else if (!/\S+@\S+\.\S+/.test(email.value)) {
    errors.value.email = '올바른 이메일 형식을 입력해주세요.'
  }
  
  if (!password.value) {
    errors.value.password = '비밀번호를 입력해주세요.'
  } else if (password.value.length < 6) {
    errors.value.password = '비밀번호는 최소 6자 이상이어야 합니다.'
  }
  
  return Object.keys(errors.value).length === 0
}

const handleLogin = async () => {
  if (!validateForm()) return
  
  try {
    await authStore.login(email.value, password.value)
    message.value = '로그인 성공!'
    setTimeout(() => {
      router.push('/')
    }, 1000)
  } catch (error) {
    message.value = error.response?.data?.message || '로그인에 실패했습니다.'
  }
}
</script> 