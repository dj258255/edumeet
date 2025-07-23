<template>
  <div class="signup-view">
    <div class="signup-container">
      <h1>회원가입</h1>

      <!-- 이메일 & 인증 -->
      <form @submit.prevent>
        <div class="form-group">
          <label for="email">이메일</label>

          <div class="email-with-button">
            <input 
              type="email" 
              id="email" 
              v-model="email" 
              required
              placeholder="example@email.com"
              :class="{ 'error': errors.email }"
            />

            <button 
              type="button" 
              @click="sendCode" 
              :disabled="isSending"
            >
              {{ codeSent ? '재발송' : '인증발송' }}
            </button>
          </div>

          <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
        </div>
      </form>

      <!-- 인증번호 입력 -->
      <form @submit.prevent="verifyCode" v-if="codeSent && !formData.email">
        <div class="form-group">
          <input v-model="code" type="text" placeholder="인증번호를 입력해주세요" />
          <button type="button" @click="verifyCode">인증 확인</button>
        </div>
      </form>

      <!-- 회원가입 폼 -->
      <form @submit.prevent="handleSignup" class="signup-form">
        <div class="form-group">
          <label for="password">비밀번호</label>
          <input 
            type="password" 
            id="password" 
            v-model="formData.password" 
            required
            placeholder="8자 이상 입력해주세요"
            :class="{ 'error': errors.password }"
          />
          <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
        </div>

        <div class="form-group">
          <label for="confirmPassword">비밀번호 확인</label>
          <input 
            type="password" 
            id="confirmPassword" 
            v-model="formData.confirmPassword" 
            required
            placeholder="비밀번호를 다시 입력해주세요"
            :class="{ 'error': errors.confirmPassword }"
          />
          <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
        </div>

        <div class="form-group">
          <label for="name">이름</label>
          <input 
            type="text" 
            id="name" 
            v-model="formData.name" 
            required
            placeholder="이름을 입력해주세요"
            :class="{ 'error': errors.name }"
          />
          <span v-if="errors.name" class="error-message">{{ errors.name }}</span>
        </div>

        <button type="submit" 
                :disabled="isLoading || !formData.email" 
                class="signup-btn">
          {{ isLoading ? '가입 중...' : '회원가입' }}
        </button>
      </form>

      <div class="login-link">
        이미 계정이 있으신가요? <router-link to="/login">로그인하기</router-link>
      </div>

      <div v-if="message" :class="['message', messageType]">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '../stores/auth.js'

const router = useRouter()

const email = ref('')
const code = ref('')
const codeSent = ref(false)

const formData = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  name: ''
})

const isLoading = ref(false)
const isSending = ref(false)
const message = ref('')
const messageType = ref('')

const errors = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  name: ''
})


const validateForm = () => {
  let isValid = true

  Object.keys(errors).forEach(key => {
    errors[key] = ''
  })

  if (!formData.email) {
    errors.email = '이메일 인증을 완료해주세요.'
    isValid = false
  }

  if (!formData.password) {
    errors.password = '비밀번호를 입력해주세요.'
    isValid = false
  } else if (formData.password.length < 8) {
    errors.password = '비밀번호는 8자 이상이어야 합니다.'
    isValid = false
  }

  if (!formData.confirmPassword) {
    errors.confirmPassword = '비밀번호 확인을 입력해주세요.'
    isValid = false
  } else if (formData.password !== formData.confirmPassword) {
    errors.confirmPassword = '비밀번호가 일치하지 않습니다.'
    isValid = false
  }

  if (!formData.name) {
    errors.name = '이름을 입력해주세요.'
    isValid = false
  } else if (formData.name.length < 2) {
    errors.name = '이름은 2자 이상이어야 합니다.'
    isValid = false
  }

  return isValid
}
const sendCode = async () => {
  if (!email.value) {
    message.value = '이메일을 입력해주세요.'
    messageType.value = 'error'
    return
  }

  isSending.value = true
  try {
    await authAPI.sendcode({
      email:email.value})
    message.value = codeSent.value
      ? '인증번호를 재발송했습니다.'
      : '이메일로 인증번호를 보냈습니다.'
    messageType.value = 'success'
    codeSent.value = true
  } catch (err) {
    message.value = '인증번호 발송에 실패했습니다.'
    messageType.value = 'error'
  } finally {
    isSending.value = false
  }
}

const verifyCode = async () => {
  try {
    await authAPI.verifycode({
      email: formData.email,
      code:code.value
    })
    if (res.data.success) {
      message.value = '이메일 인증이 완료되었습니다.'
      messageType.value = 'success'
      formData.email = email.value
    } else {
      message.value = '인증번호가 올바르지 않습니다.'
      messageType.value = 'error'
    }
  } catch (err) {
    message.value = '인증 과정에서 오류가 발생했습니다.'
    messageType.value = 'error'
  }
}

const handleSignup = async () => {
  if (!validateForm()) {
    return
  }

  isLoading.value = true
  message.value = ''
  messageType.value = ''

  try {
    await authAPI.signup({
      email: formData.email,
      password: formData.password,
      name: formData.name
    })

    message.value = '회원가입이 완료되었습니다!'
    messageType.value = 'success'

    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error) {
    console.error('회원가입 오류:', error)

    if (error.response) {
      const errorMessage = error.response.data?.message || '회원가입 중 오류가 발생했습니다.'
      message.value = errorMessage
    } else {
      message.value = '네트워크 오류가 발생했습니다. 다시 시도해주세요.'
    }
    messageType.value = 'error'
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.signup-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  padding: 20px;
}

.signup-container {
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

.signup-form {
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

.email-with-button {
  display: flex;
  gap: 8px;
  width: 100%;
}

.email-with-button input {
  flex: 1;
  min-width: 0;
}

.email-with-button button {
  flex: 0 0 auto;
  background: linear-gradient(135deg, #227a53 0%, #b3f0c7 100%);
  color: white;
  border: none;
  padding: 0 12px;
  height: 44px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.signup-btn {
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

.signup-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.3);
}

.signup-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.login-link {
  text-align: center;
  margin-top: 20px;
  color: #666;
  font-size: 14px;
}

.login-link a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.login-link a:hover {
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
  .signup-container {
    padding: 30px 20px;
  }

  h1 {
    font-size: 24px;
  }
}
</style>
