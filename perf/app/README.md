# perf 앱 환경 — 2코어 측정용 컨테이너 (#200)

`#200` 게이트는 **"2코어에서 채팅 역압이 조각 업로드를 밀어내는가"** 를 묻는다.
OCI 측정 인스턴스가 2코어(aarch64, `docs/performance/08`)라, 맥의 12코어로 재면
게이트의 답이 조건과 무관해진다. 그래서 앱을 컨테이너로 묶어 코어 수를 맞춘다.

```bash
./scripts/run-perf-app-2cpu.sh                 # jar 빌드 → 이미지 빌드 → 스택 → 앱 → health 대기
eval "$(node perf/app/provision.mjs)"          # TOKEN·MEETING_ID 를 만든다
TOKEN=... MEETING_ID=... ./scripts/run-chunk-under-fanout.sh
./scripts/run-perf-app-2cpu.sh stop            # 앱만 내린다
./scripts/run-perf-app-2cpu.sh down            # 스택까지 내린다
```

- 앱: `--cpus=2`(PERF_CPUS) · `--memory=4g`(PERF_MEMORY) · 네트워크 `edumeet_default` · 포트 8081
- 스택: `docker-compose.perf.yml` (MySQL 3307 · Redis 6380 · LiveKit · Toxiproxy)
- 헬스는 8081 로 모은다(`MANAGEMENT_PORT=8081`) - 게이트가 한 포트만 보면 되게.
  기본은 9090 이고 운영은 그 포트를 publish 하지 않는다.
- HLS 출력은 **컨테이너 안**(`/tmp/edumeet-hls`)이다. 호스트로 꺼내지 않는다 -
  이 게이트는 조각 업로드를 재지 재생을 재지 않는다.

## 두 프로파일 설정을 덮어써야 한다

perf 프로파일은 컨테이너 밖을 전제로 주소를 박아 둔다. 그래서 프로퍼티 이름을 직접 준다.

| 설정 | 프로파일 기본값 | 컨테이너에서 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3307/edumeet_perf` | `SPRING_DATASOURCE_URL=jdbc:mysql://edumeet-perf-mysql:3306/...` |
| `spring.data.redis.port` | `6380` (호스트 포트) | `SPRING_DATA_REDIS_HOST/PORT=edumeet-perf-redis/6379` |
| `LIVEKIT_URL` | `http://localhost:7881` | `http://edumeet-perf-toxiproxy:7881` |

`DB_URL`(기본 설정의 자리표시자)로는 안 된다 - perf 프로파일이 그 값을 **덮어쓴다**.
프로퍼티 이름(`SPRING_DATASOURCE_URL`)을 주면 yml 보다 우선한다. 실제로 이걸 몰라서
처음 두 번은 `Unable to determine Dialect`(DB) · `Unable to connect to Redis` 로 죽었다.

시드 데이터는 기본으로 끈다(`EDUMEET_PERF_SEED_CLASSES=0`). `PerfDataSeeder` 는 N+1 측정용
과제·제출을 수천 행 만드는데 이 게이트는 안 쓴다 - `ddl-auto=create` 라 시작할 때마다 다시 만든다.

## 이 환경의 한계 (수치를 읽을 때 같이 봐야 한다)

1. **맥의 2 vCPU ≠ OCI Ampere 2 OCPU.** 도커의 `--cpus=2` 는 cgroup 쿼터라 "2코어어치 시간" 을
   보장할 뿐, 명령어 집합도 캐시도 다르다(x86/arm 맥 vs Ampere aarch64). 절대 수치를 OCI 와
   나란히 놓으면 안 된다 - **조건 A 대비 조건 B 의 상대 변화**만 본다.
2. **네트워크 왕복이 없다.** 하네스도 k6 도 같은 호스트에서 loopback 으로 붙는다.
   OCI 측정은 노트북에서 인터넷을 건너 k6 를 걸었다(`docs/performance/09`) - 여기 지연은 그만큼 낙관적이다.
3. **OCI 인스턴스는 이미 절반이 쓰이고 있었다.** 측정 당시 load average 1.13(2코어 기준)이었다
   (`docs/performance/08`). 이 컨테이너는 그런 배경 부하가 없다.
4. **ffmpeg 는 컨테이너 안에서 돈다.** 합성 방송의 인코딩·리먹싱 비용이 앱과 같은 2코어를 나눠 쓴다 -
   이건 OCI 와 같은 조건이다(그래서 이미지에 ffmpeg 를 넣었다). 반대로 k6 는 호스트에서 돌아
   컨테이너 쿼터 밖이다 - 이것도 OCI(노트북에서 k6)와 같은 모양이다.
5. **DB 는 빈 스키마로 시작한다.** 운영·OCI perf 앱은 시드가 켜져 있었지만 여기서는 껐다(위).
   조각 업로드 경로는 시드와 무관하지만, 다른 지표와 비교할 때는 이 차이를 기억한다.

## 검증한 것 (이 세션, 로컬)

```
./scripts/run-perf-app-2cpu.sh           exit 0 · /actuator/health UP · http://localhost:8081
docker inspect → NanoCpus=2000000000 (2코어) · Memory=4294967296 (4GiB)
node perf/app/provision.mjs              회원가입 → 로그인 → 수업 → 방송 회의 → TOKEN·MEETING_ID
                                         재실행하면 가입·생성은 건너뛴다 (멱등)
짧은 합성 방송 (8초)                      조각 4건 · 전부 202 · p50 22.5ms · max 31ms
                                         HLS 는 컨테이너 안 /tmp/edumeet-hls/meeting-2 에 쌓였다
```
