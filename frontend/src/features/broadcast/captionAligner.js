/**
 * 자막을 화면 시각에 맞춰 내보낸다. (#185)
 *
 * ── 무엇이 문제였나 ──────────────────────────────────────────────
 *
 *   자막은 WebSocket 으로 온다. 1초 안에 도착한다.
 *   영상은 HLS 로 온다. 세그먼트가 2초이고 플레이어가 라이브 엣지에서
 *   2세그먼트 뒤를 따라가므로 6초 남짓 뒤에 보인다.
 *
 *   그래서 **자막이 화면보다 먼저 뜬다.** 말하는 입 모양과 글자가 어긋나고,
 *   화면에서 아직 안 일어난 일이 글자로 먼저 나온다.
 *
 *   업계에서는 이걸 chat-to-stream sync 라고 부른다. 스포츠 중계에서
 *   "채팅이 중계보다 먼저 결과를 알려 주는" 문제와 같은 것이다.
 *
 *   ★ 이 서비스에서는 더 나쁘다. 청각장애 학습자에게 자막은 곁들이가 아니라
 *     내용 그 자체다. 그 내용이 화면보다 6초 앞서 간다.
 *
 * ── 어떻게 맞추나 ────────────────────────────────────────────────
 *
 *   자막에는 말한 시각(spokenAt)이 실려 온다.
 *   플레이어는 지금 보여 주는 화면의 시각을 안다 —
 *   HLS 매니페스트에 EXT-X-PROGRAM-DATE-TIME 이 있으면 hls.js 가 playingDate 로 준다.
 *
 *   그래서 규칙은 한 줄이다.
 *
 *       화면 시각이 말한 시각을 지나갈 때까지 자막을 붙잡는다.
 *
 * ── 붙잡기만 하면 안 되는 이유 ──────────────────────────────────
 *
 *   ★ 화면 시각을 모를 수 있다. Safari 의 기본 재생이나, 매니페스트에
 *     그 태그가 없는 경우다. 그때 붙잡으면 **자막이 영영 안 나온다.**
 *     어긋난 자막보다 없는 자막이 훨씬 나쁘다 - 그냥 바로 내보낸다.
 *
 *   ★ 시각이 이상할 수도 있다. 기기 시계가 틀어져 있으면 화면 시각이
 *     영원히 말한 시각을 못 따라잡는다. 그래서 **상한**을 둔다.
 *     상한을 넘긴 자막은 어긋난 채로라도 내보낸다.
 *
 *   ★ 복구본(replay)은 여기 오지 않는다. 지나간 구간을 채우는 것이라
 *     실시간 줄이 아니라 전사 영역에 붙는다. (#165)
 */

/** 화면 시각을 못 구할 때 이만큼 기다렸으면 그냥 내보낸다. */
export const MAX_HOLD_MS = 12_000

/**
 * @param {() => (Date|null)} getPlayerDate 지금 화면에 보이는 장면의 시각
 * @param {object} [opts]
 * @param {number} [opts.maxHoldMs] 붙잡아 둘 수 있는 최대 시간
 * @param {() => number} [opts.now] 시험에서 시간을 고정하려고 넣는다
 */
export function createCaptionAligner(getPlayerDate, opts = {}) {
  const maxHoldMs = opts.maxHoldMs ?? MAX_HOLD_MS
  const now = opts.now ?? (() => Date.now())

  /** 아직 못 내보낸 자막. 들어온 순서를 지킨다. */
  const pending = []

  /** 마지막으로 잰 어긋남(ms). 양수면 자막이 화면보다 앞서 있었다. */
  let lastLeadMs = null
  let releasedAligned = 0
  let releasedUnaligned = 0

  return {
    /** 새 자막을 받는다. 바로 나가지 않을 수 있다. */
    push(caption) {
      pending.push({ caption, queuedAt: now() })
    },

    /**
     * 지금 내보낼 자막을 꺼낸다. 없으면 빈 배열이다.
     *
     * <p>순서를 지킨다 - 앞의 것이 아직 때가 아니면 뒤의 것도 안 내보낸다.
     * 전사는 순서가 곧 내용이라 뒤집히면 뜻이 달라진다.
     */
    tick() {
      const t = now()
      const playerDate = getPlayerDate()
      const playerMs = playerDate instanceof Date && !Number.isNaN(playerDate.getTime())
        ? playerDate.getTime()
        : null

      const out = []
      while (pending.length) {
        const head = pending[0]
        const spokenAt = Number(head.caption?.spokenAt)
        const held = t - head.queuedAt

        let release
        if (playerMs === null || !Number.isFinite(spokenAt)) {
          // 화면 시각을 모른다. 맞출 방법이 없으니 붙잡지 않는다.
          release = true
          if (playerMs === null) releasedUnaligned += 1
        } else if (playerMs >= spokenAt) {
          release = true
          lastLeadMs = 0
          releasedAligned += 1
        } else if (held >= maxHoldMs) {
          // 못 따라잡고 있다. 어긋난 채로라도 내보낸다.
          release = true
          lastLeadMs = spokenAt - playerMs
          releasedUnaligned += 1
        } else {
          lastLeadMs = spokenAt - playerMs
          release = false
        }

        if (!release) break
        out.push(pending.shift().caption)
      }
      return out
    },

    /** 지금 붙잡고 있는 개수. 화면에 "곧 나옴" 을 보여 줄 때 쓴다. */
    get pendingCount() {
      return pending.length
    },

    /**
     * 자막이 화면보다 얼마나 앞서 있는가(ms).
     *
     * <p>이 값이 이 작업의 측정 대상이다. 고치기 전에는 곧 화면 지연 그대로이고,
     * 고친 뒤에는 0 이어야 한다.
     */
    get leadMs() {
      return lastLeadMs
    },

    /** 맞춰서 내보낸 것과 못 맞추고 내보낸 것. 둘을 갈라야 "되고 있다" 를 안다. */
    get stats() {
      return { aligned: releasedAligned, unaligned: releasedUnaligned };
    },
  }
}
