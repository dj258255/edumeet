import axios from 'axios'

// API 기본 설정 (환경변수가 없으면 /api/v1)
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL
  ? import.meta.env.VITE_API_BASE_URL.replace(/\/$/, '')
  : '/api/v1')

// axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 요청 인터셉터: 공개 엔드포인트에는 토큰 미첨부
apiClient.interceptors.request.use(
  (config) => {
    const base = (config.baseURL || '').toString()
    const url = (config.url || '').toString()
    const full = url.startsWith('http')
      ? url
      : (base ? `${base.replace(/\/$/, '')}/${url.replace(/^\//, '')}` : url)

    let effectivePath = url
    try {
      effectivePath = new URL(full, window.location.origin).pathname
    } catch (_) {}

    const publicAuthSegments = [
      '/members/login',
      '/members/signup',
      '/members/refresh',
      '/members/send-code',
      '/members/verification',
      '/members/check-email'
    ]
    const isAuthEndpoint = publicAuthSegments.some((seg) => effectivePath.includes(seg))

    if (!isAuthEndpoint) {
      const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }

    return config
  },
  (error) => Promise.reject(error)
)

// ===== 단일 토큰 갱신 처리 (동시 401 방지) =====
let isRefreshing = false
let refreshSubscribers = []

function subscribeTokenRefresh(callback) {
  refreshSubscribers.push(callback)
}

function onRefreshed(newAccessToken) {
  refreshSubscribers.forEach((cb) => cb(newAccessToken))
  refreshSubscribers = []
}

// 응답 인터셉터 - 에러 처리 및 토큰 갱신
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const serverUnavailableStatuses = [502, 503]

    const originalRequest = error.config || {}
    const base = (originalRequest.baseURL || '').toString()
    const url = (originalRequest.url || '').toString()
    const full = url.startsWith('http')
      ? url
      : (base ? `${base.replace(/\/$/, '')}/${url.replace(/^\//, '')}` : url)
    let effectivePath = url
    try {
      effectivePath = new URL(full, window.location.origin).pathname
    } catch (_) {}

    const publicAuthSegments = [
      '/members/login',
      '/members/signup',
      '/members/send-code',
      '/members/verification',
      '/members/check-email',
      '/members/refresh'
    ]
    const isPublicAuthEndpoint = publicAuthSegments.some((seg) => effectivePath.includes(seg))

    if (!error.response || serverUnavailableStatuses.includes(error.response?.status)) {
      try {
        console.error('서버 연결 문제 감지')
        alert('서버와의 연결에 문제가 있습니다. 잠시 후 다시 시도해주세요.')
      } catch (_) {}
      return Promise.reject(error)
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      if (isPublicAuthEndpoint) {
        return Promise.reject(error)
      }

      const existingRefreshToken = localStorage.getItem('refreshToken')
      if (!existingRefreshToken) {
        localStorage.removeItem('token')
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((newToken) => {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(apiClient(originalRequest))
          })
        })
      }

      isRefreshing = true
      try {
        const refreshResponse = await axios.post(`${API_BASE_URL}/members/refresh`, {
          refreshToken: existingRefreshToken
        })
        const newAccessToken = refreshResponse.data.accessToken
        const newRefreshToken = refreshResponse.data.refreshToken

        localStorage.setItem('token', newAccessToken)
        localStorage.setItem('refreshToken', newRefreshToken)
        onRefreshed(newAccessToken)

        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(originalRequest)
      } catch (refreshError) {
        console.error('토큰 갱신 실패:', refreshError)
        localStorage.removeItem('token')
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient


