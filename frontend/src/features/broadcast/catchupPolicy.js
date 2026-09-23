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
 * <p><b>v2 (#233c)</b> 에서 두 가지를 고쳤다.
 * <ol>
 *   <li>우리 방송은 master 플레이리스트가 없어 hls.js 가 레벨 bitrate 를 모른다. 그때 대역 조건을
 *       <b>건너뛰고 있었으므로</b> 제한망에서도 버퍼만 보고 켰다. 이제는 조각 크기로 추정하고,
 *       <b>추정도 없으면 켜지 않는다</b>(모르면 보수적으로).</li>
 *   <li>켜면 버퍼를 당겨 곧 2초 아래로 내려가 꺼지고, 회복하면 다시 켜지는 <b>깜빡임</b>이 있었다
 *       (정상망 시청자당 3분에 약 21회). 켜는 문턱과 끄는 문턱을 다르게 둔다(히스테리시스).</li>
 * </ol>
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
 * 켜는 쪽 버퍼 문턱(초).
 *
 * <p>당기기 시작하려면 조각 하나를 받는 동안 버틸 만큼의 여유가 있어야 한다.
 * 조각 하나가 기본 2초(#198)이므로 그보다 조금 위에 둔다.
 */
export const CATCHUP_ENABLE_AHEAD_SEC = 2.5

/**
 * 끄는 쪽 버퍼 문턱(초). 켜는 문턱보다 <b>낮다</b> - 그 사이 구간이 히스테리시스다.
 *
 * <p>hls.js 의 latency-controller 는 앞쪽 버퍼가 1초 이하이면 재생 속도를 올리지 않는다
 * (1.5.20 `dist/hls.mjs` 4926·4939행). 즉 그 아래에서는 <b>켜져 있어도 아무 일이 일어나지 않고</b>,
 * 다시 1초를 넘기는 순간부터 당긴다. 그래서 끄는 문턱을 그 경계에 맞춘다 -
 * 여기서 내려놓으면 hls.js 가 스스로 멈추는 지점과 겹치지 않아 의미 없는 켜짐만 사라진다.
 */
export const CATCHUP_DISABLE_AHEAD_SEC = 1.0

/**
 * 켜는 쪽 대역 여유 배수.
 *
 * <p>재생 속도를 올리면 같은 시간에 더 많은 데이터가 필요하다 - 1.1배면 필요 대역도 1.1배다.
 * 그 위에 다음 조각을 미리 받을 여유가 있어야 한다(1.5배면 1.1배를 빼고도 0.4배가 남는다).
 */
export const CATCHUP_ENABLE_BANDWIDTH_RATIO = 1.5

/**
 * 끄는 쪽 대역 여유 배수. 켜는 문턱보다 <b>낮다</b>.
 *
 * <p>대역 추정은 조각마다 흔들린다. 켜는 문턱과 같은 값으로 끄면 그 흔들림마다 켜짐/꺼짐이
 * 반복된다. 1.2배는 "여유가 거의 사라진" 상태이고, 1.1배 재생에 필요한 몫은 아직 남아 있다.
 */
export const CATCHUP_DISABLE_BANDWIDTH_RATIO = 1.2

/**
 * 껐다가 다시 켜기까지 최소 시간(ms).
 *
 * <p>켜짐/꺼짐이 반복되면 화면 속도가 계속 바뀌어 더 나빠 보인다. 짧은 끊김 하나가
 * 지나가길 기다리는 시간이다.
 */
export const CATCHUP_MIN_OFF_MS = 10_000

/** 조각 크기로 bitrate 를 추정할 때 보는 최근 조각 수. 하나는 너무 흔들리고, 다섯이면 중앙값이 선다. */
export const CATCHUP_FRAGMENT_SAMPLES = 5

/** 추정에 필요한 최소 조각 수. 이보다 적으면 추정하지 않는다 - 두 조각의 중앙값은 평균과 같다. */
export const CATCHUP_MIN_FRAGMENTS = 3

/** 따라잡기를 처음 켤 때의 배속. 1.25·1.5 는 제한망 끊김이 더 늘어(394·378초) 후보에서 뺐다. */
export const CATCHUP_ADAPTIVE_RATE = 1.1

/**
 * 다음에 켤지 정한다.
 *
 * <p>켜져 있을 때와 꺼져 있을 때의 문턱이 다르다(히스테리시스). 켤 때는 버퍼 2.5초·대역 1.5배,
 * 유지할 때는 버퍼 1.0초·대역 1.2배를 본다.
 *
 * @param {object} state  { enabled, lastStallAt, disabledAt } - 호출자가 들고 다니는 상태(ms, 벽시계)
 * @param {object} sample { now, aheadSec, bandwidthBps, streamBitrateBps }
 *   streamBitrateBps 는 레벨 bitrate 또는 조각에서 추정한 값이다. <b>null 이면 켜지 않는다</b> -
 *   모르는 채로 켜면 제한망에서 끊김만 늘어난다(#233c). 켜져 있는 중에 모르게 되면 끈다.
 * @returns {boolean} 다음 상태
 */
export function decideCatchup(state, sample) {
  const now = Number(state?.now ?? sample?.now)
  const quiet = !Number.isFinite(state?.lastStallAt) ||
    !Number.isFinite(now) ||
    now - state.lastStallAt >= CATCHUP_MIN_QUIET_MS

  const ahead = Number(sample?.aheadSec)
  const bandwidth = Number(sample?.bandwidthBps)
  const bitrate = Number(sample?.streamBitrateBps)
  const bitrateKnown = Number.isFinite(bitrate) && bitrate > 0

  if (state?.enabled) {
    // 켜져 있다: 유지 문턱(낮은 쪽)으로 본다. 조건이 깨지는 순간 끈다.
    const aheadHold = Number.isFinite(ahead) && ahead >= CATCHUP_DISABLE_AHEAD_SEC
    const bandwidthHold = bitrateKnown &&
      Number.isFinite(bandwidth) && bandwidth >= bitrate * CATCHUP_DISABLE_BANDWIDTH_RATIO
    return quiet && aheadHold && bandwidthHold
  }

  // 꺼져 있다: 막 껐으면 잠시 그대로 둔다(깜빡임 방지).
  if (Number.isFinite(state?.disabledAt) && Number.isFinite(now) &&
      now - state.disabledAt < CATCHUP_MIN_OFF_MS) {
    return false
  }

  // 켜는 문턱(높은 쪽). 추정이 없으면 여기서 걸린다 - 모르면 켜지 않는다.
  const aheadEnable = Number.isFinite(ahead) && ahead >= CATCHUP_ENABLE_AHEAD_SEC
  const bandwidthEnable = bitrateKnown &&
    Number.isFinite(bandwidth) && bandwidth >= bitrate * CATCHUP_ENABLE_BANDWIDTH_RATIO
  return quiet && aheadEnable && bandwidthEnable
}

/** 정책 상태를 만든다. 호출자가 이 객체를 들고 decideCatchup 을 부른다. */
export function createCatchupState(now = null) {
  return { enabled: false, lastStallAt: null, disabledAt: null, toggles: 0, enabledMs: 0, disabledMs: 0, now }
}
