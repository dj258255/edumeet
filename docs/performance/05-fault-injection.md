# 05. 장애 주입 — 타임아웃이 실제로 무엇을 막는가

> 이슈 [#19](https://github.com/dj258255/edumeet/issues/19)

## 순서를 지켰다

[#17](https://github.com/dj258255/edumeet/issues/17) 을 정리하면서 Toxiproxy 를
**의도적으로 뒤로 미뤘다.** 그때 EduMeet 에는 타임아웃도 폴백도 없었기 때문이다.

> **막을 장치가 없는 상태에서 장애를 주입하면 앱이 죽는 것을 증명할 뿐이다.**
> 그건 성과가 아니라 버그 리포트다.

그래서 `타임아웃·폴백 구현` → `장애 주입으로 검증` 순으로 진행했다.

## 무엇이 문제였나

`OpenviduService.getRoomInfo` 는 이랬다.

```java
public Map<String, Object> getRoomInfo(String roomName) {
    RestTemplate restTemplate = new RestTemplate();                  // (1)
    String url = "http://localhost:7880/api/v1/rooms/" + roomName;   // (2)
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);       // (3)
    ...
```

**(1) 타임아웃이 없다.** `new RestTemplate()` 의 `SimpleClientHttpRequestFactory` 는
connect/read timeout 이 둘 다 `-1`(무한)이다. 상대가 응답하지 않으면 요청 스레드가
영원히 잡히고, 톰캣 스레드 풀이 마르면 **그 외부 시스템과 무관한 요청까지 같이 죽는다.**

**(2) 주소가 하드코딩됐다.** 설정값 `openvidu.url` 을 무시한다.

**(3) 인증 방식이 틀렸다.** 여기서 더 큰 것이 나왔다.

## 타임아웃을 넣으려다 더 큰 버그를 찾았다

장애 주입 실험의 **정상 케이스**가 실패했다. 지연을 하나도 주지 않았는데 503 이 났다.
LiveKit 서버 로그를 봤다.

```
{"status": 401, "method": "GET", "path": "/api/v1/rooms/probe",
 "error": "invalid authorization header. Must start with Bearer "}
```

**이 메서드는 실제 LiveKit 서버에서 한 번도 동작한 적이 없다.**
주석에 `OpenVidu Admin API 사용` 이라고 적혀 있다. OpenVidu 에서 LiveKit 으로
옮기면서 이 메서드만 갱신되지 않았다. LiveKit 은

- 경로가 `/api/v1/rooms/{name}` 이 아니라 **Twirp RPC** (`/twirp/livekit.RoomService/ListRooms`)
- 인증이 Basic 이 아니라 **Bearer JWT**

프로토콜을 직접 맞추는 대신 **공식 SDK 의 `RoomServiceClient`** 로 바꿨다.
SDK 는 OkHttp 를 쓰는데 기본 타임아웃이 10초라, 같은 기준(연결 2초, 읽기 3초)으로 맞췄다.

> ### 왜 지금까지 아무도 몰랐나
>
> **타임아웃이 없을 때는 이 호출이 어떻게 실패하든 똑같이 500 이었다.**
> 인증 실패든 연결 실패든 타임아웃이든 구분이 없었다.
> 실패를 **분류**하기 시작하니까 "인증 실패" 가 드러났다.
>
> 관측 가능성의 실질은 로그를 늘리는 것이 아니라
> **실패를 구분해서 다르게 처리하는 것**이다.

## 실험 방법

```
앱 --> toxiproxy:7881 --[toxic]--> livekit:7880
```

실제 LiveKit 서버(`livekit/livekit-server --dev`)를 띄우고 그 앞에 Toxiproxy 를 둔다.
목(mock)이 아니라 **진짜 서버**여야 인증·프로토콜까지 검증된다.

대조군(`legacy=true`)은 **수정 전 코드**를 그대로 탄다. 타임아웃을 넣은 것이 실제로
무엇을 막고 있는지 보이려면 막지 않았을 때를 함께 재야 한다.

```bash
./scripts/run-fault-injection.sh
```

## 결과

| 상황 | 수정 후 | 수정 전 (대조군) |
|---|---|---|
| **정상** | `404 ROOM_NOT_FOUND` · 4 ms | `500 ERROR` · 38 ms ← **인증 실패. 애초에 동작 안 함** |
| **지연 10초** | `503` · **3.01 s** | `500` · **10.01 s** ← 상대가 늦는 만큼 그대로 기다림 |
| **연결 불가** | `503` · 11 ms | `500` · 7 ms |
| **무응답** | `503` · **3.01 s** | **30초에도 안 돌아옴** ← 클라이언트가 먼저 포기 |
| **복구** | `404 ROOM_NOT_FOUND` · 7 ms | — |

### 읽는 법

**지연 10초 케이스** — 수정 전은 10.01초를 다 기다렸다. 지연이 10분이면 10분을
기다린다. **상한이 없다.**

**무응답 케이스가 핵심이다.** 연결은 되지만 한 바이트도 오지 않는 상태다.
수정 후는 3초에 끊고 503 을 준다. 수정 전은 **30초가 지나도 돌아오지 않아
클라이언트가 먼저 포기했다.** 운영이라면 그 스레드는 계속 잡혀 있다.

**정상 케이스와 장애 케이스가 다른 상태 코드다.** 룸이 없는 것은 `404`,
LiveKit 에 닿지 못한 것은 `503` 이다. 이를 404 로 뭉뚱그리면 클라이언트는
"방이 삭제됐구나" 로 오해하고 운영에서는 장애가 보이지 않는다.

## 같이 손본 것

| 대상 | 문제 | 조치 |
|---|---|---|
| LiveKit | 타임아웃 무한 + API 불일치 | 공식 SDK + OkHttp 타임아웃 (연결 2s / 읽기 3s) |
| S3 | `apiCallTimeout` 미설정 → 재시도가 쌓이면 상한 없음 | `apiCallTimeout` 2분 |
| Redis | 타임아웃 미설정 | `timeout: 2s`, `connect-timeout: 1s` |
| 정원 초과 예외 | 핸들러가 없어 500 | `409 CONFLICT` 로 매핑 |

**S3 타임아웃을 왜 2분이나 잡았나** — `apiCallTimeout` 은 **전송 시간까지 포함**한다.
업로드 상한이 100MB 라 짧게 걸면 정상 업로드가 죽는다. 목표는 업로드를 빠르게
실패시키는 것이 아니라 **병적인 상황에 상한을 두는 것**이다.
멈춤(stall)은 SDK 기본 소켓 타임아웃(30초)이 이미 막는다.

## 하지 않은 것

**서킷 브레이커를 넣지 않았다.** 지금은 LiveKit 장애가 3초 만에 503 으로 끝난다.
서킷 브레이커는 *"실패가 반복될 때 아예 시도하지 않아 3초조차 아끼는"* 장치인데,
그 3초가 문제가 된다는 근거가 아직 없다. 필요해지면 그때 **측정 결과를 근거로** 넣는다.

## 한계

- 앱·LiveKit·Toxiproxy·k6 가 모두 같은 노트북에서 돌았다.
- 단일 요청 실험이다. **부하 상태에서 스레드 풀이 실제로 마르는지**까지는 재지 않았다.
  타임아웃이 상한을 만든다는 것은 보였지만, 그 상한이 스레드 풀을 지키기에
  충분한지는 별도 측정이 필요하다.
- LiveKit 은 `--dev` 모드다. 인증·프로토콜은 같지만 운영 설정과는 다르다.
