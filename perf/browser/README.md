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

## 실행

```bash
./scripts/run-qoe-crosscheck.sh
```

`RUN`(이름) · `VIEWERS`(기본 5) · `DURATION_S`(기본 210) 로 바꿀 수 있다.
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
