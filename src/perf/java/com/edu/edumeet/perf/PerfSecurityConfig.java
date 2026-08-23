package com.edu.edumeet.perf;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 벤치마크 엔드포인트만 인증 없이 연다.
 *
 * <p>운영 SecurityConfig 의 permitAll 목록을 건드리지 않는다. 그쪽에 경로를 추가하면
 * perf 프로파일이 아닐 때도 규칙이 남는다. 여기서 별도 필터 체인을 {@code @Order(1)} 로
 * 먼저 태우되 {@code /api/perf/**} 에만 적용한다. 운영에서는 이 설정 클래스 자체가
 * 로드되지 않으므로 규칙이 존재하지 않는다.
 */
@Profile("perf")
@Configuration
public class PerfSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain perfSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/perf/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
