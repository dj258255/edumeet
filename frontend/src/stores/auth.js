import axios from "axios"
import { defineStore } from 'pinia'
import { sendVerificationCode as sendDummyCode, verifyEmailCode as verifyDummyCode, resendVerificationCode as resendDummyCode } from '@/utils/emailVerification.js'

// API 기본 설정
const API_BASE_URL = "http://localhost:8080/api/v1"

// axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
})

// 요청 인터셉터 - 토큰 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token")
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 응답 인터셉터 - 에러 처리
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      // 토큰이 만료되었거나 유효하지 않은 경우
      localStorage.removeItem("token")
      localStorage.removeItem("user")
      // 로그인 페이지로 리다이렉트
      window.location.href = "/login"
    }
    return Promise.reject(error)
  }
)

// 인증 관련 API 함수들
const authAPI = {
  // 회원가입
  signup: (userData) => {
    return apiClient.post("/members/signup", userData)
  },

  // 로그인
  login: (credentials) => {
    return apiClient.post("/members/login", credentials)
  },

  // 로그아웃
  logout: () => {
    return apiClient.post("/members/logout")
  },

  // 사용자 정보 조회
  getProfile: () => {
    return apiClient.get("/members/profile")
  },

  // 비밀번호 변경
  changePassword: (passwordData) => {
    return apiClient.put("/members/password", passwordData)
  },

  // 이메일 인증
  sendVerificationCode:(email)=>{
    return apiClient.post("/members/sendcode",email)
  },

  // 인증 확인
  verifyCode:(verifyInfo)=>{
    return apiClient.get("/members/verification",verifyInfo)
  },

  // 이메일 중복 확인
  checkEmail: (email) => {
    return apiClient.get(`/members/check-email?email=${email}`)
  }
}

// 토큰 관리 함수들
const tokenManager = {
  // 토큰 저장
  setToken: (token) => {
    localStorage.setItem("token", token)
  },

  // 토큰 가져오기
  getToken: () => {
    return localStorage.getItem("token")
  },

  // 토큰 삭제
  removeToken: () => {
    localStorage.removeItem("token")
  },

  // 토큰 유효성 확인
  isTokenValid: () => {
    const token = localStorage.getItem("token")
    if (!token || token === "undefined" || token === "null") return false
    
    // mock 토큰인 경우 (개발용)
    if (token.startsWith('mock_token_')) {
      return true
    }
    
    try {
      // JWT 토큰의 만료 시간 확인 (선택사항)
      const payload = JSON.parse(atob(token.split(".")[1]))
      return payload.exp * 1000 > Date.now()
    } catch (error) {
      console.error('토큰 파싱 에러:', error)
      localStorage.removeItem("token") // 잘못된 토큰 삭제
      return false
    }
  }
}

// 사용자 정보 관리 함수들
const userManager = {
  // 사용자 정보 저장
  setUser: (user) => {
    localStorage.setItem("user", JSON.stringify(user))
  },

  // 사용자 정보 가져오기
  getUser: () => {
    const user = localStorage.getItem("user")
    if (!user || user === "undefined" || user === "null") {
      return null
    }
    try {
      return JSON.parse(user)
    } catch (error) {
      console.error('사용자 정보 파싱 에러:', error)
      localStorage.removeItem("user") // 잘못된 데이터 삭제
      return null
    }
  },

  // 사용자 정보 삭제
  removeUser: () => {
    localStorage.removeItem("user")
  },

  // 로그인 상태 확인
  isLoggedIn: () => {
    return tokenManager.isTokenValid() && userManager.getUser() !== null
  }
}

// Pinia Store 정의
const useAuthStore = defineStore('auth', {
  state: () => ({
    user: userManager.getUser(),
    isAuthenticated: userManager.isLoggedIn(),
    loading: false,
    error: null
  }),

  getters: {
    // 현재 사용자 정보
    currentUser: (state) => state.user,
    
    // 로그인 상태
    isLoggedIn: (state) => state.isAuthenticated,
    
    // 로딩 상태
    isLoading: (state) => state.loading,
    
    // 에러 상태
    hasError: (state) => state.error !== null
  },

  actions: {
    // 로그인
    async login(email, password) {
      this.loading = true
      this.error = null
      
      try {
        const response = await authAPI.login({ email, password })
        console.log('로그인 응답:', response.data)
        
        // 응답 데이터 구조 확인 및 안전한 처리
        const responseData = response.data || {}
        const userEmail = responseData.email || email // 백엔드에서 email이 없으면 입력한 email 사용
        const accessToken = responseData.accessToken || responseData.token || 'mock_token_' + Date.now()
        const refreshToken = responseData.refreshToken
        
        // 토큰 저장
        tokenManager.setToken(accessToken)
        
        // 임시 사용자 정보 (이메일 기반)
        const tempUser = {
          email: userEmail,
          nickname: userEmail ? userEmail.split('@')[0] : '사용자' // 이메일에서 닉네임 추출, 안전 처리
        }
        
        // 사용자 정보 저장
        userManager.setUser(tempUser)
        this.user = tempUser
        this.isAuthenticated = true
        
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '로그인에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 회원가입
    async signup(userData) {
      this.loading = true
      this.error = null
      
      try {
        const response = await authAPI.signup(userData)
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '회원가입에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 이메일 인증 코드 전송
    async sendVerificationCode(email) {
      this.loading = true
      this.error = null
      try {
        // 더미 데이터 사용
        const result = await sendDummyCode(email)
        if (result.success) {
          console.log('발송된 인증 코드:', result.code) // 개발용 로그
          return { success: true, message: result.message }
        } else {
          this.error = result.message
          throw new Error(result.message)
        }
      } catch (error) {
        this.error = error.message || '인증 코드 전송에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 인증 코드 검증
    async verifyCode(email, code) {
      this.loading = true
      this.error = null
      try {
        // 더미 데이터 사용
        const result = await verifyDummyCode(email, code)
        if (result.success) {
          return { success: true, message: result.message }
        } else {
          this.error = result.message
          throw new Error(result.message)
        }
      } catch (error) {
        this.error = error.message || '인증 코드 검증에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 인증 코드 재전송
    async resendCode(email) {
      this.loading = true
      this.error = null
      try {
        // 더미 데이터 사용
        const result = await resendCode(email)
        if (result.success) {
          console.log('재발송된 인증 코드:', result.code) // 개발용 로그
          return { success: true, message: result.message }
        } else {
          this.error = result.message
          throw new Error(result.message)
        }
      } catch (error) {
        this.error = error.message || '인증 코드 재전송에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 로그아웃
    async logout() {
      this.loading = true
      
      try {
        await authAPI.logout()
      } catch (error) {
        console.error('로그아웃 API 호출 실패:', error)
      } finally {
        // 로컬 상태 정리
        tokenManager.removeToken()
        userManager.removeUser()
        
        // 상태 초기화
        this.user = null
        this.isAuthenticated = false
        this.loading = false
        this.error = null
      }
    },

    // 사용자 정보 조회
    async getProfile() {
      this.loading = true
      this.error = null
      
      try {
        const response = await authAPI.getProfile()
        const user = response.data
        
        // 사용자 정보 업데이트
        userManager.setUser(user)
        this.user = user
        
        return user
      } catch (error) {
        this.error = error.response?.data?.message || '사용자 정보 조회에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 비밀번호 변경
    async changePassword(passwordData) {
      this.loading = true
      this.error = null
      
      try {
        const response = await authAPI.changePassword(passwordData)
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '비밀번호 변경에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 이메일 중복 확인
    async checkEmail(email) {
      try {
        const response = await authAPI.checkEmail(email)
        return response.data
      } catch (error) {
        throw error
      }
    },

    // 에러 초기화
    clearError() {
      this.error = null
    },

    // 초기화 (앱 시작 시 호출)
    initialize() {
      // 잘못된 localStorage 데이터 정리
      this.cleanupLocalStorage()
      
      const user = userManager.getUser()
      const isAuthenticated = userManager.isLoggedIn()
      
      this.user = user
      this.isAuthenticated = isAuthenticated
    },

    // localStorage 정리
    cleanupLocalStorage() {
      const token = localStorage.getItem("token")
      const user = localStorage.getItem("user")
      
      if (token === "undefined" || token === "null") {
        localStorage.removeItem("token")
      }
      
      if (user === "undefined" || user === "null") {
        localStorage.removeItem("user")
      }
    }
  }
})

// 기존 export 유지 (하위 호환성)
export { authAPI, tokenManager, userManager }
export default apiClient 

export async function login(email, password) {
  const response = await apiClient.post("/members/login", { email, password })
  // 필요시 토큰 저장 등 추가
  return response.data
}

export async function signup(userData) {
  const response = await apiClient.post("/members/signup", userData)
  return response.data
}

export async function sendVerificationCode(email) {
  const response = await apiClient.post("/members/sendcode", email)
  return response.data
}

export async function verifyCode(email, code) {
  const response = await apiClient.post("/members/verify", { email, code })
  return response.data
}

export async function resendCode(email) {
  return await sendVerificationCode(email)
}

// auth store export
export { useAuthStore } 