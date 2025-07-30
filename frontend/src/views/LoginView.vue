<template>
  <div class="login-view">
    <!-- 왼쪽 브랜딩 섹션 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-header">
          <div class="brand-logo">
            <img alt="EduMeet Logo" class="logo" src="@/assets/edumeet_logo.png" />
            <h2 class="brand-name">EduMeet</h2>
          </div>
          <p class="brand-slogan">Education At Home</p>
        </div>
        
        <div class="brand-main">
          <h1 class="brand-title">Knowledge From Home</h1>
          <p class="brand-description">
            It is a long established fact that a reader will be distracted by the readable content of a page when looking at its layout.
          </p>
        </div>
        
        <div class="brand-footer">
          <span class="region">KOREA</span>
          <span class="region">ASIA</span>
          <span class="region">GLOBAL</span>
        </div>
      </div>
    </div>

    <!-- 오른쪽 로그인 폼 섹션 -->
    <div class="form-section">
      <div class="form-container">
        <h2 class="form-title">Login</h2>

        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label for="email">EMAIL</label>
            <div class="input-wrapper">
              <span class="input-icon">✉️</span>
              <input
                id="email"
                v-model="email"
                type="email"
                :class="{ error: errors.email }"
                placeholder="Type your Email"
              />
            </div>
            <div v-if="errors.email" class="error-message">{{ errors.email }}</div>
          </div>
          
          <div class="form-group">
            <label for="password">PASSWORD</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                id="password"
                v-model="password"
                type="password"
                :class="{ error: errors.password }"
                placeholder="Type your password"
              />
            </div>
            <div v-if="errors.password" class="error-message">{{ errors.password }}</div>
          </div>
          
          <button type="submit" class="login-btn" :disabled="!email || !password || !selectedRole">
            Login
          </button>
        </form>
        
        <div v-if="message" :class="['message', message.includes('성공') ? 'success' : 'error']">
          {{ message }}
        </div>
        
        <div class="forgot-password">
          <a href="#" class="forgot-link">Forgot your password?</a>
        </div>
        
        <div class="signup-link">
          Don't have an account? <RouterLink to="/signup">Sign Up</RouterLink>
        </div>
        <div class="role-selection">
          <p class="role-label">또는 다음으로 로그인</p>
          <div class="role-buttons">
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'student' }"
              @click="selectedRole = 'student'"
            >
              <span class="role-icon">🎓</span>
              <span class="role-text">STUDENT</span>
            </button>
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'tutor' }"
              @click="selectedRole = 'tutor'"
            >
              <span class="role-icon">📊</span>
              <span class="role-text">TUTOR</span>
            </button>
            <button 
              class="role-btn" 
              :class="{ active: selectedRole === 'parent' }"
              @click="selectedRole = 'parent'"
            >
              <span class="role-icon">👨‍👩‍👧‍👦</span>
              <span class="role-text">PARENT</span>
            </button>
          </div>
        </div>
        
        <!-- 카카오 로그인 섹션 -->
        <div class="social-login-section">
          <p class="social-login-label">소셜 로그인</p>
          <div class="social-login-buttons">
            <button 
              class="kakao-login-btn"
              @click="handleKakaoLogin"
              :disabled="isKakaoLoading"
            >
              <img
                src="//k.kakaocdn.net/14/dn/btqCn0WEmI3/nijroPfbpCa4at5EIsjyf0/o.jpg"
                alt="카카오 로그인"
                class="kakao-icon"
              />
              <span class="kakao-text">
                {{ isKakaoLoading ? '로그인 중...' : '카카오로 로그인' }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import '../styles/LoginView.css'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const selectedRole = ref('tutor')
const errors = ref({})
const message = ref('')
const isKakaoLoading = ref(false)
const kakaoUser = ref({})

const validateForm = () => {
  errors.value = {}
  if (!email.value) errors.value.email = '이메일을 입력해주세요.'
  else if (!/\S+@\S+\.\S+/.test(email.value)) errors.value.email = '올바른 이메일 형식을 입력해주세요.'
  if (!password.value) errors.value.password = '비밀번호를 입력해주세요.'
  else if (password.value.length < 6) errors.value.password = '비밀번호는 최소 6자 이상이어야 합니다.'
  return Object.keys(errors.value).length === 0
}
const handleLogin = async () => {
  if (!validateForm()) return
  try {
    console.log('로그인 시도:', email.value)
    
    // 실제 백엔드 API를 통한 로그인
    const result = await authStore.login(email.value, password.value)
    console.log('로그인 결과:', result)
    
    message.value = '로그인 성공!'
    console.log('로그인 후 상태:', authStore.isLoggedIn)
    console.log('로그인 후 사용자:', authStore.currentUser)
    
    // 홈페이지로 이동
    router.push('/')
  } catch (error) {
    message.value = error.message || '로그인에 실패했습니다.'
    console.error('로그인 에러:', error)
  }
}

// 카카오 로그인 관련 함수들
const getKakaoToken = async (code) => {
  try {
    const data = {
      grant_type: "authorization_code",
      client_id: import.meta.env.VITE_KAKAO_REST_API_KEY,
      redirect_uri: "http://localhost:5173/login",
      code: code,
    };

    const queryString = Object.keys(data)
      .map((k) => encodeURIComponent(k) + "=" + encodeURIComponent(data[k]))
      .join("&");

    const result = await fetch("https://kauth.kakao.com/oauth/token", {
      method: "POST",
      headers: {
        "Content-type": "application/x-www-form-urlencoded;charset=utf-8",
      },
      body: queryString,
    });
    
    return await result.json();
  } catch (e) {
    console.log('토큰 요청 에러:', e);
    return { error: e };
  }
};

const getKakaoUserInfo = async () => {
  let data = "";
  await window.Kakao.API.request({
    url: "/v2/user/me",
  })
    .then(function (response) {
      console.log(response);
      data = response;
    })
    .catch(function (error) {
      console.log(error);
    });
  console.log("카카오 계정 정보", data);
  return data;
};

const waitForKakaoSDK = () => {
  return new Promise((resolve) => {
    let attempts = 0;
    const maxAttempts = 50; // 최대 시도 횟수 줄임
    
    const checkSDK = () => {
      attempts++;
      console.log(`카카오 SDK 체크 시도 ${attempts}/${maxAttempts}`);
      
      if (window.Kakao && window.Kakao.Auth) {
        console.log('카카오 SDK 및 Auth 모듈 준비 완료');
        resolve();
      } else if (attempts >= maxAttempts) {
        console.error('카카오 SDK 로드 타임아웃');
        resolve();
      } else {
        console.log('카카오 SDK 대기 중...');
        setTimeout(checkSDK, 50); // 대기 시간을 50ms로 줄임
      }
    };
    checkSDK();
  });
};

const setKakaoToken = async (code) => {
  const result = await getKakaoToken(code);
  
  if (result.error) {
    console.error('토큰 요청 실패:', result.error);
    const status = result.error.response?.status;
    
    if (status === 429) {
      alert('요청이 너무 많습니다. 5-10분 후 다시 시도해주세요.');
      return;
    } else if (status === 400) {
      alert('인증 코드가 만료되었습니다. 다시 로그인해주세요.');
      return;
    } else {
      alert('카카오 로그인 중 오류가 발생했습니다.');
      return;
    }
  }
  
  if (result.error) {
    console.log('카카오 API 에러:', result.error);
    return;
  }
  
  console.log(result);
  
  await waitForKakaoSDK();
  
  window.Kakao.Auth.setAccessToken(result.access_token);
  
  try {
    await setUserInfo();
  } catch (error) {
    console.log('사용자 정보 조회 실패, 하지만 토큰은 저장됨:', error);
  }
  
  isKakaoLoading.value = false;
  message.value = '카카오 로그인 성공!';
  router.push('/');
};

const setUserInfo = async () => {
  try {
    const res = await getKakaoUserInfo();
    
    if (!res || !res.kakao_account) {
      console.error('사용자 정보를 가져올 수 없습니다:', res);
      return;
    }
    
    const userInfo = {
      name: res.kakao_account.profile?.nickname || '사용자',
      email: res.kakao_account.email || '',
      role: selectedRole.value,
    };
    console.log('카카오 사용자 정보:', userInfo);
    
    // 이메일이 있으면 DB에 회원가입 시도
    if (userInfo.email) {
      try {
        // 기존 회원가입과 동일한 형식으로 DB에 전송
        const signupData = {
          name: userInfo.name,
          email: userInfo.email,
          password: 'kakao_' + Date.now(), // 임시 비밀번호 (카카오 로그인용)
          role: userInfo.role
        };
        
        console.log('DB에 전송할 데이터:', signupData);
        
        // auth store를 사용해서 회원가입 API 호출
        const authStore = useAuthStore();
        await authStore.signup(signupData);
        
        console.log('카카오 회원가입 성공!');
        message.value = '카카오 로그인 성공!';
        router.push('/');
        
      } catch (error) {
        console.error('카카오 회원가입 실패:', error);
        // 이미 가입된 사용자일 수 있으므로 로그인 시도
        try {
          await authStore.login(userInfo.email, 'kakao_' + Date.now());
          message.value = '카카오 로그인 성공!';
          router.push('/');
        } catch (loginError) {
          console.error('카카오 로그인 실패:', loginError);
          message.value = '로그인에 실패했습니다.';
        }
      }
    } else {
      // 이메일이 없으면 추가 정보 입력 필요
      console.log('이메일이 없어서 추가 정보 입력이 필요합니다.');
      message.value = '이메일 정보가 필요합니다.';
    }
    
    // localStorage에 저장
    localStorage.setItem('kakaoUser', JSON.stringify(userInfo));
    kakaoUser.value = userInfo;
    
  } catch (error) {
    console.error('사용자 정보 조회 실패:', error);
    if (error.response?.status !== 429) {
      alert('사용자 정보를 가져오는데 실패했습니다.');
    }
  }
};

const handleKakaoLogin = () => {
  console.log('카카오 로그인 버튼 클릭됨');
  
  if (!window.Kakao || !window.Kakao.Auth) {
    console.error('카카오 SDK가 로드되지 않았습니다.');
    alert('카카오 SDK 로딩 중입니다. 잠시 후 다시 시도해주세요.');
    return;
  }
  
  console.log('카카오 로그인 시작...');
  isKakaoLoading.value = true;
  
  try {
    window.Kakao.Auth.authorize({
      redirectUri: "http://localhost:5173/kakao",
      prompt: 'login'
    });
  } catch (error) {
    console.error('카카오 로그인 에러:', error);
    alert('카카오 로그인 중 오류가 발생했습니다.');
    isKakaoLoading.value = false;
  }
};

// 컴포넌트 마운트 시 카카오 인증 코드 처리
onMounted(() => {
  const savedUser = localStorage.getItem('kakaoUser');
  if (savedUser) {
    kakaoUser.value = JSON.parse(savedUser);
  }
  
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has("code")) {
    const code = urlParams.get("code");
    console.log("카카오 인증 코드 발견:", code);
    isKakaoLoading.value = true;
    setKakaoToken(code);
    
    const newUrl = window.location.pathname;
    window.history.replaceState({}, document.title, newUrl);
  }
});
</script> 