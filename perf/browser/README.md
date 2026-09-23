# 시청 품질 대조 하네스 (#197)

**운영 사이트에서** 헤드리스 브라우저가 시청 화면을 열고, 네트워크를 조여 끊김을
일부러 만든 뒤, 세 지점의 끊김을 한 표에서 맞춘다.

```
① 정답     브라우저 안에서 앱과 독립적으로 센 끊김   lib/truth.js
② 보낸 값  앱이 서버로 보낸 보고 본문               qoe-crosscheck.mjs
③ 받은 값  서버가 기록한 값 (Loki 로그 · Prometheus 합계)  server-side.mjs
```

`①≠②` 면 앱의 계측이 틀렸고, `②≠③` 이면 전송·검증에서 빠졌다.

## 준비물

- Node 20+, ffmpeg, `ssh` 로 닿는 운영 서버
- `~/.edumeet-perf.env` (권한 600)

  ```
  EDUMEET_TOKEN=...          # 측정 계정 access token (1시간 유효)
  EDUMEET_MEETING_ID=...     # 그 계정이 호스트인 BROADCAST 회의
  ```

선택: `SITE`, `API`, `SSH_HOST`, `DOCKER_NET`.
기본값은 `lib/env.mjs` 에 있다. **토큰은 로그·산출물에 절대 찍지 않는다.**

```bash
cd perf/browser
npm install
npx playwright install chromium
```

## 원격 시청자 부하

노트북의 브라우저 부하가 커서 시청자를 다른 호스트에서 만들 때는
`VIEWER_HOST`를 준다. 예를 들어 `byeolchi-oci`는 같은 리전의 ARM64 호스트다.

```bash
VIEWER_HOST=byeolchi-oci ./scripts/run-qoe-crosscheck.sh
```

이 모드에서도 방송 합성·운영 서버 수집·대조는 로컬에서 한다. 시청자 하네스만
`mcr.microsoft.com/playwright:v1.63.0-noble` 공식 이미지의 Docker 컨테이너에서 실행하고,
이미지 버전은 `perf/browser/package.json`의 Playwright 버전에서 자동으로 맞춘다.
원격에는 `perf/browser/`를 `rsync`하고, 측정 설정은 `~/.edumeet-perf.env`를 stdin으로
`~/edumeet-perf/.perf.env`에 보낸다. 측정이 끝나면 이 파일과
`edumeet-perf-viewers` 컨테이너만 trap에서 지우며 `~/edumeet-perf` 캐시는 남긴다.

`perf/browser/package.json`에는 잠금 파일이 없으므로 원격 컨테이너는
`npm install --no-audit --no-fund`를 사용한다(`npm ci`를 사용하지 않는다).
원격 호스트의 다른 컨테이너는 건드리지 않으며, 측정 직전·직후 `docker stats --no-stream`
결과를 `out/<run>/viewer-host-cpu.txt`에 남긴다.

기존의 `SCHEDULE`과 진단용 `FORCE_PATH=native`도 원격 시청자 컨테이너에 그대로 전달된다.

## 원격 합성 방송

합성 방송의 ffmpeg 부하를 측정 노트북에서 분리할 때는 `BROADCAST_HOST`를 준다.
`VIEWER_HOST`와 같은 호스트를 써도 된다.

```bash
BROADCAST_HOST=byeolchi-oci ./scripts/run-qoe-crosscheck.sh
```

방송 시작 전에 `perf/browser/`를 `~/edumeet-perf-bcast/`에 동기화하고,
`~/.edumeet-perf.env`는 stdin으로만 전달한다. 원격에는
`node:22-bookworm-slim`에 ffmpeg를 설치한 작은 이미지
`edumeet-perf-bcast`를 처음 한 번만 Docker build하며, 이후 회차는 그 이미지를
재사용한다. 합성 방송 컨테이너의 `broadcast.json`과 `broadcast.log`는 회차가
끝날 때 로컬 `out/<run>/`으로 가져온다. 종료 trap은 원격 컨테이너에
SIGINT를 보내 Node가 DELETE를 끝내기를 기다리고, 그 제어가 실패하면 로컬
`--stop-only`로 DELETE를 한 번 더 보장한 뒤 컨테이너를 정리한다.

## 실행

```bash
./scripts/run-qoe-crosscheck.sh
```

`RUN`(이름) · `VIEWERS`(기본 5) 로 바꿀 수 있다.

**송출 비트레이트도 회차 조건이다.** `BITRATE_K`(kbps)를 주면 합성 방송 인코더가 그 값으로 송출한다
(`-b:v`). 비우면 `broadcast-synthetic.mjs` 기본 2500 을 쓴다. 끊김 축을 재는 회차에서 쓴다 (`#199`).

```bash
BITRATE_K=1200 RUN=c199-1200 ./scripts/run-qoe-crosscheck.sh
```

요청값은 `broadcast.json` 의 `bitrateK` 로 남는다. `compare.mjs` 의 "비트레이트" 절이
**실제 조각 크기로 본 값**(조각 content-length ÷ 매니페스트 EXTINF 평균)을 요청과 나란히 적는다.

★ **비교 기준이 다르다.** `BITRATE_K` 는 **영상** 비트레이트(`-b:v`)이고, 합성 방송은 오디오를
따로 `-b:a 96k` 로 넣는다. 실측은 **조각 전체**(영상+오디오+컨테이너)다. 그래서 기준을
`영상 + 오디오 96` 으로 잡고, 컨테이너 오버헤드 때문에 실측이 조금 큰 것은 정상으로 적는다.
실측이 기준의 **50% 미만이거나 150% 초과**면 경고한다(반올림 전 값으로 판정 - 정확히 50%·150% 는 경고하지 않는다).
인코더가 조건을 안 따랐으면 그 회차는 그 조건의 측정이 아니다.
방송은 기본 `BROADCAST_DURATION_S=86400`의 안전 상한으로 시작하고, 시청자 프로세스가
끝난 뒤 30초 drain을 거쳐 스크립트가 SIGINT로 명시적으로 내린다.
산출물은 `perf/browser/out/<run>/` 에 쌓인다.

따라잡기(재생 속도로 지연을 줄이는 손잡이)는 진단용으로만 켠다. (#233)

```bash
CATCHUP_RATE=1.25 ./scripts/run-qoe-crosscheck.sh
```

`CATCHUP_RATE` 는 `1 · 1.05 · 1.1 · 1.25 · 1.5` 중 하나다. 그 밖의 값은 무시되고 따라잡기를 켜지 않는다.
켜면 `qoe-crosscheck` 가 시청자마다 `localStorage['edumeet.hls.maxLiveSyncPlaybackRate']` 를 심고,
정답 수집이 **재생 속도를 1초마다** 남긴다. `compare.mjs` 의 "따라잡기" 절이
재생 속도가 1을 넘은 비율 · 연쇄 끊김(끊김 뒤 10초 안의 끊김) 수 · 화면 지연 p50/p95 를 낸다.
자막 읽기와 함께 봐야 한다 - `perf/captions/reading-speed.py` 가 그 표를 낸다.

**넘긴 설정이 실제로 적용됐는지는 회차마다 확인된다.** 앱이 `window.__edumeetHlsConfig` 로
**hls.js 가 받아들인 값**(`hls.config`)을 노출하고, 시청자는 `viewer-k.json` 에
`requestedConfig`(우리가 넘긴 값)와 `effectiveConfig`(적용된 값)를 나란히 남긴다.
`compare.mjs` 의 "설정 적용" 절이 둘을 맞춰 보고, **다르면 조건 불성립(exit 2)** 으로 끝난다.
네이티브 재생 시청자는 "적용 불가" 로 따로 센다 - hls.js 설정이 없는 경로이기 때문이다.
(원격 경로가 `--catchup-rate` 를 안 넘겨 #233 그리드 11회차가 전부 "따라잡기 끔" 으로 돌았고,
그 사실이 산출물에 없어서 나중에야 알았다.)

또 하나 - 따라잡기를 1보다 크게 요청했는데 **전원의 최대 재생 속도가 1** 이고 화면 지연 p50 이
목표(조각 길이 × `liveSyncDurationCount`)보다 크면 경고 한 줄을 낸다. 켰는데 일을 안 한 경우다.

## 방송 시작 몰림·CDN (#235)

방송이 시작되는 순간 대기하던 시청자가 한꺼번에 몰린다. 시청 화면은 방송 전이면 3초마다
`GET /meeting/{id}` 로 상태를 묻는다(`BroadcastView`). 그 몰림을 만들려면 **시청자를 먼저** 띄운다.

```bash
START_MODE=waiting START_DELAY_S=30 ./scripts/run-qoe-crosscheck.sh
```

`START_MODE=waiting` 이면 시청자를 먼저 띄우고 `START_DELAY_S`(기본 30)초 뒤에 합성 방송을 시작한다.
기본값은 지금까지와 같은 `broadcast-first`(방송 먼저)다.

**START_DELAY_S 는 "모두가 대기 화면에 도달한 뒤" 부터 센다.** 시청자는 폴링을 한 번 보낸 순간
`ready-<k>.json` 을 남기고, 셸이 그것을 세어 `VIEWERS` 만큼 모일 때까지 기다린다
(`READY_TIMEOUT_S`, 기본 90초). 못 모이면 **조건 불성립(exit 2)** 이다 - 로그인·브라우저 기동이
30초보다 오래 걸리는데 방송을 먼저 켜면 몰림이 아예 관측되지 않는다.

**시청자 호스트와 방송 호스트가 같으면 시계 오프셋을 한 번만 잰다** - 따로 재면 회선 지터 때문에
같은 시계인데 값이 갈린다(운영 첫 회차: 145ms · 149ms). `clock.json` 의 `sameHost` 가 그 사실을 남긴다.
`compare.mjs` 는 `clock.json` 이 있는데 오프셋이 null 이면 "보정했다" 가 아니라
"**못 쟀다 - 보정 없이 계산했다**" 고 적는다.

시작 시각과 첫 재생 시각은 **다른 호스트의 시계**일 수 있다(시청자 VM · 방송 VM). 셸이 회차 시작 때
`ssh <host> 'date +%s%N'` 왕복 5회로 편도 지연을 지운 중간값을 재서 `clock.json` 에 남기고,
`compare.mjs` 가 그 값으로 보정해 "시작 → 첫 재생" 을 낸다. 보정량은 표 아래에 함께 적는다.

시청자는 `/hls/` 응답마다 `{파일, 종류, 상태, cf-cache-status, age, 바이트}` 를 `viewer-k.json` 의 `hls` 에,
대기 중 폴링은 `lookups` 에 남긴다. `compare.mjs` 의 두 절이 그것을 읽는다.

| 절 | 무엇을 보나 |
|---|---|
| **CDN** | 파일 종류별 cf-cache-status 분포 · 조각 하나를 몇 명이 받았고 그중 원본까지 간(MISS) 비율 = 요청 병합 |
| **방송 시작** | 시청자별 `방송 시작 → 첫 재생/첫 매니페스트/첫 조각` · 시작 ±10초의 초당 `GET /meeting/{id}` 수 · 서버 REST p50/p95/p99 |

`cf-cache-status` 가 **없으면 CDN 을 안 거친 것**이다(로컬 하네스). 적중률 0% 와는 다른 사실이라
그대로 적는다 - 로컬 회차에서는 적중률을 말할 수 없다.

원본 쪽 수치는 `lib/nginx-log.mjs` 가 운영 nginx 접근 로그에서 읽는다(운영에서 확인한 사실):
경로 `/var/log/nginx/access.log`, `$time_local` 은 **+0000**, 회전 파일은
`access.log-YYYYMMDD`(가장 최근 것은 비압축) 와 `access.log-YYYYMMDD.gz` 다 - `access.log.1.gz` 는 없다.
`sudo -n ls /var/log/nginx` 로 목록을 받아 창이 걸친 날짜의 파일을 고르고, `sudo -n` 으로 읽는다.
**grep 의 0건(exit 1)은 성공**이고, 못 읽은 파일은 빈 줄이 아니라 `error` 로 남는다.

규칙은 `perf/browser/selftest-nginx-log.mjs` 가 운영 없이 검증한다(가짜 원격 실행기).

```bash
node perf/browser/selftest-nginx-log.mjs
```

**로그를 통째로 당기지 않는다.** 원격 grep 에 회의 경로(`/hls/meeting-<id>/`)를 넣어 그 회의 것만 받는다 -
운영 access.log 의 `/hls/` 줄이 하루 14만 개였다. 그리고 줄을 이어 붙일 때 전개(`push(...lines)`)를 쓰지 않는다:
그 14만 줄에서 `RangeError: Maximum call stack size exceeded` 로 죽었다(인자 개수 한계). 파일마다 `concat` 한다.

**서버 쪽만 다시 돌릴 수 있다.** 조회 창은 회차 폴더에 남은 `viewer-*.json`(또는 `broadcast.json`)에서
복원하므로, 시청자 측정이 이미 끝난 회차에도 서버 수집만 재실행할 수 있다.

```bash
node perf/browser/server-side.mjs --run c235-before-base          # 창은 산출물에서 복원
node perf/browser/server-side.mjs --run c235-before-base --from 1790168400 --to 1790168700   # 창을 직접 주려면
```

## 원격 시청자 한도 (#235 · #160)

부하 생성기가 병목이면 정답이 틀어진다. 스로틀 없이 시청자 수를 올려 보며 **하네스가 먼저 무너지는 지점**을 찾는다.

```bash
COUNTS="10 20 30" ./scripts/run-viewer-capacity.sh
```

원격 VM CPU(**부하 중 1초 간격 표본**, `/proc/stat` 의 busy 비율 - 최대/평균)와 정답 끊김을
회차마다 표로 남긴다. 회차마다 산출물 디렉터리를 새로 시작한다(이전 회차의 `viewer-*.json` 이
섞이면 표본 수가 부풀려진다). 이 스크립트가 지우는 것은 `RUN_PREFIX` 로 시작하는 자기 회차뿐이다.
스로틀이 없는데 끊김이 나오면 그건 네트워크가 아니라 생성기 한계다 - 그 위 수치는 조건 불성립이다.
**이 스크립트는 원격 VM 에 들어간다.** 다른 측정이 같은 VM 을 쓰는 중이면 돌리지 마라.

각 단계를 따로 돌릴 수도 있다.

```bash
node perf/browser/broadcast-synthetic.mjs --run r1 --duration-s 210 &
node perf/browser/qoe-crosscheck.mjs --run r1 --viewers 5
node perf/browser/server-side.mjs --run r1
node perf/browser/compare.mjs --run r1
```

## 산출물 (`out/<run>/`)

| 파일 | 내용 |
|---|---|
| `broadcast.json` | 보낸 조각 수 · 429 수 · 실패 수 · 시작/끝 시각 · 재생목록 주소 |
| `viewer-<k>.json` | 정답(①) · 보낸 보고들(②) · 요청 실패 · 스로틀 일정 · 종료 방식 |
| `load.json` | 1초마다 이 프로세스와 자식의 CPU 사용률 (부하 생성기가 병목이면 정답이 틀어진다) |
| `server.json` | Prometheus 합계 · Loki 세션 로그(③) |
| `compare.json` | ①②③ 대조 |
| `compare.md` | 같은 표를 사람이 읽는 형태로 |

## 자기 시험

`lib/truth.js` 가 로컬 영상에서 `offline` 5초를 끊김으로 잡는지는 운영 없이 확인할 수 있다.
`perf/browser/selftest/` 를 두지 않는다 — 실행 중 임시 디렉터리에 HTML·영상을 만든다.
