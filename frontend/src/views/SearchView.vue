<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const query = route.query.query || ''
</script>
<template>
  <div class="search-view">
    <h2>검색 결과</h2>
    <p v-if="query">'{{ query }}'에 대한 검색 결과입니다.</p>
    <p v-else>검색어가 입력되지 않았습니다.</p>
  </div>
</template>
<style scoped>
.search-view {
  max-width: 700px;
  margin: 60px auto;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  padding: 40px 32px;
  text-align: center;
}
</style> 