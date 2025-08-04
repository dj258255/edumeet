import { createRouter, createWebHistory } from "vue-router"
import HomeView from "../views/HomeView.vue"
import LoginView from "../views/LoginView.vue"
import SignupView from "../views/SignupView.vue"
import ArticlesView from "../views/ArticlesView.vue"
import SearchView from "../views/SearchView.vue"
import AboutView from "../views/AboutView.vue"
import CreateClassView from "../views/CreateClassView.vue"
import KaKaoView from "../views/KaKaoView.vue"
import ClassVideoRoomView from "@/views/ClassVideoRoomView.vue"
import MyPageView from "@/views/MyPageView.vue"

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/login",
      name: "login",
      component: LoginView,
    },
    {
      path: "/signup",
      name: "signup",
      component: SignupView,
    },
    {
      path: "/articles",
      name: "articles",
      component: ArticlesView,
      meta: { requiresAuth: true }   // 로그인 필요
    },
    {
      path: "/search",
      name: "search",
      component: SearchView,
      meta: { requiresAuth: true }
    },
    {
      path: "/about",
      name: "about",
      component: AboutView,
      // meta: { requiresAuth: true }
    },
    {
      path: '/class/create',
      name: "create-class",
      component: CreateClassView,
      // meta: { requiresAuth: true }
    },
    {
      path: '/kakao',
      name: 'kakao',
      component: KaKaoView,
      meta: { requiresAuth: true }
    },
    // router 설정
    { path: '/class/:classId/video',
      name: 'class-video-room',
      component: ClassVideoRoomView 
    },
    {
      path: '/mypage',
      name: 'mypage',
      component: MyPageView,
      meta: { requiresAuth: true }
    },
  ],
})

/**
 * 전역 라우터 가드 설정
 * 로그인 여부에 따라 접근 제어
 */
router.beforeEach((to, from, next) => {
  // 로그인 여부 (예시: localStorage에 토큰 있는지 확인)
  const isLoggedIn = !!localStorage.getItem('token')

  if (to.meta.requiresAuth && !isLoggedIn) {
    alert('로그인이 필요한 기능입니다.')
    next('/login')
  } else {
    next()
  }
})

export default router
