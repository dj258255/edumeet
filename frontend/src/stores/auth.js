import axios from 'axios'

// API 기본 설정
const API_BASE_URL = 'http://localhost:8080/api'

// axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 요청 인터셉터 - 토큰 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
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
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 로그인 페이지로 리다이렉트
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// 인증 관련 API 함수들
export const authAPI = {
  // 회원가입
  signup: (userData) => {
    return apiClient.post('/auth/signup', userData)
  },

  // 로그인
  login: (credentials) => {
    return apiClient.post('/auth/login', credentials)
  },

  // 로그아웃
  logout: () => {
    return apiClient.post('/auth/logout')
  },

  // 사용자 정보 조회
  getProfile: () => {
    return apiClient.get('/auth/profile')
  },

  // 비밀번호 변경
  changePassword: (passwordData) => {
    return apiClient.put('/auth/password', passwordData)
  },

  // 이메일 중복 확인
  checkEmail: (email) => {
    return apiClient.get(`/auth/check-email?email=${email}`)
  }
}

// 토큰 관리 함수들
export const tokenManager = {
  // 토큰 저장
  setToken: (token) => {
    localStorage.setItem('token', token)
  },

  // 토큰 가져오기
  getToken: () => {
    return localStorage.getItem('token')
  },

  // 토큰 삭제
  removeToken: () => {
    localStorage.removeItem('token')
  },

  // 토큰 유효성 확인
  isTokenValid: () => {
    const token = localStorage.getItem('token')
    if (!token) return false
    
    try {
      // JWT 토큰의 만료 시간 확인 (선택사항)
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.exp * 1000 > Date.now()
    } catch (error) {
      return false
    }
  }
}

// 사용자 정보 관리 함수들
export const userManager = {
  // 사용자 정보 저장
  setUser: (user) => {
    localStorage.setItem('user', JSON.stringify(user))
  },

  // 사용자 정보 가져오기
  getUser: () => {
    const user = localStorage.getItem('user')
    return user ? JSON.parse(user) : null
  },

  // 사용자 정보 삭제
  removeUser: () => {
    localStorage.removeItem('user')
  },

  // 로그인 상태 확인
  isLoggedIn: () => {
    return tokenManager.isTokenValid() && userManager.getUser() !== null
  }
}

export default apiClient 