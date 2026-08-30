<script setup>
/**
 * 방송 화면의 실시간 자막. (#185)
 *
 * ── 왜 이제야 생겼나 ────────────────────────────────────────────
 *
 *   자막은 백엔드에서 이미 나가고 있었다. 화상강의 화면은 그걸 받아 띄운다.
 *   그런데 **많이 뿌리려고 만든 방송 화면에는 듣는 곳이 없었다.**
 *   이 서비스의 존재 이유가 실시간 자막인데 정작 방송 모드에 없었다.
 *
 * ── 그냥 띄우면 안 되는 이유 ────────────────────────────────────
 *
 *   자막은 WebSocket 으로 1초 안에 오고, 영상은 HLS 라 6초쯤 뒤에 온다.
 *   그대로 띄우면 **자막이 화면보다 먼저 나간다** - 말하는 입 모양과 어긋나고,
 *   화면에서 아직 안 일어난 일이 글자로 먼저 나온다.
 *
 *   그래서 화면 시각이 따라올 때까지 붙잡았다가 내보낸다.
 *   그 규칙은 captionAligner 에 순수 함수로 두고 시험으로 고정했다.
 */
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { createCaptionAligner } from '@/features/broadcast/captionAligner'

const props = defineProps({
  /** 지금 화면에 보이는 장면의 시각을 주는 함수. 없으면 붙잡지 않는다. */
  getPlayingDate: { type: Function, required: true },
  /** 한 줄에 남길 자막 개수. 넘치면 오래된 것부터 지운다. */
  keep: { type: Number, default: 3 },
})

/** 부모(STOMP 구독)가 자막을 넣어 준다. */
const aligner = createCaptionAligner(() => props.getPlayingDate())
const lines = ref([])
const leadMs = ref(null)
const holding = ref(0)

let timer = null

function accept(caption) {
  // 복구본은 실시간 줄이 아니다. 지나간 구간을 채우는 것이라 여기 오면 안 된다. (#165)
  if (caption?.replay) return
  aligner.push(caption)
}

onMounted(() => {
  // 200ms 마다 본다. 자막은 사람이 읽는 것이라 이보다 촘촘할 이유가 없고,
  // 세그먼트가 2초라 화면 시각도 이보다 자주 바뀌지 않는다.
  timer = setInterval(() => {
    const due = aligner.tick()
    if (due.length) {
      lines.value = [...lines.value, ...due].slice(-props.keep)
    }
    leadMs.value = aligner.leadMs
    holding.value = aligner.pendingCount
  }, 200)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

defineExpose({ accept, stats: () => aligner.stats })

const leadText = computed(() => {
  if (leadMs.value === null) return null
  if (leadMs.value <= 0) return '화면과 맞음'
  return `화면보다 ${(leadMs.value / 1000).toFixed(1)}초 앞 · 기다리는 중`
})
</script>

<template>
  <section class="cap" aria-label="실시간 자막" aria-live="polite">
    <p v-for="(line, i) in lines" :key="i" class="cap__line">{{ line.text }}</p>
    <p v-if="!lines.length" class="cap__idle">자막을 기다리는 중입니다…</p>

    <!-- 왜 이걸 보여 주나 - 자막이 안 뜨는 동안 "고장" 과 "화면을 기다리는 중" 을
         사용자가 구분할 수 있어야 한다. 조용히 비어 있으면 둘이 같아 보인다. -->
    <p v-if="holding && leadText" class="cap__hint">{{ leadText }}</p>
  </section>
</template>

<style scoped>
.cap {
  background: rgba(0, 0, 0, .72);
  color: #fff;
  border-radius: .5rem;
  padding: .6rem .9rem;
  min-height: 3.2rem;
  font-size: 1.15rem;
  line-height: 1.5;
}
.cap__line { margin: 0 0 .2rem; }
.cap__line:last-of-type { margin-bottom: 0; font-weight: 600; }
.cap__idle { margin: 0; opacity: .6; font-size: .95rem; }
.cap__hint { margin: .35rem 0 0; opacity: .6; font-size: .8rem; }
</style>
