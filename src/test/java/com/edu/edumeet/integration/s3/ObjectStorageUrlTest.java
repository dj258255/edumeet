package com.edu.edumeet.integration.s3;

import com.edu.edumeet.s3.util.S3Uploader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 객체 저장소 주소와 읽기 링크. (#64)
 *
 * <p><b>왜 이 테스트가 필요한가</b> — 주소가 AWS 형식으로 하드코딩돼 있었다.
 * <pre>
 * https://{bucket}.s3.{region}.amazonaws.com/{key}
 * </pre>
 * 이 문자열이 <b>DB 에 그대로 저장</b>되기 때문에, 스토리지를 바꾸면
 * 기존 데이터에 박힌 도메인 때문에 파일을 못 찾는다.
 * Cloudflare R2 는 경로 스타일이라 형식 자체가 다르다.
 *
 * <p>그리고 업로드가 더 이상 {@code PUBLIC_READ} 가 아니다.
 * <b>주소를 아는 사람이 곧 볼 자격이 있는 사람</b>이던 구조를 끝냈다.
 */
@DisplayName("객체 저장소 주소")
class ObjectStorageUrlTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @DisplayName("AWS S3 (엔드포인트 미지정)")
    class Aws {

        @Autowired S3Uploader s3Uploader;

        @Test
        @DisplayName("가상 호스트 스타일 주소를 만든다")
        void builds_virtual_hosted_url() {
            assertThat(s3Uploader.objectUrl("board/abc_file.png"))
                    .isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/board/abc_file.png");
        }

        @Test
        @DisplayName("★ 읽기 링크는 서명된 URL 이다 - 공개 주소가 아니다")
        void read_link_is_signed() {
            String link = s3Uploader.presignedGetUrl(
                    "https://test-bucket.s3.ap-northeast-2.amazonaws.com/board/abc_file.png",
                    Duration.ofMinutes(15));

            assertThat(link)
                    .as("서명이 없으면 그냥 공개 주소다. 그러면 고친 의미가 없다")
                    .contains("X-Amz-Signature")
                    .contains("X-Amz-Expires");
        }

        @Test
        @DisplayName("null 주소는 null 을 준다 - 파일이 없는 경우가 정상 경로다")
        void null_url_stays_null() {
            assertThat(s3Uploader.presignedGetUrl(null, Duration.ofMinutes(1))).isNull();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties =
            "spring.cloud.aws.s3.endpoint=https://acct.r2.cloudflarestorage.com")
    @DisplayName("Cloudflare R2 (엔드포인트 지정)")
    class R2 {

        @Autowired S3Uploader s3Uploader;

        @Test
        @DisplayName("★ 경로 스타일 주소를 만든다 - AWS 도메인이 들어가면 안 된다")
        void builds_path_style_url() {
            String url = s3Uploader.objectUrl("board/abc_file.png");

            assertThat(url)
                    .isEqualTo("https://acct.r2.cloudflarestorage.com/test-bucket/board/abc_file.png");
            assertThat(url)
                    .as("주소가 DB 에 저장되므로 여기에 AWS 도메인이 박히면 이전이 불가능해진다")
                    .doesNotContain("amazonaws.com");
        }

        /*
         * 키 추출은 내부 구현이라 직접 부르지 않는다.
         * 공개 동작(프리사인 결과)의 경로로 확인한다 - 잘못 뽑으면 경로가 어긋난다.
         */

        @Test
        @DisplayName("★ 예전에 저장된 AWS 주소도 읽을 수 있다 - 마이그레이션 없이")
        void reads_legacy_aws_url() {
            String link = s3Uploader.presignedGetUrl(
                    "https://test-bucket.s3.ap-northeast-2.amazonaws.com/board/abc_file.png",
                    Duration.ofMinutes(15));

            assertThat(link)
                    .as("R2 로 옮겨도 예전 주소로 저장된 파일을 찾을 수 있어야 한다")
                    .contains("/test-bucket/board/abc_file.png")
                    .contains("X-Amz-Signature");
        }

        @Test
        @DisplayName("경로 스타일 주소에서 버킷 이름이 키에 섞이지 않는다")
        void does_not_duplicate_bucket_in_key() {
            String link = s3Uploader.presignedGetUrl(
                    "https://acct.r2.cloudflarestorage.com/test-bucket/board/abc_file.png",
                    Duration.ofMinutes(15));

            assertThat(link)
                    .as("버킷을 두 번 넣으면 /test-bucket/test-bucket/... 이 된다")
                    .doesNotContain("test-bucket/test-bucket")
                    .contains("/test-bucket/board/abc_file.png");
        }
    }
}
