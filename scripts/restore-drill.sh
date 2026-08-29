#!/usr/bin/env bash
# 백업이 실제로 복구되는지 확인한다. (#175)
#
# ★ 왜 이 스크립트가 따로 있는가.
#
#   이 저장소는 "선언은 있는데 아무도 안 쓴다" 를 열 번 만났다(07-declared-but-unused).
#   백업은 그 함정의 교과서다 - 덤프 파일이 매일 쌓이는 것을 보면 일이 된 것 같은데,
#   그 파일로 실제 DB 를 세울 수 있는지는 아무도 확인하지 않는다.
#   확인하는 날은 보통 이미 늦은 날이다.
#
#   그래서 재현한다 - 버릴 MySQL 컨테이너를 하나 띄워 최신 덤프를 붓고,
#   운영과 행 수를 대조한다.
#
# ★ 운영 DB 를 건드리지 않는다. 읽기만 한다(행 수를 세려고).
#   복구는 전부 임시 컨테이너 안에서 일어나고, 끝나면 지운다.
set -euo pipefail

DIR="${BACKUP_DIR:-/var/lib/edumeet/backup}"
CONTAINER="${MYSQL_CONTAINER:-edumeet-mysql}"
PROBE="edumeet-restore-drill"
PROBE_PW="drill-only-not-a-secret"

latest=$(ls -1t "$DIR"/edumeet-*.sql.gz 2>/dev/null | head -1 || true)
[ -n "$latest" ] || { echo "덤프가 없다: $DIR"; exit 1; }
echo "대상 $latest ($(du -h "$latest" | cut -f1))"

started=$(date +%s)
cleanup() { docker rm -f "$PROBE" >/dev/null 2>&1 || true; }
trap cleanup EXIT

image=$(docker inspect "$CONTAINER" --format '{{.Config.Image}}')
db=$(docker exec "$CONTAINER" printenv MYSQL_DATABASE)

docker run -d --name "$PROBE" \
    -e MYSQL_ROOT_PASSWORD="$PROBE_PW" -e MYSQL_DATABASE="$db" \
    "$image" >/dev/null

# ★ mysqladmin ping 으로 기다리면 안 된다.
#
#   ping 은 "서버가 응답했다" 만 확인하고, 인증이 실패해도 성공을 반환한다.
#   MySQL 컨테이너는 초기화 중에 임시 서버를 한 번 띄웠다 내리므로,
#   ping 만 보면 아직 root 비밀번호가 설정되기 전에 통과해 버린다.
#   실제로 그렇게 통과한 다음 복구가 Access denied 로 죽었다.
#
#   그래서 우리가 실제로 할 일(비밀번호로 붙어 질의하기)을 그대로 시켜 본다.
probe_ready() {
    docker exec "$PROBE" sh -c \
        "exec mysql -N -B -uroot -p'$PROBE_PW' -e 'SELECT 1'" >/dev/null 2>&1
}
echo -n "  임시 MySQL 기동 대기"
for i in $(seq 1 90); do
    if probe_ready; then echo " (${i}회차)"; break; fi
    echo -n "."; sleep 2
done
probe_ready || { echo; echo "임시 MySQL 이 뜨지 않았다"; docker logs --tail 20 "$PROBE"; exit 1; }

echo "  복구 중"
gunzip -c "$latest" | docker exec -i "$PROBE" \
    sh -c "exec mysql --default-character-set=utf8mb4 -uroot -p'$PROBE_PW' '$db'"

# ── 대조 ──────────────────────────────────────────────────────────
#
# 행 수를 표마다 센다. "복구가 에러 없이 끝났다" 는 것과
# "데이터가 들어 있다" 는 것은 다른 일이다 - 빈 덤프도 에러 없이 복구된다.
counts() {
    local c="$1" pw="$2"
    docker exec "$c" sh -c \
        "exec mysql -N -B -uroot -p'$pw' '$db' -e \"
           SELECT table_name FROM information_schema.tables
           WHERE table_schema='$db' AND table_type='BASE TABLE' ORDER BY table_name\"" \
    | while read -r t; do
        n=$(docker exec "$c" sh -c \
            "exec mysql -N -B -uroot -p'$pw' '$db' -e 'SELECT COUNT(*) FROM \`$t\`'")
        echo "$t $n"
      done
}

prod_pw=$(docker exec "$CONTAINER" printenv MYSQL_ROOT_PASSWORD)
counts "$CONTAINER" "$prod_pw" > /tmp/drill-prod.txt
counts "$PROBE"     "$PROBE_PW" > /tmp/drill-restored.txt

secs=$(( $(date +%s) - started ))
echo
printf "  %-34s %10s %10s\n" "표" "운영" "복구본"
fail=0
while read -r t n; do
    r=$(awk -v k="$t" '$1==k{print $2}' /tmp/drill-restored.txt)
    r=${r:-없음}
    mark=""
    if [ "$r" != "$n" ]; then mark="  <-- 다르다"; fail=1; fi
    printf "  %-34s %10s %10s%s\n" "$t" "$n" "$r" "$mark"
done < /tmp/drill-prod.txt

# ★ 결과를 지표로 남긴다. (#175)
#
#   훈련을 돌리는 것만으로는 부족하다 - 돌다가 조용히 멈추면 아무도 모른다.
#   백업이 그 함정에 빠지는 것을 막으려고 만든 훈련이 같은 함정에 빠지면
#   두 겹 다 무너진 채로 조용해진다.
TEXTFILE="${TEXTFILE_DIR:-/var/lib/edumeet/node-exporter-textfile}"
if [ -d "$TEXTFILE" ]; then
    cat > "$TEXTFILE/edumeet_restore_drill.prom.$$" <<EOF
# HELP edumeet_restore_drill_success 마지막 복구 훈련이 통과했는가
# TYPE edumeet_restore_drill_success gauge
edumeet_restore_drill_success $(( 1 - fail ))
# HELP edumeet_restore_drill_timestamp_seconds 마지막 복구 훈련 시각
# TYPE edumeet_restore_drill_timestamp_seconds gauge
edumeet_restore_drill_timestamp_seconds $(date +%s)
# HELP edumeet_restore_drill_duration_seconds 복구에 걸린 시간
# TYPE edumeet_restore_drill_duration_seconds gauge
edumeet_restore_drill_duration_seconds $secs
EOF
    mv "$TEXTFILE/edumeet_restore_drill.prom.$$" "$TEXTFILE/edumeet_restore_drill.prom"
fi

echo
if [ "$fail" = 0 ]; then
    echo "★ 복구 확인 - 표 $(wc -l < /tmp/drill-prod.txt)개의 행 수가 전부 일치. ${secs}초 걸렸다"
else
    echo "★ 복구본이 운영과 다르다. 위 표를 본다"
    echo "  덤프 시각 이후에 들어온 쓰기라면 정상이다 - 그 경우 차이는 늘어나는 쪽뿐이다"
    exit 1
fi
