import axios from "axios"
import { defineStore } from 'pinia'
import { sendVerificationCode as sendDummyCode, verifyEmailCode as verifyDummyCode, resendVerificationCode as resendDummyCode } from '@/utils/emailVerification.js'
import router from '@/router'

// API 기본 설정
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

// axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
})

apiClient.interceptors.request.use(
  (config) => {
    // 인증이 불필요한 엔드포인트에서는 토큰을 붙이지 않음
    const urlPath = (config.url || '').toString();
    const isAuthEndpoint = [
      '/members/login',
      '/members/signup',
      '/members/refresh',
      '/members/send-code',
      '/members/verification',
      '/members/check-email'
    ].some((path) => urlPath.startsWith(path));

    if (!isAuthEndpoint) {
      const token = localStorage.getItem("token") || localStorage.getItem("accessToken")
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ===== 단일 토큰 갱신 처리 (동시 401 방지) =====
let isRefreshing = false;
let refreshSubscribers = [];

function subscribeTokenRefresh(callback) {
  refreshSubscribers.push(callback);
}

function onRefreshed(newAccessToken) {
  refreshSubscribers.forEach((cb) => cb(newAccessToken));
  refreshSubscribers = [];
}

// 응답 인터셉터 - 에러 처리 및 토큰 갱신
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config || {};

    // URL path 계산 (어떤 엔드포인트인지 식별)
    const base = (originalRequest.baseURL || '').toString();
    const url = (originalRequest.url || '').toString();
    const full = url.startsWith('http') ? url : (base ? `${base.replace(/\/$/, '')}/${url.replace(/^\//, '')}` : url);
    let effectivePath = url;
    try {
      effectivePath = new URL(full, window.location.origin).pathname;
    } catch (_) {}

    const publicAuthSegments = [
      '/members/login',
      '/members/signup',
      '/members/send-code',
      '/members/verification',
      '/members/check-email',
      '/members/refresh'
    ];
    const isPublicAuthEndpoint = publicAuthSegments.some((seg) => effectivePath.includes(seg));

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      // 로그인/회원가입 등 공개 엔드포인트의 401은 리다이렉트 하지 않고 에러만 반환
      if (isPublicAuthEndpoint) {
        return Promise.reject(error);
      }

      const existingRefreshToken = localStorage.getItem('refreshToken');
      if (!existingRefreshToken) {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken) => {
            originalRequest.headers = originalRequest.headers || {};
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            resolve(apiClient(originalRequest));
          });
        });
      }

      isRefreshing = true;
      try {
        const refreshResponse = await axios.post(`${API_BASE_URL}/members/refresh`, {
          refreshToken: existingRefreshToken,
        });
        const newAccessToken = refreshResponse.data.accessToken;
        const newRefreshToken = refreshResponse.data.refreshToken;

        localStorage.setItem('token', newAccessToken);
        localStorage.setItem('refreshToken', newRefreshToken);
        onRefreshed(newAccessToken);

        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        console.error('토큰 갱신 실패:', refreshError);
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

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
    return apiClient.post("/members/send-code",email)
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
    // OAuth2 로그인 (카카오 등)
    async loginWithOAuth2(tokenData) {
      this.loading = true
      this.error = null
      
      try {
        // OAuth2로 받은 토큰 정보 저장
        if (tokenData.accessToken) {
          tokenManager.setToken(tokenData.accessToken)
          localStorage.setItem('accessToken', tokenData.accessToken)
        }
        
        if (tokenData.refreshToken) {
          localStorage.setItem('refreshToken', tokenData.refreshToken)
        }
        
        // 사용자 정보 저장
        if (tokenData.user) {
          userManager.setUser(tokenData.user)
          this.user = tokenData.user
        }
        
        this.isAuthenticated = true
        
        console.log('✅ OAuth2 로그인 성공:', this.user)
        return tokenData
        
      } catch (error) {
        this.error = 'OAuth2 로그인 처리에 실패했습니다.'
        console.error('OAuth2 로그인 오류:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    // 일반 로그인
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

        // 4) 프로필 동기화는 선택 (사용자 화면 이동은 컴포넌트에서 제어)
        try {
          await this.getProfile()
        } catch (_) {}

        return responseData
      } catch (error) {
        this.error = error.response?.data?.message || '로그인에 실패했습니다.'
        // 페이지 리로드 방지: 에러만 던지고 상태 유지
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
        
        // ✅ 회원가입 성공 시 로그인 페이지로 이동
        router.push("/login")

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
        const result = await authAPI.sendVerificationCode(email);
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
        // 실제 백엔드 API 호출 (sendVerificationCode와 동일한 엔드포인트 사용)
        const response = await authAPI.sendVerificationCode(email)
        console.log('재전송 API 응답:', response)
        return { success: true, message: '인증 코드가 재발송되었습니다.' }
      } catch (error) {
        console.error('재전송 API 에러:', error)
        this.error = error.response?.data?.message || '인증 코드 재전송에 실패했습니다.'
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
        
        this.user = null;
        this.isAuthenticated = false;
        this.loading = false;
        this.error = null;
        
        // 여기에 페이지 리다이렉션 로직 추가
        router.push("/login")
        // 또는
        // window.location.href = "/"; // 홈 페이지로 리다이렉션
      }
    },

    // 자동 로그아웃 (API 호출 없이 강제 로그아웃)
    async forceLogout(reason = '자동 로그아웃') {
      console.warn(`강제 로그아웃 실행: ${reason}`);
      
      // 토큰 및 사용자 정보 즉시 삭제
      tokenManager.removeToken();
      userManager.removeUser();
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("accessToken");
      
      this.user = null;
      this.isAuthenticated = false;
      this.loading = false;
      this.error = null;
      
      // 즉시 로그인 페이지로 리다이렉트
      router.push("/login");
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
      const refreshToken = localStorage.getItem("refreshToken")
      const user = localStorage.getItem("user")
      
      if (token === "undefined" || token === "null") {
        localStorage.removeItem("token")
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

// 기존 export 유지 (하위 호환성)
export { authAPI, tokenManager, userManager }
export default apiClient 

export async function login(email, password) {
  const response = await apiClient.post("/members/login", { email, password })
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
