import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import App from './App.vue'
import router from './router'

// 전역 CSS 파일들 import
import './styles/global.css'
import './styles/App.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
