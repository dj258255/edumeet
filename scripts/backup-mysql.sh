#!/usr/bin/env bash
# MySQL 백업. (#175)
#
# ★ 왜 뒤늦게 생겼나 - 저장소 어디에도 백업이 없었다.
#   운영 DB 204MB 가 컨테이너 볼륨 하나에만 있었고, 그것이 사라지면
#   되돌릴 방법이 없었다. 잘못된 DELETE 한 번이면 끝이다.
#
# ★ 이 백업이 지켜 주는 것과 못 지켜 주는 것.
#
#     지켜 준다      실수로 지운 데이터, MySQL 이 자기 데이터 파일을 망가뜨린 경우
#     못 지켜 준다   디스크·서버 자체의 소실 (같은 디스크에 있다)
#
#   같은 디스크에 두는 것은 타협이다. 오브젝트 스토리지로 보내려면 자격증명이
#   필요한데 이 서버에는 없다(#51 에서 자리표시자로 두고 있는 그것이다).
#   "어디에도 없다" 에서 "여기에는 있다" 로 가는 것이 먼저다.
#   한계를 적어 두고, 자격증명이 생기면 목적지만 추가한다.
#
# ★ --single-transaction 을 쓰는 이유.
#   InnoDB 는 이 옵션으로 테이블을 잠그지 않고도 일관된 스냅숏을 뜬다.
#   없으면 덤프 중에 들어온 쓰기가 절반만 담겨, 복구했을 때
#   외래키가 안 맞는 상태가 된다 - 그런 백업은 있으나 마나다.
set -euo pipefail

DIR="${BACKUP_DIR:-/var/lib/edumeet/backup}"
KEEP="${BACKUP_KEEP:-7}"
TEXTFILE="${TEXTFILE_DIR:-/var/lib/edumeet/node-exporter-textfile}"
CONTAINER="${MYSQL_CONTAINER:-edumeet-mysql}"

mkdir -p "$DIR" "$TEXTFILE"

stamp=$(date +%Y%m%d-%H%M%S)
out="$DIR/edumeet-$stamp.sql.gz"
started=$(date +%s)

# ★ 지표를 먼저 실패로 써 둔다.
#   나중에 성공했을 때 덮어쓴다. 스크립트가 중간에 죽으면 실패가 그대로 남아
#   경보가 울린다 - "아무것도 안 쓰고 죽어서 조용한" 상태를 만들지 않는다.
write_metrics() {
    local ok="$1" size="$2" secs="$3"
    cat > "$TEXTFILE/edumeet_backup.prom.$$" <<EOF
# HELP edumeet_backup_last_success_timestamp_seconds 마지막으로 성공한 백업 시각
# TYPE edumeet_backup_last_success_timestamp_seconds gauge
edumeet_backup_last_success_timestamp_seconds $( [ "$ok" = 1 ] && date +%s || echo "${LAST_OK:-0}" )
# HELP edumeet_backup_success 직전 백업이 성공했는가
# TYPE edumeet_backup_success gauge
edumeet_backup_success $ok
# HELP edumeet_backup_size_bytes 직전 백업 파일 크기
# TYPE edumeet_backup_size_bytes gauge
edumeet_backup_size_bytes $size
# HELP edumeet_backup_duration_seconds 직전 백업에 걸린 시간
# TYPE edumeet_backup_duration_seconds gauge
edumeet_backup_duration_seconds $secs
EOF
    mv "$TEXTFILE/edumeet_backup.prom.$$" "$TEXTFILE/edumeet_backup.prom"
}

# 이전 성공 시각을 보존한다. 이번에 실패해도 "마지막 성공이 언제인지" 는 남아야
# 경보가 "며칠째 실패 중" 을 말할 수 있다.
LAST_OK=$(awk '/^edumeet_backup_last_success_timestamp_seconds /{print $2}' \
          "$TEXTFILE/edumeet_backup.prom" 2>/dev/null || echo 0)

trap 'write_metrics 0 0 $(( $(date +%s) - started ))' ERR

docker exec "$CONTAINER" sh -c \
    'exec mysqldump --single-transaction --routines --triggers --events \
        --default-character-set=utf8mb4 \
        -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
    | gzip -6 > "$out.part"

# ★ 내용을 확인하고 나서 이름을 바꾼다.
#
#   mysqldump 가 실패해도 gzip 은 성공하므로 빈 파일이 남는다.
#   파이프 안의 실패는 pipefail 이 잡지만, "덤프는 됐는데 알맹이가 없는"
#   경우는 따로 봐야 한다.
#
#   처음엔 크기로 봤다 - 볼륨이 204MB 니 10KB 미만이면 이상하다고 잡았다.
#   그런데 그 204MB 는 대부분 InnoDB 의 자리(로그·테이블스페이스)였고
#   실제 데이터는 0.6MB, 정상 덤프가 4KB 였다. 멀쩡한 백업이 매번 실패했다.
#   크기는 신호가 아니다.
#
#   대신 표 개수를 센다. 운영에 있는 표가 전부 덤프 안에 있어야 한다.
#   행이 0개여도 이 검사는 유효하다 - 우리가 확인하려는 것은
#   "덤프가 스키마 전체를 담았는가" 이기 때문이다.
tables_live=$(docker exec "$CONTAINER" sh -c \
    'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
       SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema=\"$MYSQL_DATABASE\" AND table_type=\"BASE TABLE\""' 2>/dev/null)
tables_dump=$(gunzip -c "$out.part" | grep -c '^CREATE TABLE' || true)

if [ "$tables_dump" != "$tables_live" ]; then
    echo "덤프의 표 개수가 다르다 - 운영 ${tables_live}개, 덤프 ${tables_dump}개"
    rm -f "$out.part"
    exit 1
fi
size=$(stat -c%s "$out.part")
mv "$out.part" "$out"

# 오래된 것을 지운다. 최신 KEEP 개만 남긴다.
ls -1t "$DIR"/edumeet-*.sql.gz 2>/dev/null | tail -n "+$((KEEP + 1))" | xargs -r rm -f

secs=$(( $(date +%s) - started ))
trap - ERR
write_metrics 1 "$size" "$secs"

echo "백업 완료 $out ($(numfmt --to=iec "$size" 2>/dev/null || echo "$size")B, ${secs}초)"
echo "보관 중: $(ls -1 "$DIR"/edumeet-*.sql.gz | wc -l)개"
