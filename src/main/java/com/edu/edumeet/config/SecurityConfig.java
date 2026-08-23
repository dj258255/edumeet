package com.edu.edumeet.config;

import com.edu.edumeet.config.jwt.JwtAuthenticationEntryPoint;
import com.edu.edumeet.config.jwt.JwtAuthenticationFilter;
import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.member.service.CustomOauth2UserService;
import com.edu.edumeet.util.CustomOAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiTokenFilter internalApiTokenFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;
    private final CustomOauth2UserService customOauth2UserService;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        log.info("SecurityFilterChain 설정 시작");
        
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .oauth2Login(oauth2 -> {
                    log.info("OAuth2 로그인 설정 적용");
                    oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOauth2UserService))
                        .successHandler(customOAuth2SuccessHandler);
                })

                // 이전에는 목록 마지막에 "/api/v1/**" 이 있어서 위에 나열한 경로가 전부 무의미했고
                // anyRequest().authenticated() 가 사실상 죽은 코드였다. (#23)
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(CorsUtils::isPreFlightRequest).permitAll();

                    // 관리 포트가 서비스 포트와 분리되어 있으면 actuator 를 통과시킨다. (#28)
                    //
                    // 지표는 URI 패턴·호출량·커넥션 수 같은 내부 정보를 담는다.
                    // 그래서 인증이 아니라 "네트워크로 격리" 한다 -
                    // docker-compose 에서 관리 포트는 expose 만 하고 publish 하지 않으므로
                    // 컨테이너 네트워크 안(Prometheus)에서만 닿는다.
                    //
                    // 포트 번호를 손으로 비교하지 않는다. management.server.port=0 (임의 포트) 이면
                    // server.port 도 0 일 수 있어서 "같은 포트" 로 잘못 판정되고, 규칙이 조용히 안 걸린다.
                    // Boot 가 이미 이 판단을 한다 - 0 은 DIFFERENT 로 친다.
                    //
                    // 매처는 URI 로 직접 판단한다. EndpointRequest.toAnyEndpoint() 도,
                    // requestMatchers("/actuator/**") 도 여기서는 걸리지 않는다 -
                    // 관리 포트를 분리하면 액추에이터가 별도 자식 컨텍스트에서 돌아가는데
                    // 이 필터 체인은 부모 컨텍스트에 있어서 핸들러 매핑을 해석하지 못하고
                    // 매칭이 "조용히" 실패한다. 예외가 아니라 401 로만 드러나서 찾기 어렵다.
                    //
                    // 서비스 포트에서는 이 경로가 아예 존재하지 않으므로(404) permitAll 이어도 안전하다.
                    if (ManagementPortType.get(environment) == ManagementPortType.DIFFERENT) {
                        String basePath = environment.getProperty("management.endpoints.web.base-path", "/actuator");
                        log.info("관리 포트가 분리되어 있다. {} 이하는 네트워크 격리로 보호한다.", basePath);
                        authorize.requestMatchers(r -> {
                            String uri = r.getRequestURI();
                            return uri.equals(basePath) || uri.startsWith(basePath + "/");
                        }).permitAll();
                    } else {
                        log.warn("관리 포트가 분리되지 않았다. actuator 는 health 외에는 인증을 요구한다.");
                    }

                    authorize.requestMatchers(
                                "/api/v1/members/signup",
                                "/api/v1/members/login",
                                "/api/v1/members/refresh",
                                "/login/oauth2/success",
                                "/oauth2/authorization/**",
                                "/api/v1/oauth2/token",
                                "/api/v1/oauth2/status",
                                "/login/oauth2/code/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                // 이메일 인증은 기본으로 꺼져 있다(#55). 규칙은 남긴다 -
                                // EMAIL_ENABLED=true 로 되살릴 때 이것까지 기억해야 하면 또 막힌다.
                                "/api/v1/members/send-code",
                                "/api/v1/members/verification",
                                "/api/v1/members/check-email",
                                // LiveKit 서버가 호출한다. 사용자 인증이 없으므로 permitAll 이지만
                                // WebhookReceiver 가 API 시크릿으로 서명한 JWT 를 검증한다.
                                "/api/v1/livekit/webhook",
                                // 컨테이너 healthcheck 와 로드밸런서가 부른다.
                                // show-details 를 when-authorized 로 막아 내부 정보는 안 나간다.
                                "/actuator/health",
                                "/actuator/health/**",
                                // WebSocket 핸드셰이크. 여기서 막지 않고 STOMP CONNECT 에서 JWT 를 본다. (#33)
                                // HTTP 필터는 핸드셰이크까지만 관여하고 이후 STOMP 프레임에는 관여하지 않으므로,
                                // 어차피 프레임 단계의 인증이 따로 필요하다. StompAuthChannelInterceptor 가 한다.
                                "/ws/**",
                                // Spring 은 처리되지 않은 요청을 /error 로 포워드한다.
                                // 여기가 인증을 요구하면 404 든 500 이든 전부 401 로 둔갑해서
                                // "인증이 잘못됐나" 를 한참 뒤진다. 실제 상태 코드가 나가게 열어둔다.
                                "/error"
                        ).permitAll()
                        // 서버 간 호출. 사용자 JWT 가 아니라 공유 시크릿으로 인증한다. (#27)
                        // 경로를 분리해야 "사용자 인증" 과 "서비스 인증" 규칙이 섞이지 않는다.
                            .requestMatchers("/api/v1/internal/**").hasRole("INTERNAL")
                            .anyRequest().authenticated();
                })

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}