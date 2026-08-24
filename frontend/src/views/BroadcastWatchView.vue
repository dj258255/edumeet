<script setup>
/**
 * 시청 화면. (#123)
 *
 * ★ 방송 시작 전 404 는 오류가 아니다.
 *   발표자가 아직 안 켰으면 플레이리스트 파일이 없다. 그때 "재생 실패" 를 띄우면
 *   시청자는 서비스가 고장 난 줄 안다. 기다리는 상태로 보여 준다.
 *
 * ★ 지연을 화면에 띄운다.
 *   HLS 는 세그먼트 길이만큼 늦는다. 숨기면 "왜 늦지" 가 되고,
 *   보여 주면 "원래 그런 것" 이 된다. 채팅과 방송이 어긋나 보이는 이유이기도 하다.
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import apiClient from '@/utils/apiClient'
import { attachHls } from '@/features/broadcast/hlsPlayer'
import BroadcastChat from '@/components/BroadcastChat.vue'

const route = useRoute()
const meetingId = route.params.meetingId

const videoEl = ref(null)
const playlistUrl = ref('')
const waiting = ref(true)
const error = ref('')
const latency = ref(null)

let handle = null
let timer = null

async function findPlaylist() {
  try {
    const { data } = await apiClient.get(`/meeting/${meetingId}`)
    return data.hlsPlaylistUrl || null
  } catch {
    return null
  }
}

onMounted(async () => {
  // 방송이 아직 안 켜졌을 수 있다. 몇 초마다 다시 본다.
  const tryAttach = async () => {
    const url = await findPlaylist()
    if (!url) return false
    playlistUrl.value = url
    handle = attachHls(videoEl.value, url, { onError: (e) => { error.value = e.message } })
    waiting.value = false
    return true
  }

  if (!(await tryAttach())) {
    timer = setInterval(async () => {
      if (await tryAttach()) clearInterval(timer)
    }, 3000)
  }

  // 라이브 가장자리에서 얼마나 뒤에 있는지. 이것이 체감 지연이다.
  setInterval(() => {
    const v = videoEl.value
    if (!v || !v.buffered.length) return
    latency.value = (v.buffered.end(v.buffered.length - 1) - v.currentTime).toFixed(1)
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (handle) handle.destroy()
})
</script>

<template>
  <main class="watch">
    <h1>라이브</h1>

    <section class="watch__stage">
      <video ref="videoEl" controls autoplay playsinline muted class="watch__video"></video>

      <p v-if="waiting" class="watch__overlay">방송이 시작되기를 기다리는 중입니다…</p>
      <p v-else-if="error" class="watch__overlay watch__overlay--error" role="alert">{{ error }}</p>
    </section>

    <p v-if="latency !== null" class="watch__latency">
      라이브 가장자리에서 <strong>{{ latency }}초</strong> 뒤 · HLS 는 세그먼트 길이만큼 늦습니다
    </p>

    <BroadcastChat :meeting-id="meetingId" class="watch__chat" />
  </main>
</template>

<style scoped>
.watch { display: grid; grid-template-columns: 1fr 320px; gap: 1rem; padding: 1rem; align-items: start; }
.watch h1 { grid-column: 1 / -1; margin: 0 0 .5rem; }
.watch__stage { position: relative; background: #000; border-radius: .5rem; aspect-ratio: 16/9; }
.watch__video { width: 100%; height: 100%; object-fit: contain; }
.watch__overlay {
  position: absolute; inset: 0; display: grid; place-items: center;
  color: #fff; margin: 0; background: rgba(0,0,0,.5);
}
.watch__overlay--error { color: #ff9a9a; }
.watch__latency { opacity: .75; font-size: .9em; }
.watch__chat { grid-row: 2 / span 3; grid-column: 2; height: 60vh; }
</style>
