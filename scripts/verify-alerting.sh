#!/usr/bin/env bash
# 경보가 실제로 사람에게 도달하는지 확인한다. (#139)
#
# ★ 왜 이 스크립트가 있는가.
#
#   이 저장소는 "선언은 있는데 아무도 안 쓴다" 를 여덟 번 만났다.
#   경보는 그 함정에 특히 잘 빠진다 - 규칙 파일을 커밋하면 일이 끝난 것처럼 보이는데,
#   실제로는 네 군데에서 조용히 끊길 수 있다.
#
#     ① Prometheus 가 규칙 파일을 아예 안 읽었다 (경로 오타, 마운트 누락)
#     ② 규칙은 읽었는데 물어보는 지표가 없다 -> 빈 결과. 빈 결과는 "정상" 과 같다
#     ③ firing 이 됐는데 Alertmanager 로 안 간다 (alerting: 설정 누락)
#     ④ Alertmanager 가 받았는데 수신자가 없다 -> 조용히 버린다
#
#   ①②는 여기서, ③④는 --fire 로 실제 경보를 만들어 확인한다.
#
# 사용법 (서버 안에서)
#   ./scripts/verify-alerting.sh            구조만 확인 (빠름, 안전)
#   ./scripts/verify-alerting.sh --fire     실제 경보를 하나 쏴서 끝까지 도달하는지 확인
set -euo pipefail

PROM="${PROM_URL:-http://localhost:9090}"
ALERTMANAGER="${ALERTMANAGER_URL:-http://localhost:9093}"
FIRE=0
[ "${1:-}" = "--fire" ] && FIRE=1

fail=0
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$1"; fail=1; }
head() { printf "\n\033[1m%s\033[0m\n" "$1"; }

need() { command -v "$1" >/dev/null || { echo "$1 이 필요하다"; exit 2; }; }
need curl; need jq

# ── ① 규칙을 실제로 로드했는가 ────────────────────────────────────────
head "① Prometheus 가 규칙을 로드했는가"

rules_json=$(curl -sf "$PROM/api/v1/rules" || echo '')
if [ -z "$rules_json" ]; then
  bad "$PROM/api/v1/rules 에 닿지 못했다"
else
  n=$(jq '[.data.groups[].rules[]] | length' <<<"$rules_json")
  if [ "$n" -gt 0 ]; then ok "규칙 ${n}개 로드됨"; else
    bad "규칙이 0개다. rule_files 경로와 볼륨 마운트를 본다"
  fi

  # 파일에 있는 규칙 이름이 전부 로드됐는지 대조한다.
  # 파일에만 있고 Prometheus 에 없으면 그 규칙은 존재하지 않는 것과 같다.
  loaded=$(jq -r '[.data.groups[].rules[].name] | sort | .[]' <<<"$rules_json")
  declared=$(grep -oE '^\s*- alert:\s*\S+' observability/rules/*.yml | awk '{print $NF}' | sort)
  missing=$(comm -13 <(echo "$loaded") <(echo "$declared") || true)
  if [ -z "$missing" ]; then ok "파일의 규칙이 전부 로드됨"; else
    bad "파일에 있는데 로드 안 된 규칙:"; echo "$missing" | sed 's/^/      /'
  fi

  # 평가 자체가 실패하는 규칙 (문법은 맞는데 런타임 에러)
  errs=$(jq -r '[.data.groups[].rules[] | select(.health != "ok") | "\(.name): \(.lastError // .health)"] | .[]' <<<"$rules_json")
  if [ -z "$errs" ]; then ok "평가 오류 없음"; else
    bad "평가에 실패하는 규칙:"; echo "$errs" | sed 's/^/      /'
  fi
fi

# ── ② 규칙이 물어보는 지표가 실제로 있는가 ────────────────────────────
head "② 규칙이 물어보는 지표가 실제로 나오는가"
#
#   없는 지표를 물어보면 Prometheus 는 에러가 아니라 빈 결과를 낸다.
#   빈 결과는 "정상" 과 구분되지 않으므로, 그 경보는 영원히 안 울린다.
for metric in chat_channel_queued chat_channel_capacity chat_fanout_recipients_bucket \
              caption_archive_dropped_total chat_archive_queued caption_archive_queued \
              chat_sessions_active chat_messages_published_total; do
  cnt=$(curl -sf --get "$PROM/api/v1/query" --data-urlencode "query=$metric" \
        | jq '.data.result | length' 2>/dev/null || echo 0)
  if [ "$cnt" -gt 0 ]; then ok "$metric (시계열 ${cnt}개)"; else
    bad "$metric 이 없다 - 이 지표를 쓰는 경보는 절대 안 울린다"
  fi
done

# ── ③ Alertmanager 가 연결돼 있는가 ───────────────────────────────────
head "③ Prometheus 가 Alertmanager 를 알고 있는가"
active=$(curl -sf "$PROM/api/v1/alertmanagers" | jq '.data.activeAlertmanagers | length' 2>/dev/null || echo 0)
if [ "$active" -gt 0 ]; then ok "Alertmanager ${active}개 연결됨"; else
  bad "연결된 Alertmanager 가 없다. prometheus.yml 의 alerting: 을 본다"
fi

# ── ④ 수신자가 실제로 설정돼 있는가 ───────────────────────────────────
head "④ 경보를 받을 곳이 있는가"
#
#   receivers 에 이름만 있고 webhook_configs 등이 없으면 Alertmanager 는
#   경보를 받아서 조용히 버린다. 이 저장소가 여덟 번 만난 모양 그대로다.
recv=$(curl -sf "$ALERTMANAGER/api/v2/status" | jq -r '.config.original' 2>/dev/null || echo '')
if [ -z "$recv" ]; then
  bad "$ALERTMANAGER 에 닿지 못했다"
elif grep -qE '(webhook|slack|email|pagerduty|opsgenie|telegram|discord)_configs' <<<"$recv"; then
  ok "수신자가 설정돼 있다"
else
  bad "수신자가 비어 있다 - firing 이 돼도 아무 데도 안 간다. Ansible vault 의 웹훅을 확인한다"
fi

# ── 실제로 쏴 본다 ────────────────────────────────────────────────────
if [ "$FIRE" = 1 ]; then
  head "⑤ 경보를 하나 실제로 보내 본다"
  #
  #   규칙을 기다리지 않고 Alertmanager 에 직접 넣는다.
  #   확인하려는 것은 "규칙이 맞나" 가 아니라 "받은 뒤 나가는가" 이므로 이 경로면 충분하다.
  now=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
  end=$(date -u -d '+2 minutes' +%Y-%m-%dT%H:%M:%S.000Z 2>/dev/null \
        || date -u -v+2M +%Y-%m-%dT%H:%M:%S.000Z)
  code=$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$ALERTMANAGER/api/v2/alerts" \
    -H 'Content-Type: application/json' -d "[{
      \"labels\": {\"alertname\":\"AlertingPipelineSmokeTest\",\"severity\":\"warning\",\"service\":\"edumeet\"},
      \"annotations\": {\"summary\":\"경보 경로 점검용. 이 알림이 보이면 경로가 살아 있다\"},
      \"startsAt\": \"$now\", \"endsAt\": \"$end\"
    }]")
  if [ "$code" = "200" ]; then
    ok "Alertmanager 가 접수했다 (HTTP 200)"
    echo "      → 실제 알림이 도착했는지는 사람이 확인한다. 2분 뒤 자동 해제된다."
  else
    bad "Alertmanager 가 거부했다 (HTTP $code)"
  fi
fi

head "결과"
if [ "$fail" = 0 ]; then
  ok "경보 경로가 끊긴 곳 없이 이어져 있다"
else
  bad "위의 ✗ 를 고치기 전까지 이 경보들은 울리지 않는다"
  exit 1
fi
