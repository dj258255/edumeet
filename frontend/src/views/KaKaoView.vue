<template>
    <div class="kakao-container">
      <!-- 로딩 상태 -->
      <div v-if="isLoading" class="loading">
        <p>카카오 로그인 처리 중...</p>
      </div>
      
      <!-- 로그인 버튼 -->
      <div v-else-if="!user.name" class="login-section">
        <h3>카카오 로그인</h3>
        <a @click="kakaoLogin()" class="kakao-login-btn">
          <img
            src="//k.kakaocdn.net/14/dn/btqCn0WEmI3/nijroPfbpCa4at5EIsjyf0/o.jpg"
            width="222"
            alt="카카오 로그인"
          />
        </a>
      </div>
      
      <!-- 로그인된 상태 -->
      <div v-else class="user-section">
        <h3>로그인된 사용자</h3>
        <div class="user-info">
          <p><strong>닉네임:</strong> {{ user.name }}</p>
          <p v-if="user.email"><strong>이메일:</strong> {{ user.email }}</p>
          <p v-else><strong>이메일:</strong> <em>이메일 정보 없음</em></p>
        </div>
        <button type="button" @click="kakaoLogout" class="logout-btn">
          카카오 로그아웃
        </button>
      </div>
    </div>
  </template>
  <script>
  import axios from "axios";
  
  const getKakaoToken = async (code) => {
    try {
      const data = {
        grant_type: "authorization_code",
        client_id: "3179fe89597741094d1d98dfe9820fe9", // REST API 키
        redirect_uri: "http://localhost:5173/kakao",
        code: code,
      };
  
      const queryString = Object.keys(data)
        .map((k) => encodeURIComponent(k) + "=" + encodeURIComponent(data[k]))
        .join("&");
      //console.log(queryString);
  
      const result = await axios.post(
        "https://kauth.kakao.com/oauth/token",
        queryString,
        {
          headers: {
            "Content-type": "application/x-www-form-urlencoded;charset=utf-8",
          },
        }
      );
      console.log(result);
      return result;
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
  
  export default {
    data() {
      return {
        user: {}, // TODO store로 이관 필요
        isLoading: false,
      };
    },
    created() {
      // 저장된 사용자 정보가 있는지 확인
      const savedUser = localStorage.getItem('kakaoUser');
      if (savedUser) {
        this.user = JSON.parse(savedUser);
      }
      
      // URL에 code 파라미터가 있는 경우에만 토큰 발급 요청 (카카오 리다이렉트)
      const urlParams = new URLSearchParams(window.location.search);
      if (urlParams.has("code")) {
        const code = urlParams.get("code");
        console.log("카카오 인증 코드 발견:", code);
        this.isLoading = true;
        this.setKakaoToken(code);
        
        // URL에서 code 파라미터 제거 (한 번만 처리하도록)
        const newUrl = window.location.pathname;
        window.history.replaceState({}, document.title, newUrl);
      }
    },
    methods: {
      // 1. 인가 코드 얻기
      // https://developers.kakao.com/docs/latest/ko/kakaologin/js#login
      kakaoLogin() {
        console.log('카카오 로그인 버튼 클릭됨');
        
        if (!window.Kakao || !window.Kakao.Auth) {
          console.error('카카오 SDK가 로드되지 않았습니다.');
          alert('카카오 SDK 로딩 중입니다. 잠시 후 다시 시도해주세요.');
          return;
        }
        
        console.log('카카오 로그인 시작...');
        console.log('현재 window.Kakao:', window.Kakao);
        console.log('현재 window.Kakao.Auth:', window.Kakao.Auth);
        
        try {
          window.Kakao.Auth.authorize({
            redirectUri: "http://localhost:5173/kakao",
            // 강제로 로그인 페이지 표시
            prompt: 'login'
          });
        } catch (error) {
          console.error('카카오 로그인 에러:', error);
          alert('카카오 로그인 중 오류가 발생했습니다.');
        }
      },
  
      // 2. 토큰 조회
      // https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#request-token
      async setKakaoToken(code) {
        const result = await getKakaoToken(code);
        
        // 에러 체크
        if (result.error) {
          console.error('토큰 요청 실패:', result.error);
          const status = result.error.response?.status;
          
          if (status === 429) {
            alert('요청이 너무 많습니다. 5-10분 후 다시 시도해주세요.\n\n해결 방법:\n1. 잠시 기다린 후 다시 시도\n2. 다른 브라우저 사용\n3. 시크릿 모드에서 시도');
            this.$router.push({ path: "/" });
          } else if (status === 400) {
            alert('인증 코드가 만료되었습니다. 다시 로그인해주세요.');
            this.$router.push({ path: "/" });
          } else {
            alert('카카오 로그인 중 오류가 발생했습니다.');
          }
          return;
        }
        
        const { data } = result;
        if (data.error) {
          console.log('카카오 API 에러:', data.error);
          return;
        }
        
        console.log(data);
        
        // SDK가 완전히 로드될 때까지 대기
        await this.waitForKakaoSDK();
        
        window.Kakao.Auth.setAccessToken(data.access_token);
        
        // 사용자 정보 가져오기 시도 (429 에러가 있어도 토큰은 저장됨)
        try {
          await this.setUserInfo();
        } catch (error) {
          console.log('사용자 정보 조회 실패, 하지만 토큰은 저장됨:', error);
        }
        
        this.isLoading = false;
        this.$router.push({ path: "/kakao" });
      },
      
      // SDK 로드 대기 함수
      waitForKakaoSDK() {
        return new Promise((resolve) => {
          let attempts = 0;
          const maxAttempts = 100; // 최대 10초 대기
          
          const checkSDK = () => {
            attempts++;
            console.log(`카카오 SDK 체크 시도 ${attempts}/${maxAttempts}`);
            
            if (window.Kakao && window.Kakao.Auth) {
              console.log('카카오 SDK 및 Auth 모듈 준비 완료');
              resolve();
            } else if (attempts >= maxAttempts) {
              console.error('카카오 SDK 로드 타임아웃');
              resolve(); // 타임아웃 시에도 resolve
            } else {
              console.log('카카오 SDK 대기 중...');
              setTimeout(checkSDK, 100);
            }
          };
          checkSDK();
        });
      },
  
      // 3. 사용자 정보 조회
      // https://developers.kakao.com/docs/latest/ko/kakaologin/js#req-user-info
      async setUserInfo() {
        try {
          const res = await getKakaoUserInfo();
          
          // 응답 확인
          if (!res || !res.kakao_account) {
            console.error('사용자 정보를 가져올 수 없습니다:', res);
            return;
          }
          
          const userInfo = {
            name: res.kakao_account.profile?.nickname || '사용자',
            email: res.kakao_account.email || '',
          };
          console.log('사용자 정보:', userInfo);
          
          // 사용자 정보를 localStorage에 저장
          localStorage.setItem('kakaoUser', JSON.stringify(userInfo));
          this.user = userInfo;
          
        } catch (error) {
          console.error('사용자 정보 조회 실패:', error);
          // 429 에러가 아닌 경우에만 에러 메시지 표시
          if (error.response?.status !== 429) {
            alert('사용자 정보를 가져오는데 실패했습니다.');
          }
        }
      },
  
      // 로그아웃
      kakaoLogout() {
        if (confirm('정말 로그아웃하시겠습니까?')) {
          console.log('카카오 로그아웃 시작...');
          
          // localStorage에서 사용자 정보 제거
          localStorage.removeItem('kakaoUser');
          this.user = {};
          
          if (window.Kakao && window.Kakao.Auth) {
            // 카카오 토큰 제거
            window.Kakao.Auth.logout()
              .then(function (response) {
                console.log('카카오 로그아웃 성공:', response);
              })
              .catch(function (error) {
                console.log('카카오 로그아웃 에러:', error);
              });
            
            // 카카오 계정 연결 해제 (더 강력한 로그아웃)
            window.Kakao.API.request({
              url: '/v1/user/unlink',
            })
            .then(function(response) {
              console.log('카카오 계정 연결 해제 성공:', response);
            })
            .catch(function(error) {
              console.log('카카오 계정 연결 해제 에러:', error);
            });
          }
          
          // 홈페이지로 리다이렉트
          this.$router.push({ path: "/" });
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