<script setup>
/**
 * 다시보기 채팅 패널. (#115)
 *
 * 재생 위치를 받아 그 시점까지의 대화를 보여준다.
 * 영상 플레이어와 분리했다 - 플레이어는 S3 재생이라 별개 관심사이고,
 * 이 컴포넌트는 "재생 위치 -> 그때의 대화" 만 담당한다.
 *
 * 쓰는 쪽:
 *   <ChatReplayPanel :meeting-id="7" :position-ms="video.currentTime * 1000" />
 */
import { ref, watch, computed } from 'vue'
import { fetchChatWindow, windowOf, visibleAt, WINDOW_MS } from '@/features/replay/chatReplayApi'

const props = defineProps({
  meetingId: { type: [Number, String], required: true },
  /** 재생 위치(밀리초). 영상 플레이어가 준다. */
  positionMs: { type: Number, default: 0 },
})

const loaded = ref([])          // 현재 구간에서 받아 온 전체
const loadedIndex = ref(-1)     // 그 구간 번호. -1 이면 아직 없다
const truncated = ref(false)
const error = ref('')
const loading = ref(false)

/**
 * 지금까지 나온 대화만 보여준다.
 *
 * 구간을 통째로 그리면 아직 오지 않은 대화가 미리 보인다 - 스포일러다.
 */
const visible = computed(() => visibleAt(loaded.value, props.positionMs))

watch(
  () => windowOf(props.positionMs).index,
  async (index) => {
    // 같은 구간 안에서는 다시 부르지 않는다.
    // 재생 중 위치는 초당 여러 번 바뀌므로 매번 요청하면 서버가 견디지 못한다.
    if (index === loadedIndex.value) return

    const { from, to } = windowOf(props.positionMs)
    loading.value = true
    error.value = ''
    try {
      const data = await fetchChatWindow(props.meetingId, from, to)
      loaded.value = data.messages
      truncated.value = data.hasMore
      loadedIndex.value = index
    } catch (e) {
      // 채팅을 못 불러왔다고 영상 재생을 막지 않는다.
      error.value = '이 구간의 대화를 불러오지 못했습니다.'
      loaded.value = []
      console.warn('다시보기 채팅 조회 실패', e)
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

/** 0:12:34 형태로. 재생 위치와 눈으로 맞춰 볼 수 있게. */
function formatOffset(ms) {
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`
}
</script>

<template>
  <section class="chat-replay" aria-label="다시보기 대화">
    <header class="chat-replay__head">
      <h3>대화</h3>
      <span class="chat-replay__pos">{{ formatOffset(positionMs) }}</span>
    </header>

    <p v-if="loading && !visible.length" class="chat-replay__msg">불러오는 중…</p>
    <p v-else-if="error" class="chat-replay__msg chat-replay__msg--error" role="alert">
      {{ error }}
    </p>
    <p v-else-if="!visible.length" class="chat-replay__msg">
      이 시점에는 대화가 없습니다.
    </p>

    <ol v-else class="chat-replay__list">
      <li v-for="(m, i) in visible" :key="`${m.offsetMillis}-${i}`">
        <time :datetime="`PT${Math.floor(m.offsetMillis / 1000)}S`">
          {{ formatOffset(m.offsetMillis) }}
        </time>
        <strong>{{ m.sender }}</strong>
        <span>{{ m.content }}</span>
      </li>
    </ol>

    <!--
      잘렸다는 사실을 숨기지 않는다. 서버가 한 구간에 500건까지만 준다.
      말없이 자르면 "그때 분명 더 있었는데" 가 된다.
    -->
    <p v-if="truncated" class="chat-replay__msg chat-replay__msg--note">
      이 구간의 대화가 많아 일부만 표시됩니다.
    </p>
  </section>
</template>

<style scoped>
.chat-replay {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
}
.chat-replay__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.chat-replay__pos {
  font-variant-numeric: tabular-nums;
  opacity: 0.7;
  font-size: 0.9em;
}
.chat-replay__list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.chat-replay__list li {
  display: grid;
  grid-template-columns: auto auto 1fr;
  gap: 0.5rem;
  align-items: baseline;
}
.chat-replay__list time {
  font-variant-numeric: tabular-nums;
  opacity: 0.6;
  font-size: 0.85em;
}
.chat-replay__msg {
  opacity: 0.7;
  margin: 0;
}
.chat-replay__msg--error {
  color: #b00020;
}
.chat-replay__msg--note {
  font-size: 0.85em;
}
</style>
