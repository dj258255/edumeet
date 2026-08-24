package com.edu.edumeet.integration.contract;

import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 가 실제로 노출하는 내부 API 가 공유 계약과 같은지 본다. (#91)
 *
 * <p><b>왜 이 테스트가 생겼나.</b>
 * 이 계약은 {@code docs/ops/03-internal-api-contract.md} 에 이미 적혀 있었다.
 * "파이썬이 바꿔야 하는 것" 이라는 표까지 있었고, 마지막 줄은
 * <i>"파이썬 저장소가 이 리포에 없어서 클라이언트 쪽 변경은 미반영이다"</i> 였다.
 *
 * <p>즉 <b>몰라서 안 고친 게 아니라 고칠 수 없는 위치에 있었다.</b>
 * 그리고 <b>문서는 CI 를 실패시키지 못한다.</b>
 * 그래서 계약을 기계가 읽는 파일로 옮기고, 양쪽 테스트가 그 파일을 원본으로 삼는다.
 *
 * <p>이 테스트가 잡는 것 — <b>Java 가 경로나 헤더 이름을 바꾸면 여기서 깨진다.</b>
 * 파이썬 쪽 테스트도 같은 파일을 읽으므로 양쪽이 동시에 움직이지 않으면 CI 가 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("내부 API 계약 - Java 와 파이썬이 같은 파일을 읽는다")
class InternalApiContractTest {

    /** 저장소 루트의 contracts/internal-api.json. backend/ 에서 두 단계 위다. */
    private static final Path CONTRACT = Path.of("..", "contracts", "internal-api.json");

    @Autowired RequestMappingHandlerMapping handlerMapping;

    private JsonNode contract() throws Exception {
        assertThat(Files.exists(CONTRACT))
                .as("공유 계약 파일이 없다: %s", CONTRACT.toAbsolutePath().normalize())
                .isTrue();
        return new ObjectMapper().readTree(Files.readString(CONTRACT));
    }

    private Set<String> declaredPaths() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(e -> patternsOf(e.getKey()).stream())
                .collect(Collectors.toSet());
    }

    private Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition() == null ? Set.of() : info.getPatternsCondition().getPatterns();
    }

    @Test
    @DisplayName("★ 계약에 적힌 경로를 Java 가 실제로 노출한다")
    void every_declared_endpoint_exists() throws Exception {
        Set<String> actual = declaredPaths();
        JsonNode endpoints = contract().get("endpoints");

        endpoints.fields().forEachRemaining(entry -> {
            String path = entry.getValue().get("path").asText();
            assertThat(actual)
                    .as("""
                        계약(%s)에 있는 경로를 Java 가 노출하지 않는다.
                        계약을 바꿨으면 파이썬 쪽(ai/tests)도 같이 바꿔야 한다.""",
                            entry.getKey())
                    .contains(path);
        });
    }

    @Test
    @DisplayName("★ 인증 헤더 이름이 계약과 같다")
    void auth_header_name_matches() throws Exception {
        assertThat(InternalApiTokenFilter.HEADER)
                .as("""
                    헤더 이름을 바꾸면 파이썬이 못 따라온다.
                    실제로 #27 에서 이 헤더를 도입하고 파이썬을 안 고쳐서 오래 403 이었다.""")
                .isEqualTo(contract().get("authHeader").asText());
    }

    @Test
    @DisplayName("★ 계약의 경로는 전부 보호 구간 아래에 있다")
    void all_endpoints_are_under_the_protected_prefix() throws Exception {
        JsonNode c = contract();
        String prefix = c.get("pathPrefix").asText();

        c.get("endpoints").fields().forEachRemaining(entry -> {
            String path = entry.getValue().get("path").asText();
            assertThat(path)
                    .as("""
                        %s 가 %s 밖에 있다. SecurityConfig 는 이 접두어에만
                        hasRole("INTERNAL") 을 건다 - 밖에 두면 인증 없이 열린다.""",
                            entry.getKey(), prefix)
                    .startsWith(prefix);
        });
    }

    @Test
    @DisplayName("계약이 실제 구현보다 좁지 않은지 - 보호 구간의 엔드포인트가 계약에 다 있다")
    void no_undeclared_internal_endpoint() throws Exception {
        JsonNode c = contract();
        String prefix = c.get("pathPrefix").asText();

        Set<String> declared = Set.copyOf(
                c.get("endpoints").findValuesAsText("path"));
        Set<String> actualInternal = declaredPaths().stream()
                .filter(p -> p.startsWith(prefix))
                .collect(Collectors.toSet());

        assertThat(actualInternal)
                .as("""
                    계약에 없는 내부 엔드포인트가 있다.
                    파이썬은 계약 파일만 보므로, 여기 없는 것은 아무도 부르지 않거나
                    부르는 쪽이 계약 밖에서 임의로 맞춘 것이다 - 둘 다 좋지 않다.""")
                .isSubsetOf(declared);
    }
}
