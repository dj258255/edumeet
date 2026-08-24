# 선언은 있는데 아무도 안 쓴다 — 네 번

> **이 저장소에서 같은 모양의 버그를 네 번 만났다.**
> 넷 다 코드에 선언이 있었고, 넷 다 테스트를 통과하고 있었고,
> 넷 다 **아무 효과가 없었다.**

---

## 네 번

| # | 무엇 | 어떻게 드러났나 |
|---|---|---|
| 1 | `management.endpoint.health.probes.enabled` | 배포 헬스체크가 계속 unhealthy. 켜져 있는데 그룹을 안 만들었다 |
| 2 | `management.prometheus.metrics.export.enabled` | `/actuator/prometheus` 404. **원인을 세 번 잘못 짚었다** |
| 3 | `SessionType.isAudioOnly()` | 참조가 테스트뿐이었다. "오디오 전용" 이 클라이언트 UI 관례로만 존재 |
| 4 | `MeetingCreateRequestDto` 에 `sessionType` 부재 | **기능 네 개가 통째로 도달 불가** |
| 5 | `docker compose up -d` 에 `--profile observability` 부재 | **관측 스택이 한 번도 켜진 적 없음** |
| 6 | 파이썬이 `X-Internal-Token` 미전송 + 경로 변수 미치환 | **AI 요약본이 서비스에 도달한 적 없음** |
| 7 | 프론트에 STOMP 클라이언트 부재 | **AI 자막이 화면에 뜬 적 없음** |

---

## 4번이 제일 컸다

`sessionType` 을 요청에서 받지 않으니 `Meeting.builder()` 도 그것을 넣지 않았고,
**API 로 만든 모든 세션이 필드 기본값 `INTERACTIVE`** 였다.

```
#43  세션 타입별 정원·발행 정책    도달 불가
#65  오디오 방송 + 자막            도달 불가
#72  오디오 전용 토큰 강제         도달 불가
#75  HLS 송출                      절대 실행 불가 ← INTERACTIVE 를 거부하는데 그것뿐이었다
```

**HLS 는 특히 고약하다.** `INTERACTIVE` 를 명시적으로 거부하도록 짜 두었는데,
만들 수 있는 세션이 `INTERACTIVE` 뿐이었으므로 **이 코드는 어떤 입력으로도 성공할 수 없었다.**
그런데 단위 테스트는 전부 통과했다 — `BROADCAST` 세션을 **테스트 안에서 직접 만들어** 검증했기 때문이다.

---

## 5번은 종류가 다르다

앞의 넷은 **코드 안**이라 테스트로 잡을 수 있는 것이었다. 5번은 **배포 스크립트**다.

```yaml
prometheus:
  profiles: [observability]     # 프로필이 켜져야만 뜬다
```

```bash
docker compose -f docker-compose.prod.yml up -d     # 배포가 하던 것
```

**Compose 는 프로필이 꺼진 서비스를 조용히 건너뛴다.** 에러도 경고도 없다.
`docker ps` 를 보지 않는 한 알 방법이 없고, 배포는 초록으로 끝난다.

그래서 배포 스크립트에 **"떴는지 확인하는 줄"** 을 넣었다.
프로필을 붙였다는 사실만으로는 떴는지 모른다 — 이건 코드에서 `assertThat` 을 쓰는 것과 같은 이유다.

```bash
for c in edumeet-prometheus edumeet-grafana; do
  if [ -z "$(docker ps -q -f name=^${c}$)" ]; then exit 1; fi
done
```

## 6번은 서비스 경계에 있었다

앞의 다섯은 **한 서비스 안**이라 그 서비스의 테스트로 잡을 수 있는 것이었다.
6번은 다르다 — **양쪽 다 자기 쪽 코드는 맞다.**

```
Java    토큰 필터도 맞고, 없으면 401 을 내는 테스트도 있다
파이썬  업로드 함수도 그 자체로는 동작한다
```

**맞춰 본 적이 없을 뿐이다.** 그래서 어느 쪽 단위 테스트로도 안 잡힌다.

그리고 이건 문서가 **알고 있었다.**
`03-internal-api-contract.md` 에 "파이썬이 바꿔야 하는 것" 표가 있었고
마지막 줄이 *"파이썬 저장소가 이 리포에 없어서 클라이언트 쪽 변경은 미반영이다"* 였다.

> **몰라서가 아니라 고칠 수 없는 위치에 있었다.**
> 그리고 **문서는 CI 를 실패시키지 못한다.**

계약을 `contracts/internal-api.json` 으로 옮기고 양쪽 테스트가 그 파일을 읽게 했다.
**손으로 옮겨 적으면 의미가 없다** — 한쪽이 바뀌어도 반대쪽 테스트는 초록으로 남는다.

## 7번은 제품의 존재 이유였다

이 서비스는 **청각장애 학습자를 위한 실시간 자막**이 존재 이유다.
그 경로가 이랬다.

```
파이썬 AI   자막 전송 코드 없음
백엔드      내부 API -> STOMP /topic/rooms/{id}/captions   O  (#65, 지연까지 측정)
프론트      STOMP 클라이언트 없음. LiveKit 데이터채널만 들었다
```

프론트는 **참가자 브라우저끼리** 자막을 주고받고 있었다. 서버가 관여하지 않으므로
**AI 가 만든 자막이 화면에 올 길이 없다.**

#65 에서 백엔드 자막 경로를 만들고 세 개의 타임스탬프로 홉 비용까지 쟀는데,
**받는 쪽이 없었다.** 측정은 정확했고 그 측정이 아무 데도 쓰이지 않았다.

> 앞의 여섯은 기능 하나가 죽어 있었다. 7번은 **제품이 하겠다고 말한 것**이 죽어 있었다.

## 왜 테스트가 못 잡았나

넷의 공통점이 여기 있다.

> **테스트가 부품을 검증했지, 부품이 연결되어 있는지는 검증하지 않았다.**

```
✓  SessionType.AUDIO_BROADCAST.isAudioOnly() == true      부품은 맞다
✗  실제 발급된 토큰에 그 정책이 반영되는가                아무도 안 물었다

✓  BROADCAST 세션의 HLS 요청이 올바르게 만들어지는가      부품은 맞다
✗  BROADCAST 세션을 만들 수 있는가                        아무도 안 물었다
```

테스트가 픽스처를 **직접 만들면** 그 픽스처가 실제로 만들어질 수 있는지는 영원히 안 물어보게 된다.

### 그래서 바꾼 것

**"의도" 가 아니라 "실제로 나간 것" 을 본다.**

| 전 | 후 |
|---|---|
| `service.canPublish == false` 확인 | **발급된 JWT 를 열어 `video.canPublishSources` 를 본다** |
| 테스트가 `BROADCAST` 엔티티를 만듦 | **생성 API 로 만들고 저장된 타입을 확인** |

그리고 `@EnumSource(SessionType.class)` 를 썼다.
**모드를 추가하면 그 모드에 대해서도 테스트가 돈다.** 새 모드가 죽어 있는 것을 잊고 넘어갈 수 없다.

---

## 곁다리로 나온 것 — 실패 지점이 원인에서 20줄 떨어져 있었다

4번을 고치려고 `create()` 를 부르자 `LazyInitializationException` 이 났다.
`@Transactional` 이 없고 `open-in-view: false` 였다.

```java
ClassRoom classRoom = classRepository.findById(...)   // 세션이 여기서 닫힌다
boolean isCreator = classRoom.getMember().getId()...  // ← 통과한다
.email(classRoom.getMember().getEmail())              // ← 20줄 뒤, 여기서 터진다
```

**`getId()` 는 프록시가 DB 를 타지 않고 답한다.** 식별자는 프록시가 이미 갖고 있다.
초기화가 필요한 첫 필드는 응답을 만들 때 나오는 `email` 이다.

그리고 `meetingRepository.save()` 는 `SimpleJpaRepository.save` 라 **자기 트랜잭션으로 먼저 커밋된다.**

> **DB 에는 세션이 생기는데 클라이언트는 500 을 받는다.**
> 아무도 존재를 모르는 세션이 쌓인다.

---

## 남은 질문

**이 패턴을 다음에는 어떻게 먼저 잡나.**

지금 답은 "통합 지점을 테스트가 지나가게 한다" 정도다.
정적으로 잡을 방법(예: `@Component` 인데 참조가 테스트뿐인 public 메서드 검출)은 아직 안 만들었다.
만든다면 그때 이 문서에 이어 적는다.
