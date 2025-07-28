import { createRouter, createWebHistory } from "vue-router"
import HomeView from "../views/HomeView.vue"
import LoginView from "../views/LoginView.vue"
import SignupView from "../views/SignupView.vue"
import ArticlesView from "../views/ArticlesView.vue"
import SearchView from "../views/SearchView.vue"
import RoomView from "../views/RoomView.vue"
import AboutView from "../views/AboutView.vue"
import ClassView from "../views/ClassView.vue"
import CreateClassView from "../views/CreateClassView.vue"
import KaKaoView from "../views/KaKaoView.vue"

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
    },
    {
      path: "/search",
      name: "search",
      component: SearchView,
    },
    {
      path: "/room/:roomId",
      name: "room",
      component: RoomView,
    },
    {
      path: "/about",
      name: "about",
      component: AboutView,
    },
    {
      path: "/class/:classId",
      name: "class",
      component: ClassView,
    },
    {
      path: '/class/create',
      name: "create-class",
      component: CreateClassView,
    },
    {
      path: '/class/:classId/room/:roomId',  // ✅ 화상채팅 방 URL
      name: 'room',
      component: RoomView
    },
    {
      path: '/kakao',
      name: 'kakao',
      component: KaKaoView
    }
  ],
})
  

export default router
