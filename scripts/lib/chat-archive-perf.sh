#!/usr/bin/env bash
# 다시보기 채팅 저장 측정의 공통 도구. (#201, B9r)
#
# run-chat-archive-loss.sh · run-chat-archive-recovery.sh · run-chat-archive-throughput.sh 가 source 한다.
# 중복을 막으려고 여기에 모았다 - 세 스크립트가 각자 들고 있으면 하나만 고치는 날이 온다.
#
# ★ 스키마를 회차 사이에 유지한다.
#   예전 하네스는 회차마다 앱을 새로 띄우며 ddl-auto=create 로 스키마를 지웠다.
#   그래서 앞 회차가 남긴 스트림 항목을 다음 회차가 **저장할 수 없었고**,
#   "미저장" 과 "유실" 이 구분되지 않았다. 이제 한 번만 만들고 그 뒤로는 none 으로 띄운다.
#
# 제공하는 것
#   compose_up / build_jar / perf_setup        환경
#   app_start / app_wait_ready / app_stop      앱
#   stall_on / stall_off                       DB 장애(진짜 stall)
#   mysql_count / redis_cli / xlen / lag / pending   저장·대기열 상태
#   scrape_metrics / scrape_field              앱 지표
#   wait_drained                               배출 대기 (미저장과 유실을 가른다)

PERF_PORT="${PERF_PORT:-8081}"
MGMT_PORT="${MGMT_PORT:-9090}"
TOXIPROXY="${TOXIPROXY:-http://localhost:8474}"
MYSQL_PROXY="${MYSQL_PROXY:-mysql-loss}"
MYSQL_PROXY_PORT="${MYSQL_PROXY_PORT:-13307}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-edumeet-perf-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-edumeet-perf-redis}"
DB_NAME="${DB_NAME:-edumeet_perf}"
DB_USER="${DB_USER:-edumeet}"
DB_PASS="${DB_PASS:-perfpass}"
DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:${MYSQL_PROXY_PORT}/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true}"
STREAM_KEY="${STREAM_KEY:-chat:archive}"
STREAM_GROUP="${STREAM_GROUP:-archiver}"
# timeout 독은 0 이면 연결을 닫지 않고 멈춘다. 그래서 **풀에 이미 있는 연결도** 멈춘다.
STALL_TOXIC="${STALL_TOXIC:-mysql-stall}"

APP_PID=""

log() { printf '%s\n' "$*"; }
banner() {
  printf '\n==============================================================\n %s\n==============================================================\n' "$*"
}
need() { command -v "$1" >/dev/null || { log "없다: $1"; exit 1; }; }

# 시각 도우미. python3 -c 안에서 time 을 import 하는 것을 잊어
# 조용히 빈 값이 나온 적이 있다(B9r) - 한 곳에 모아 둔다.
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
now_s() { python3 -c 'import time;print(time.time())'; }
elapsed_s() { python3 -c "import time;print(round(time.time()-$1, 1))"; }
elapsed_ms() { python3 -c "import time;print(round((time.time()-$1)*1000))"; }

# ── 환경 ──────────────────────────────────────────────────────────
compose_up() {
  docker compose -f docker-compose.perf.yml up -d >/dev/null
  for _ in $(seq 1 60); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null)" = "healthy" ] && break
    sleep 2
  done
  [ "$(docker inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null)" = "healthy" ] \
    || { log "MySQL 이 healthy 가 아니다"; exit 1; }
}

# app_start 에 넘길 jar. app_start 가 한 번만 부르므로 여기서는 찾기만 한다.
build_jar() {
  (cd backend && ./gradlew perfBootJar -q) || { log "빌드 실패"; exit 1; }
  JAR=$(find backend/build/libs -maxdepth 1 -name '*-perf.jar' 2>/dev/null | head -1)
  [ -n "$JAR" ] || { log "perf jar 를 찾지 못했다"; exit 1; }
  log "   jar: $JAR"
}

toxiproxy_ready() {
  if ! curl -s "$TOXIPROXY/proxies/$MYSQL_PROXY" | grep -q '"name"'; then
    curl -s -X POST "$TOXIPROXY/proxies" -H 'Content-Type: application/json' \
      -d "{\"name\":\"$MYSQL_PROXY\",\"listen\":\"0.0.0.0:${MYSQL_PROXY_PORT}\",\"upstream\":\"${MYSQL_CONTAINER}:3306\",\"enabled\":true}" >/dev/null
  fi
  curl -s -X POST "$TOXIPROXY/proxies/$MYSQL_PROXY" -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null
  stall_off
}

# ── DB 장애 ───────────────────────────────────────────────────────
#
# ★ 왜 프록시를 끄지 않는가. (B9 의 caveat)
#   `enabled:false` 는 **새 연결만** 막는다. HikariCP 는 이미 맺어 둔 연결을 재사용하므로
#   풀에 남은 연결로 계속 쿼리가 나가 "DB 가 죽었다" 가 되지 않는다.
#   timeout 독(timeout=0)은 연결을 닫지 않고 **데이터 흐름을 멈춘다** -
#   풀에 있는 연결까지 멈추므로 진짜 장애가 된다.
stall_on() {  # stall_on [ms]  (기본은 무기한, stall_off 로 푼다)
  curl -s -X POST "$TOXIPROXY/proxies/$MYSQL_PROXY/toxics" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$STALL_TOXIC\",\"type\":\"timeout\",\"attributes\":{\"timeout\":0}}" >/dev/null
}

stall_off() {
  curl -s -X DELETE "$TOXIPROXY/proxies/$MYSQL_PROXY/toxics/$STALL_TOXIC" >/dev/null 2>&1
}

# HikariCP 가 실제로 어떤 얼굴로 드러나는지 로그에서 센다.
hikari_symptoms() {  # hikari_symptoms <앱로그> <라벨>
  local f="$1" label="$2"
  [ -f "$f" ] || return 0
  printf '   %s: SQLTransientConnectionException=%s · Connection is not available=%s · CommunicationsException=%s · SQLTimeoutException=%s\n' \
    "$label" \
    "$(grep -c 'SQLTransientConnectionException' "$f" 2>/dev/null)" \
    "$(grep -c 'Connection is not available' "$f" 2>/dev/null)" \
    "$(grep -c 'CommunicationsException' "$f" 2>/dev/null)" \
    "$(grep -c 'SQLTimeoutException' "$f" 2>/dev/null)"
  grep -m 1 -h -o 'HikariPool-[0-9]* - Connection is not available, request timed out after [0-9]*ms' "$f" 2>/dev/null \
    | sed 's/^/   첫 메시지: /'
}

# ── 앱 ────────────────────────────────────────────────────────────
app_start() {  # app_start <로그파일> [추가 인자...]
  local logfile="$1"; shift
  # B8 과 같은 조건을 유지한다 (Hibernate default_batch_fetch_size).
  PERF_BATCH_SIZE="${PERF_BATCH_SIZE:-100}" java -jar "$JAR" --spring.profiles.active=perf \
    --spring.datasource.url="$DB_URL" --logging.level.com.edu.edumeet="${LOG_LEVEL:-WARN}" \
    "$@" > "$logfile" 2>&1 &
  APP_PID=$!
}

app_alive() { [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; }

app_wait_ready() {  # 120초까지 기다린다
  for _ in $(seq 1 120); do
    curl -sf --max-time 3 "http://localhost:${PERF_PORT}/api/perf/chat/stats/1" >/dev/null 2>&1 && return 0
    app_alive || return 1
    sleep 1
  done
  return 1
}

app_stop() {  # app_stop [TERM|KILL] [대기ms]
  local signal="${1:-TERM}" waited="${2:-10000}"
  [ -n "$APP_PID" ] || return 0
  if app_alive; then
    kill "-$signal" "$APP_PID" 2>/dev/null
    local elapsed=0
    while [ "$elapsed" -lt "$waited" ] && kill -0 "$APP_PID" 2>/dev/null; do
      sleep 0.1
      elapsed=$((elapsed + 100))
    done
    kill -9 "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
}

# perf 전용 준비 엔드포인트. 회의 하나와 그 회의로 발행할 토큰을 준다.
perf_setup() {  # perf_setup <BROADCAST|INTERACTIVE|...>  → "meetingId token"
  curl -s -X POST "http://localhost:${PERF_PORT}/api/perf/chat/setup?type=${1:-BROADCAST}"
}

# ── 읽기 ──────────────────────────────────────────────────────────
mysql_query() {
  docker exec "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -N -B -e "$1" 2>/dev/null
}

# 회의 하나의 저장 행 수와 uid 중복 상황을 함께 본다.
mysql_count() {  # mysql_count <meetingId>
  mysql_query "SELECT COUNT(*) FROM $DB_NAME.chat_message WHERE meeting_id=$1" | tr -d '[:space:]'
}

# uid 중복 행 수. (#201)
# ★ NULL 을 빼야 한다. COUNT(DISTINCT) 는 NULL 을 안 세므로
#   memory 모드의 행(uid 가 전부 NULL)이 전부 "중복" 으로 잡힌다 - 실제로 그렇게 나왔다.
mysql_uid_duplicates() {  # mysql_uid_duplicates <meetingId>
  mysql_query "SELECT COUNT(*)-COUNT(DISTINCT message_uid) FROM $DB_NAME.chat_message
               WHERE meeting_id=$1 AND message_uid IS NOT NULL" | tr -d '[:space:]'
}

# 서버가 실제로 처리한 발행 수. (#201)
# ★ 구독자(수락)로 세면 안 되는 구간이 있다 - 부하 도구의 구독자 VU 하나가
#   초당 1,500건 근처에서 포화해 **서버가 처리한 것보다 적게** 센다(B9r 보정).
#   저장 대기열에 들어간 수는 서버가 아는 수로 봐야 한다.
server_published() {
  curl -sf --max-time 3 "http://localhost:${MGMT_PORT}/actuator/prometheus" 2>/dev/null \
    | awk '$1 ~ /^chat_messages_published_total/ { print $2; exit }'
}

redis_cli() { docker exec "$REDIS_CONTAINER" redis-cli "$@"; }

xlen() { redis_cli XLEN "$STREAM_KEY" | tr -d '[:space:]'; }

# lag = 그룹에 아직 배달되지 않은 항목 수 (미독분). Redis 7 의 XINFO GROUPS 가 준다.
stream_lag() {
  redis_cli XINFO GROUPS "$STREAM_KEY" | awk '/^lag$/{getline; print; exit}' | tr -d '[:space:]'
}

# pending = 배달됐지만 ACK 안 된 항목 수.
stream_pending() {
  redis_cli XPENDING "$STREAM_KEY" "$STREAM_GROUP" | head -1 | tr -d '[:space:]'
}

# 한 번 긁어서 필요한 값을 한 줄로 낸다.
#   1 queued  2 persist_failed  3 dropped  4 duplicates  5 fallback
#   6 length  7 pending(summary)  8 발행 p50(ms)  9 발행 p99(ms)
#
# 발행 지연은 **히스토그램 버킷에서 계산한다.** Micrometer 의 Prometheus 레지스트리는
# publishPercentiles 로 등록한 클라이언트 백분위를 내보내지 않는다(버킷만 낸다).
scrape_metrics() {
  curl -sf --max-time 3 "http://localhost:${MGMT_PORT}/actuator/prometheus" 2>/dev/null \
    | python3 -c '
import math
import sys

WANT = {
    "chat_archive_queued": 1,
    "chat_archive_persist_failed_total": 2,
    "chat_archive_dropped_total": 3,
    "chat_archive_stream_duplicates_total": 4,
    "chat_archive_stream_fallback_total": 5,
    "chat_archive_stream_length": 6,
    "chat_archive_stream_pending": 7,
}
values = [""] * 10
buckets = []
for line in sys.stdin:
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    head, _, rest = line.rpartition(" ")
    name = head.split("{")[0]
    if name == "chat_publish_latency_seconds_bucket":
        try:
            le = float(head.split("le=\"")[1].split("\"")[0])
            buckets.append((le, float(rest)))
        except (IndexError, ValueError):
            pass
        continue
    idx = WANT.get(name)
    if idx is not None:
        values[idx] = rest

def quantile(q):
    if not buckets:
        return ""
    buckets.sort()
    total = buckets[-1][1]
    if total <= 0:
        return "0.000"
    target = q * total
    prev_le, prev_count = 0.0, 0.0
    for le, count in buckets:
        if count >= target:
            # 마지막 버킷은 +Inf 다. 그 안까지 가면 값을 알 수 없으므로
            # 마지막 **유한** 상한을 낸다(히스토그램 백분위의 표준 처리).
            if math.isinf(le) or count == prev_count:
                return f"{prev_le * 1000:.3f}"
            frac = (target - prev_count) / (count - prev_count)
            return f"{(prev_le + frac * (le - prev_le)) * 1000:.3f}"
        prev_le, prev_count = le, count
    return f"{prev_le * 1000:.3f}"

values[8] = quantile(0.50)
values[9] = quantile(0.99)
print(" ".join(values))
'
}

# scrape_metrics 의 n번째 값. 앱이 죽어 있으면 빈 문자열.
scrape_field() {  # scrape_field <1..9>
  scrape_metrics | awk -v n="$1" '{ print $n }'
}

# ── 배출 대기 ─────────────────────────────────────────────────────
#
# ★ 왜 필요한가.
#   "수락 − 저장" 은 발행이 끝난 직후에는 **미저장**과 **유실** 이 섞인 값이다.
#   대기열이 빌 때까지 기다려야 그 차이가 진짜 유실이 된다. (#201 검토)
#
# stream : lag(미독) 과 pending(ACK 전) 이 둘 다 0 이 될 때까지
# memory : chat_archive_queued 가 0 이 될 때까지 (프로세스가 살아 있을 때만)
wait_drained() {  # wait_drained <stream|memory> [상한초]  → stdout "lag pending" 또는 "queued"
  local mode="$1" cap="${2:-60}" waited=0
  local lag=0 pending=0 queued=0
  while [ "$waited" -lt "$cap" ]; do
    if [ "$mode" = "stream" ]; then
      lag="$(stream_lag)"; pending="$(stream_pending)"
      [ "${lag:-x}" = "0" ] && [ "${pending:-x}" = "0" ] && { echo "0 0"; return 0; }
    else
      queued="$(scrape_field 1)"
      case "$queued" in 0|0.0|"") echo "${queued:-?}"; return 0;; esac
    fi
    sleep 2
    waited=$((waited + 2))
  done
  if [ "$mode" = "stream" ]; then echo "${lag:-?} ${pending:-?}"; else echo "${queued:-?}"; fi
  return 1
}

# ── k6 ────────────────────────────────────────────────────────────
run_k6() {  # run_k6 <meetingId> <token> <publishMs> [요약JSON] [RATE] [PUBLISHERS]
  local meeting_id="$1" token="$2" publish_ms="$3" summary="${4:-}" rate="${5:-50}" pubs="${6:-1}"
  local args=(-q -e "BASE_URL=ws://localhost:${PERF_PORT}" -e "TOKEN=$token"
              -e "MEETING_ID=$meeting_id" -e "RATE=$rate" -e "PUBLISHERS=$pubs"
              -e "PUBLISH_MS=$publish_ms")
  [ -n "$summary" ] && args+=(-e "SUMMARY_JSON=$summary")
  k6 run "${args[@]}" k6/chat-archive-publish.js
}
