/**
 * 조건부 따라잡기 정책. (#233)
 *
 * <p>재생 속도를 올려 지연을 줄이는 손잡이(hls.js `maxLiveSyncPlaybackRate`)는 <b>정상망에서는
 * 지연을 절반으로 줄이지만 제한망에서는 끊김을 30~65% 늘린다</b>(그리드 16회차 측정).
 * 그래서 항상 켜는 대신 <b>지금 이 시청자가 감당할 수 있을 때만</b> 켠다.
 *
 * <pre>
 *   끔      9.0 / 10.8초 지연 · 끊김 0        제한망 끊김 219 / 204초
 *   1.1배   4.7 /  5.1초 지연 · 끊김 0        제한망 끊김 305 / 263초   ← 정상망 이득, 제한망 손해
 * </pre>
 *
 * <p>상수는 <b>일반 원리</b>에서 정했다 - 특정 측정 일정에 맞춘 값이 아니다.
 * 검증은 #233 제한망 일정(60~120초 3Mbps, 120~125초 끊김, 125~180초 3Mbps)으로 한다.
 *
 * <p><b>순수 함수다.</b> 시계(`now`)와 관측값을 인자로 받는다 - 시험이 조건을 하나씩 고정할 수 있다.
 * 상태(켜져 있는지·마지막 끊김·언제 껐는지)는 호출자가 들고 다니고, 이 함수는 다음 상태만 정한다.
 */

/**
 * 조용해야 하는 시간(ms). 이 안에 끊김이 있었으면 켜지 않는다.
 *
 * <p>끊긴 뒤의 망은 한동안 나쁜 채로 남는 경우가 많다 - 끊김 하나가 지나갔다고 곧바로
 * 재생 속도를 올리면, 아직 회복되지 않은 망 위에 부하를 더 얹어 다시 끊긴다.
 * 이 값은 그 "한동안" 을 기다리는 시간이다.
 */
export const CATCHUP_MIN_QUIET_MS = 30_000

/**
 * 앞쪽 버퍼 하한(초).
 *
 * <p>조각 하나가 기본 2초다(#198). 그보다 적으면 배속으로 당길 여유가 없다 -
 * 당기는 만큼 곧바로 바닥나 다시 끊긴다.
 */
export const CATCHUP_MIN_AHEAD_SEC = 2

/**
 * 대역 여유 배수.
 *
 * <p>재생 속도를 올리면 같은 시간에 더 많은 데이터가 필요하다 - 1.1배면 필요 대역도 1.1배다.
 * 그 위에 다음 조각을 미리 받을 여유가 있어야 하므로 1.5배를 문턱으로 둔다
 * (1.1배에 필요한 몫을 빼고도 여유가 남는다).
 */
export const CATCHUP_BANDWIDTH_RATIO = 1.5

/**
 * 껐다가 다시 켜기까지 최소 시간(ms).
 *
 * <p>켜짐/꺼짐이 반복되면 화면 속도가 계속 바뀌어 더 나빠 보인다. 짧은 끊김 하나가
 * 지나가길 기다리는 시간이다.
 */
export const CATCHUP_MIN_OFF_MS = 10_000

/** 따라잡기를 처음 켤 때의 배속. 1.25·1.5 는 제한망 끊김이 더 늘어(394·378초) 후보에서 뺐다. */
export const CATCHUP_ADAPTIVE_RATE = 1.1

/**
 * 다음에 켤지 정한다.
 *
 * @param {object} state  { enabled, lastStallAt, disabledAt } - 호출자가 들고 다니는 상태(ms, 벽시계)
 * @param {object} sample { now, aheadSec, bandwidthBps, levelBitrateBps }
 *   levelBitrateBps 가 null/0 이면 그 조건은 <b>건너뛴다</b> - 레벨 정보가 아직 없을 때
 *   대역 조건을 못 채워 영영 안 켜지는 것을 막는다(매니페스트 직후가 그렇다)
 * @returns {boolean} 다음 상태
 */
export function decideCatchup(state, sample) {
  const now = Number(state?.now ?? sample?.now)
  const quiet = !Number.isFinite(state?.lastStallAt) ||
    !Number.isFinite(now) ||
    now - state.lastStallAt >= CATCHUP_MIN_QUIET_MS

  const ahead = Number(sample?.aheadSec)
  const aheadOk = Number.isFinite(ahead) && ahead >= CATCHUP_MIN_AHEAD_SEC

  const levelBitrate = Number(sample?.levelBitrateBps)
  const bandwidth = Number(sample?.bandwidthBps)
  const bandwidthKnown = Number.isFinite(levelBitrate) && levelBitrate > 0
  const bandwidthOk = !bandwidthKnown ||
    (Number.isFinite(bandwidth) && bandwidth >= levelBitrate * CATCHUP_BANDWIDTH_RATIO)

  const conditions = quiet && aheadOk && bandwidthOk
  if (state?.enabled) {
    // 켜져 있으면 조건이 깨지는 순간 끈다 - 끊기거나 버퍼가 마르기 전에 내려놓는다.
    return conditions
  }

  // 꺼져 있다: 막 껐으면 잠시 그대로 둔다(깜빡임 방지).
  if (Number.isFinite(state?.disabledAt) && Number.isFinite(now) &&
      now - state.disabledAt < CATCHUP_MIN_OFF_MS) {
    return false
  }
  return conditions
}

/** 정책 상태를 만든다. 호출자가 이 객체를 들고 decideCatchup 을 부른다. */
export function createCatchupState(now = null) {
  return { enabled: false, lastStallAt: null, disabledAt: null, toggles: 0, enabledMs: 0, disabledMs: 0, now }
}
