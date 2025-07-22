<template>
  <div class="login-view">
    <div class="login-container">
      <h1>로그인</h1>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="email">이메일</label>
          <input 
            type="email" 
            id="email" 
            v-model="formData.email" 
            required
            placeholder="example@email.com"
            :class="{ 'error': errors.email }"
          />
          <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
        </div>

        <div class="form-group">
          <label for="password">비밀번호</label>
          <input 
            type="password" 
            id="password" 
            v-model="formData.password" 
            required
            placeholder="비밀번호를 입력해주세요"
            :class="{ 'error': errors.password }"
          />
          <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
        </div>

        <button type="submit" :disabled="isLoading" class="login-btn">
          {{ isLoading ? "로그인 중..." : "로그인" }}
        </button>
      </form>

      <div class="signup-link">
        계정이 없으신가요? <router-link to="/signup">회원가입하기</router-link>
      </div>

      <div v-if="message" :class="['message', messageType]">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue"
import { useRouter } from "vue-router"
import { authAPI, userManager, tokenManager } from "../stores/auth.js"

const router = useRouter()

// 폼 데이터
const formData = reactive({
  email: "",
  password: ""
})

// 상태 관리
const isLoading = ref(false)
const message = ref("")
const messageType = ref("")

// 에러 메시지
const errors = reactive({
  email: "",
  password: ""
})

// 유효성 검사
const validateForm = () => {
  let isValid = true
  
  // 에러 메시지 초기화
  Object.keys(errors).forEach((key) => {
    errors[key] = ""
  })

  // 이메일 검사
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!formData.email) {
    errors.email = "이메일을 입력해주세요."
    isValid = false
  } else if (!emailRegex.test(formData.email)) {
    errors.email = "올바른 이메일 형식을 입력해주세요."
    isValid = false
  }

  // 비밀번호 검사
  if (!formData.password) {
    errors.password = "비밀번호를 입력해주세요."
    isValid = false
  }

  return isValid
}

// 로그인 처리
const handleLogin = async () => {
  if (!validateForm()) {
    return
  }

  isLoading.value = true
  message.value = ""
  messageType.value = ""

  try {
    // Spring 백엔드 API 호출
    const response = await authAPI.login({
      email: formData.email,
      password: formData.password
    })

    // 로그인 성공 시 토큰과 사용자 정보 저장
    if (response.data.token) {
      tokenManager.setToken(response.data.token)
      userManager.setUser(response.data.user)
    }

    message.value = "로그인되었습니다!"
    messageType.value = "success"
    
    // 1초 후 홈페이지로 이동
    setTimeout(() => {
      router.push("/")
    }, 1000)

  } catch (error) {
    console.error("로그인 오류:", error)
    
    if (error.response) {
      // 서버에서 오는 에러 메시지
      const errorMessage = error.response.data?.message || "로그인 중 오류가 발생했습니다."
      message.value = errorMessage
    } else {
      message.value = "네트워크 오류가 발생했습니다. 다시 시도해주세요."
    }
    messageType.value = "error"
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.login-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  padding: 20px;
}

.login-container {
  max-width: 400px;
  width: 100%;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.1);
  padding: 40px;
}

h1 {
  text-align: center;
  color: #333;
  margin-bottom: 30px;
  font-size: 28px;
  font-weight: 600;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

label {
  font-weight: 500;
  color: #555;
  font-size: 14px;
}

input {
  padding: 12px 16px;
  border: 2px solid #e1e5e9;
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.3s ease;
  background: #fafbfc;
}

input:focus {
  outline: none;
  border-color: #667eea;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

input.error {
  border-color: #e74c3c;
  background: #fff5f5;
}

.error-message {
  color: #e74c3c;
  font-size: 12px;
  margin-top: 4px;
}

.login-btn {
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  color: white;
  border: none;
  padding: 14px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 10px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.3);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.signup-link {
  text-align: center;
  margin-top: 20px;
  color: #666;
  font-size: 14px;
}

.signup-link a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.signup-link a:hover {
  text-decoration: underline;
}

.message {
  margin-top: 20px;
  padding: 12px 16px;
  border-radius: 8px;
  text-align: center;
  font-weight: 500;
}

.message.success {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.message.error {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

@media (max-width: 480px) {
  .login-container {
    padding: 30px 20px;
  }
  
  h1 {
    font-size: 24px;
  }
}
</style> 