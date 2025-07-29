<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from "@/stores/auth.js"
import './styles/App.css'

import TeamModal from './components/TeamModal.vue'

const sidebarOpen = ref(false)
const searchText = ref('')
const searchOpen = ref(false) 
const isDarkMode = ref(false) 
const router = useRouter()
const authStore = useAuthStore()

// 로그인 상태 관리
const isLoggedIn = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)

// 다크모드 로컬 스토리지
onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDarkMode.value = true
    document.documentElement.classList.add('dark-mode')
  }
  authStore.initialize()
})

// 다크모드 변경 감지
watch(isDarkMode, (newValue) => {
  if (newValue) {
    document.documentElement.classList.add('dark-mode')
    localStorage.setItem('theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark-mode')
    localStorage.setItem('theme', 'light')
  }
})

const toggleSidebar = () => sidebarOpen.value = !sidebarOpen.value
const toggleSearch = () => {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) {
    searchText.value = ''
    setTimeout(() => {
      const input = document.getElementById('global-search-input')
      if (input) input.focus()
    }, 100)
  }
}
const toggleDarkMode = () => isDarkMode.value = !isDarkMode.value

const handleSearch = () => {
  if (searchText.value.trim()) {
    router.push({ path: '/search', query: { query: searchText.value } })
    searchOpen.value = false
    searchText.value = ''
  }
}

/* 로고 꾹 누름 → 모달 열기 */
const pressTimer = ref(null)
const modalOpen = ref(false)

const startPress = () => {
  pressTimer.value = setTimeout(() => {
    modalOpen.value = true
  }, 2000)
}
const cancelPress = () => {
  if (pressTimer.value) {
    clearTimeout(pressTimer.value)
    pressTimer.value = null
  }
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-left">
      <button class="hamburger mobile-only" @click="toggleSidebar">☰</button>
      <!-- 로고 꾹 누르면 모달 열림 -->
      <RouterLink to="/" 
        @mousedown="startPress" 
        @mouseup="cancelPress" 
        @mouseleave="cancelPress">
        <img alt="EduMeet Logo" class="logo" src="@/assets/edumeet_logo.png" />
        <span>EduMeet</span>
      </RouterLink>
    </div>
    <nav class="navbar-center desktop-only">
      <RouterLink to="/class/create">Class</RouterLink>
      <RouterLink to="/solutions">Solutions</RouterLink>
      <RouterLink to="/community">Community</RouterLink>
      <RouterLink to="/articles">Articles</RouterLink>
      <RouterLink to="/pricing">Pricing</RouterLink>
      <RouterLink to="/contact">Contact</RouterLink>
      <RouterLink to="/link">Link</RouterLink>
    </nav>
    <div class="navbar-right desktop-only">
      <button class="search-button" @click="toggleSearch" aria-label="검색">🔍</button>
      <button class="dark-mode-button" @click="toggleDarkMode">
        {{ isDarkMode ? '☀️' : '🌙' }}
      </button>
      <RouterLink v-if="!isLoggedIn" class="btn login" to="/login">login</RouterLink>
      <RouterLink v-if="!isLoggedIn" class="btn signup" to="/signup">Register</RouterLink>
      <div v-if="isLoggedIn" class="user-info">
        <span class="user-name">{{ currentUser?.name || currentUser?.email || '사용자' }}</span>
        <button class="btn logout" @click="authStore.logout">로그아웃</button>
      </div>
    </div>
  </header>

  <!-- 팀 모달 -->
  <TeamModal :open="modalOpen" @close="modalOpen = false" />

  <!-- 검색 모달 -->
  <div v-if="searchOpen" class="search-modal" @click="toggleSearch">
    <div class="search-modal-content" @click.stop>
      <input
        id="global-search-input"
        v-model="searchText"
        class="search-modal-input"
        placeholder="검색어를 입력하세요"
        @keyup.enter="handleSearch"
      />
      <button class="search-modal-btn" @click="handleSearch">🔍</button>
    </div>
  </div>

  <RouterView />
</template>
