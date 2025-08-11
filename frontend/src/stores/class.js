// src/stores/class.js
import { defineStore } from 'pinia'
// 공용 axios 클라이언트 사용
import apiClient from '@/utils/apiClient';

export const useClassStore = defineStore('class', {
  state: () => ({
    loading: false,
    error: null,

    // DB (클래스 관련)
    classInfo: null,
    myCreatedClasses: [], // 내가 만든 클래스
    myJoinedClasses: [], // 내가 속한 클래스

    // Redis (화상채팅 관련)
    roomList: [],
    activeRooms: [],

    // 개발용 Mock 모드
    useMock: false,
  }),

  getters: {
    isLoading: (state) => state.loading,
    hasError: (state) => state.error !== null,

    // DB
    getCurrentClassInfo: (state) => state.classInfo,
    getMyCreatedClasses: (state) => state.myCreatedClasses,
    getMyJoinedClasses: (state) => state.myJoinedClasses,
    
    // 모든 클래스 합치기 (필요시 사용)
    getAllMyClasses: (state) => {
      return [...state.myCreatedClasses, ...state.myJoinedClasses]
    },

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
      this.myCreatedClasses = []
      this.myJoinedClasses = []
      this.roomList = []
      this.activeRooms = []

      // Mock 해제 → 실제 데이터 자동 로드
      if (!value) {
        await this.fetchMyCreatedClasses()
        await this.fetchMyJoinedClasses()
        if (classId) {
          await this.fetchRoomList(classId)
          await this.fetchActiveRooms(classId)
        }
      }
    },

    // ==============================
    //        DB 관련 (Class)
    // ==============================
  /** 새로운 클래스 생성 */
  async createClass(classData) {
    this.loading = true
    this.error = null

    if (this.useMock) {
      // Mock 모드 로직도 JSON 객체에 맞춰 수정
      const newClass = {
        id: Date.now(),
        title: classData.title || `테스트 반 ${Date.now()}`,
        description: classData.description || '설명 없음',
        thumbnailUrl: classData.thumbnailUrl || null,
        limit: classData.limit,
        tags: classData.tags || ['Mock', '테스트'],
      }
      this.myCreatedClasses.push(newClass)
      this.loading = false
      return newClass
    }

    try {
      // Axios는 객체를 JSON으로 자동 직렬화하고
      // Content-Type을 'application/json'으로 설정합니다.
      console.log('classData:', classData)
      
      // 백엔드 API 호출 - /classroom 엔드포인트는 그대로 유지
      const response = await apiClient.post('/classroom', classData)
      console.log('aaa',response)
      // 클래스 생성 후 목록을 새로고침합니다.
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
        // /api/v1이 중복되므로 엔드포인트를 `/class/${classId}`로 수정
        const response = await apiClient.get(`/class/${classId}`)
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

    /** 내가 만든 클래스 목록 가져오기 */
    async fetchMyCreatedClasses() {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.myCreatedClasses = [
          {
            id: 1,
            title: '테스트 반 1',
            description: '임시 데이터입니다.',
            image: null,
            tags: ['Vue3', 'Mock'],
          },
          {
            id: 3,
            title: '내가 만든 수학반',
            description: '기초 수학을 배우는 반입니다.',
            image: null,
            tags: ['수학', '기초'],
          }
        ]
        this.loading = false
        return this.myCreatedClasses
      }

      try {
        // /api/v1이 중복되므로 엔드포인트를 `/class`로 수정
        const response = await apiClient.get('/classroom')
        console.log('방목록:',response)
        this.myCreatedClasses = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '내가 만든 클래스 목록을 불러오는 데 실패했습니다.'
        this.myCreatedClasses = []
        throw error
      } finally {
        this.loading = false
      }
    },

    /** 내가 속한 클래스 목록 가져오기 */
    async fetchMyJoinedClasses() {
      this.loading = true
      this.error = null

      if (this.useMock) {
        this.myJoinedClasses = [
          {
            id: 2,
            title: '테스트 반 2',
            description: '임시 데이터입니다.',
            image: null,
            tags: ['Pinia', 'Mock'],
          },
          {
            id: 4,
            title: '친구가 만든 영어반',
            description: '영어 회화를 연습하는 반입니다.',
            image: null,
            tags: ['영어', '회화'],
          }
        ]
        this.loading = false
        return this.myJoinedClasses
      }

      try {
        // /api/v1이 중복되므로 엔드포인트를 `/class/joined`로 수정
        const response = await apiClient.get('/classroom/joined')
        this.myJoinedClasses = response.data
        return response.data
      } catch (error) {
        this.error = error.response?.data?.message || '내가 속한 클래스 목록을 불러오는 데 실패했습니다.'
        this.myJoinedClasses = []
        throw error
      } finally {
        this.loading = false
      }
    },

    // ==============================
    //     초대 관련 API
    // ==============================

    // 회원 검색 API
    async searchMembers(keyword, page = 0, size = 10) {
      console.log('🔍 class.js - searchMembers 호출됨:', { keyword, page, size })
      try {
        const url = `/members/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
        console.log('🔍 class.js - API URL:', url)
        const response = await apiClient.get(url)
        console.log('🔍 class.js - API 응답:', response.data)
        return response.data
      } catch (error) {
        console.error('🔍 class.js - 회원 검색 실패:', error)
        throw error
      }
    },

    // 학생 초대 API
    async inviteStudents(classId, emails) {
      try {
        const response = await apiClient.post(`/classroom/${classId}/invite`, {
          emails: emails
        })
        return response.data
      } catch (error) {
        console.error('학생 초대 실패:', error)
        throw error
      }
    },

    // 초대 목록 조회 API
    async fetchInviteList() {
      console.log('🔍 class.js - fetchInviteList 호출됨');
      console.log('🔍 localStorage token:', localStorage.getItem('token') ? '있음' : '없음');
      
      try {
        console.log('🔍 초대 목록 API 요청 시작: GET /classroom/invite');
        const response = await apiClient.get('/classroom/invite');
        console.log('🔍 초대 목록 API 응답:', response.data);
        console.log('🔍 응답 데이터 타입:', typeof response.data);
        console.log('🔍 응답 데이터 길이:', Array.isArray(response.data) ? response.data.length : '배열이 아님');
        return response.data;
      } catch (error) {
        console.error('🔍 초대 목록 조회 실패 - 상세 정보:');
        console.error('🔍 에러 메시지:', error.message);
        console.error('🔍 응답 상태:', error.response?.status);
        console.error('🔍 응답 데이터:', error.response?.data);
        console.error('🔍 요청 URL:', error.config?.url);
        console.error('🔍 요청 헤더:', error.config?.headers);
        throw error;
      }
    },

    // 클래스 삭제 API
    async deleteClass(classId) {
      try {
        const response = await apiClient.delete(`/classroom/${classId}`)
        return response.data
      } catch (error) {
        console.error('클래스 삭제 실패:', error)
        throw error
      }
    },

    // 초대 응답 API (수락/거절)
    async respondToInvite(requestData) {
      console.log('🔥🔥🔥 NEW VERSION - 초대 응답 API 호출됨 🔥🔥🔥');
      console.log('🔍 받은 requestData:', requestData);
      console.log('🔍 requestData 타입:', typeof requestData);
      console.log('🔍 localStorage token:', localStorage.getItem('token') ? '있음' : '없음');
      
      const classId = requestData.classId;
      const status = requestData.status;
      
      console.log('🔍 classId:', classId, 'type:', typeof classId);
      console.log('🔍 status:', status);
      
      try {
        console.log('🔍 요청 URL:', `/classroom/status`);
        
        const response = await apiClient.patch(`/classroom/status`, requestData)
        console.log('🔍 초대 응답 성공:', response.data)
        return response.data
      } catch (error) {
        console.error('🔍 초대 응답 실패 - 상세 정보:');
        console.error('🔍 에러 메시지:', error.message);
        console.error('🔍 응답 상태:', error.response?.status);
        console.error('🔍 응답 데이터:', error.response?.data);
        console.error('🔍 요청 헤더:', error.config?.headers);
        throw error
      }
    },

    // ==============================
    //     Redis 관련 (화상채팅)
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
        // URL 매개변수를 params 옵션으로 전달하는 방식으로 수정
        const response = await apiClient.get('/meeting', { params: { classId } })
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
        // URL 매개변수를 params 옵션으로 전달하는 방식으로 수정
        const response = await apiClient.get('/meeting/active', { params: { classId } })
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
        // URL 매개변수를 params 옵션으로 전달하는 방식으로 수정
        const response = await apiClient.post('/meeting', roomData, { params: { classId } })
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