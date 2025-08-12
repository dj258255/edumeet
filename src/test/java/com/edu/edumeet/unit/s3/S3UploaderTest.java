package com.edu.edumeet.unit.s3;

import com.edu.edumeet.s3.util.S3Uploader;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Log4j2
@DisplayName("S3Uploader 단위테스트")
class S3UploaderTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Uploader s3Uploader;

    @TempDir
    Path tempDir;

    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        s3Uploader = new S3Uploader(s3Client, s3Presigner);
        
        // 테스트용 필드 설정
        ReflectionTestUtils.setField(s3Uploader, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Uploader, "region", "us-east-1");
        
        // 테스트용 임시 파일 생성
        testFile = tempDir.resolve("uuid123_test-image.jpg").toFile();
        Files.write(testFile.toPath(), "test image content".getBytes());
    }

    @Test
    @DisplayName("URL 생성 테스트 - 원본 파일")
    void 테스트_원본파일URL생성() {
        // Given
        String uuid = "test-uuid-123";
        String fileName = "image.jpg";
        
        // When
        String url = s3Uploader.getOriginalUrl(uuid, fileName);
        
        // Then
        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/test-uuid-123_image.jpg", url);
        assertTrue(url.contains(uuid));
        assertTrue(url.contains(fileName));
        assertTrue(url.startsWith("https://"));
        assertTrue(url.contains("s3.us-east-1.amazonaws.com"));
    }

    @Test
    @DisplayName("URL 생성 테스트 - 썸네일 파일")
    void 테스트_썸네일URL생성() {
        // Given
        String uuid = "test-uuid-456";
        String fileName = "photo.png";
        
        // When
        String url = s3Uploader.getThumbnailUrl(uuid, fileName);
        
        // Then
        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/s_test-uuid-456_photo.png", url);
        assertTrue(url.contains("s_" + uuid));
        assertTrue(url.contains(fileName));
        assertTrue(url.startsWith("https://"));
    }

    @Test
    @DisplayName("URL 생성 테스트 - 도메인별 원본 파일")
    void 테스트_도메인별원본URL생성() {
        // Given
        String domain = "board";
        String uuid = "uuid-789";
        String fileName = "document.pdf";
        
        // When
        String url = s3Uploader.getDomainOriginalUrl(domain, uuid, fileName);
        
        // Then
        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/board/uuid-789_document.pdf", url);
        assertTrue(url.contains(domain));
        assertTrue(url.contains(uuid));
        assertTrue(url.contains(fileName));
    }

    @Test
    @DisplayName("URL 생성 테스트 - 도메인별 썸네일 파일")
    void 테스트_도메인별썸네일URL생성() {
        // Given
        String domain = "classroom";
        String uuid = "uuid-abc";
        String fileName = "thumbnail.jpg";
        
        // When
        String url = s3Uploader.getDomainThumbnailUrl(domain, uuid, fileName);
        
        // Then
        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/classroom/s_uuid-abc_thumbnail.jpg", url);
        assertTrue(url.contains(domain));
        assertTrue(url.contains("s_" + uuid));
        assertTrue(url.contains(fileName));
    }

    @Test
    @DisplayName("파일 업로드 테스트 - URL 생성 확인")
    void 테스트_파일업로드_URL완성도검증() {
        // Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = s3Uploader.upload(testFile.getAbsolutePath());

        // Then
        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://"));
        assertTrue(resultUrl.contains("s3.us-east-1.amazonaws.com"));
        assertTrue(resultUrl.contains("uuid123_test-image.jpg"));
        assertTrue(resultUrl.contains("test-bucket"));
        
        // S3 클라이언트 호출 검증
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
        
        // 원본 파일 삭제 확인
        assertFalse(testFile.exists());
    }

    @Test
    @DisplayName("파일 업로드 테스트 - 디렉토리 지정")
    void 테스트_디렉토리지정_파일업로드() {
        // Given
        String directory = "uploads/images";
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String resultUrl = s3Uploader.upload(testFile.getAbsolutePath(), directory);

        // Then
        assertNotNull(resultUrl);
        assertTrue(resultUrl.contains(directory + "/uuid123_test-image.jpg"));
        
        // PutObjectRequest 검증
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(Path.class));
        
        PutObjectRequest request = captor.getValue();
        assertEquals("test-bucket", request.bucket());
        assertTrue(request.key().startsWith(directory + "/"));
    }

    @Test
    @DisplayName("Presigned URL 생성 테스트")
    void 테스트_PreSignedURL생성() throws Exception {
        // Given
        String directory = "temp";
        String uuid = "presign-uuid";
        String fileName = "upload.jpg";
        Duration duration = Duration.ofMinutes(10);
        
        URL mockUrl = new URL("https://test-bucket.s3.us-east-1.amazonaws.com/temp/presign-uuid_upload.jpg?presigned=true");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        String presignedUrl = s3Uploader.generatePresignedUrl(directory, uuid, fileName, duration);

        // Then
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains("temp/presign-uuid_upload.jpg"));
        assertTrue(presignedUrl.contains("presigned=true"));
        
        verify(s3Presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("파일 삭제 테스트")
    void 테스트_S3파일삭제() {
        // Given
        String fileName = "uuid456_delete-test.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // When
        assertDoesNotThrow(() -> s3Uploader.removeS3File(fileName));

        // Then
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        
        DeleteObjectRequest request = captor.getValue();
        assertEquals("test-bucket", request.bucket());
        assertEquals(fileName, request.key());
    }

    @Test
    @DisplayName("URL 파싱 테스트 - UUID 추출")
    void 테스트_URL에서UUID추출() {
        // Given
        String url = "https://test-bucket.s3.us-east-1.amazonaws.com/uuid-test-123_filename.jpg";
        
        // When
        String uuid = s3Uploader.extractUuidFromUrl(url);
        
        // Then
        assertEquals("uuid-test-123", uuid);
    }

    @Test
    @DisplayName("URL 파싱 테스트 - 파일명 추출")
    void 테스트_URL에서파일명추출() {
        // Given
        String url = "https://test-bucket.s3.us-east-1.amazonaws.com/uuid-456_original-name.pdf";
        
        // When
        String fileName = s3Uploader.extractFileNameFromUrl(url);
        
        // Then
        assertEquals("original-name.pdf", fileName);
    }

    @Test
    @DisplayName("URL 파싱 테스트 - 전체 파일명 추출")
    void 테스트_URL에서전체파일명추출() {
        // Given
        String url = "https://test-bucket.s3.us-east-1.amazonaws.com/board/uuid-789_full-filename.docx";
        
        // When
        String fullFileName = s3Uploader.extractFullFileNameFromUrl(url);
        
        // Then
        assertEquals("uuid-789_full-filename.docx", fullFileName);
    }

    @Test
    @DisplayName("헬퍼 메서드 테스트 - 전체 파일명 생성")
    void 테스트_전체파일명생성() {
        // Given
        String uuid = "helper-uuid";
        String fileName = "test.jpg";
        
        // When
        String fullFileName = s3Uploader.createFullFileName(uuid, fileName);
        
        // Then
        assertEquals("helper-uuid_test.jpg", fullFileName);
    }

    @Test
    @DisplayName("헬퍼 메서드 테스트 - 썸네일 파일명 생성")
    void 테스트_썸네일파일명생성() {
        // Given
        String uuid = "thumb-uuid";
        String fileName = "image.png";
        
        // When
        String thumbnailFileName = s3Uploader.createThumbnailFileName(uuid, fileName);
        
        // Then
        assertEquals("s_thumb-uuid_image.png", thumbnailFileName);
    }

    @Test
    @DisplayName("특수 문자 파일명 URL 생성 테스트")
    void 테스트_특수문자파일명URL생성() {
        // Given
        String uuid = "korean-uuid";
        String fileName = "한글파일명.jpg";
        
        // When
        String url = s3Uploader.getOriginalUrl(uuid, fileName);
        
        // Then
        assertTrue(url.contains("한글파일명.jpg"));
        assertTrue(url.contains(uuid));
    }

    @Test
    @DisplayName("업로드 실패 예외 처리 테스트")
    void 테스트_업로드실패예외처리() {
        // Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenThrow(new RuntimeException("S3 업로드 실패"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Uploader.upload(testFile.getAbsolutePath());
        });

        assertTrue(exception.getMessage().contains("S3 업로드 실패"));
    }

    @Test
    @DisplayName("삭제 실패 예외 처리 테스트")
    void 테스트_삭제실패예외처리() {
        // Given
        String fileName = "fail-delete.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 삭제 권한 없음"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Uploader.removeS3File(fileName);
        });

        assertTrue(exception.getMessage().contains("S3 삭제 권한 없음"));
    }
}