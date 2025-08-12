package com.edu.edumeet.config;

import com.edu.edumeet.config.jwt.JwtAuthenticationEntryPoint;
import com.edu.edumeet.config.jwt.JwtAuthenticationFilter;
import com.edu.edumeet.member.service.CustomOauth2UserService;
import com.edu.edumeet.util.CustomOAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;
    private final CustomOauth2UserService customOauth2UserService;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers(
                                "/api/v1/members/signup",
                                "/api/v1/members/login",
                                "/api/v1/members/refresh",
                                "/login/oauth2/success",
                                "/oauth2/authorization/**",
                                "/api/oauth2/token",
                                "/api/oauth2/status",
                                "/login/oauth2/code/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/api/v1/members/send-code",
                                "/api/v1/members/verification",
                                "/api/v1/members/check-email",
                                "api/v1/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}