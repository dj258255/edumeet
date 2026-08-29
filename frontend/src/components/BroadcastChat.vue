<script setup>
/**
 * 방송용 실시간 채팅. (#123)
 *
 * 세 모드에 공통으로 붙는다 - 화상강의든 라이브방송이든 오디오방송이든
 * 채팅은 같은 것이다. 그래서 송출 화면과 시청 화면이 이걸 함께 쓴다.
 *
 * ★ 미디어와 다른 경로를 탄다.
 *   채팅은 WebSocket(STOMP), 미디어는 HTTP 다. 방송이 밀려도 채팅은 산다.
 */
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { createRealtimeClient } from '@/features/realtime/stompClient'
import { fetchRecentChat } from '@/features/chat/recentChatApi'

const props = defineProps({
  meetingId: { type: [Number, String], required: true },
})

const messages = ref([])
const captions = ref([])
const draft = ref('')
const state = ref('disconnected')
const listEl = ref(null)

/** 화면에 남기는 최대 개수. 없으면 긴 방송에서 탭이 느려진다. */
const MAX_KEPT = 200

let client = null

function push(bucket, item) {
  bucket.value.push(item)
  if (bucket.value.length > MAX_KEPT) bucket.value.splice(0, bucket.value.length - MAX_KEPT)
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}

onMounted(async () => {
  const token = localStorage.getItem('token') || localStorage.getItem('accessToken')
  if (!token) {
    state.value = 'error'
    return
  }

  // 지난 대화를 먼저 채운다. (#170)
  //
  // 이게 없으면 새로고침할 때마다 채팅이 통째로 비어서 고장처럼 보인다.
  // 라이브 채팅은 들어온 시점부터 보는 게 표준이지만, 화면이 완전히 비는 것과
  // "방금까지 이런 얘기가 오갔다" 가 보이는 것은 다르다.
  //
  // ★ 연결보다 먼저 부르되 기다리지 않는다.
  //   서버가 느리면 실시간 채팅 연결이 그만큼 늦어진다. 지난 대화는 있으면
  //   좋은 것이고, 실시간은 없으면 못 쓰는 것이다. 급한 쪽을 안 막는다.
  fetchRecentChat(props.meetingId).then((past) => {
    // 그 사이 실시간 메시지가 먼저 왔을 수 있다. 지난 것을 앞에 붙인다.
    if (past.length) messages.value.unshift(...past)
  })

  client = createRealtimeClient({
    baseUrl: import.meta.env.VITE_API_ORIGIN || window.location.origin,
    token,
    meetingId: props.meetingId,
    onChat: (m) => push(messages, m),
    onCaption: (c) => push(captions, c),
    onState: (s) => { state.value = s },
  })
  client.connect()
})

onBeforeUnmount(() => client && client.disconnect())

function send() {
  const text = draft.value.trim()
  if (!text) return
  client.send(text)
  draft.value = ''
}
</script>

<template>
  <aside class="bchat">
    <header class="bchat__head">
      <h3>채팅</h3>
      <span class="bchat__state" :data-state="state">
        {{ state === 'connected' ? '연결됨' : state === 'error' ? '오류' : '연결 중…' }}
      </span>
    </header>

    <!--
      자막을 채팅과 나눠 보여 준다. 섞으면 자막이 채팅에 밀려 올라가는데,
      이 서비스에서 자막은 "놓치면 안 되는 것" 이다.
    -->
    <div v-if="captions.length" class="bchat__caption" aria-live="polite">
      {{ captions[captions.length - 1].text }}
    </div>

    <ol ref="listEl" class="bchat__list">
      <li v-for="(m, i) in messages" :key="i">
        <strong>{{ m.sender }}</strong><span>{{ m.content }}</span>
      </li>
      <li v-if="!messages.length" class="bchat__empty">아직 대화가 없습니다.</li>
    </ol>

    <form class="bchat__form" @submit.prevent="send">
      <input v-model="draft" maxlength="500" placeholder="메시지를 입력하세요"
             :disabled="state !== 'connected'" />
      <button type="submit" :disabled="state !== 'connected' || !draft.trim()">보내기</button>
    </form>
  </aside>
</template>

<style scoped>
.bchat { display: flex; flex-direction: column; gap: .5rem; min-height: 0; height: 100%; }
.bchat__head { display: flex; align-items: baseline; justify-content: space-between; }
.bchat__state { font-size: .85em; opacity: .7; }
.bchat__state[data-state="error"] { color: #b00020; }
.bchat__caption {
  background: #111; color: #fff; padding: .5rem .75rem; border-radius: .4rem;
  font-size: 1.05em; line-height: 1.4;
}
.bchat__list {
  list-style: none; margin: 0; padding: 0; flex: 1; overflow-y: auto;
  display: flex; flex-direction: column; gap: .3rem;
}
.bchat__list li { display: grid; grid-template-columns: auto 1fr; gap: .5rem; }
.bchat__empty { opacity: .6; }
.bchat__form { display: flex; gap: .4rem; }
.bchat__form input { flex: 1; padding: .45rem .6rem; }
</style>
