import axios from 'axios'

// API 기본 설정
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

// axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  }
})

// 요청 인터셉터 (토큰 추가)
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

// 응답 인터셉터 (에러 처리)
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 토큰 만료 시 로그인 페이지로 리다이렉트
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// 클래스 관련 API 함수들
export const classService = {
  // 모든 클래스 목록 조회
  async getAllClasses() {
    try {
      const response = await apiClient.get('/class')
      return response.data
    } catch (error) {
      console.error('클래스 목록 조회 실패:', error)
      throw error
    }
  },

  // 인기 클래스 목록 조회
  async getPopularClasses(limit = 8) {
    try {
      const response = await apiClient.get(`/class/popular?limit=${limit}`)
      return response.data
    } catch (error) {
      console.error('인기 클래스 조회 실패:', error)
      throw error
    }
  },

  // 카테고리별 클래스 조회
  async getClassesByCategory(category) {
    try {
      const response = await apiClient.get(`/class/category/${category}`)
      return response.data
    } catch (error) {
      console.error('카테고리별 클래스 조회 실패:', error)
      throw error
    }
  },

  // 클래스 상세 정보 조회
  async getClassDetail(classId) {
    try {
      const response = await apiClient.get(`/class/${classId}`)
      return response.data
    } catch (error) {
      console.error('클래스 상세 정보 조회 실패:', error)
      throw error
    }
  },

  // 클래스 검색
  async searchClasses(query) {
    try {
      const response = await apiClient.get(`/class/search?q=${encodeURIComponent(query)}`)
      return response.data
    } catch (error) {
      console.error('클래스 검색 실패:', error)
      throw error
    }
  },

  // 클래스 수강 신청
  async enrollClass(classId) {
    try {
      const response = await apiClient.post(`/class/${classId}/enroll`)
      return response.data
    } catch (error) {
      console.error('클래스 수강 신청 실패:', error)
      throw error
    }
  },

  // 클래스 평점 조회
  async getClassRating(classId) {
    try {
      const response = await apiClient.get(`/class/${classId}/rating`)
      return response.data
    } catch (error) {
      console.error('클래스 평점 조회 실패:', error)
      throw error
    }
  },

  // 클래스 생성 (관리자용)
  async createClass(classData) {
    try {
      const response = await apiClient.post('/class', classData)
      return response.data
    } catch (error) {
      console.error('클래스 생성 실패:', error)
      throw error
    }
  },

  // 클래스 수정 (관리자용)
  async updateClass(classId, classData) {
    try {
      const response = await apiClient.put(`/class/${classId}`, classData)
      return response.data
    } catch (error) {
      console.error('클래스 수정 실패:', error)
      throw error
    }
  },

  // 클래스 삭제 (관리자용)
  async deleteClass(classId) {
    try {
      const response = await apiClient.delete(`/class/${classId}`)
      return response.data
    } catch (error) {
      console.error('클래스 삭제 실패:', error)
      throw error
    }
  }
}

export default classService 