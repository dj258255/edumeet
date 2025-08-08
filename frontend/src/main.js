import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth.js'

// 전역 CSS 파일들 import
import './styles/global.css'
import './styles/App.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// Auth Store 초기화 (OAuth2 토큰도 확인)
const authStore = useAuthStore()
authStore.initialize()

app.mount('#app')

// kakao로그인 javascript key
window.Kakao.init(import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY)
