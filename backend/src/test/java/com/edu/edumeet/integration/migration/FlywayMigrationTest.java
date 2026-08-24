package com.edu.edumeet.integration.migration;

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
 * Flyway 마이그레이션이 실제 MySQL 에서 도는지 확인한다. (#29)
 *
 * H2 로는 검증할 수 없다. V1 baseline 은 engine=InnoDB, enum(...) 같은
 * MySQL 전용 문법을 담고 있어서 H2 MySQL 모드에서도 깨진다.
 *
 * 이 테스트가 없으면 "마이그레이션을 넣었다" 는 주장은 배포 시점에야 검증된다.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Flyway 마이그레이션")
class FlywayMigrationTest {

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
        // 스키마는 Flyway 가 만든다. Hibernate 가 손대면 무엇이 만들었는지 알 수 없다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("빈 DB 에서 V1 baseline 이 실제로 실행된다")
    void v1_runs_on_empty_database() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);

        assertThat(applied)
                .as("빈 DB 이므로 baseline 으로 건너뛰지 않고 V1 이 실행되어야 한다")
                .contains("1");
    }

    /**
     * 마이그레이션을 전부 적용한 뒤 있어야 하는 테이블.
     *
     * <p>처음엔 개수를 셌는데 V2·V3 가 추가될 때마다 깨졌다. 이름으로 바꿨다.
     * 그리고 <b>V5 가 {@code refresh_token} 을 지우면서 또 깨졌다</b> —
     * 이 테스트는 "V1 이 만든 것" 이 아니라 <b>"지금 스키마가 무엇을 갖고 있는가"</b> 를 봐야 한다.
     *
     * <p>{@code refresh_token} 은 목록에서 뺐다. refresh token 은 Redis 로 옮겼다(#70).
     */
    private static final List<String> EXPECTED_TABLES = List.of(
            "assignment", "assignment_file_upload", "board", "board_category", "board_file_upload",
            "class_invite", "class_member", "class_room", "class_room_seq", "class_room_tags",
            "meeting", "meeting_participant", "member", "reply",
            "student_submission_status", "submission", "submission_file_upload");

    @Test
    @DisplayName("마이그레이션을 전부 적용하면 엔티티가 기대하는 테이블이 있다")
    void schema_has_all_expected_tables() {
        assertThat(allTables())
                .as("엔티티가 요구하는 테이블이 하나라도 없으면 부팅 시점이 아니라 첫 질의에서 죽는다")
                .containsAll(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("★ V5 가 refresh_token 을 지운다 - Redis 로 옮겼다")
    void v5_drops_refresh_token() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1", String.class))
                .contains("5");
        assertThat(allTables())
                .as("남겨두면 '어느 쪽이 진짜인가' 가 생긴다")
                .doesNotContain("refresh_token");
    }

    private List<String> allTables() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'",
                String.class);
    }

    @Test
    @DisplayName("Hibernate 가 이 스키마를 그대로 쓸 수 있다 - 컨텍스트가 뜨면 검증된 것")
    void hibernate_accepts_the_migrated_schema() {
        // ddl-auto=none 인데 컨텍스트가 떴다는 것은
        // Flyway 가 만든 스키마로 EntityManagerFactory 가 구성됐다는 뜻이다.
        // 컬럼 개수를 세는 단언은 마이그레이션이 추가될 때마다 깨진다.
        // 개수가 아니라 "엔티티가 요구하는 컬럼이 있는가" 를 본다.
        assertThat(columnsOf("meeting"))
                .contains("id", "class_room_id", "title", "start_time", "session_type", "s3url");
    }

    @Test
    @DisplayName("V2 가 요약본 컬럼을 추가한다")
    void v2_adds_summary_columns() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(applied).as("V2 도 적용되어야 한다").contains("2");

        assertThat(columnsOf("meeting"))
                .as("MD 와 PDF URL 을 각각 저장한다 (#27)")
                .contains("summary_md_url", "summary_pdf_url");
    }

    @Test
    @DisplayName("V3 가 채팅 테이블을 만든다")
    void v3_creates_chat_message() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class);
        assertThat(applied).contains("3");

        assertThat(allTables()).contains("chat_message");
        assertThat(columnsOf("chat_message"))
                .contains("meeting_id", "sender_email", "content", "sent_at");
    }

    @Test
    @DisplayName("★ V7 이 다시보기 채팅 조회용 복합 인덱스를 만든다")
    void v7_adds_chat_replay_index() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1", String.class))
                .contains("7");

        assertThat(columnsOf("chat_message"))
                .as("다시보기는 절대 시각이 아니라 재생 위치로 자른다")
                .contains("offset_millis");
        assertThat(indexesOf("chat_message"))
                .as("meeting_id + offset_millis 범위 조회라 sent_at 인덱스로는 질문이 다르다")
                .contains("idx_chat_message_replay");
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }

    private List<String> indexesOf(String table) {
        return jdbc.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }
}
