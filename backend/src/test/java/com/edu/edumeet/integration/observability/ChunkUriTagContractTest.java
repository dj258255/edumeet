package com.edu.edumeet.integration.observability;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조각 업로드의 {@code uri} 라벨이 규칙 파일에 적힌 문자열과 <b>같은지</b> 본다. (#234b 검토 5)
 *
 * <p><b>왜 이 시험이 필요한가.</b> 조각 SLI 는 이렇게 묻는다.
 *
 * <pre>http_server_requests_seconds_count{uri="/api/v1/meeting/{meetingId}/broadcast/chunk"}</pre>
 *
 * <p>Spring 은 그 태그를 <b>템플릿</b>으로 남긴다 - 회의 번호가 들어간 실제 경로가 아니다.
 * 그래서 규칙에 손으로 적은 문자열이 조금이라도 다르면(컨트롤러의 {@code @RequestMapping} 을
 * 바꿨거나 오타가 났거나) 그 셀렉터는 <b>빈 결과</b>를 낸다.
 * 빈 결과는 "정상" 과 구분되지 않는다 - SLO 경보가 조용히 죽는다.
 *
 * <p>{@code contracts/} 와 같은 발상이다. 규칙 파일과 코드를 따로 두면 한쪽만 바뀐다.
 * 그래서 여기서는 <b>규칙 파일을 읽어</b> 실제 요청이 만든 태그와 대조한다.
 *
 * <p><b>실제 요청을 보낸다.</b> {@code uri} 태그는 핸들러 매핑이 정하므로 요청이 매핑을
 * 통과해야만 붙는다. 인증은 실제 JWT 로 통과시킨다 - 401 이면 매핑에 닿지 못하고
 * 태그가 {@code /error} 가 된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("조각 업로드 uri 라벨 계약")
class ChunkUriTagContractTest {

    /** 저장소 루트의 경보 규칙. backend/ 에서 두 단계 위다. */
    private static final Path RULES = Path.of("..", "observability", "rules", "edumeet.yml");

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final long MEETING_ID = 987_654L;

    @LocalServerPort int port;
    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private String memberEmail;

    @BeforeEach
    void setUp() {
        memberEmail = "uri-contract-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            em.persist(Member.builder()
                    .email(memberEmail).nickname("발표자").password("x").build());
            em.flush();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                        "DELETE FROM Member m WHERE m.email = :email")
                .setParameter("email", memberEmail)
                .executeUpdate());
    }

    @Test
    @DisplayName("★ 규칙 파일의 uri 문자열이 실제 요청이 만든 태그와 같다")
    void chunk_uri_tag_matches_rules_file() throws Exception {
        assertThat(Files.exists(RULES))
                .as("경보 규칙 파일이 없다: %s", RULES.toAbsolutePath().normalize())
                .isTrue();

        Set<String> ruleUris = urisInRulesFile(Files.readString(RULES));
        assertThat(ruleUris)
                .as("규칙 파일에서 uri= 를 못 찾았다 - 파서가 깨졌거나 조각 SLI 가 사라졌다")
                .contains("/api/v1/meeting/{meetingId}/broadcast/chunk");

        // 실제로 조각 하나를 보낸다. 회의가 없으면 4xx 지만 매핑은 지나갔고 태그는 붙는다.
        String token = jwtService.generateAccessToken(1L, memberEmail);
        var response = rest.exchange(
                "http://localhost:" + port + "/api/v1/meeting/" + MEETING_ID + "/broadcast/chunk?seq=1",
                HttpMethod.POST,
                new HttpEntity<>(new byte[]{1, 2, 3}, bearer(token)),
                Void.class);
        assertThat(response.getStatusCode().value())
                .as("401 이면 매핑에 닿지 못해 uri 태그를 확인할 수 없다 (인증 설정이 바뀌었나)")
                .isNotEqualTo(401);

        Set<String> exposed = urisInScrape(scrape());
        assertThat(exposed)
                .as("""
                    요청을 보냈는데 http_server_requests_seconds_count 의 uri 태그가 없다.
                    요청 계측(WebMvcMetricsFilter)이 사라졌을 수 있다.""")
                .isNotEmpty();

        for (String ruleUri : ruleUris) {
            assertThat(exposed)
                    .as("""
                        규칙 파일은 uri="%s" 를 묻는데 실제 요청의 uri 태그는 %s 다.
                        이대로면 조각 SLI 는 빈 결과를 내고 SLO 경보가 조용히 죽는다 -
                        빈 결과는 "정상" 과 구분되지 않는다.""", ruleUri, exposed)
                    .contains(ruleUri);
        }
    }

    private String scrape() {
        String body = rest.getForObject(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(body).as("/actuator/prometheus 가 비어 있다").isNotBlank();
        return body;
    }

    /** 규칙 파일에서 {@code uri="..."} 의 값만 모은다. */
    private static Set<String> urisInRulesFile(String yaml) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("uri=\"([^\"]+)\"").matcher(yaml);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** 노출된 {@code http_server_requests_seconds_count} 의 uri 태그 값만 모은다. */
    private static Set<String> urisInScrape(String body) {
        Set<String> found = new LinkedHashSet<>();
        // ★ 라벨 값에 '}' 가 들어 있다 - uri 템플릿이 "/meeting/{meetingId}/..." 이기 때문이다.
        //   [^}]* 로 잡으면 거기서 끊겨 한 줄도 못 찾는다. 줄 끝까지 탐욕적으로 잡는다.
        Matcher line = Pattern.compile("^http_server_requests_seconds_count\\{(.*)}\\s",
                Pattern.MULTILINE).matcher(body);
        while (line.find()) {
            Matcher uri = Pattern.compile("uri=\"([^\"]*)\"").matcher(line.group(1));
            while (uri.find()) {
                found.add(uri.group(1));
            }
        }
        return found;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setBearerAuth(token);
        return headers;
    }
}
