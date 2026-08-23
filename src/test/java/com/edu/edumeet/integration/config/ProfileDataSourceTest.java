package com.edu.edumeet.integration.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필별 datasource 설정을 검증한다. (#49)
 *
 * <h3>왜 이 테스트가 있나</h3>
 * #47 에서 프로필 YAML 을 합치면서 {@code ${DB_URL}} 을 <b>local 문서에만</b> 넣었다.
 * prod 문서에는 datasource 가 없어서 {@code .env} 의 값을 아무도 읽지 않았고,
 * <b>운영 컨테이너가 임베디드 H2 로 떴다.</b>
 *
 * <pre>
 * Caused by: org.h2.jdbc.JdbcSQLSyntaxErrorException:
 *   Syntax error in SQL statement "... engine[*]=InnoDB"
 * </pre>
 *
 * <p>MySQL 문법인 V1 baseline 을 H2 에 실행하려다 죽은 것이다.
 *
 * <h3>왜 스프링 컨텍스트를 안 띄우나</h3>
 * prod 프로필로 컨텍스트를 띄우면 실제 MySQL 에 붙으려 한다.
 * 여기서 확인할 것은 <b>연결이 되는가</b> 가 아니라 <b>어떤 DB 를 가리키는가</b> 이므로
 * 설정 파일을 직접 읽는다.
 *
 * <p>H2 가 클래스패스에 없는지 단언하는 편이 더 근본적이지만,
 * <b>테스트 클래스패스에는 H2 가 있어야 하므로</b> 그 방법은 쓸 수 없다.
 * 대신 {@code build.gradle.kts} 에서 {@code runtimeOnly} 를 걷어냈다.
 */
@DisplayName("프로필 datasource")
class ProfileDataSourceTest {

    /** 프로필 이름 → 그 프로필이 실제로 쓰게 되는 datasource.url */
    private Map<String, String> datasourceUrlByProfile() throws Exception {
        List<Map<String, Object>> documents = new ArrayList<>();
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            for (Object doc : new Yaml().loadAll(in)) {
                if (doc instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    documents.add(typed);
                }
            }
        }

        String common = url(documents.get(0));
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> doc : documents.subList(1, documents.size())) {
            String profile = profileOf(doc);
            String own = url(doc);
            // 프로필이 안 덮으면 공통을 상속한다
            result.put(profile, own != null ? own : common);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String url(Map<String, Object> doc) {
        Map<String, Object> spring = (Map<String, Object>) doc.get("spring");
        if (spring == null) return null;
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        return datasource == null ? null : (String) datasource.get("url");
    }

    @SuppressWarnings("unchecked")
    private String profileOf(Map<String, Object> doc) {
        Map<String, Object> spring = (Map<String, Object>) doc.get("spring");
        Map<String, Object> config = (Map<String, Object>) spring.get("config");
        Map<String, Object> activate = (Map<String, Object>) config.get("activate");
        return (String) activate.get("on-profile");
    }

    @Test
    @DisplayName("★ 운영은 MySQL 을 가리킨다 - H2 로 뜨면 안 된다")
    void prod_points_to_mysql() throws Exception {
        String prodUrl = datasourceUrlByProfile().get("prod");

        assertThat(prodUrl)
                .as("prod 문서가 datasource 를 안 두면 공통을 상속해야 한다. "
                    + "둘 다 없으면 Spring 이 임베디드 DB 로 폴백한다")
                .isNotNull();
        assertThat(prodUrl)
                .as("운영이 H2 로 뜨면 MySQL 문법 마이그레이션이 전부 깨진다")
                .doesNotContain("h2:")
                .contains("jdbc:mysql");
        assertThat(prodUrl)
                .as("실제 주소는 .env 에서 온다. 하드코딩되어 있으면 안 된다")
                .contains("${DB_URL");
    }

    @Test
    @DisplayName("local 도 MySQL 이다 - 운영과 같은 DB 로 개발한다")
    void local_points_to_mysql() throws Exception {
        assertThat(datasourceUrlByProfile().get("local"))
                .doesNotContain("h2:")
                .contains("jdbc:mysql");
    }

    @Test
    @DisplayName("test 만 H2 다 - clone 직후 추가 설정 없이 돌아야 한다")
    void only_test_uses_h2() throws Exception {
        Map<String, String> urls = datasourceUrlByProfile();

        assertThat(urls.get("test")).contains("jdbc:h2:mem");
        assertThat(urls)
                .as("H2 를 쓰는 프로필은 test 하나뿐이어야 한다")
                .allSatisfy((profile, url) -> {
                    if (!"test".equals(profile)) {
                        assertThat(url).as("%s 프로필", profile).doesNotContain("h2:");
                    }
                });
    }
}
