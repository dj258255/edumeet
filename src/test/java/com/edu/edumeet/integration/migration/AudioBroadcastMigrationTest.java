package com.edu.edumeet.integration.migration;

import com.edu.edumeet.meeting.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오디오 방송 세션 타입이 DB 에 실제로 저장 가능한지 확인한다. (#65)
 *
 * <p>{@code session_type} 은 MySQL {@code ENUM} 이다.
 * <b>애플리케이션 enum 에만 값을 추가하면 저장 시점에 {@code Data truncated} 로 실패한다.</b>
 * H2 는 이 제약을 재현하지 못하므로 진짜 MySQL 에서 본다.
 */
@SpringBootTest
@Testcontainers
@DisplayName("오디오 방송 세션 타입 마이그레이션")
class AudioBroadcastMigrationTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("V4 가 적용된다")
    void v4_is_applied() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1", String.class))
                .contains("4");
    }

    @Test
    @DisplayName("★ ENUM 에 세 값이 모두 있다 - 애플리케이션 enum 과 어긋나면 저장이 실패한다")
    void column_accepts_all_session_types() {
        String columnType = jdbc.queryForObject(
                "SELECT column_type FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'meeting' " +
                "AND column_name = 'session_type'",
                String.class);

        assertThat(columnType).isNotNull();
        for (SessionType type : SessionType.values()) {
            assertThat(columnType)
                    .as("애플리케이션에 %s 가 있는데 컬럼에 없으면 Data truncated 로 저장이 실패한다", type)
                    .contains(type.name());
        }
    }

    @Test
    @DisplayName("기존 값의 순서가 바뀌지 않았다 - ENUM 은 순서 번호로 저장된다")
    void existing_values_keep_their_order() {
        String columnType = jdbc.queryForObject(
                "SELECT column_type FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'meeting' " +
                "AND column_name = 'session_type'",
                String.class);

        // ENUM('BROADCAST','INTERACTIVE','AUDIO_BROADCAST') 에서 값 목록만 뽑는다
        List<String> order = List.of(columnType
                .replaceAll("^enum\\(|\\)$", "").replace("'", "").split(","));

        assertThat(order)
                .as("기존 값 사이에 끼워 넣으면 이미 저장된 행의 의미가 바뀐다")
                .startsWith("BROADCAST", "INTERACTIVE");
    }
}
