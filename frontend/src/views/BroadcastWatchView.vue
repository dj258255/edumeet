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
 *
 * ★ 지연과 버퍼는 다르다.
 *   hls.js 의 latency 는 live edge 와의 거리이고, video.buffered 는 당장 재생 가능한
 *   버퍼다. 둘을 섞으면 "지연이 줄었다" 와 "버퍼가 줄었다" 를 구분하지 못한다.
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import apiClient from '@/utils/apiClient'
import { attachHls } from '@/features/broadcast/hlsPlayer'
import { metricText } from '@/features/broadcast/hlsMetrics'
import BroadcastChat from '@/components/BroadcastChat.vue'

const route = useRoute()
const meetingId = route.params.meetingId

const videoEl = ref(null)
const playlistUrl = ref('')
const waiting = ref(true)
const error = ref('')
const metrics = ref(null)

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
    handle = await attachHls(videoEl.value, url, {
      onError: (e) => { error.value = e.message },
      onMetrics: (m) => { metrics.value = m },
    })
    waiting.value = false
    return true
  }

  if (!(await tryAttach())) {
    timer = setInterval(async () => {
      if (await tryAttach()) clearInterval(timer)
    }, 3000)
  }
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

    <section v-if="metrics" class="watch__metrics" aria-label="HLS 품질 지표">
      <dl>
        <dt>라이브 지연</dt><dd>{{ metricText(metrics.latency, '초') }}</dd>
        <dt>목표 지연</dt><dd>{{ metricText(metrics.targetLatency, '초') }}</dd>
        <dt>버퍼</dt><dd>{{ metricText(metrics.buffer, '초') }}</dd>
        <dt>대역폭 추정</dt><dd>{{ metricText(metrics.bandwidthKbps, 'kbps') }}</dd>
        <dt>화질 레벨</dt><dd>{{ metrics.level }}</dd>
        <dt>조각 로드</dt><dd>{{ metricText(metrics.fragLoadMs, 'ms') }}</dd>
        <dt>드롭 프레임</dt><dd>{{ metricText(metrics.droppedFrames) }}</dd>
        <dt>오류</dt><dd :class="{ warn: metrics.errors }">{{ metrics.errors }}</dd>
      </dl>
      <p>
        HLS 는 WebRTC 보다 늦지만 HTTP 캐시와 CDN 으로 많이 뿌리기 쉽습니다.
        지연·버퍼·로드 시간을 따로 봐야 원인을 분리할 수 있습니다.
      </p>
    </section>

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
.watch__metrics {
  font-size: .9em;
  border: 1px solid #dde3ea;
  border-radius: .5rem;
  padding: .75rem;
}
.watch__metrics dl { display: grid; grid-template-columns: auto 1fr; gap: .25rem .75rem; margin: 0; }
.watch__metrics dt { opacity: .7; }
.watch__metrics dd { margin: 0; font-variant-numeric: tabular-nums; }
.watch__metrics p { margin: .65rem 0 0; opacity: .75; line-height: 1.45; }
.watch__metrics .warn { color: #b00020; font-weight: 600; }
.watch__chat { grid-row: 2 / span 3; grid-column: 2; height: 60vh; }
</style>
