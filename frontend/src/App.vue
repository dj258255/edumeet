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
      <button class="search-button" @click="toggleSearch" aria-label="검색">
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
      </button>
      <button class="dark-mode-button" @click="toggleDarkMode" :aria-label="isDarkMode ? '라이트모드로 전환' : '다크모드로 전환'">
        <svg v-if="!isDarkMode" xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="5"></circle>
          <line x1="12" y1="1" x2="12" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="23"></line>
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
          <line x1="1" y1="12" x2="3" y2="12"></line>
          <line x1="21" y1="12" x2="23" y2="12"></line>
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
        </svg>

      </button>
      <RouterLink v-if="!isLoggedIn" class="btn login" to="/login">login</RouterLink>
      <RouterLink v-if="!isLoggedIn" class="btn signup" to="/signup">Register</RouterLink>
      <div v-if="isLoggedIn" class="user-info">
        <RouterLink to="/mypage" class="profile-link">
          <div class="profile-avatar">
            <img 
              src="@/assets/member/1.png" 
              alt="프로필 이미지" 
              class="avatar-image"
            />
          </div>
        </RouterLink>
        <div class="user-details">
          <div class="user-info-row">
            <span class="user-name">{{ currentUser?.name || currentUser?.email || '사용자' }}</span>
            <button class="btn logout" @click="authStore.logout" title="로그아웃">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                <polyline points="16,17 21,12 16,7"></polyline>
                <line x1="21" y1="12" x2="9" y2="12"></line>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
    <!-- 모바일 사이드바 -->
    <div v-if="sidebarOpen" class="sidebar mobile-only">
      <div class="sidebar-header">
        <button class="close-btn" @click="toggleSidebar">×</button>
      </div>
      <div class="sidebar-content">
        <RouterLink to="/class/create" @click="toggleSidebar">Class</RouterLink>
        <RouterLink to="/solutions" @click="toggleSidebar">Solutions</RouterLink>
        <RouterLink to="/community" @click="toggleSidebar">Community</RouterLink>
        <RouterLink to="/articles" @click="toggleSidebar">Articles</RouterLink>
        <RouterLink to="/pricing" @click="toggleSidebar">Pricing</RouterLink>
        <RouterLink to="/contact" @click="toggleSidebar">Contact</RouterLink>
        <RouterLink to="/link" @click="toggleSidebar">Link</RouterLink>
        <button class="search-button sidebar-search" @click="() => { toggleSidebar(); toggleSearch(); }" aria-label="검색">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <span>검색</span>
        </button>
        <button class="dark-mode-button sidebar-dark-mode" @click="toggleDarkMode" :aria-label="isDarkMode ? '라이트모드로 전환' : '다크모드로 전환'">
          <svg v-if="!isDarkMode" xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="5"></circle>
            <line x1="12" y1="1" x2="12" y2="3"></line>
            <line x1="12" y1="21" x2="12" y2="23"></line>
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
            <line x1="1" y1="12" x2="3" y2="12"></line>
            <line x1="21" y1="12" x2="23" y2="12"></line>
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
          </svg>
          <span>{{ isDarkMode ? '라이트모드' : '다크모드' }}</span>
        </button>
        <RouterLink v-if="!isLoggedIn" class="btn login" to="/login" @click="toggleSidebar">login</RouterLink>
        <RouterLink v-if="!isLoggedIn" class="btn signup" to="/signup" @click="toggleSidebar">Register</RouterLink>
        <div v-if="isLoggedIn" class="user-info sidebar-user">
          <RouterLink to="/mypage" class="profile-link" @click="toggleSidebar">
            <div class="profile-avatar">
              <img 
                src="@/assets/member/1.png" 
                alt="프로필 이미지" 
                class="avatar-image"
              />
            </div>
          </RouterLink>
          <div class="user-details">
            <div class="user-info-row">
              <span class="user-name">{{ currentUser?.name || currentUser?.email || '사용자' }}</span>
              <button class="btn logout" @click="() => { authStore.logout(); toggleSidebar(); }" title="로그아웃">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                  <polyline points="16,17 21,12 16,7"></polyline>
                  <line x1="21" y1="12" x2="9" y2="12"></line>
                </svg>
              </button>
            </div>
            <span class="user-email">{{ currentUser?.email }}</span>
          </div>
        </div>
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
