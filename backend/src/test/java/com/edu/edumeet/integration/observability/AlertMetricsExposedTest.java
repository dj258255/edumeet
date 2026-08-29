package com.edu.edumeet.integration.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;

import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경보가 물어보는 지표가 실제로 나오는지 확인한다. (#139)
 *
 * <p><b>이 시험이 생긴 이유.</b> {@code WebSocketConfig} 의 JavaDoc 에 이렇게 적혀 있었다.
 *
 * <blockquote>느려지는 것은 지표에 드러난다(큐 길이가 상한에 붙는다)</blockquote>
 *
 * <b>그 지표가 없었다.</b> Spring Boot 의 executor 계측 자동 설정은 <b>빈</b>만 계측하는데,
 * 그 실행기는 {@code configureClientOutboundChannel} 안에서 직접 만들어져 빈이 아니었다.
 * 주석이 존재를 주장한 지표가 존재하지 않은 것이다 —
 * {@code docs/ops/07-declared-but-unused.md} 의 아홉 번째다.
 *
 * <p><b>그리고 경보 규칙과 지표를 따로 두면 같은 일이 또 난다.</b>
 * 규칙 파일이 없는 지표를 물어보면 Prometheus 는 <b>에러가 아니라 빈 결과</b>를 낸다.
 * 빈 결과는 "정상" 과 구분되지 않는다. 그래서 이 시험은 규칙 파일을 읽어
 * <b>거기 적힌 지표 이름이 실제 노출 목록에 있는지</b> 대조한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("경보용 지표 노출")
class AlertMetricsExposedTest {

    /** 저장소 루트의 경보 규칙. backend/ 에서 두 단계 위다. */
    private static final Path RULES = Path.of("..", "observability", "rules", "edumeet.yml");

    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate rest;

    private String scrape() {
        String body = rest.getForObject(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(body).as("/actuator/prometheus 가 비어 있다").isNotBlank();
        return body;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // 아웃바운드 큐 — #61 에서 107만까지 쌓여 84초에 OOM 났던 그 큐다
            "chat_channel_queued",
            "chat_channel_capacity",
            "chat_channel_active",
            // 비동기 저장 큐 — 밀리면 다시보기와 요약 입력이 빈다
            "chat_archive_queued",
            "caption_archive_queued",
            "caption_archive_dropped_total",
            // fan-out 비용의 본체
            "chat_fanout_recipients",
            "chat_sessions_active",
    })
    @DisplayName("★ 경보가 물어보는 지표가 실제로 나온다")
    void metric_is_exposed(String metric) {
        assertThat(scrape())
                .as("""
                    %s 가 /actuator/prometheus 에 없다.
                    없는 지표를 물어보면 Prometheus 는 에러가 아니라 빈 결과를 낸다 -
                    빈 결과는 "정상" 과 구분되지 않으므로 경보가 조용히 죽는다.""", metric)
                .contains(metric);
    }

    @Test
    @DisplayName("★ 채널 큐 지표에 inbound/outbound 가 나뉘어 나온다")
    void channel_queue_is_tagged_per_direction() {
        String body = scrape();
        assertThat(body)
                .as("아웃바운드가 붕괴 지점이다. 둘을 합쳐 내면 어디가 막혔는지 알 수 없다")
                .contains("channel=\"out\"")
                .contains("channel=\"in\"");
    }

    @Test
    @DisplayName("★ 상한을 지표로 함께 낸다 - 경보를 절대값으로 쓰지 않기 위해서")
    void capacity_is_exposed_so_alerts_can_use_a_ratio() {
        assertThat(scrape())
                .as("""
                    상한을 코드에서 바꿨는데 경보가 옛 절대값을 물어보면
                    경보는 조용히 무의미해진다. 비율로 쓰게 상한도 같이 낸다.""")
                .containsPattern("chat_channel_capacity\\{[^}]*channel=\"out\"[^}]*\\}\\s+20000");
    }

    /**
     * 앱이 내지 <b>않는</b> 지표를 쓰는 규칙군. (#173)
     *
     * <p>디스크 경보는 호스트 수집기(node-exporter)가 내는 값을 본다.
     * 백업 경보는 systemd 타이머가 파일로 쓰고 그 수집기가 읽어 올린 값을 본다.
     * 앱의 {@code /actuator/prometheus} 에는 당연히 없다.
     *
     * <p><b>여기 이름을 적는 것으로 검사를 면제하지 않는다.</b> 면제하면
     * 이 시험이 막으려던 바로 그 구멍이 다시 열린다 - 규칙은 있는데 지표가 없고,
     * 빈 결과는 "정상" 과 구분되지 않는다.
     * 대신 <b>보증하는 곳을 옮긴다</b>. 그 지표는 실행 중인 Prometheus 에
     * 직접 물어봐야 확인되므로 {@code scripts/verify-alerting.sh} 가 맡는다.
     * 아래 시험이 "옮겼는지" 를 검사한다.
     */
    private static final Set<String> HOST_RULE_GROUPS = Set.of("edumeet-host", "edumeet-backup");

    /** 실행 중인 Prometheus 에 직접 물어보는 쪽. */
    private static final Path VERIFY_SCRIPT = Path.of("..", "scripts", "verify-alerting.sh");

    @Test
    @DisplayName("★ 경보 규칙이 물어보는 지표가 전부 노출 목록에 있다")
    void every_metric_used_by_alert_rules_exists() throws Exception {
        assertThat(Files.exists(RULES))
                .as("경보 규칙 파일이 없다: %s", RULES.toAbsolutePath().normalize())
                .isTrue();

        String body = scrape();
        List<String> used = metricNamesIn(Files.readString(RULES), false);

        assertThat(used)
                .as("규칙 파일에서 지표 이름을 하나도 못 찾았다 - 파서가 깨졌을 수 있다")
                .isNotEmpty();

        for (String metric : used) {
            assertThat(body)
                    .as("""
                        경보 규칙이 %s 를 물어보는데 앱이 그 지표를 내지 않는다.
                        규칙을 고쳤으면 지표도 같이 확인해야 한다.""", metric)
                    .contains(metric);
        }
    }

    @Test
    @DisplayName("★ 앱이 안 내는 지표는 검사를 면제하는 게 아니라 확인하는 곳을 옮긴다")
    void host_metrics_are_verified_somewhere_else() throws Exception {
        List<String> hostMetrics = metricNamesIn(Files.readString(RULES), true);

        assertThat(hostMetrics)
                .as("""
                    호스트 규칙군에서 지표를 하나도 못 찾았다.
                    규칙군 이름을 바꿨다면 HOST_RULE_GROUPS 도 같이 고쳐야 한다 -
                    안 고치면 그 지표들이 위 시험에서 앱 지표로 취급돼 빨개진다.""")
                .isNotEmpty();

        assertThat(Files.exists(VERIFY_SCRIPT))
                .as("확인을 넘긴 곳이 없다: %s", VERIFY_SCRIPT.toAbsolutePath().normalize())
                .isTrue();

        String script = Files.readString(VERIFY_SCRIPT);
        for (String metric : hostMetrics) {
            assertThat(script)
                    .as("""
                        %s 는 앱이 내지 않으므로 여기서는 확인할 수 없다.
                        그러면 아무도 확인하지 않는 지표가 된다 -
                        규칙은 있는데 값이 없고, 빈 결과는 "정상" 과 구분되지 않는다.
                        scripts/verify-alerting.sh 의 확인 목록에 넣어야 한다.""", metric)
                    .contains(metric);
        }
    }

    /**
     * 규칙의 {@code expr} 에서 지표 이름만 뽑는다.
     *
     * <p>YAML 을 정규식으로 긁지 않는다 - 처음엔 그렇게 했는데 {@code expr} 블록이
     * 어디서 끝나는지 못 잡아 뒤따르는 {@code labels:} 키를 지표로 오인했다.
     * 들여쓰기를 아는 것은 파서다.
     *
     * <p>PromQL 자체는 파싱하지 않는다. 함수 이름·라벨 값·숫자를 걸러내고
     * 지표처럼 생긴 식별자만 남기는 정도다 - 이 시험의 목적은 문법 검증이 아니라
     * <b>"규칙과 지표가 같이 움직이는가"</b> 이므로 그 정도로 충분하다.
     */
    @SuppressWarnings("unchecked")
    private List<String> metricNamesIn(String yaml, boolean hostGroupsOnly) {
        Map<String, Object> root = new Yaml().load(yaml);
        List<String> names = new ArrayList<>();

        for (Map<String, Object> group : (List<Map<String, Object>>) root.get("groups")) {
            if (HOST_RULE_GROUPS.contains(String.valueOf(group.get("name"))) != hostGroupsOnly) {
                continue;
            }
            for (Map<String, Object> rule : (List<Map<String, Object>>) group.get("rules")) {
                Object expr = rule.get("expr");
                if (expr != null) {
                    collectMetricNames(expr.toString(), names);
                }
            }
        }
        return names;
    }

    private static final Set<String> PROMQL_FUNCTIONS = Set.of(
            "rate", "irate", "increase", "delta", "deriv", "changes", "absent",
            "sum", "avg", "max", "min", "count", "topk", "bottomk", "quantile",
            "histogram_quantile", "predict_linear", "clamp_max", "clamp_min",
            "by", "without", "on", "ignoring", "offset", "and", "or", "unless",
            // *_over_time 계열. 하나를 쓰면 나머지도 곧 쓰게 되므로 같이 적는다.
            "count_over_time", "avg_over_time", "sum_over_time", "min_over_time",
            "max_over_time", "last_over_time", "present_over_time",
            "stddev_over_time", "stdvar_over_time", "quantile_over_time",
            "absent_over_time", "time", "vector", "scalar", "round", "abs");

    /** {@code by (job, instance)} 처럼 괄호 안에 라벨 이름을 나열하는 절. */
    private static final Pattern GROUPING = Pattern.compile(
            "\\b(by|without|on|ignoring|group_left|group_right)\\s*\\([^)]*\\)");

    private void collectMetricNames(String expr, List<String> into) {
        // 라벨 셀렉터 안({...})은 지표 이름이 아니다. 통째로 지운 뒤 식별자를 본다.
        String cleaned = expr.replaceAll("\\{[^}]*}", " ");
        // ★ 집계 절의 라벨 목록도 지운다.
        //   sum by (job, instance) (...) 에서 job·instance 는 라벨이지 지표가 아닌데,
        //   중괄호 밖이라 위에서 안 걸린다. 실제로 이걸 안 지워서 시험이 빨개졌다.
        cleaned = GROUPING.matcher(cleaned).replaceAll(" ");
        Matcher ident = Pattern.compile("\\b([a-z][a-z0-9_]{4,})\\b").matcher(cleaned);
        while (ident.find()) {
            String name = ident.group(1);
            if (!PROMQL_FUNCTIONS.contains(name) && !into.contains(name)) {
                into.add(name);
            }
        }
    }
}
