<template>
    <div class="post-card" >
        <div class="post-thumb">
          <div class="img-placeholder">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="8" fill="#E6F4EA"/><path d="M16 32L22 24L28 32H16Z" fill="#B3E6C7"/><circle cx="20" cy="20" r="3" fill="#B3E6C7"/></svg>
          </div>
        </div>
        <div class="post-content">
          <div class="post-title">{{ props.post.title }}</div>
          <div class="post-body">{{ props.post.body }}</div>
          <button class="post-btn">Button</button>
        </div>
      </div>
</template>

<script setup>
    import '../styles/ArticleView.css'
    import { defineProps } from 'vue'
    const props = defineProps({
        post:Object,
    })
</script>
