#!/usr/bin/env python3
"""Grafana 대시보드 JSON 생성. (#28)

대시보드를 UI 로 만들면 재현이 안 된다. 누가 무엇을 왜 넣었는지도 남지 않는다.
여기서 만들고 결과 JSON 을 저장소에 커밋한다.

    python3 scripts/make_dashboard.py

패널을 바꾸려면 이 파일을 고치고 다시 돌린다. JSON 을 직접 편집하지 않는다.
"""
import json
import pathlib

OUT_DIR = pathlib.Path("observability/grafana/dashboards")
JOB = 'service="edumeet"'


def target(expr, legend):
    return {"expr": expr, "legendFormat": legend, "refId": "A", "datasource": {"type": "prometheus", "uid": "PBFA97CFB590B2093"}}


def panel(pid, title, x, y, w, h, targets, unit="short", desc="", stack=False):
    return {
        "id": pid, "title": title, "type": "timeseries", "description": desc,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "datasource": {"type": "prometheus", "uid": "PBFA97CFB590B2093"},
        "targets": [dict(t, refId=chr(65 + i)) for i, t in enumerate(targets)],
        "fieldConfig": {
            "defaults": {
                "unit": unit,
                "custom": {"lineWidth": 1, "fillOpacity": 15 if not stack else 40,
                           "stacking": {"mode": "normal" if stack else "none"}},
            },
            "overrides": [],
        },
        "options": {"legend": {"displayMode": "list", "placement": "bottom"},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
    }


# ── 붕괴를 보려면 "평균" 이 아니라 "꼬리" 와 "대기열" 을 봐야 한다.
#    처리율이 그대로인데 p99 만 오르는 구간이 포화의 첫 신호다.
panels = [
    panel(1, "HTTP 처리율 (req/s)", 0, 0, 12, 8, [
        target(f'sum(rate(http_server_requests_seconds_count{{{JOB}}}[1m]))', "전체"),
        target(f'sum by (status) (rate(http_server_requests_seconds_count{{{JOB}}}[1m]))', "{{status}}"),
    ], "reqps", "처리율이 멈추면 포화다. 다만 포화의 첫 신호는 여기가 아니라 p99 다."),

    panel(2, "HTTP 지연 p50 / p95 / p99", 12, 0, 12, 8, [
        target(f'histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{{{JOB}}}[1m])))', "p50"),
        target(f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{JOB}}}[1m])))', "p95"),
        target(f'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{{{JOB}}}[1m])))', "p99"),
    ], "s", "평균은 포화를 숨긴다. p99 가 먼저 벌어진다."),

    # `or vector(0)` 이 없으면 5xx 가 하나도 없을 때 시계열 자체가 안 생겨서
    # 패널이 "No data" 를 띄운다. "에러 0" 과 "지표 고장" 이 구분되지 않는다.
    panel(3, "5xx 비율", 0, 8, 8, 6, [
        target(f'(sum(rate(http_server_requests_seconds_count{{{JOB},status=~"5.."}}[1m])) or vector(0)) '
               f'/ clamp_min(sum(rate(http_server_requests_seconds_count{{{JOB}}}[1m])), 0.001)', "5xx 비율"),
    ], "percentunit", "에러가 아니라 지연으로만 나타나는 포화도 있다. 이 값이 0 이어도 안심할 수 없다."),

    panel(4, "느린 엔드포인트 top (p95)", 8, 8, 16, 6, [
        target(f'topk(5, histogram_quantile(0.95, sum by (le, uri) '
               f'(rate(http_server_requests_seconds_bucket{{{JOB}}}[5m]))))', "{{uri}}"),
    ], "s", "어디가 느린지 URI 단위로 본다."),

    panel(5, "JVM 힙", 0, 14, 8, 7, [
        target(f'sum(jvm_memory_used_bytes{{{JOB},area="heap"}})', "사용"),
        target(f'sum(jvm_memory_committed_bytes{{{JOB},area="heap"}})', "커밋"),
        target(f'sum(jvm_memory_max_bytes{{{JOB},area="heap"}})', "최대"),
    ], "bytes", "MaxRAMPercentage 가 안 먹으면 여기가 호스트 메모리 기준으로 잡힌다."),

    panel(6, "GC 정지 시간", 8, 14, 8, 7, [
        target(f'rate(jvm_gc_pause_seconds_sum{{{JOB}}}[1m])', "{{action}} {{cause}}"),
    ], "s", "브로드캐스트가 힙을 밀어올리면 여기부터 반응한다."),

    panel(7, "CPU 사용률", 16, 14, 8, 7, [
        target(f'process_cpu_usage{{{JOB}}}', "프로세스"),
        target(f'system_cpu_usage{{{JOB}}}', "시스템"),
    ], "percentunit", "2 OCPU 다. 여기가 먼저 차면 비용이 아니라 가용성이 한계다."),

    panel(8, "DB 커넥션 풀 (Hikari)", 0, 21, 12, 7, [
        target(f'hikaricp_connections_active{{{JOB}}}', "사용 중"),
        target(f'hikaricp_connections_idle{{{JOB}}}', "유휴"),
        target(f'hikaricp_connections_pending{{{JOB}}}', "대기"),
    ], "short", "대기(pending)가 0 이 아니면 커넥션을 기다리는 스레드가 있다는 뜻이다. "
                "트랜잭션 안에서 느린 I/O 를 하면 여기가 먼저 드러난다."),

    panel(9, "커넥션 획득 대기 시간", 12, 21, 12, 7, [
        target(f'rate(hikaricp_connections_acquire_seconds_sum{{{JOB}}}[1m]) '
               f'/ clamp_min(rate(hikaricp_connections_acquire_seconds_count{{{JOB}}}[1m]), 0.001)', "평균 획득 시간"),
    ], "s", "풀이 마르면 이 값이 먼저 튄다."),
]

# ── 채팅 붕괴 측정 (#39) ─────────────────────────────────────────────
# 계획 문서의 붕괴 5단계를 이 대시보드로 본다.
# 핵심은 처리율이 아니라 "대기열" 과 "fan-out 배수" 다.
chat_panels = [
    panel(1, "★ STOMP 큐 길이 - 포화의 첫 신호", 0, 0, 24, 8, [
        target(f'executor_queued_tasks{{{JOB},name="clientInboundChannelExecutor"}}', "inbound"),
        target(f'executor_queued_tasks{{{JOB},name="clientOutboundChannelExecutor"}}', "outbound"),
    ], "short",
       "기본 실행기는 큐가 무한이라 스레드 풀이 절대 거부하지 않는다. "
       "그래서 포화가 에러가 아니라 지연으로만 나타난다. 에러율은 0인데 여기가 오른다. 붕괴 2·3단계."),

    panel(2, "STOMP 활성 스레드", 0, 8, 12, 7, [
        target(f'executor_active_threads{{{JOB},name="clientInboundChannelExecutor"}}', "inbound active"),
        target(f'executor_pool_size_threads{{{JOB},name="clientInboundChannelExecutor"}}', "inbound pool"),
        target(f'executor_active_threads{{{JOB},name="clientOutboundChannelExecutor"}}', "outbound active"),
        target(f'executor_pool_size_threads{{{JOB},name="clientOutboundChannelExecutor"}}', "outbound pool"),
    ], "short", "active 가 pool 에 붙으면 더 못 밀어 넣는다. 그 뒤로는 전부 큐로 간다."),

    panel(3, "★ fan-out 배수 - 브로드캐스트 비용의 본체", 12, 8, 12, 7, [
        target(f'histogram_quantile(0.50, sum by (le) (rate(chat_fanout_recipients_bucket{{{JOB}}}[1m])))', "p50"),
        target(f'histogram_quantile(0.95, sum by (le) (rate(chat_fanout_recipients_bucket{{{JOB}}}[1m])))', "p95"),
        target(f'histogram_quantile(0.99, sum by (le) (rate(chat_fanout_recipients_bucket{{{JOB}}}[1m])))', "p99"),
    ], "short",
       "발행 1건이 수신 N건이 된다. 발행량만 보면 30명 방과 3,000명 방이 같아 보인다. "
       "실제 쓰기량 = 발행량 x 이 값."),

    panel(4, "발행량 / 실제 전달량", 0, 15, 12, 7, [
        target(f'rate(chat_messages_published_total{{{JOB}}}[1m])', "발행 (msg/s)"),
        target(f'rate(chat_fanout_recipients_sum{{{JOB}}}[1m])', "실제 전달 (msg/s)"),
    ], "short", "두 선의 간격이 곧 fan-out 배수다. 방이 커질수록 벌어진다."),

    panel(5, "세션 수 / 활성 방 수", 12, 15, 12, 7, [
        target(f'chat_sessions_active{{{JOB}}}', "STOMP 세션"),
        target(f'chat_rooms_active{{{JOB}}}', "활성 방"),
    ], "short", "세션 수가 커넥션 한계(붕괴 4단계)에 닿는지 본다."),

    panel(6, "JVM 힙 - 큐가 무한이면 여기가 먼저 찬다", 0, 22, 12, 7, [
        target(f'sum(jvm_memory_used_bytes{{{JOB},area="heap"}})', "사용"),
        target(f'sum(jvm_memory_max_bytes{{{JOB},area="heap"}})', "최대"),
    ], "bytes", "붕괴 3단계(무한 큐 OOM)는 큐 길이와 힙이 같이 오르는 형태로 나타난다."),

    panel(7, "GC 정지 시간", 12, 22, 12, 7, [
        target(f'rate(jvm_gc_pause_seconds_sum{{{JOB}}}[1m])', "{{action}} {{cause}}"),
    ], "s", "힙이 차면 GC 가 먼저 반응하고, 그게 다시 큐를 밀어올린다."),
]

dashboard = {
    "uid": "edumeet-baseline",
    "title": "EduMeet — 기준 지표",
    "description": "붕괴 측정 이전의 기준선. 채팅을 붙이기 전 상태를 여기서 확인한다. (#28)",
    "tags": ["edumeet", "baseline"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "10s",
    "time": {"from": "now-30m", "to": "now"},
    "panels": panels,
}

chat_dashboard = {
    "uid": "edumeet-chat",
    "title": "EduMeet — 채팅 붕괴 측정",
    "description": "붕괴 5단계를 보는 대시보드. 처리율이 아니라 대기열과 fan-out 배수를 본다. (#39)",
    "tags": ["edumeet", "chat", "breaking-points"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "5s",   # 붕괴는 빠르게 온다. 기준 대시보드보다 촘촘하게 본다.
    "time": {"from": "now-15m", "to": "now"},
    "panels": chat_panels,
}

OUT_DIR.mkdir(parents=True, exist_ok=True)
for name, d in [("edumeet-baseline", dashboard), ("edumeet-chat", chat_dashboard)]:
    path = OUT_DIR / f"{name}.json"
    path.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path}  패널 {len(d['panels'])}개")
