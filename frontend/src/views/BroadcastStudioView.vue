<script setup>
/**
 * 발표자 송출 화면. (#123)
 *
 * ★ 서버가 재인코딩하는지 아닌지를 화면에 띄운다.
 *   그냥 "방송 중" 만 보여 주면, 브라우저가 VP8 밖에 못 낼 때 서버 CPU 가
 *   조용히 100% 로 붙는데 아무도 모른다. 어느 경로인지가 운영 정보다.
 */
import { ref, computed, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { createPublisher, CHUNK_MS } from '@/features/broadcast/broadcastPublisher'
import { BROADCAST_PROFILES } from '@/features/broadcast/broadcastProfiles'
import BroadcastChat from '@/components/BroadcastChat.vue'

const route = useRoute()
const meetingId = route.params.meetingId

const audioOnly = ref(route.query.mode === 'audio')
const profileId = ref('standard')
const status = ref({ state: 'idle' })
const error = ref('')
const preview = ref(null)

let publisher = null

const isLive = computed(() => status.value.state === 'live')

async function start() {
  error.value = ''
  publisher = createPublisher(meetingId, {
    audioOnly: audioOnly.value,
    profileId: profileId.value,
    onStatus: (s) => { status.value = { ...status.value, ...s } },
  })
  try {
    await publisher.start()
    // 비디오면 자기 화면을 보여 준다. 안 보이면 카메라가 켜졌는지 알 수 없다.
    if (!audioOnly.value && preview.value) {
      preview.value.srcObject = publisher.mediaStream()
      preview.value.muted = true
      await preview.value.play()
    }
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || '송출을 시작하지 못했습니다.'
    status.value = { state: 'idle' }
  }
}

async function stop() {
  if (publisher) await publisher.stop()
  if (preview.value?.srcObject) {
    preview.value.srcObject = null
  }
}

onBeforeUnmount(stop)
</script>

<template>
  <main class="studio">
    <h1>방송 송출</h1>

    <section class="studio__stage">
      <video v-if="!audioOnly" ref="preview" class="studio__preview" playsinline></video>
      <div v-else class="studio__audio">🎙️ 오디오 방송</div>
    </section>

    <section class="studio__controls">
      <label v-if="!isLive">
        <input type="checkbox" v-model="audioOnly" /> 오디오만 내보내기
      </label>

      <label v-if="!isLive && !audioOnly" class="studio__profile">
        화질
        <select v-model="profileId">
          <option
            v-for="profile in BROADCAST_PROFILES"
            :key="profile.id"
            :value="profile.id"
          >
            {{ profile.label }} — {{ profile.description }}
          </option>
        </select>
      </label>

      <button v-if="!isLive" class="studio__go" @click="start">송출 시작</button>
      <button v-else class="studio__stop" @click="stop">송출 중지</button>
    </section>

    <p v-if="error" class="studio__error" role="alert">{{ error }}</p>

    <!--
      ★ 여기가 핵심 정보다.
        remuxable 이 참이면 서버는 컨테이너만 바꾼다(거의 공짜).
        거짓이면 전부 다시 인코딩한다(코어 하나). 같은 "방송 중" 이 아니다.
    -->
    <section v-if="isLive" class="studio__meta">
      <dl>
        <dt>코덱</dt><dd><code>{{ status.mimeType }}</code></dd>
        <dt>송출 프로파일</dt><dd>{{ status.profileLabel || status.profileSummary }}</dd>
        <dt>서버 처리</dt>
        <dd :class="status.remuxable ? 'ok' : 'warn'">
          {{ status.remuxable ? '컨테이너만 변환 (CPU 거의 안 씀)' : '재인코딩 (CPU 많이 씀)' }}
        </dd>
        <dt>조각 길이</dt><dd>{{ CHUNK_MS / 1000 }}초</dd>
        <dt>보냄 / 거부 / 실패</dt>
        <dd>
          {{ status.sent || 0 }} /
          <span :class="{ warn: status.rejected }">{{ status.rejected || 0 }}</span> /
          <span :class="{ warn: status.failed }">{{ status.failed || 0 }}</span>
        </dd>
        <dt>시청 주소</dt><dd><code>{{ status.playlistUrl }}</code></dd>
      </dl>
      <!-- 거부가 늘면 서버가 못 따라가는 것이다. 숨기면 발표자가 원인을 모른다. -->
      <p v-if="status.rejected" class="studio__warn">
        서버가 조각을 {{ status.rejected }}개 거부했습니다. 화질을 낮추거나 조각 길이를 늘려야 합니다.
      </p>
    </section>

    <BroadcastChat :meeting-id="meetingId" class="studio__chat" />
  </main>
</template>

<style scoped>
.studio { display: grid; grid-template-columns: 1fr 320px; gap: 1rem; padding: 1rem; align-items: start; }
.studio h1 { grid-column: 1 / -1; margin: 0 0 .5rem; }
.studio__stage { background: #000; border-radius: .5rem; aspect-ratio: 16/9; display: grid; place-items: center; }
.studio__preview { width: 100%; height: 100%; object-fit: contain; }
.studio__audio { color: #fff; font-size: 1.4rem; }
.studio__controls { display: flex; gap: .75rem; align-items: center; }
.studio__profile { display: inline-flex; gap: .4rem; align-items: center; }
.studio__profile select { max-width: 15rem; padding: .35rem .45rem; }
.studio__go, .studio__stop { padding: .5rem 1.1rem; border-radius: .4rem; border: 0; cursor: pointer; }
.studio__go { background: #1b6ef3; color: #fff; }
.studio__stop { background: #b00020; color: #fff; }
.studio__error { color: #b00020; grid-column: 1; }
.studio__meta dl { display: grid; grid-template-columns: auto 1fr; gap: .3rem .8rem; margin: 0; }
.studio__meta dt { opacity: .7; }
.studio__meta dd { margin: 0; }
.studio__meta .ok { color: #0a7d33; }
.studio__meta .warn { color: #b06a00; font-weight: 600; }
.studio__warn { color: #b06a00; }
.studio__chat { grid-row: 2 / span 4; grid-column: 2; height: 60vh; }
</style>
