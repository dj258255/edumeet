package com.edu.edumeet.unit.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영에서 로그가 JSON 으로 나가는지 고정한다. (#166)
 *
 * <h3>왜 이 시험이 필요한가</h3>
 * {@code logback-spring.xml} 의 {@code <springProfile>} 은 <b>조용히 안 걸린다.</b>
 * 프로필 이름을 잘못 적거나 블록이 어긋나면 예외 없이 그 블록만 무시되고,
 * 로그는 기본 포맷(평문)으로 나간다.
 *
 * <p>그러면 <b>Loki 는 계속 로그를 받는다. 받기는 받는데 필드가 없다.</b>
 * {@code | json | meetingId="3"} 이 에러가 아니라 <b>0건</b>을 낸다.
 * 이 저장소에서 여러 번 잡은 모양이다 — 있는데 아무 일도 안 하는 설정.
 * 그때마다 <b>되돌려서 깨지는 시험</b>으로 고정해 왔다.
 *
 * <h3>어떻게 재나</h3>
 * 실제로 한 줄 찍어서 <b>그 줄이 JSON 으로 파싱되는지</b> 본다.
 * 설정 파일을 읽어 어펜더 종류를 보는 것보다 이쪽이 낫다 —
 * 인코더가 붙어 있어도 출력이 JSON 이 아닐 수 있다.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("프로필별 로그 포맷")
class LogFormatByProfileTest {

    private final LoggingSystem loggingSystem =
            LoggingSystem.get(LogFormatByProfileTest.class.getClassLoader());

    @AfterEach
    void restore() {
        MDC.clear();
        // 다음 시험이 이 설정을 물려받지 않게 되돌린다.
        loggingSystem.cleanUp();
        loggingSystem.initialize(context("test"), "classpath:logback-spring.xml", null);
    }

    @Test
    @DisplayName("운영이면 JSON 한 줄로 나가고 MDC 가 필드가 된다")
    void 운영이면_JSON(CapturedOutput output) throws Exception {
        initializeWith("prod");

        MDC.put("meetingId", "42");
        MDC.put("requestId", "abc12345");
        LoggerFactory.getLogger("com.edu.edumeet.자막").info("자막을 버렸다");

        java.util.List<String> hits = linesWith(output, "자막을 버렸다");
        assertThat(hits)
                .as("운영 프로필에서 어펜더가 둘이면 같은 줄이 두 번 나간다. 진단용 출력: %s", hits)
                .hasSize(1);
        JsonNode json = new ObjectMapper().readTree(hits.get(0));

        assertThat(json.path("message").asText()).isEqualTo("자막을 버렸다");
        assertThat(json.path("level").asText()).isEqualTo("INFO");
        assertThat(json.path("service").asText())
                .as("파이썬·MCP 로그와 섞일 때 어느 서비스인지 구분해야 한다")
                .isEqualTo("backend");
        assertThat(json.path("meetingId").asText())
                .as("MDC 가 최상위 필드로 안 올라오면 Loki 에서 회의별로 못 거른다. "
                        + "메시지 문자열을 정규식으로 파싱해야 하고, 그 정규식은 포맷이 바뀌면 조용히 0건이 된다")
                .isEqualTo("42");
        assertThat(json.path("requestId").asText()).isEqualTo("abc12345");
    }

    @Test
    @DisplayName("운영이 아니면 사람이 읽는 평문이다")
    void 개발이면_평문(CapturedOutput output) {
        initializeWith("local");

        LoggerFactory.getLogger("com.edu.edumeet.자막").info("사람이 읽는 줄");

        String line = lastLineWith(output, "사람이 읽는 줄");
        assertThat(line.trim())
                .as("개발에서 JSON 을 보면 docker logs 를 눈으로 못 읽는다")
                .doesNotStartWith("{");
    }

    /**
     * {@code springProfile} 은 로거 컨텍스트에 심어 둔 Environment 로 평가된다.
     * {@code initialize()} 만 부르면 그 자리가 비어 블록이 통째로 무시된다.
     */
    private void initializeWith(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        ch.qos.logback.classic.LoggerContext lc =
                (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        lc.putObject(org.springframework.core.env.Environment.class.getName(), env);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(env),
                "classpath:logback-spring.xml", null);
    }

    private LoggingInitializationContext context(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return new LoggingInitializationContext(env);
    }

    private String lastLineWith(CapturedOutput output, String needle) {
        java.util.List<String> hits = linesWith(output, needle);
        if (hits.isEmpty()) throw new AssertionError("찍은 줄을 못 찾았다: " + needle);
        return hits.get(hits.size() - 1);
    }

    private java.util.List<String> linesWith(CapturedOutput output, String needle) {
        return java.util.Arrays.stream(output.getAll().split("\\R"))
                .filter(l -> l.contains(needle))
                .toList();
    }
}
