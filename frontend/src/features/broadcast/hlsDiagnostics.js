/**
 * hls.js 진단 로그. (#210)
 *
 * ★ 왜 필요한가.
 *   지금은 hls.js 오류의 <b>개수</b>만 센다(`state.errors`). 종류·시점·앱이 한 조치를 남기지 않아
 *   "왜 플레이어가 죽었는지" 를 사후에 볼 수 없다. 35초 offline 뒤 전원이 죽는 것을 재현해 두고도
 *   원인을 못 짚는다.
 *
 * ★ 왜 고리 버퍼인가.
 *   오류는 드물지만 한 번 터지면 연달아 온다. 전부 쌓으면 방송이 길어질수록 메모리가 계속 는다.
 *   최근 몇 건만 남기면 "터지기 직전" 이 항상 들어 있고 크기도 고정이다.
 */

/** 남기는 최근 건수. 50건이면 수 KB 다 - 운영에서도 켜 둘 수 있는 크기다. */
export const HLS_LOG_LIMIT = 50

export function createRingLog(limit = HLS_LOG_LIMIT) {
  const size = Math.max(1, Math.trunc(limit))
  const entries = []

  return {
    /** 최근 size 건만 남긴다. 넘치면 가장 오래된 것부터 버린다. */
    push(entry) {
      entries.push(entry)
      while (entries.length > size) entries.shift()
      return entry
    },

    /** 지금까지의 사본. 오래된 것이 앞이다. */
    snapshot() {
      return entries.slice()
    },
  }
}
