package com.edu.edumeet.integration.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨테이너 헬스체크가 보는 지표. (#53)
 *
 * <p>배포에서 앱은 <b>정상 기동했는데</b> 컨테이너가 unhealthy 로 남았다.
 * <pre>
 * Started EduMeetApplication in 32.812 seconds
 * edumeet-app   Up 2 minutes (unhealthy)
 * </pre>
 *
 * <p>HEALTHCHECK 가 {@code /actuator/health} 전체를 보는데, 메일 설정이 없어
 * 메일 헬스가 DOWN 이고 전체가 DOWN 이 됐다.
 *
 * <p><b>메일이 안 되는 것과 앱이 못 뜨는 것은 다른 문제다.</b>
 * 컨테이너가 알아야 하는 건 "모든 의존성이 살아 있나" 가 아니라
 * <b>"이 앱이 트래픽을 받을 수 있나"</b> 다.
 *
 * <p>테스트 환경도 메일 서버가 없다. 그래서 이 상황을 그대로 재현한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("readiness 프로브")
class ReadinessProbeTest {

    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate rest;
    @Autowired Environment environment;

    private ResponseEntity<String> get(String path) {
        return rest.getForEntity("http://localhost:" + managementPort + path, String.class);
    }

    @Test
    @DisplayName("★ 메일이 없어도 readiness 는 UP - 컨테이너를 죽이면 안 된다")
    void readiness_is_up_without_mail() {
        ResponseEntity<String> response = get("/actuator/health/readiness");

        assertThat(response.getStatusCode())
                .as("메일 하나 때문에 컨테이너가 죽으면 나머지 기능까지 같이 죽는다")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("전체 health 는 메일까지 보므로 DOWN 이다 - 그래서 컨테이너가 보면 안 된다")
    void full_health_includes_mail_and_is_down() {
        assertThat(get("/actuator/health").getStatusCode())
                .as("이 경로를 HEALTHCHECK 에 쓰면 메일 없이는 절대 healthy 가 못 된다")
                .isNotEqualTo(HttpStatus.OK);
    }

    /**
     * 응답 본문으로는 확인할 수 없다. {@code show-details: when-authorized} 라
     * 비인증 요청에는 {@code {"status":"UP"}} 만 온다 - 그게 의도한 동작이다.
     * 그래서 설정을 직접 본다.
     */
    @Test
    @DisplayName("readiness 그룹에 DB 와 Redis 가 들어 있다")
    void readiness_group_includes_db_and_redis() {
        String include = environment.getProperty(
                "management.endpoint.health.group.readiness.include");

        assertThat(include)
                .as("이게 비면 readiness 가 readinessState 만 보고 항상 UP 이 된다. "
                    + "DB 가 죽어도 컨테이너는 healthy 로 남는다")
                .isNotNull()
                .contains("db")
                .contains("redis");
        assertThat(include)
                .as("메일·S3 는 없어도 서비스가 의미를 잃지 않는다. 넣으면 안 된다")
                .doesNotContain("mail")
                .doesNotContain("s3");
    }
}
