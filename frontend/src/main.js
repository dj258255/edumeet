import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import App from './App.vue'
import router from './router'

// 전역 CSS 파일들 import
import './styles/global.css'
import './styles/App.css'

// Auth Store import
import { useAuthStore } from '@/stores/auth'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)

app.mount('#app')

// kakao로그인 javascript key
window.Kakao.init(import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY)

// ✅ 앱 시작 시 로그인 상태 복원
const authStore = useAuthStore()
authStore.initialize()
