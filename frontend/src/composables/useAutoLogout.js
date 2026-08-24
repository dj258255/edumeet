import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth.js'
import apiClient from '@/utils/apiClient.js'

/**
 * 자동 로그아웃 기능을 제공하는 Composable
 * 
 * 감지 조건:
 * 1. 화면 비활성화 (30분)
 * 2. 서버 연결 끊김 (5분)
 * 3. 사용자 비활성화 (1시간)
 */
export function useAutoLogout() {
  const authStore = useAuthStore()
  
  // 타이머 관련 변수들
  const inactivityTimer = ref(null)
  const visibilityTimer = ref(null)
  const heartbeatTimer = ref(null)
  const lastActivityTime = ref(Date.now())
  const lastHeartbeatTime = ref(Date.now())
  
  // 설정값 (밀리초)
// 설정값 (밀리초)
  const TIMEOUTS = {
    USER_INACTIVITY: 60 * 60 * 1000,    // 1시간
    PAGE_VISIBILITY: 2 * 60 * 60 * 1000,    // 2시간으로 변경
    SERVER_HEARTBEAT: 5 * 60 * 1000,    // 5분
    HEARTBEAT_INTERVAL: 2 * 60 * 1000   // 2분마다 heartbeat 체크
  }
  
  // 로그인 상태 확인
  const isLoggedIn = () => {
    return authStore.isLoggedIn && authStore.currentUser
  }
  
  /**
   * 자동 로그아웃 실행
   * @param {string} reason - 로그아웃 이유
   */
  const performAutoLogout = async (reason) => {
    if (!isLoggedIn()) return
    
    console.warn(`자동 로그아웃 실행: ${reason}`)
    
    try {
      // 사용자에게 알림
      const confirmLogout = confirm(`${reason}\n\n자동으로 로그아웃됩니다. 계속하시겠습니까?`)
      
      if (confirmLogout) {
        await authStore.forceLogout(reason)
        alert('자동 로그아웃되었습니다. 다시 로그인해주세요.')
      } else {
        // 사용자가 거부하면 타이머 리셋
        resetAllTimers()
      }
    } catch (error) {
      console.error('자동 로그아웃 중 오류:', error)
      // 에러가 발생해도 강제 로그아웃
      await authStore.forceLogout(`${reason} (오류 발생)`)
    }
  }
  
  /**
   * 사용자 활동 감지 및 타이머 리셋
   */
  const resetUserActivityTimer = () => {
    if (!isLoggedIn()) return
    
    lastActivityTime.value = Date.now()
    
    // 기존 타이머 클리어
    if (inactivityTimer.value) {
      clearTimeout(inactivityTimer.value)
    }
    
    // 새 타이머 설정
    inactivityTimer.value = setTimeout(() => {
      performAutoLogout('오랜 시간 동안 활동이 없었습니다.')
    }, TIMEOUTS.USER_INACTIVITY)
  }
  
  /**
   * 페이지 가시성 변경 처리
   */
  const handleVisibilityChange = () => {
    if (!isLoggedIn()) return
    
    if (document.hidden) {
      // 페이지가 숨겨졌을 때
      console.log('페이지 비활성화 감지')
      
      visibilityTimer.value = setTimeout(() => {
        performAutoLogout('화면이 오랫동안 비활성화되어 있었습니다.')
      }, TIMEOUTS.PAGE_VISIBILITY)
    } else {
      // 페이지가 다시 보일 때
      console.log('페이지 활성화 감지')
      
      if (visibilityTimer.value) {
        clearTimeout(visibilityTimer.value)
        visibilityTimer.value = null
      }
      
      // 사용자 활동 타이머도 리셋
      resetUserActivityTimer()
    }
  }
  
  /**
   * 서버 연결 상태 확인 (Heartbeat)
   */
/**
 * 서버 연결 상태 확인 (Heartbeat)
 */
  const checkServerConnection = async () => {
    if (!isLoggedIn()) return;

    try {
      // API 호출을 프로필 조회 대신 더 가벼운 상태 체크 API로 변경
      // 예를 들어, 서버의 상태를 확인하는 헬스 체크 엔드포인트가 있다면 그것을 사용
      // 예시: await apiClient.get('/api/v1/health-check');
      // 현재 프로젝트에 적합한 다른 엔드포인트를 사용해야 합니다.

      // 만약 그런 엔드포인트가 없다면, API 호출 자체를 제거하고 네트워크 상태만 확인
      // 네트워크가 오프라인 상태인지 확인
      if (!navigator.onLine) {
          throw new Error('네트워크 연결 끊김');
      }

      lastHeartbeatTime.value = Date.now();
      console.log('네트워크 연결 정상');
    } catch (error) {
      console.error('서버 연결 확인 실패:', error);
      
      // 마지막 성공적인 heartbeat로부터 5분이 지났는지 확인
      const timeSinceLastHeartbeat = Date.now() - lastHeartbeatTime.value;
      
      if (timeSinceLastHeartbeat > TIMEOUTS.SERVER_HEARTBEAT) {
        performAutoLogout('서버와의 연결이 끊어졌습니다.');
      }
    }
  };
  
  /**
   * Heartbeat 타이머 설정
   */
  const startHeartbeat = () => {
    if (!isLoggedIn()) return
    
    // 즉시 한 번 체크
    checkServerConnection()
    
    // 주기적으로 체크
    heartbeatTimer.value = setInterval(() => {
      checkServerConnection()
    }, TIMEOUTS.HEARTBEAT_INTERVAL)
  }
  
  /**
   * 모든 타이머 리셋
   */
  const resetAllTimers = () => {
    lastActivityTime.value = Date.now()
    lastHeartbeatTime.value = Date.now()
    resetUserActivityTimer()
  }
  
  /**
   * 자동 로그아웃 기능 시작
   */
  const startAutoLogout = () => {
    if (!isLoggedIn()) return
    
    console.log('자동 로그아웃 기능 시작')
    
    // 1. 사용자 활동 감지 이벤트 등록
    const activityEvents = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click']
    
    activityEvents.forEach(event => {
      document.addEventListener(event, resetUserActivityTimer, { passive: true })
    })
    
    // 2. 페이지 가시성 변경 감지
    document.addEventListener('visibilitychange', handleVisibilityChange)
    
    // 3. 서버 연결 상태 체크 시작
    startHeartbeat()
    
    // 4. 초기 타이머 설정
    resetAllTimers()
  }
  
  /**
   * 자동 로그아웃 기능 중지
   */
  const stopAutoLogout = () => {
    console.log('자동 로그아웃 기능 중지')
    
    // 모든 타이머 클리어
    if (inactivityTimer.value) {
      clearTimeout(inactivityTimer.value)
      inactivityTimer.value = null
    }
    
    if (visibilityTimer.value) {
      clearTimeout(visibilityTimer.value)
      visibilityTimer.value = null
    }
    
    if (heartbeatTimer.value) {
      clearInterval(heartbeatTimer.value)
      heartbeatTimer.value = null
    }
    
    // 이벤트 리스너 제거
    const activityEvents = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click']
    
    activityEvents.forEach(event => {
      document.removeEventListener(event, resetUserActivityTimer)
    })
    
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
  
  /**
   * 자동 로그아웃 설정 업데이트
   * @param {Object} newTimeouts - 새로운 타임아웃 설정
   */
  const updateTimeouts = (newTimeouts) => {
    Object.assign(TIMEOUTS, newTimeouts)
    console.log('자동 로그아웃 설정 업데이트:', TIMEOUTS)
    
    // 기존 타이머들 재시작
    if (isLoggedIn()) {
      stopAutoLogout()
      startAutoLogout()
    }
  }
  
  /**
   * 현재 상태 정보 반환
   */
  const getStatus = () => {
    return {
      isActive: isLoggedIn() && !!inactivityTimer.value,
      lastActivityTime: lastActivityTime.value,
      lastHeartbeatTime: lastHeartbeatTime.value,
      timeouts: { ...TIMEOUTS }
    }
  }
  
  // 컴포넌트 마운트/언마운트 시 자동 처리
  onMounted(() => {
    if (isLoggedIn()) {
      startAutoLogout()
    }
  })
  
  onUnmounted(() => {
    stopAutoLogout()
  })
  
  return {
    // 메서드
    startAutoLogout,
    stopAutoLogout,
    resetAllTimers,
    updateTimeouts,
    getStatus,
    performAutoLogout,
    
    // 상태 정보 (readonly)
    isActive: () => isLoggedIn() && !!inactivityTimer.value,
    timeouts: TIMEOUTS
  }
}
