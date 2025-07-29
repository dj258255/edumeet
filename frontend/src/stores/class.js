// src/stores/class.js
import { defineStore } from 'pinia'
import apiClient from '@/stores/auth' // auth.js에서 내보낸 apiClient를 가져옵니다.

export const useClassStore = defineStore('class', {
  state: () => ({
    loading: false,
    error: null,
    classInfo: null, // 특정 반 정보를 저장할 상태
    roomList: [],    // 화상채팅 방 목록을 저장할 상태
    myClasses: [],   // 추가: 내가 속한 클래스 목록을 저장할 상태
  }),

  getters: {
    isLoading: (state) => state.loading,
    hasError: (state) => state.error !== null,
    getCurrentClassInfo: (state) => state.classInfo,
    getRoomList: (state) => state.roomList,
    getMyClasses: (state) => state.myClasses, // 추가: 내가 속한 클래스 목록 getter
  },

  actions: {
    /**
     * 새로운 클래스를 생성합니다. (기존 로직 유지)
     * @param {object} classData - 생성할 클래스의 데이터 ({ name: string, description: string })
     * @returns {Promise<object>} 생성된 클래스 정보
     */
    async createClass(classData) {
      this.loading = true
      this.error = null

      try {
        const response = await apiClient.post('/class', classData) // API 경로
        // 클래스 생성 후, 내가 속한 클래스 목록을 새로고침할 수 있습니다.
        await this.fetchMyClasses(); 
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '클래스 생성에 실패했습니다.'
        console.error('클래스 생성 API 호출 에러:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * 특정 반 정보를 불러옵니다.
     * @param {string} classId - 불러올 반의 ID
     */
    async fetchClassInfo(classId) {
      this.loading = true
      this.error = null
      try {
        const response = await apiClient.get(`/class/${classId}`)
        this.classInfo = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '반 정보를 불러오는 데 실패했습니다.'
        console.error('반 정보 불러오기 API 호출 에러:', error)
        this.classInfo = { name: '알 수 없는 반', description: '' }; // 에러 시 기본값 설정
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * 특정 반의 화상 채팅방 목록을 불러옵니다.
     * @param {string} classId - 방 목록을 가져올 반의 ID
     */
    async fetchRoomList(classId) {
      this.loading = true
      this.error = null
      try {
        const response = await apiClient.get(`/meeting?classId=${classId}`) // API 경로
        this.roomList = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '화상 채팅방 목록을 불러오는 데 실패했습니다.'
        console.error('방 목록 불러오기 API 호출 에러:', error)
        this.roomList = []
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * 새로운 화상 채팅방을 생성합니다.
     * @param {string} classId - 방을 생성할 반의 ID
     * @param {object} roomData - 생성할 방의 데이터 ({ name: string, maxParticipants: number })
     * @returns {Promise<object>} 생성된 방 정보
     */
    async createMeetingRoom(classId, roomData) {
      this.loading = true
      this.error = null
      try {
        const response = await apiClient.post(`/meeting?classId=${classId}`, roomData); 
        await this.fetchRoomList(classId); 
        return response.data;
      } catch (error) {
        this.error = error.response?.data?.message || '화상 채팅방 생성에 실패했습니다.';
        console.error('화상 채팅방 생성 API 호출 에러:', error);
        throw error;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 추가: 내가 속한 클래스 목록을 불러옵니다.
     */
    async fetchMyClasses() {
      this.loading = true;
      this.error = null;
      try {
        // API URL이 /api/v1/class 이므로, apiClient의 baseURL과 합쳐져 정확한 경로가 됩니다.
        const response = await apiClient.get('/class'); 
        this.myClasses = response.data; // 서버 응답이 클래스 목록 배열이라고 가정
        return response.data;
      } catch (error) {
        this.error = error.response?.data?.message || '클래스 목록을 불러오는 데 실패했습니다.';
        console.error('내 클래스 목록 불러오기 API 호출 에러:', error);
        this.myClasses = []; // 에러 시 빈 배열로 초기화
        throw error;
      } finally {
        this.loading = false;
      }
    },

    clearError() {
      this.error = null
    },
  },
})