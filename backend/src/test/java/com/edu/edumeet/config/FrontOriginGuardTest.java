package com.edu.edumeet.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영 허용 출처 검사. (#186)
 *
 * <p>실제로 운영을 멈춰 세웠던 값이 첫 번째 사례다.
 */
@DisplayName("운영 허용 출처")
class FrontOriginGuardTest {

    @Test
    @DisplayName("★ 실제로 운영을 막고 있던 값 - 줄바꿈이 빠져 두 변수가 붙었다")
    void the_value_that_actually_broke_production() {
        // .env 에 이렇게 있었다. FRONT_URL2 는 아예 존재하지 않게 됐다.
        String merged = "http://localhost:3000FRONT_URL2=http://localhost:5173";

        assertThatThrownBy(() -> FrontOriginGuard.check("FRONT_URL", merged))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("=")
                .hasMessageContaining("줄바꿈");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://localhost:3000",
            "https://localhost:3000",
            "http://127.0.0.1:5173",
            "https://studywithtymee.com https://www.studywithtymee.com",
            "http://studywithtymee.com",
    })
    @DisplayName("★ 운영에서 못 쓰는 값은 앱을 안 띄운다")
    void rejects_values_that_break_browsers(String bad) {
        assertThatThrownBy(() -> FrontOriginGuard.check("FRONT_URL", bad))
                .as("""
                    이 값이 통과하면 브라우저만 403 을 받는 상태로 배포된다.
                    부하 도구도 헬스체크도 Origin 을 안 보내므로 아무것도 못 잡는다.""")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("★ 비어 있어도 안 띄운다 - 안 정한 것과 잘못 정한 것은 결과가 같다")
    void rejects_blank() {
        // 표시 이름이 비면 JUnit 이 파라미터화 시험을 못 만든다.
        // 그래서 이 경우만 따로 둔다 - 값 목록에 빈 문자열을 넣으면
        // 시험이 "실패" 가 아니라 "실행 오류" 로 끝나 원인이 안 보인다.
        for (String blank : new String[] {"", "   ", null}) {
            assertThatThrownBy(() -> FrontOriginGuard.check("FRONT_URL", blank))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "https://studywithtymee.com",
            "https://www.studywithtymee.com",
            "https://*.studywithtymee.com",
    })
    @DisplayName("정상 값은 통과한다 - 다 막으면 고칠 방법이 없어진다")
    void accepts_production_origins(String good) {
        assertThatCode(() -> FrontOriginGuard.check("FRONT_URL", good))
                .doesNotThrowAnyException();
    }
}
