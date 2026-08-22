package com.edu.edumeet.integration.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관측 엔드포인트가 실제로 존재하고, 인터넷 쪽 포트에는 노출되지 않는지 확인한다. (#28)
 *
 * <p><b>왜 실제 포트를 띄우는가</b> — 이 설계의 핵심은 <b>포트 분리</b>다.
 * MockMvc 는 포트 개념이 없어서 "관리 포트로만 열려 있다" 를 검증할 수 없다.
 * 두 포트를 실제로 띄워야 의미가 있다.
 *
 * <p>이전에는 {@code exposure.include} 에 {@code prometheus} 가 적혀 있었지만
 * {@code micrometer-registry-prometheus} 의존성이 없어서 <b>엔드포인트 자체가 404</b> 였다.
 * {@code include} 는 "있으면 열어라" 이지 "만들어라" 가 아니다.
 * <b>설정 파일만 보고 관측이 되어 있다고 믿으면 안 된다.</b>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")   // 0 = 임의 포트. 서비스 포트와 분리된다.   // 0 = 임의 포트. 8080 과 분리된다.
@ActiveProfiles("test")
@DisplayName("관측 엔드포인트")
class PrometheusEndpointTest {

    @LocalServerPort int serverPort;
    @LocalManagementPort int managementPort;

    @Autowired TestRestTemplate rest;
    @Autowired Environment environment;

    private ResponseEntity<String> get(int port, String path) {
        return rest.getForEntity("http://localhost:" + port + path, String.class);
    }

    @Test
    @DisplayName("Boot 가 관리 포트를 분리된 것으로 판정한다 - 보안 규칙이 여기에 달려 있다")
    void management_port_is_detected_as_separated() {
        assertThat(ManagementPortType.get(environment))
                .as("SAME 이면 SecurityConfig 의 actuator permitAll 규칙이 걸리지 않는다")
                .isEqualTo(ManagementPortType.DIFFERENT);
        assertThat(managementPort).isNotEqualTo(serverPort);
    }

    @Test
    @DisplayName("관리 포트에서 Prometheus 지표가 나온다")
    void prometheus_is_served_on_management_port() {
        ResponseEntity<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.getStatusCode())
                .as("레지스트리 의존성이 없으면 여기서 404 가 난다")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("JVM 지표는 레지스트리가 살아 있으면 무조건 나온다")
                .contains("jvm_memory_used_bytes")
                .as("공통 태그. 다중 인스턴스가 되면 이게 없어서 어느 서버인지 구분이 안 된다")
                .contains("application=\"edumeet\"");
    }

    @Test
    @DisplayName("HTTP 요청 히스토그램이 기록된다 - 평균이 아니라 꼬리를 봐야 한다")
    void http_histogram_is_recorded() {
        get(serverPort, "/api/v1/members/login");   // 지표를 하나 만든다

        assertThat(get(managementPort, "/actuator/prometheus").getBody())
                .as("percentiles-histogram 이 꺼져 있으면 bucket 계열이 안 나온다")
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    @DisplayName("★ 서비스 포트에서는 지표가 나오지 않는다")
    void prometheus_is_not_exposed_on_service_port() {
        ResponseEntity<String> response = get(serverPort, "/actuator/prometheus");

        assertThat(response.getStatusCode())
                .as("지표는 URI 패턴·호출량·커넥션 수 같은 내부 정보를 담는다. "
                    + "8080 은 인터넷에 열리는 포트이므로 여기서 나오면 안 된다")
                .isNotEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("헬스체크도 관리 포트로 옮겨간다 - Dockerfile HEALTHCHECK 가 여기를 봐야 한다")
    void health_moves_to_management_port() {
        // 테스트 환경에는 Redis 가 없어서 health 는 DOWN(503) 이다.
        // 여기서 확인하려는 건 "상태가 UP 인가" 가 아니라 "이 포트에서 응답하는가" 다.
        assertThat(get(managementPort, "/actuator/health").getStatusCode())
                .as("컨테이너 HEALTHCHECK 는 이 포트를 부른다. 401/404 면 영영 unhealthy 다")
                .isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(get(serverPort, "/actuator/health").getStatusCode())
                .as("포트를 분리하면 액추에이터는 서비스 포트에서 통째로 사라진다. "
                    + "이걸 모르면 Dockerfile 의 healthcheck 가 조용히 깨진다")
                .isNotEqualTo(HttpStatus.OK);
    }
}
