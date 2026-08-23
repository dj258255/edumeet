package com.edu.edumeet.integration.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AWS 자격증명이 없어도 앱이 뜬다. (#51)
 *
 * <p>배포가 여기서 멈췄었다.
 * <pre>
 * Caused by: java.lang.NullPointerException: Access key ID cannot be blank.
 *   Error creating bean with name 's3Client'
 * </pre>
 *
 * <p>S3 는 첨부·썸네일·요약본에만 쓰인다. <b>그것 때문에 서비스 전체가 못 뜨는 건 과하다.</b>
 *
 * <p>다만 <b>조용히 넘어가면 안 된다.</b> {@code S3Config} 가 기동 시 경고를 남긴다 —
 * #49 에서 배운 게 정확히 그것이다. 설정 실수는 또 나고,
 * <b>그 실수가 조용해지는 쪽을 막는 게 더 중요하다.</b>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 자격증명을 아예 안 준 상태를 만든다. 기본값(not-configured)이 적용된다.
        "spring.cloud.aws.credentials.access-key=not-configured",
        "spring.cloud.aws.credentials.secret-key=not-configured",
})
@DisplayName("AWS 자격증명 없이 기동")
class S3OptionalCredentialsTest {

    @Autowired ApplicationContext context;

    @Test
    @DisplayName("★ 자격증명이 없어도 컨텍스트가 뜬다")
    void context_starts_without_credentials() {
        assertThat(context)
                .as("S3 하나 때문에 앱 전체가 못 뜨면 안 된다")
                .isNotNull();
        assertThat(context.getBean(S3Client.class))
                .as("빈은 만들어진다. 실제 호출은 그때 가서 실패한다")
                .isNotNull();
    }

    @Test
    @DisplayName("기본값이 비어 있지 않다 - 빈 문자열이면 NPE 로 죽는다")
    void placeholder_is_not_blank() {
        String accessKey = context.getEnvironment()
                .getProperty("spring.cloud.aws.credentials.access-key");

        assertThat(accessKey)
                .as("AwsBasicCredentials.create 는 빈 값에서 NPE 를 던진다")
                .isNotBlank();
    }
}
