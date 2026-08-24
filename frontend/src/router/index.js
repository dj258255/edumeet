import { createRouter, createWebHistory } from "vue-router"
import HomeView from "../views/HomeView.vue"
import LoginView from "../views/LoginView.vue"
import SignupView from "../views/SignupView.vue"
import ArticlesView from "../views/ArticlesView.vue"
import SearchView from "../views/SearchView.vue"
import AboutView from "../views/AboutView.vue"
import CreateClassView from "../views/CreateClassView.vue"
import KaKaoView from "../views/KaKaoView.vue"
import OAuth2SuccessView from "../views/OAuth2SuccessView.vue"
import ClassVideoRoomView from "@/views/ClassVideoRoomView.vue"
import MyPageView from "@/views/MyPageView.vue"
import DocumentSummaryView from "@/views/DocumentSummaryView.vue"

// Pinia 스토어 import
import { useAuthStore } from '@/stores/auth.js';

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
      meta: { requiresAuth: true }  // 로그인 필요
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
      meta: { requiresAuth: true }
    },
    {
      path: '/kakao',
      name: 'kakao',
      component: KaKaoView,
      meta: { requiresAuth: true }
    },
    {
      path: '/oauth2/success',
      name: 'oauth2-success',
      component: OAuth2SuccessView,
      // OAuth2 처리 중이므로 인증 불필요
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
    {
      path: '/document-summary',
      name: 'document-summary',
      component: DocumentSummaryView,
      // meta: { requiresAuth: true }  // 필요시 주석 해제
    },
  ],
})

/**
 * 전역 라우터 가드 설정
 * Pinia 스토어의 상태를 사용하여 접근 제어
 */
router.beforeEach((to, from, next) => {
  // 스토어 인스턴스를 가져와 사용
  const authStore = useAuthStore();

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    alert('로그인이 필요한 기능입니다.');
    next('/login');
  } else {
    next();
  }
})

export default router