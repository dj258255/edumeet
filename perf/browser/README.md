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
방송은 기본 `BROADCAST_DURATION_S=86400`의 안전 상한으로 시작하고, 시청자 프로세스가
끝난 뒤 30초 drain을 거쳐 스크립트가 SIGINT로 명시적으로 내린다.
산출물은 `perf/browser/out/<run>/` 에 쌓인다.

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
