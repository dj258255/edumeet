# 방송 모드 세 개 — 무엇이 공통이고 무엇이 갈리는가

> 모드는 셋이고 **채팅은 셋 모두의 공통 기능**이다.
> 이 문서는 무엇을 공통으로 두고 무엇을 갈랐는지, 그리고 그 판단의 근거를 적는다.

```
INTERACTIVE       화상채팅        WebRTC        1초 미만
BROADCAST         라이브스트리밍   WebRTC / HLS  수 초
AUDIO_BROADCAST   오디오스트리밍   WebRTC / HLS  수 초
```

---

## 1. 공통으로 둔 것 — 채팅

채팅 코드에서 세션 타입으로 갈리는 곳은 **저장 정책 하나뿐**이다.

```java
// 접근 정책 — 모드 무관. 이 방에 참가했는가만 본다
MeetingParticipant.findActive(meetingId, email)

// 저장 정책 — 모드별
sessionType.persistsChatInline()
```

**이 분리가 핵심이다.** 섞으면 모드가 늘 때마다 접근 로직을 건드리게 된다.

`@EnumSource(SessionType.class)` 로 세 모드 전부에 대해 구독 → 발행 → 수신을 검증한다.
**모드를 추가하면 이 테스트가 그 모드에 대해서도 돈다.**

> 방송이 발행 경로에서 채팅을 저장하지 않는 이유는 저장을 안 한다는 뜻이 아니다.
> **발행 경로의 DB 쓰기가 브로드캐스트 측정을 가리기 때문**이고,
> 다시보기용 비동기 저장은 [#61](https://github.com/dj258255/edumeet/issues/61) 에서 다룬다.

---

## 2. 갈라야 했던 것 — 발행 권한

`CanPublish(true)` 만으로는 **"오디오 전용" 이 클라이언트 UI 관례로만 존재한다.**
토큰을 다른 SDK 에 넣으면 카메라가 그대로 올라간다.

```java
// 서명된 JWT 안에 들어가므로 SFU 가 강제한다. 클라이언트를 고쳐도 못 뚫는다
return this == AUDIO_BROADCAST ? List.of("microphone") : List.of();
```

### 비디오 세션에는 일부러 넣지 않았다

LiveKit 은 `canPublishSources` 가 없으면 모든 소스를 허용한다.
`INTERACTIVE` 에 `["camera","microphone","screen_share"]` 를 나열해도 **표현되는 정책이 없고**,
오늘의 소스 목록을 얼려서 **LiveKit 이 소스를 추가하면 화상 세션이 조용히 막히게** 만들 뿐이다.

**제한은 제한이 의도된 곳에만 건다.** 테스트가 "빠뜨린 것" 과 "일부러 안 넣은 것" 을 구분해 고정한다.

### 왜 서버가 막아야 하나

실측 egress 는 **시청자당 1.42 Mbps**, 비디오 기준이다.
오디오 전용의 비용 모델은 그 전제 위에 서 있으므로,
**전제를 서버가 지키지 않으면 비용 계산이 근거를 잃는다.**

---

## 3. ★ 우리 서버에서 무엇이 되는가 — 소스로 확인한 경계

LiveKit egress 소스를 읽고 대조했다. (**계획 문서에 적어 둔 3.0 은 낡은 값이었다. 현재 4다.**)

```go
// pkg/config/service.go
roomCompositeCpuCost      = 4
audioRoomCompositeCpuCost = 1

// pkg/stats/monitor.go
if r.RoomComposite.AudioOnly { costs.cpu = AudioRoomCompositeCpuCost }
else                         { costs.cpu = RoomCompositeCpuCost }

required := costs.cpu
accept   := available >= required      // 아니면 ErrNotEnoughCPU
```

**우리 OCI 서버는 2 OCPU 다.**

| | 필요 코어 | `2 >= 필요` | |
|---|---:|---|---|
| 비디오 방송 HLS | 4 | 거짓 | **거부된다** |
| 오디오 방송 HLS | 1 | 참 | **된다** |

**싼 게 아니라 파이프라인이 다르다.** 오디오 전용은 `ShouldUseSDKSource` 경로를 타서
**헤드리스 Chrome 합성을 통째로 건너뛴다** — Chromium · Xvfb · 화면 합성이 빠진다.

### 이 사실의 진짜 함의

시작 시점 검사(`validateCPUConfig`)는 **가장 싼 egress 타입**(`trackCpuCost = 0.5`)과만 비교한다.

> **egress 프로세스는 정상으로 뜬다. 비디오 방송 시작만 실패한다.**

헬스체크는 초록이고 로그도 조용한데 특정 기능만 안 되는, 원인 찾기 어려운 모양이다.
**그래서 이 숫자를 코드에 상수로 박고 테스트로 고정했다** — 문서에만 적으면 잊는다.

```java
assertThat(plan.fitsOn(2)).isFalse();   // 비디오 방송은 우리 서버에서 안 된다
```

---

## 4. HLS 요청의 함정 — 조용히 깨지는 것들

HLS 의 실패는 대부분 **요청에서 나고, 에러를 내지 않는다.**

| 실수 | 증상 |
|---|---|
| `live_playlist_name` 미지정 | 라이브인데 **VOD 플레이리스트**가 나간다. 방송 시작점부터 재생되고 플레이리스트가 무한 누적 |
| 두 플레이리스트가 다른 디렉터리 | LiveKit `ErrInvalidInput` |
| `segment_duration` 기본값 4초 | 지연이 **12초**부터 (세그먼트 × 플레이어 버퍼 3) |
| `forcePathStyle` 미설정 | R2 가 가상 호스트 주소를 거부해 업로드 실패 |

egress 인스턴스를 띄우려면 `--cap-add=SYS_ADMIN` · Chrome · Xvfb · Redis · 4코어가 필요하고
**이 노트북에도 우리 서버에도 없다.**

그래서 **요청 생성을 순수 계산으로 분리**했다.
실행해서 눈으로 확인할 수 없는 버그라면, 오히려 요청을 값으로 두고 고정해야 한다.

```
HlsEgressPlanner   요청을 만든다. 네트워크를 타지 않는다. 함정이 전부 여기 있다
HlsEgressPlan      요청 + LiveKit 이 매길 CPU 비용
HlsEgressService   부르고 기록한다
```

---

## 5. `INTERACTIVE` 는 HLS 로 내보내지 않는다

**못 해서가 아니라 하면 안 되기 때문이다.**
화상강의의 존재 이유는 1초 미만 지연인데 HLS 는 6초부터 출발한다.
손을 들고 6초 뒤에 지목받는 수업은 수업이 아니다.

---

## 6. 그런데 방송 세션을 만들 수가 없었다

여기까지 만들고 **"그럼 방송 세션은 어떻게 만들지?"** 를 따라가 보니,
`MeetingCreateRequestDto` 에 `sessionType` 이 없었다. **위의 전부가 도달 불가능한 코드였다.**

→ [`../ops/07-declared-but-unused.md`](../ops/07-declared-but-unused.md)

---

## 아직 안 한 것

- [ ] **실제로 재생되는가, 지연이 몇 초인가** — egress 인스턴스를 붙여야 잰다
- [ ] `segment_duration` 4 → 2 트레이드오프 실측 (요청 수 2배, 키프레임 증가로 비트레이트 상승)
- [ ] 라이브 창 5 vs Apple 스펙 8.11(6) 위반 검증
- [ ] 자막 vs 채팅 부하 특성 비교
- [ ] 다시보기 채팅 비동기 저장 ([#61](https://github.com/dj258255/edumeet/issues/61))
