import { defineStore } from 'pinia'
import apiClient from '@/utils/apiClient'
import { sendVerificationCode as sendDummyCode, verifyEmailCode as verifyDummyCode, resendVerificationCode as resendDummyCode } from '@/utils/emailVerification.js'

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
  sendVerificationCode:(emailData)=>{
    return apiClient.post("/members/send-code", emailData)
  },

  // 인증 확인
  verifyCode: (payload) => {
    return apiClient.post("/members/verification", payload, {
      headers: { "Content-Type": "application/json" }
    });
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

  // 토큰 유효성 확인 (존재 여부 위주)
  isTokenValid: () => {
    const token = localStorage.getItem("token") || localStorage.getItem("accessToken")
    return !!(token && token !== "undefined" && token !== "null")
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
        console.log('로그인 응답:', response)

        // 1) 토큰 추출 (바디 우선, 없으면 헤더 검사)
        const responseData = response?.data || {}
        const headerAuth = response?.headers?.authorization || response?.headers?.Authorization
        let accessToken = responseData.accessToken || responseData.token || null
        if (!accessToken && typeof headerAuth === 'string' && headerAuth.toLowerCase().startsWith('bearer ')) {
          accessToken = headerAuth.split(' ')[1]
        }
        const refreshToken = responseData.refreshToken || response?.headers?.['x-refresh-token'] || null

        // 2) 토큰 저장 (문자열이면 저장)
        if (typeof accessToken === 'string' && accessToken !== 'undefined' && accessToken !== 'null' && accessToken.length > 0) {
          tokenManager.setToken(accessToken)
          localStorage.setItem('accessToken', accessToken)
        } else {
          tokenManager.removeToken()
          localStorage.removeItem('accessToken')
        }
        if (refreshToken && typeof refreshToken === 'string' && refreshToken !== 'undefined' && refreshToken !== 'null') {
          localStorage.setItem('refreshToken', refreshToken)
        } else {
          localStorage.removeItem('refreshToken')
        }

        // 3) 사용자 정보 저장 (응답 또는 이메일 기반)
        const userEmail = responseData.email || email
        const tempUser = {
          email: userEmail,
          nickname: responseData.nickname || (userEmail ? userEmail.split('@')[0] : '사용자')
        }
        userManager.setUser(tempUser)
        this.user = tempUser
        this.isAuthenticated = !!tokenManager.getToken()

        // 4) 프로필 동기화는 선택 (실패해도 흐름 계속)
        try {
          await this.getProfile()
        } catch (_) {}

        return responseData
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
        console.log("이메일 인증 코드 전송:", email)
        const emailData = { email: email }
        console.log("이메일 인증 코드 전송:", emailData)
        const result = await authAPI.sendVerificationCode(emailData);
        console.log("API 응답 결과:", result);
        console.log('API 응답 결과:', result.data.message);
      } catch (error) {
        this.error = error.message || '인증 코드 전송에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    // 인증 코드 검증
async verifyCode(verifyInfo) {
  this.loading = true;
  this.error = null;

  try {
    const payload = verifyInfo.value || verifyInfo;
    
    // API 호출
    const result = await authAPI.verifyCode(payload);

    // 백엔드의 메시지 내용을 직접 확인하여 성공/실패를 구분
    if (result.data?.message === "인증 성공") {
      return { success: true, message: result.data.message };
    } else {
      // '인증 실패' 메시지를 받으면, error 상태 업데이트 후 실패 반환
      this.error = result.data?.message || '인증 코드 검증에 실패했습니다.';
      return { success: false, message: this.error };
    }
  } catch (error) {
    // API 호출 자체에 실패했을 때 (네트워크 오류 등)만 이 블록이 실행됩니다.
    this.error = error.message || '인증 코드 검증 중 네트워크 오류가 발생했습니다.';
    return { success: false, message: this.error };
  } finally {
    this.loading = false;
  }
},


    // 인증 코드 재전송
    async resendCode(email) {
      this.loading = true
      this.error = null
      try {
        const result = await resendDummyCode(email)
        if (result.success) {
          console.log('재발송된 인증 코드:', result.code)
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
      this.loading = true;
      
      try {
        await authAPI.logout();
        console.log('로그아웃 API 호출 성공');
      } catch (error) {
        console.error('로그아웃 API 호출 실패:', error);
      } finally {
        tokenManager.removeToken();
        userManager.removeUser();
        localStorage.removeItem("refreshToken");
        localStorage.removeItem("accessToken");
        
        this.user = null;
        this.isAuthenticated = false;
        this.loading = false;
        this.error = null;
        
        // 여기에 페이지 리다이렉션 로직 추가
        window.location.href = "/login"; // 로그인 페이지로 리다이렉션
        // 또는
        // window.location.href = "/"; // 홈 페이지로 리다이렉션
      }
    },

    // 사용자 정보 조회
    async getProfile() {
      this.loading = true
      this.error = null
      
      try {
        const response = await authAPI.getProfile()
        const user = response.data
        
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
      this.cleanupLocalStorage()
      
      const user = userManager.getUser()
      const isAuthenticated = userManager.isLoggedIn()
      
      this.user = user
      this.isAuthenticated = isAuthenticated
    },

    // localStorage 정리
    cleanupLocalStorage() {
      const token = localStorage.getItem("token")
      const accessToken = localStorage.getItem("accessToken")
      const refreshToken = localStorage.getItem("refreshToken")
      const user = localStorage.getItem("user")
      
      if (token === "undefined" || token === "null") {
        localStorage.removeItem("token")
      }
      if (accessToken === "undefined" || accessToken === "null") {
        localStorage.removeItem("accessToken")
      }
      
      if (refreshToken === "undefined" || refreshToken === "null") {
        localStorage.removeItem("refreshToken")
      }
      
      if (user === "undefined" || user === "null") {
        localStorage.removeItem("user")
      }
    }
  }
})

// 기존 export 유지 (스토어/유틸만 노출)
export { authAPI, tokenManager, userManager }
export { useAuthStore }