package com.edu.edumeet.util;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@Log4j2
@Transactional
@DisplayName("S3Uploader 테스트")
class S3UploaderTest {

    @MockitoBean  // 이게 맞습니다!
    private S3Client s3Client;

    @Autowired
    private S3Uploader s3Uploader;

    @TempDir
    Path tempDir;

    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        //테스트용 bucket 이름 설정
        ReflectionTestUtils.setField(s3Uploader, "bucket", "test-bucket");
        
        //테스트용 임시 파일 생성
        testFile = tempDir.resolve("uuid123_test-image.jpg").toFile();
        Files.write(testFile.toPath(), "test image content".getBytes());
    }

    @Test
    void 테스트_S3파일업로드_성공() {
        //Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        //When
        String result = s3Uploader.upload(testFile.getAbsolutePath());

        //Then
        assertNotNull(result);
        assertTrue(result.startsWith("https://"));
        assertTrue(result.contains("s3.amazonaws.com"));
        assertTrue(result.contains("uuid123_test-image.jpg"));
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
        assertFalse(testFile.exists()); // 원본 파일 삭제 확인
    }

    @Test
    void 테스트_섬네일파일_S3업로드() throws IOException {
        //Given
        File thumbnailFile = tempDir.resolve("s_uuid123_test-image.jpg").toFile();
        Files.write(thumbnailFile.toPath(), "thumbnail content".getBytes());
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        //When
        String result = s3Uploader.upload(thumbnailFile.getAbsolutePath());

        //Then
        assertNotNull(result);
        assertTrue(result.startsWith("https://"));
        assertTrue(result.contains("s3.amazonaws.com"));
        assertTrue(result.contains("s_uuid123_test-image.jpg"));
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
        assertFalse(thumbnailFile.exists());
    }

    @Test
    void 테스트_S3업로드_예외발생() {
        //Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenThrow(new RuntimeException("S3 연결 실패"));

        //When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Uploader.upload(testFile.getAbsolutePath());
        });

        assertEquals("S3 연결 실패", exception.getMessage());
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
    }

    @Test
    void 테스트_S3파일삭제_성공() {
        // Given
        String fileName = "uuid123_test-image.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        //When
        assertDoesNotThrow(() -> {
            s3Uploader.removeS3File(fileName);
        });

        // Then
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 테스트_S3파일삭제_실패() {
        //Given
        String fileName = "uuid123_test-image.jpg";
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 삭제 권한 없음"));

        //When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Uploader.removeS3File(fileName);
        });

        assertEquals("S3 삭제 권한 없음", exception.getMessage());
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 테스트_일반파일PDF등_S3업로드() throws IOException {
        //Given
        File pdfFile = tempDir.resolve("uuid789_document.pdf").toFile();
        Files.write(pdfFile.toPath(), "PDF content".getBytes());
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = s3Uploader.upload(pdfFile.getAbsolutePath());

        // Then
        assertNotNull(result);
        assertTrue(result.startsWith("https://"));
        assertTrue(result.contains("s3.amazonaws.com"));
        assertTrue(result.contains("uuid789_document.pdf"));
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
        assertFalse(pdfFile.exists());
    }

    @Test
    void 테스트_이미지섬네일_삭제() {
        // Given
        String originalFileName = "uuid123_test-image.jpg";
        String thumbnailFileName = "s_uuid123_test-image.jpg";
        
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // When
        assertDoesNotThrow(() -> {
            s3Uploader.removeS3File(originalFileName);
            s3Uploader.removeS3File(thumbnailFileName);
        });

        // Then
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 테스트_빈파일_S3업로드() throws IOException {
        // Given
        File emptyFile = tempDir.resolve("uuid456_empty.txt").toFile();
        Files.write(emptyFile.toPath(), new byte[0]);
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = s3Uploader.upload(emptyFile.getAbsolutePath());

        // Then
        assertNotNull(result);
        assertTrue(result.startsWith("https://"));
        assertTrue(result.contains("s3.amazonaws.com"));
        assertTrue(result.contains("uuid456_empty.txt"));
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
        assertFalse(emptyFile.exists());
    }

    @Test
    void 테스트_특수문자파일명_업로드() throws IOException {
        // Given
        File koreanFile = tempDir.resolve("uuid789_한글파일명.jpg").toFile();
        Files.write(koreanFile.toPath(), "korean filename content".getBytes());
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        String result = s3Uploader.upload(koreanFile.getAbsolutePath());

        // Then
        assertNotNull(result);
        assertTrue(result.contains("한글파일명.jpg"));
        assertTrue(result.startsWith("https://"));
        assertTrue(result.contains("s3.amazonaws.com"));
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
    }

    @Test
    void 테스트_다중파일삭제() {
        // Given
        String[] fileNames = {
            "uuid1_image1.jpg", "s_uuid1_image1.jpg",
            "uuid2_document.pdf",
            "uuid3_image2.png", "s_uuid3_image2.png"
        };
        
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // When
        for (String fileName : fileNames) {
            assertDoesNotThrow(() -> {
                s3Uploader.removeS3File(fileName);
            });
        }

        // Then
        verify(s3Client, times(fileNames.length)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 테스트_존재하지않는파일_업로드실패() {
        // Given - 존재하지 않는 파일 경로
        String nonExistentFilePath = "/non/existent/uuid456_file.jpg";
        
        //Mock 설정: 파일이 없으면 AWS SDK에서 예외 발생
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class)))
                .thenThrow(new RuntimeException("NoSuchFileException: 파일을 찾을 수 없습니다"));

        //When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Uploader.upload(nonExistentFilePath);
        });

        //예외 메시지 확인
        assertEquals("NoSuchFileException: 파일을 찾을 수 없습니다", exception.getMessage());
        
        //putObject가 실제로 호출되었는지 확인 (파일이 없어도 호출됨)
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(Path.class));
    }
}