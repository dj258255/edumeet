#!/usr/bin/env bash
# 앱을 무중단으로 바꾼다. (#180)
#
# ★ 왜 필요한가 - 지금 배포는 컨테이너를 지우고 다시 만든다.
#   그 사이 서비스가 20.5초 끊긴다(실측, docs/ops/20-disk.md).
#
# ★ 무엇을 하나
#
#     1. 지금 서비스 중인 슬롯을 nginx 설정에서 읽는다
#     2. 반대 슬롯에 새 이미지를 띄운다              ← 서비스는 계속 옛 슬롯이 한다
#     3. 새 슬롯이 healthy 가 될 때까지 기다린다
#     4. nginx 를 새 슬롯으로 옮기고 다시 읽는다      ← 여기가 전환점
#     5. 잠깐 두었다가 옛 슬롯을 내린다              ← 처리 중이던 요청을 끝내게
#
# ★ 실패하면 아무 일도 안 일어난다
#   3에서 못 뜨면 새 슬롯만 지우고 그만둔다. **옛 슬롯은 내려간 적이 없다.**
#   지금 방식은 새 것이 뜨는지 알기 전에 옛 것을 이미 지운다 -
#   무중단보다 이쪽이 더 큰 차이일 수 있다.
#
# ★ 드레인을 길게 두지 않는 이유
#   채팅 중개자가 메모리에 있어서, 두 슬롯이 같이 떠 있는 동안에는
#   양쪽에 붙은 사람이 서로의 메시지를 못 본다. 그 창을 짧게 유지한다.
#   HTTP 요청은 이 시간이면 끝나고, WebSocket 은 끊겨서 다시 붙는다
#   (재접속 150/150 · p95 292ms 로 이미 재 뒀다).
set -euo pipefail

COMPOSE="${COMPOSE:-docker compose -f docker-compose.prod.yml}"
UPSTREAM="${UPSTREAM_FILE:-/etc/nginx/conf.d/edumeet-upstream.inc}"
DRAIN="${DRAIN_SECONDS:-5}"
WAIT_TRIES="${WAIT_TRIES:-40}"

port_of()      { case "$1" in blue) echo 8081;; green) echo 8082;; *) echo 8080;; esac; }
container_of() { echo "edumeet-app-$1"; }

# ── 1. 지금 서비스 중인 슬롯 ──────────────────────────────────────
active_port=$(grep -oE 'server[[:space:]]+127\.0\.0\.1:[0-9]+' "$UPSTREAM" \
              | grep -oE '[0-9]+$' | head -1)
case "$active_port" in
    8081) active=blue;  next=green ;;
    8082) active=green; next=blue  ;;
    # 8080 은 blue/green 이전의 단일 컨테이너다. 첫 전환이라 blue 로 간다.
    *)    active=legacy; next=blue ;;
esac
next_port=$(port_of "$next")
next_container=$(container_of "$next")

echo "지금 서비스: $active (:$active_port)  ->  새 슬롯: $next (:$next_port)"

# ── 2. 새 슬롯을 띄운다 ───────────────────────────────────────────
#
# --no-deps 를 쓰지 않는다. mysql·redis 가 healthy 여야 앱이 뜬다.
#
# ★ 여기서 실패해도 옛 슬롯은 그대로다. 다만 반쯤 만들어진 컨테이너가 남으면
#   다음 배포가 그것을 물려받아 헷갈리므로 치우고 그만둔다.
#   실제로 첫 배포가 여기서 실패했다(관리 포트 충돌). 서비스는 안 끊겼지만
#   Created 상태의 컨테이너가 남았다.
if ! $COMPOSE --profile "$next" up -d "app-$next"; then
    echo "새 슬롯을 띄우지 못했다. nginx 는 그대로다 - 서비스는 $active 가 계속한다"
    $COMPOSE --profile "$next" rm -sf "app-$next" >/dev/null 2>&1 || true
    exit 1
fi

# ── 3. 건강해질 때까지 기다린다 ───────────────────────────────────
#
# ★ 호스트에서 curl 하지 않는다. 관리 포트는 컨테이너 안에서만 열려 있고,
#   Dockerfile 의 HEALTHCHECK 가 이미 그 포트를 부르고 있다.
#   컨테이너가 스스로 보고하는 상태를 그대로 읽는다.
ok=0
for i in $(seq 1 "$WAIT_TRIES"); do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$next_container" 2>/dev/null || echo starting)
    if [ "$status" = "healthy" ]; then echo "  새 슬롯 healthy (${i}회차)"; ok=1; break; fi
    if [ "$status" = "unhealthy" ]; then echo "  새 슬롯이 unhealthy 로 확정됐다"; break; fi
    sleep 5
done

if [ "$ok" != 1 ]; then
    echo "새 슬롯이 뜨지 않았다. nginx 를 옮기지 않는다 - 서비스는 $active 가 계속한다"
    docker logs --tail 80 "$next_container" 2>&1 || true
    $COMPOSE --profile "$next" rm -sf "app-$next" >/dev/null 2>&1 || true
    exit 1
fi

# ── 4. nginx 를 옮긴다 ────────────────────────────────────────────
tmp=$(mktemp)
cat > "$tmp" <<EOF
# 지금 서비스 중인 앱 슬롯. scripts/deploy-app.sh 가 쓴다. 손으로 고치지 않는다. (#180)
upstream edumeet_app {
    server 127.0.0.1:$next_port;   # $next
    keepalive 32;
}
EOF
sudo install -m 0644 "$tmp" "$UPSTREAM"
rm -f "$tmp"

# ★ 문법을 먼저 본다. 틀린 채로 reload 하면 nginx 는 옛 설정으로 계속 돌고,
#   그러면 새 슬롯은 떠 있는데 트래픽은 옛 슬롯으로 가는 상태가 조용히 이어진다.
if ! sudo nginx -t; then
    echo "nginx 설정이 틀렸다. 되돌린다"
    sudo sed -i "s/127\.0\.0\.1:$next_port/127.0.0.1:$active_port/" "$UPSTREAM"
    sudo nginx -t && sudo systemctl reload nginx
    exit 1
fi
sudo systemctl reload nginx
echo "  nginx -> :$next_port ($next)"

# ★ 정말 옮겨졌는지 확인한다. reload 는 비동기라 성공 코드만으로는 모른다.
for i in $(seq 1 20); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -k \
           -H 'Host: api.studywithtymee.com' \
           -X POST -H 'Content-Type: application/json' \
           -d '{"email":"deploy-probe@example.com","password":"x"}' \
           https://127.0.0.1/api/v1/members/login 2>/dev/null || echo 000)
    [ "$code" != "000" ] && [ "$code" != "502" ] && break
    sleep 1
done
if [ "$code" = "000" ] || [ "$code" = "502" ]; then
    echo "전환 뒤 nginx 를 통해 응답이 안 온다 (code=$code)"
    exit 1
fi
echo "  전환 확인 (nginx 경유 응답 $code)"

# ── 4-2. 브라우저와 같은 모양으로 한 번 붙어 본다 ─────────────────
#
# ★ 이 검사가 없어서 운영이 조용히 망가져 있었다. (#186)
#
#   허용 출처 설정이 개발용으로 남아 있었는데 아무것도 그걸 못 잡았다 -
#   부하 도구도, 헬스체크도, 통합 시험도 **Origin 헤더를 안 보낸다.**
#   동일 출처 정책은 그 헤더가 있을 때만 검사하므로 전부 통과했고,
#   진짜 브라우저만 403 을 받았다. 채팅도 실시간 자막도 안 됐다.
#
#   그래서 배포가 사람이 하는 것과 같은 요청을 한 번 보낸다.
#   Origin 을 붙이고, HTTP/1.1 로, WebSocket 업그레이드를 요청한다.
#   (HTTP/2 로는 WebSocket 업그레이드가 안 된다 - 브라우저도 이때는 1.1 을 쓴다)
SITE="https://studywithtymee.com"
ws_code=$(curl -sk --http1.1 -o /dev/null -w '%{http_code}' -m 10 \
    -H "Host: api.studywithtymee.com" -H "Origin: $SITE" \
    -H 'Upgrade: websocket' -H 'Connection: Upgrade' \
    -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Version: 13' \
    https://127.0.0.1/ws || echo 000)

cors_code=$(curl -sk --http1.1 -o /dev/null -w '%{http_code}' -m 10 -X OPTIONS \
    -H "Host: api.studywithtymee.com" -H "Origin: $SITE" \
    -H 'Access-Control-Request-Method: POST' \
    https://127.0.0.1/api/v1/members/login || echo 000)

if [ "$ws_code" != "101" ] || { [ "$cors_code" != "200" ] && [ "$cors_code" != "204" ]; }; then
    echo "브라우저 모양의 요청이 막힌다 - WebSocket=$ws_code CORS=$cors_code"
    echo "허용 출처(FRONT_URL / FRONT_URL2)가 운영 도메인인지 본다."
    echo "이 상태로 두면 부하 도구와 헬스체크는 전부 통과하는데 사람만 못 쓴다."
    exit 1
fi
echo "  브라우저 모양 확인 (WebSocket $ws_code · CORS $cors_code)"

# ── 5. 옛 슬롯을 내린다 ───────────────────────────────────────────
sleep "$DRAIN"
if [ "$active" = "legacy" ]; then
    # blue/green 이전의 단일 컨테이너. compose 가 더 이상 모른다.
    docker rm -f edumeet-app >/dev/null 2>&1 || true
    echo "  옛 단일 컨테이너 제거"
else
    $COMPOSE --profile "$active" rm -sf "app-$active" >/dev/null 2>&1 || true
    echo "  옛 슬롯 $active 내림"
fi

echo "배포 완료: $next (:$next_port)"
