<template>
  <div class="resources-view">
    <!-- 탭 메뉴 -->
    <div class="tabs">
      <button :class="{ active: selectedTab === 'all' }" @click="selectedTab = 'all'">전체</button>
      <button :class="{ active: selectedTab === 'notice' }" @click="selectedTab = 'notice'">공지</button>
      <button :class="{ active: selectedTab === 'qna' }" @click="selectedTab = 'qna'">Q&amp;A</button>
      <button :class="{ active: selectedTab === 'free' }" @click="selectedTab = 'free'">자유게시판</button>
    </div>

    <!-- 상단 바: 검색창, 글쓰기 버튼 -->
    <div class="top-bar">
      <div class="search-box">
        <input v-model="searchText" placeholder="Hinted search text" />
        <button class="search-btn">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
        </button>
      </div>
      <button class="write-btn">글쓰기</button>
    </div>

    <!-- 게시글 리스트 -->
    <div class="post-list">
      <div class="post-card" v-for="post in filteredPosts" :key="post.id">
        <div class="post-thumb">
          <div class="img-placeholder">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="8" fill="#E6F4EA"/><path d="M16 32L22 24L28 32H16Z" fill="#B3E6C7"/><circle cx="20" cy="20" r="3" fill="#B3E6C7"/></svg>
          </div>
        </div>
        <div class="post-content">
          <div class="post-title">{{ post.title }}</div>
          <div class="post-body">{{ post.body }}</div>
          <button class="post-btn">Button</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue"

const selectedTab = ref("all")
const searchText = ref("")

// 임시 게시글 데이터
const posts = ref([
  { id: 1, type: "notice", title: "Title", body: "Body text for whatever you'd like to say. Add main takeaway points, quotes, anecdotes, or even a very very short story." },
  { id: 2, type: "qna", title: "Title", body: "Body text for whatever you'd like to say. Add main takeaway points, quotes, anecdotes, or even a very very short story." },
  { id: 3, type: "free", title: "Title", body: "Body text for whatever you'd like to say. Add main takeaway points, quotes, anecdotes, or even a very very short story." },
  { id: 4, type: "notice", title: "Title", body: "Body text for whatever you'd like to say. Add main takeaway points, quotes, anecdotes, or even a very very short story." },
])

const filteredPosts = computed(() => {
  let filtered = posts.value
  if (selectedTab.value !== "all") {
    filtered = filtered.filter((p) => p.type === selectedTab.value)
  }
  if (searchText.value.trim()) {
    filtered = filtered.filter((p) =>
      p.title.toLowerCase().includes(searchText.value.toLowerCase()) ||
      p.body.toLowerCase().includes(searchText.value.toLowerCase())
    )
  }
  return filtered
})
</script>

<style scoped>
.resources-view {
  background: #fafbfd;
  min-height: 100vh;
  padding: 32px 0 0 0;
}
.tabs {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-bottom: 24px;
}
.tabs button {
  background: none;
  border: none;
  font-size: 22px;
  font-weight: 500;
  color: #222;
  padding: 4px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.tabs button.active {
  background: #d8f5e6;
  color: #227a53;
}
.top-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  margin-bottom: 32px;
}
.search-box {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #e6f4ea;
  border-radius: 8px;
  padding: 0 12px;
  height: 40px;
  min-width: 320px;
}
.search-box input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  flex: 1;
  padding: 8px 0;
}
.search-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #227a53;
  padding-left: 8px;
  display: flex;
  align-items: center;
}
.write-btn {
  background: #fff;
  border: 1.5px solid #b3e6c7;
  color: #227a53;
  font-size: 16px;
  font-weight: 500;
  border-radius: 8px;
  padding: 8px 18px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.write-btn:hover {
  background: #d8f5e6;
}
.post-list {
  display: flex;
  flex-direction: column;
  gap: 32px;
  max-width: 900px;
  margin: 0 auto;
}
.post-card {
  display: flex;
  align-items: flex-start;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(34, 122, 83, 0.04);
  padding: 32px 32px 32px 24px;
  gap: 32px;
  min-height: 120px;
}
.post-thumb {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4fbf7;
  border-radius: 12px;
  flex-shrink: 0;
}
.img-placeholder {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.post-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}
.post-title {
  font-size: 20px;
  font-weight: 700;
  color: #227a53;
}
.post-body {
  color: #444;
  font-size: 15px;
  margin-bottom: 8px;
}
.post-btn {
  background: #fff;
  border: 1.5px solid #b3e6c7;
  color: #227a53;
  font-size: 14px;
  border-radius: 6px;
  padding: 4px 16px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.post-btn:hover {
  background: #d8f5e6;
}
@media (max-width: 700px) {
  .post-card {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 20px 10px;
  }
  .post-thumb {
    margin: 0 auto;
  }
}
</style>
