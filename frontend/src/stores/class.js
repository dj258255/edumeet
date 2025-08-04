// src/stores/class.js
import { defineStore } from 'pinia'
import apiClient from '@/stores/auth'

export const useClassStore = defineStore('class', {
  state: () => ({
    loading: false,
    error: null,

    // DB (클래스 관련)
    classInfo: null,
    myClasses: [],

    // Redis (화상채팅 관련)
    roomList: [],
    activeRooms: [],

    // 개발용 Mock 모드
    useMock: true,
  }),

  getters: {
    isLoading: (state) => state.loading,
    hasError: (state) => state.error !== null,

    // DB
    getCurrentClassInfo: (state) => state.classInfo,
    getMyClasses: (state) => state.myClasses,

    // Redis
    getRoomList: (state) => state.roomList,
    getActiveRooms: (state) => state.activeRooms,
  },

  actions: {
    /** Mock 모드 전환 */
    async setUseMock(value, classId = null) {
      this.useMock = value
      // 데이터 초기화
      this.classInfo = null
      this.myClasses = []
      this.roomList = []
      this.activeRooms = []

      // Mock 해제 → 실제 데이터 자동 로드
      if (!value) {
        await this.fetchMyClasses()
        if (classId) {
          await this.fetchRoomList(classId)
          await this.fetchActiveRooms(classId)
        }
      }
    },

    // ==============================
    //        DB 관련 (Class)
    // ==============================

/** 새로운 클래스 생성 */
async createClass(classData) {
  this.loading = true
  this.error = null

  if (this.useMock) {
    // Mock 모드일 때 파일이 있으면 URL로 변환하여 처리
    const newClass = {
      id: Date.now(),
      title: classData.get('name') || `테스트 반 ${Date.now()}`,
      description: classData.get('description') || '설명 없음',
      image: classData.get('image') ? URL.createObjectURL(classData.get('image')) : null,
      // **FormData.getAll('tags')를 사용하여 태그 배열을 가져오도록 수정**
      tags: classData.getAll('tags').length > 0 ? classData.getAll('tags') : ['Mock', '테스트'],
    }
    this.myClasses.push(newClass)
    this.loading = false
    return newClass
  }

  try {
    // FormData인지 확인하고 헤더를 추가
    const headers = classData instanceof FormData ? { 'Content-Type': 'multipart/form-data' } : {};
    
    // DB API 호출
    const response = await apiClient.post('/class', classData, { headers })
    
    await this.fetchMyClasses()
    return response.data
  } catch (error) {
    this.error = error.response?.data?.message || '클래스 생성에 실패했습니다.'
    throw error
  } finally {
    this.loading = false
  }
},

    /** 클래스 정보 가져오기 */
    async fetchClassInfo(classId) {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.classInfo = {
          id: classId,
          title: `테스트 반 ${classId}`,
          description: 'Mock 모드에서 불러온 테스트 반 정보입니다.',
          image: null,
          tags: ['Mock', '정보'],
        }
        this.loading = false
        return this.classInfo
      }

      try {
        const response = await apiClient.get(`/class/${classId}`) // DB API
        this.classInfo = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '반 정보를 불러오는 데 실패했습니다.'
        this.classInfo = { title: '알 수 없는 반', description: '' }
        throw error
      } finally {
        this.loading = false
      }
    },

    /** 내가 속한 클래스 목록 가져오기 */
    async fetchMyClasses() {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.myClasses = [
          {
            id: 1,
            title: '테스트 반 1',
            description: '임시 데이터입니다.',
            image: null,
            tags: ['Vue3', 'Mock'],
          },
          {
            id: 2,
            title: '테스트 반 2',
            description: '임시 데이터입니다.',
            image: null,
            tags: ['Pinia', 'Mock'],
          },
        ]
        this.loading = false
        return this.myClasses
      }

      try {
        const response = await apiClient.get('/class') // DB API
        this.myClasses = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '클래스 목록을 불러오는 데 실패했습니다.'
        this.myClasses = []
        throw error
      } finally {
        this.loading = false
      }
    },

    // ==============================
    //     Redis 관련 (화상채팅)
    // ==============================

    /** 특정 클래스의 화상채팅방 목록 가져오기 */
    async fetchRoomList(classId) {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.roomList = [
          { id: 1, name: '테스트 방 A', maxParticipants: 5 },
          { id: 2, name: '테스트 방 B', maxParticipants: 10 },
        ]
        this.loading = false
        return this.roomList
      }

      try {
        const response = await apiClient.get(`/meeting?classId=${classId}`) // Redis API
        this.roomList = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '화상 채팅방 목록을 불러오는 데 실패했습니다.'
        this.roomList = []
        throw error
      } finally {
        this.loading = false
      }
    },

    /** 진행중인 화상채팅방 목록 가져오기 */
    async fetchActiveRooms(classId) {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.activeRooms = [
          { name: `${classId}-main`, participants: 3 },
          { name: `${classId}-study`, participants: 5 },
        ]
        this.loading = false
        return this.activeRooms
      }

      try {
        const response = await apiClient.get(`/meeting/active?classId=${classId}`) // Redis API
        this.activeRooms = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '진행 중인 화상 채팅방을 불러오는 데 실패했습니다.'
        this.activeRooms = []
        throw error
      } finally {
        this.loading = false
      }
    },

    /** 화상 채팅방 생성 */
    async createMeetingRoom(classId, roomData) {
      this.loading = true
      this.error = null

      if (this.useMock) {
        const newRoom = {
          id: Date.now(),
          name: roomData.name,
          maxParticipants: roomData.maxParticipants || 5,
        }
        this.roomList.push(newRoom)
        this.loading = false
        return newRoom
      }

      try {
        const response = await apiClient.post(`/meeting?classId=${classId}`, roomData) // Redis API
        await this.fetchRoomList(classId)
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '화상 채팅방 생성에 실패했습니다.'
        throw error
      } finally {
        this.loading = false
      }
    },

    /** 에러 초기화 */
    clearError() {
      this.error = null
    },
  },
})
