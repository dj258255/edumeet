<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

const sidebarOpen = ref(false)
const searchText = ref('')
const searchOpen = ref(false) // 검색창 열림 상태
const router = useRouter()

const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

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

const handleSearch = () => {
  if (searchText.value.trim()) {
    router.push({ path: '/search', query: { query: searchText.value } })
    searchOpen.value = false
    searchText.value = ''
  }
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-left">
      <button class="hamburger mobile-only" @click="toggleSidebar">☰</button>
      <RouterLink to="/">
        <img alt="EduMeet Logo" class="logo" src="@/assets/edumeet_logo.png" />
        <span>EduMeet</span>
      </RouterLink>
    </div>
    <nav class="navbar-center desktop-only">
      <RouterLink to="/products">Products</RouterLink>
      <RouterLink to="/solutions">Solutions</RouterLink>
      <RouterLink to="/community">Community</RouterLink>
      <RouterLink to="/resources">Resources</RouterLink>
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
      <RouterLink class="btn login" to="/login">Sign in</RouterLink>
      <RouterLink class="btn signup" to="/signup">Register</RouterLink>
    </div>
    <!-- 모바일 사이드바 -->
    <div v-if="sidebarOpen" class="sidebar mobile-only">
      <button class="close-btn" @click="toggleSidebar">×</button>
      <RouterLink to="/products" @click="toggleSidebar">Products</RouterLink>
      <RouterLink to="/solutions" @click="toggleSidebar">Solutions</RouterLink>
      <RouterLink to="/community" @click="toggleSidebar">Community</RouterLink>
      <RouterLink to="/resources" @click="toggleSidebar">Resources</RouterLink>
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
      <RouterLink class="btn login" to="/login" @click="toggleSidebar">Sign in</RouterLink>
      <RouterLink class="btn signup" to="/signup" @click="toggleSidebar">Register</RouterLink>
    </div>
  </header>
  <div v-if="searchOpen" class="search-modal">
    <input
      id="global-search-input"
      v-model="searchText"
      class="search-modal-input"
      placeholder="검색어를 입력하세요"
      @keyup.enter="handleSearch"
    />
    <button class="search-modal-btn" @click="handleSearch">
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <circle cx="11" cy="11" r="8"></circle>
        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
      </svg>
    </button>
  </div>
  <RouterView/>
</template>

<style scoped>

.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  height: 56px;
  background-color: #fff;
  border-bottom: 1px solid #ddd;
  position: relative;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.navbar-left span {
  display: inline-block;
  vertical-align: middle;
  font-size: 1.35rem;
  font-weight: bold;
  margin-left: 0.2rem;
  color: #27ae60;
}

.logo {
  height: 45px;
  width:auto;
  display:inline-block;
  vertical-align:middle;
}

.navbar-center {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 1;
  justify-content: center;
}

.navbar-center a {
  color: #222;
  font-size: 15px;
  padding: 6px 12px;
  border-radius: 6px;
  text-decoration: none;
  transition: background 0.2s;
}

.navbar-center a:hover {
  background: #f2f2f2;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-button {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: #444;
  display: flex;
  align-items: center;
  margin-right: 8px;
}

.btn.login {
  background: #fff;
  color: #222;
  border: 1px solid #bbb;
  font-size: 14px;
  padding: 4px 14px;
}

.btn.signup {
  background: #222;
  color: #fff;
  font-size: 14px;
  padding: 4px 14px;
}

/* 사이드바 */
.sidebar {
  position: absolute;
  top: 70px;
  left: 0;
  width: 200px;
  background-color: #ffffff;
  box-shadow: 2px 0 5px rgba(0, 0, 0, 0.1);
  padding: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.sidebar a {
  margin-bottom: 12px;
  text-decoration: none;
  color: #222;
  font-weight: 500;
}

.sidebar-auth {
  margin-top: 24px;
  display: none;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 1000px) {
  .navbar-right.desktop-only {
    display: none;
  }
  .mobile-only {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .sidebar-auth {
    display: flex;
  }
}

.search-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0,0,0,0.18);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 2000;
}
.search-modal-input {
  margin-top: 80px;
  font-size: 20px;
  padding: 12px 20px;
  border-radius: 999px;
  border: 1px solid #bbb;
  outline: none;
  background: #fff;
  min-width: 320px;
  max-width: 90vw;
}
.search-modal-btn {
  margin-top: 80px;
  margin-left: 8px;
  background: #fff;
  border: 1px solid #bbb;
  border-radius: 50%;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* 반응형 네비게이션 */
.desktop-only { display: flex; }
.mobile-only { display: none; }
@media (max-width: 1000px) {
  .desktop-only { display: none !important; }
  .mobile-only { display: flex !important; }
}
.hamburger {
  font-size: 24px;
  background: none;
  border: none;
  cursor: pointer;
  margin-right: 8px;
}
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: 80vw;
  max-width: 320px;
  height: 100vh;
  background: #fff;
  box-shadow: 2px 0 8px rgba(0,0,0,0.08);
  z-index: 3000;
  display: flex;
  flex-direction: column;
  padding: 32px 20px 20px 20px;
  gap: 18px;
  animation: sidebarIn 0.2s;
}
@keyframes sidebarIn {
  from { transform: translateX(-100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
.close-btn {
  background: none;
  border: none;
  font-size: 32px;
  align-self: flex-end;
  cursor: pointer;
  margin-bottom: 12px;
}
.sidebar-search {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f2f2f2;
  border-radius: 8px;
  padding: 6px 12px;
  border: none;
  color: #222;
  font-size: 16px;
  cursor: pointer;
}

</style>
