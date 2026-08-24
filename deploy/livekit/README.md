# LiveKit + Egress — 오디오 방송 HLS

## 왜 이제야 붙이나

`docs/plan/02-hls-optional.md` 에 **"오디오 방송 HLS 는 2 OCPU 에서 가능"** 이라고
적어 두고 실제로 돌린 적이 없었다. LiveKit egress 소스를 읽고 비용표를 옮겨 적은 것이
전부였다. **읽은 것과 돌린 것은 다르다.**

## CPU 비용 — 소스를 다시 읽었다

앞서 적어 둔 인용이 **낡아 있었다.** 그때는 분기가 둘이었는데 지금은 셋이다.

```go
// pkg/stats/monitor.go — costsForRequest()
case *rpc.StartEgressRequest_RoomComposite:
    costs.isWeb = !config.ShouldUseSDKSource(r.RoomComposite)
    if r.RoomComposite.AudioOnly {
        if costs.isWeb {
            costs.cpu = m.cpuCostConfig.AudioRoomCompositeCpuCost        // 1
        } else {
            costs.cpu = m.cpuCostConfig.SDKAudioRoomCompositeCpuCost     // 1 (기본값이 위와 같다)
            costs.memory = m.cpuCostConfig.SDKAudioRoomCompositeMemoryCost
        }
    } else {
        costs.cpu = m.cpuCostConfig.RoomCompositeCpuCost                 // 4
    }
```

결론은 안 바뀐다 — **오디오 1, 비디오 4.** 하지만 두 가지가 새로 보였다.

### 1. `layout` 이 빈 문자열이어야 SDK 경로가 유지된다

```go
// pkg/config/pipeline.go
func ShouldUseSDKSource(req) bool {
    return req.GetAudioOnly() && req.GetLayout() == "" && req.GetCustomBaseUrl() == ""
}
```

**오디오 전용인 것만으로는 부족하다.** 레이아웃이나 커스텀 URL 을 주는 순간
헤드리스 Chrome 경로로 넘어간다. 우리 코드의 `""` 두 개는 **자리를 채우는 값이 아니라
파이프라인을 고르는 값이다.**

### 2. 가용 CPU 는 코어 수가 아니다

`MaxCpuUtilization` 기본값이 **0.8** 이라, 2코어에서 출발점이 2 가 아니라 **1.6** 이다.
여기서 다른 컨테이너가 쓰는 만큼 더 빠진다. 오디오(1)는 통과하지만
**여유가 0.6 밖에 없다** — 배경 부하가 있으면 이것도 거부될 수 있다.

## 구성

```
브라우저/lk ──ws:7880──▶ livekit-server ──┐
                                          ├── redis (이미 있는 것을 공유)
                        egress ───────────┘
                          └─▶ /out/hls/meeting-{id}/  세그먼트 + 플레이리스트
```

- **시크릿을 파일에 두지 않는다.** `LIVEKIT_KEYS` · `EGRESS_CONFIG_BODY` 로 넘긴다
- **SELinux 때문에 볼륨에 `:z` 를 붙인다.** Enforcing 이라 라벨이 없으면 컨테이너가 못 쓴다
- egress 는 **root 로 안 돈다.** 출력 디렉터리에 다른 사용자 쓰기 권한이 필요하다

## 켜기

```bash
docker compose -f docker-compose.prod.yml --profile livekit up -d
```

`HLS_ENABLED=true` 를 같이 켜야 앱이 egress 를 부른다.
**egress 없이 켜면 방송 시작이 `no response from egress service` 로 실패한다.**
