/**
 * 상태 API 응답에서 지금 붙을 수 있는 라이브 플레이리스트만 고른다. (#241)
 *
 * 방송이 끝나도 hlsPlaylistUrl 은 "이 회의는 방송이었다"는 기록으로 남는다.
 * 주소가 있다고 재생을 시도하면 디스크에 남은 마지막 HLS 조각을 틀고 멈출 수 있으므로,
 * 라이브 연결 여부는 broadcasting 으로 판단한다.
 */
export function livePlaylistUrl(status) {
  if (status?.broadcasting !== true) return null
  return status.hlsPlaylistUrl || null
}

/**
 * 재생 중인 방송의 종료를 판정한다. (#241)
 *
 * 배포 슬롯 전환이나 발표자 재시작(#229) 동안에는 잠깐 broadcasting=false 가 보일 수 있다.
 * 그래서 30초 동안 계속 false 일 때만 끝난 것으로 본다. 조회 실패·옛 서버 응답은 증거가
 * 아니므로 연속 구간을 끊는다. 시계를 주입해 UI 타이머 없이 시험한다.
 */
export function createEndWatcher({ now = () => Date.now(), graceMs = 30_000 } = {}) {
  let falseSince = null
  let ended = false

  return {
    /** 이번 상태가 종료를 확정하면 true를 돌려준다. 확정 뒤에는 계속 true다. */
    observe(status) {
      if (ended) return true
      if (status?.broadcasting === true) {
        falseSince = null
        return false
      }
      if (status?.broadcasting !== false) {
        falseSince = null
        return false
      }

      const checkedAt = now()
      if (falseSince === null) {
        falseSince = checkedAt
        return false
      }
      if (checkedAt - falseSince >= graceMs) {
        ended = true
      }
      return ended
    },
  }
}
