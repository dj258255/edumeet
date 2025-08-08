<template>
  <div class="kakao-container">
    <!-- 로딩 중 -->
    <div v-if="isLoading" class="loading">
      <p>사용자 정보를 불러오는 중...</p>
    </div>

    <!-- 로그인 버튼 -->
    <div v-else-if="!user" class="login-section">
      <h3>카카오 로그인</h3>
      <button @click="redirectToKakaoLogin" class="kakao-login-btn">
        <!-- 카카오 공식 로그인 버튼 이미지 -->
        <div class="kakao-btn-custom">
          👥 카카오로 로그인
        </div>
      </button>
    </div>

    <!-- 로그인된 상태 -->
    <div v-else class="user-section">
      <h3>로그인된 사용자</h3>
      <div class="user-info">
        <p><strong>닉네임:</strong> {{ user.nickname }}</p>
        <p><strong>이메일:</strong> {{ user.email }}</p>
      </div>
      <button @click="logout" class="logout-btn">카카오 로그아웃</button>
    </div>
  </div>
</template>


<script>
import axios from "axios";
import { useAuthStore } from '@/stores/auth.js';
import { useRouter } from 'vue-router';

export default {
  name: "KakaoLoginPage",
  setup() {
    const authStore = useAuthStore();
    const router = useRouter();
    return { authStore, router };
  },
  data() {
    return {
      user: null,
      isLoading: true,
    };
  },
  async created() {
    // 카카오 SDK가 남아있다면 정리
    if (window.Kakao) {
      console.log('카카오 SDK 감지됨 - 제거 시도');
      try {
        if (window.Kakao.isInitialized && window.Kakao.isInitialized()) {
          window.Kakao.cleanup();
        }
        delete window.Kakao;
        console.log('카카오 SDK 제거 완료');
      } catch (e) {
        console.warn('카카오 SDK 제거 중 오류:', e);
      }
    }
    try {
      // URL 파라미터 확인
      const urlParams = new URLSearchParams(window.location.search);
      const accessToken = urlParams.get('accessToken'); // CustomOAuth2SuccessHandler에서 전달
      const refreshToken = urlParams.get('refreshToken');
      const error = urlParams.get('error');
      
      if (error) {
        console.error('OAuth2 인증 실패:', error);
        alert('카카오 로그인에 실패했습니다. 다시 시도해주세요.');
        this.isLoading = false;
        return;
      }

      // Spring Security OAuth2로부터 토큰을 받은 경우
      if (accessToken && refreshToken) {
        console.log('✅ Spring Security OAuth2로부터 토큰을 받았습니다!');
        
        try {
          // 토큰 저장
          localStorage.setItem("token", accessToken);
          localStorage.setItem("accessToken", accessToken);
          localStorage.setItem("refreshToken", refreshToken);
          
          // URL 정리
          window.history.replaceState({}, document.title, window.location.pathname);
          
          // 사용자 정보 조회
          const userRes = await axios.get(`${import.meta.env.VITE_API_BASE_URL}/members/me`, {
            headers: {
              Authorization: `Bearer ${accessToken}`,
            },
          });

          const userData = {
            email: userRes.data.email,
            nickname: userRes.data.nickname,
            provider: 'kakao'
          };

          // 사용자 정보 저장
          localStorage.setItem("user", JSON.stringify(userData));
          this.authStore.user = userData;
          this.authStore.isAuthenticated = true;
          this.user = userData;

          console.log('✅ 카카오 로그인 완료!');
          alert(`안녕하세요, ${userData.nickname}님! 카카오 로그인에 성공했습니다.`);
          
          await this.$nextTick();
          this.router.push('/');
          return;
          
        } catch (e) {
          console.error('사용자 정보 조회 실패:', e);
          alert('사용자 정보 조회에 실패했습니다.');
          // 토큰 정리
          localStorage.removeItem("accessToken");
          localStorage.removeItem("refreshToken");
          localStorage.removeItem("token");
          localStorage.removeItem("user");
          this.isLoading = false;
          return;
        }
      }

      // 기존에 저장된 토큰 확인
      const savedAccessToken = localStorage.getItem("accessToken") || localStorage.getItem("token");
      if (savedAccessToken) {
        try {
          const userRes = await axios.get(`${import.meta.env.VITE_API_BASE_URL}/members/me`, {
            headers: {
              Authorization: `Bearer ${savedAccessToken}`,
            },
          });
          
          const userData = {
            email: userRes.data.email,
            nickname: userRes.data.nickname,
            provider: userRes.data.provider || 'kakao'
          };
          
          this.authStore.user = userData;
          this.authStore.isAuthenticated = true;
          this.user = userData;
          
          console.log('이미 로그인된 상태입니다.');
          
        } catch (e) {
          // 토큰이 만료된 경우
          localStorage.removeItem("accessToken");
          localStorage.removeItem("refreshToken");
          localStorage.removeItem("token");
          localStorage.removeItem("user");
          console.log('기존 토큰이 만료됨');
          
          this.authStore.user = null;
          this.authStore.isAuthenticated = false;
        }
      }

    } catch (e) {
      console.error("사용자 정보 조회 실패", e);
      this.user = null;
    } finally {
      this.isLoading = false;
    }
  },
  methods: {
    redirectToKakaoLogin() {
      // Spring Security OAuth2 표준 경로로 리다이렉트
      window.location.href = `${import.meta.env.BASE_URL}/oauth2/authorization/kakao`;
    },
    async logout() {
      try {
        // Auth Store의 logout 메서드 사용 (일반 로그인과 동일)
        await this.authStore.logout();
        console.log('로그아웃 완료');
      } catch (e) {
        console.error("로그아웃 오류", e);
        // 오류가 발생해도 로컬 상태는 정리
        localStorage.clear();
        this.authStore.user = null;
        this.authStore.isAuthenticated = false;
        this.router.push('/login');
      }
    },
  },
};
</script>

<style scoped>
.kakao-container {
  max-width: 400px;
  margin: 0 auto;
  padding: 20px;
  text-align: center;
}

.loading {
  padding: 40px;
  color: #666;
}

.login-section h3,
.user-section h3 {
  margin-bottom: 20px;
  color: #333;
}

.kakao-login-btn {
  cursor: pointer;
  display: inline-block;
  background: none;
  border: none;
  padding: 0;
}

.kakao-btn-custom {
  background: #fee500;
  color: #3c1e1e;
  padding: 15px 40px;
  border-radius: 12px;
  font-weight: bold;
  font-size: 16px;
  border: 2px solid #ffd900;
  transition: all 0.2s ease;
  display: inline-block;
  min-width: 200px;
}

.kakao-btn-custom:hover {
  background: #ffd900;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 217, 0, 0.3);
}

.user-info {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 20px;
  text-align: left;
}

.user-info p {
  margin: 8px 0;
  color: #333;
}

.logout-btn {
  background: #dc3545;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.logout-btn:hover {
  background: #c82333;
}
</style>

