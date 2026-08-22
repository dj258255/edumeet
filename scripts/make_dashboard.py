#!/usr/bin/env python3
"""Grafana 대시보드 JSON 생성. (#28)

대시보드를 UI 로 만들면 재현이 안 된다. 누가 무엇을 왜 넣었는지도 남지 않는다.
여기서 만들고 결과 JSON 을 저장소에 커밋한다.

    python3 scripts/make_dashboard.py

패널을 바꾸려면 이 파일을 고치고 다시 돌린다. JSON 을 직접 편집하지 않는다.
"""
import json
import pathlib

OUT = pathlib.Path("observability/grafana/dashboards/edumeet-baseline.json")
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

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(dashboard, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{OUT}  패널 {len(panels)}개")
