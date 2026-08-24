package com.edu.edumeet.integration.email;

import com.edu.edumeet.email.service.AuthCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이메일 인증이 꺼진 상태를 고정한다. (#55)
 *
 * <p>카카오 로그인만 쓰기로 해서 SMTP 계정을 유지할 이유가 없어졌다.
 * 그리고 이 기능 때문에 메일 헬스가 DOWN 이 되어 <b>컨테이너가 unhealthy</b> 였다(#53).
 *
 * <p><b>지우지 않고 플래그로 껐다.</b> 지우면 되살릴 때 다시 짜야 하고,
 * "왜 껐는가" 라는 판단도 같이 사라진다.
 *
 * <p>끌 수 있었던 이유는 <b>회원가입이 이메일 인증을 요구하지 않기 때문</b>이다.
 * {@code MemberService} 는 {@code AuthCodeService} 를 참조하지 않는다 —
 * 이메일 인증은 프론트가 회원가입 전에 부르는 독립 기능이고 백엔드가 강제하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("이메일 인증 비활성화")
class EmailDisabledTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationContext context;

    @Test
    @DisplayName("★ 기본이 꺼짐이다 - 빈이 아예 등록되지 않는다")
    void email_beans_are_not_registered() {
        assertThat(context.getBeanNamesForType(AuthCodeService.class))
                .as("플래그가 꺼져 있으면 빈 자체가 없어야 한다. "
                    + "빈만 두고 호출을 막으면 '왜 안 되는지' 가 런타임에만 드러난다")
                .isEmpty();
    }

    @Test
    @DisplayName("인증 코드 발송 경로가 사라진다")
    void send_code_endpoint_is_gone() throws Exception {
        mockMvc.perform(post("/api/v1/members/send-code"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 코드 확인 경로가 사라진다")
    void verification_endpoint_is_gone() throws Exception {
        mockMvc.perform(post("/api/v1/members/verification"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("나머지는 그대로다 - 이메일 하나 끄는 게 다른 흐름을 건드리면 안 된다")
    void other_endpoints_still_work() throws Exception {
        // 로그인은 같은 /api/v1/members 아래에 있다. 컨트롤러를 조건부로 끄면서
        // 매핑이 통째로 사라지지 않았는지 본다. 본문이 없어 400 이 나지만 404 는 아니어야 한다.
        int status = mockMvc.perform(post("/api/v1/members/login"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("404 면 로그인 매핑까지 같이 사라진 것이다. "
                    + "본문이 없어 400 이 나는 것은 정상이다")
                .isNotEqualTo(404);
    }
}
