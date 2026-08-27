package com.edu.edumeet.integration.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기존 DB 를 올리는 경로를 검증한다. (#149)
 *
 * <p><b>왜 따로 두는가.</b> {@link FlywayMigrationTest} 는 <b>빈 DB 에 V1 부터 전부
 * 적용하는 경로</b>만 본다. 그런데 운영에서 실제로 일어나는 일은 그것이 아니다 -
 * 스키마는 이미 있고 Flyway 이력 테이블만 없는 상태에서 시작한다.
 *
 * <p><b>그 경로가 위험한 이유.</b> {@code baseline-on-migrate: true} 는
 * 기존 스키마가 V1 과 <b>같은지 검증해 주지 않는다.</b> 스키마가 있고 이력이 없으면
 * "이미 V1 상태" 로 <b>표시만</b> 하고 V2 부터 돈다. V1 과 운영 DB 가 어긋나 있어도
 * 조용히 지나간다. Flyway 문서도 이 옵션이 <b>잘못된 DB 를 대상으로 실행되는 것을 막던
 * 안전장치를 없앤다</b>고 적고 있다.
 *
 * <p>그래서 이 시험이 고정하는 것은 두 가지다.
 * <ol>
 *   <li>기존 DB 에서 <b>V1 이 다시 실행되지 않는다</b> (실행되면 create table 이 충돌한다)</li>
 *   <li>그 뒤 <b>V2 부터 끝까지 실제로 적용된다</b></li>
 * </ol>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Flyway 기존 DB 업그레이드 경로")
class FlywayUpgradePathTest {

    /**
     * 전용 컨테이너를 쓴다. 이력 테이블을 지웠다 다시 만드는 시험이라
     * 다른 시험과 DB 를 공유하면 안 된다.
     *
     * <p>패치 버전을 못 박지 않은 것은 알고 있다(#149). 운영이 지금 어느 패치인지
     * 확인한 뒤 한 곳에서 고정한다.
     */
    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(MYSQL.getJdbcUrl());
        ds.setUsername(MYSQL.getUsername());
        ds.setPassword(MYSQL.getPassword());
        return ds;
    }

    @Test
    @DisplayName("★ 스키마만 있고 이력이 없는 DB 는 V1 을 건너뛰고 V2 부터 올라간다")
    void existing_schema_is_baselined_then_upgraded() {
        DataSource ds = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // 1) "기존 운영 DB" 를 만든다 - V1 까지만 적용한다.
        Flyway.configure().dataSource(ds)
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();

        // 2) 이력 테이블을 지운다. 스키마는 있는데 Flyway 를 쓴 적 없는 상태가 된다.
        jdbc.execute("DROP TABLE flyway_schema_history");
        assertThat(tables(jdbc))
                .as("스키마 자체는 남아 있어야 이 시험이 의미가 있다")
                .contains("meeting", "member");

        // 3) 운영과 같은 설정으로 올린다.
        Flyway.configure().dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).isNotEmpty();
        assertThat(history)
                .as("전부 성공해야 한다")
                .allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));

        // 4-a) V1 은 실행되지 않고 기준선으로 표시만 된다.
        //      실행됐다면 이미 있는 테이블에 create table 이 걸려 실패했을 것이다.
        assertThat(typeOf(history, "1"))
                .as("기존 DB 에서 V1 이 다시 실행되면 안 된다. 표시만 되어야 한다")
                .isEqualTo("BASELINE");

        // 4-b) 나머지는 실제로 돈다.
        assertThat(versionsOfType(history, "SQL"))
                .as("baseline 이후 마이그레이션은 전부 적용되어야 한다")
                .contains("2", "3", "4", "5", "6", "7", "8", "9")
                .doesNotContain("1");
    }

    @Test
    @DisplayName("업그레이드를 마친 스키마가 최신 마이그레이션의 결과를 갖는다")
    void upgraded_schema_has_latest_columns() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());

        assertThat(tables(jdbc))
                .as("V9 가 만드는 자막 저장 테이블")
                .contains("caption_segment");
        assertThat(tables(jdbc))
                .as("V5 가 지우는 테이블. 업그레이드 경로에서도 지워져야 한다")
                .doesNotContain("refresh_token");
    }

    private static String typeOf(List<Map<String, Object>> history, String version) {
        return history.stream()
                .filter(r -> version.equals(String.valueOf(r.get("version"))))
                .map(r -> String.valueOf(r.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("V" + version + " 이력이 없다"));
    }

    private static List<String> versionsOfType(List<Map<String, Object>> history, String type) {
        return history.stream()
                .filter(r -> type.equals(String.valueOf(r.get("type"))))
                .map(r -> String.valueOf(r.get("version")))
                .toList();
    }

    private static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
    }
}
