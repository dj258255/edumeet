<template>
    <div class="image-modal-overlay" @click="$emit('close')">
      <div class="image-modal-container" @click.stop>
        <button class="close-btn" @click="$emit('close')">✕</button>
        <img :src="src" alt="이미지 미리보기" />
      </div>
    </div>
  </template>
  
  <script setup>
  defineProps({ src: String });
  </script>
  
  <style scoped>
  .image-modal-overlay {
    position: fixed; top:0; left:0; width:100%; height:100%;
    background: rgba(0,0,0,0.85);
    display: flex; justify-content:center; align-items:center;
    z-index: 1100;
  }
  .image-modal-container {
    position: relative;
    max-width: 90%;
    max-height: 90%;
  }
  .image-modal-container img {
    width: 100%;
    height: auto;
    border-radius: 4px;
  }
  .close-btn {
    position: absolute; top: 10px; right: 10px;
    background: #fff; border: none; cursor: pointer;
    font-size: 1.2rem; padding: 0.2rem 0.5rem;
  }
  </style>
  