package com.edu.edumeet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 엔티티에서 MySQL DDL 을 뽑는다. 평소에는 돌리지 않는다.
 * ./gradlew test --tests '*SchemaExportTest' 로 명시 실행하면 build/schema-mysql.sql 이 생긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/schema-mysql.sql",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.flyway.enabled=false"
})
class SchemaExportTest {
    @Test
    void export() { }
}
