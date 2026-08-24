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
      <ArticleCardView 
        v-for="post in filteredPosts" :key="post.id"
        :post="post"
        />
    </div>
  </div>
</template>

<script setup>
import '../styles/ArticleView.css'
import ArticleCardView from "../components/ArticleCardView.vue"
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
