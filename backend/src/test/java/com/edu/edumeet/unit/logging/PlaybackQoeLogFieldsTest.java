package com.edu.edumeet.unit.logging;

import com.edu.edumeet.meeting.broadcast.qoe.PlaybackQoeRecorder;
import com.edu.edumeet.meeting.broadcast.qoe.PlaybackQoeReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시청 품질 보고가 운영 JSON 로그에 <b>필드로</b> 나가는지 고정한다. (#197)
 *
 * <p>{@code StructuredArguments.kv(...)} 는 인코더에 {@code <arguments/>} 제공자가 없으면
 * <b>조용히 사라진다.</b> 에러도 안 나고 메시지만 남는다. 그 상태로 배포하면
 * Loki 에서 {@code stallMs} 로 거를 수 있고, 그 질의는 0건을 낸다.
 *
 * <p>그래서 {@code logback-spring.xml} 에 {@code <arguments/>} 를 넣었고,
 * <b>그 줄을 빼면 이 시험이 실패</b>하도록 실제로 찍은 줄을 JSON 으로 파싱한다.
 * {@code LogFormatByProfileTest} 와 같은 방식이다.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("시청 품질 로그 필드")
class PlaybackQoeLogFieldsTest {

    private final LoggingSystem loggingSystem =
            LoggingSystem.get(PlaybackQoeLogFieldsTest.class.getClassLoader());

    @AfterEach
    void restore() {
        loggingSystem.cleanUp();
        loggingSystem.initialize(context("test"), "classpath:logback-spring.xml", null);
    }

    @Test
    @DisplayName("★ 운영 JSON 에 sessionId·stallMs 가 최상위 필드로 나간다")
    void prod_json_has_structured_arguments(CapturedOutput output) throws Exception {
        initializeWith("prod");

        PlaybackQoeRecorder recorder = new PlaybackQoeRecorder(new SimpleMeterRegistry());
        boolean accepted = recorder.record(new PlaybackQoeReport(
                "sess-abc", 7, 30_000, 27_000, 3_000, 1, 1_200L, 0, true, false));
        assertThat(accepted).isTrue();

        List<String> hits = linesWith(output, "시청 품질 보고");
        assertThat(hits)
                .as("운영 프로필에서 줄이 안 나왔거나 여러 번 나갔다. 진단용 출력: %s", hits)
                .hasSize(1);

        JsonNode json = new ObjectMapper().readTree(hits.get(0));
        assertThat(json.path("message").asText()).isEqualTo("시청 품질 보고");
        assertThat(json.path("sessionId").asText())
                .as("kv 인자가 필드로 안 나가면 <arguments/> 가 빠진 것이다")
                .isEqualTo("sess-abc");
        assertThat(json.path("stallMs").asLong()).isEqualTo(3_000L);
        assertThat(json.path("stallCount").asLong()).isEqualTo(1L);
        assertThat(json.path("final").asBoolean()).isTrue();
        assertThat(json.path("native").asBoolean()).isFalse();
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

    private List<String> linesWith(CapturedOutput output, String needle) {
        return java.util.Arrays.stream(output.getAll().split("\\R"))
                .filter(l -> l.contains(needle))
                .toList();
    }
}
