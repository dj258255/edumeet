package com.edu.edumeet.attachment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 파일 업로드 결과를 위한 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "파일 업로드 결과 정보")
public class AttachmentResultDTO {
    
    @Schema(description = "파일 고유 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;
    
    @Schema(description = "원본 파일명", example = "example.jpg")
    private String fileName;
    
    @Schema(description = "이미지 파일 여부", example = "true")
    private boolean img;
    
    @Schema(description = "파일 크기(바이트)", example = "1048576")
    private Long fileSize;
    
    @Schema(description = "파일 MIME 타입", example = "image/jpeg")
    private String contentType;
    
    @Schema(description = "파일 도메인", example = "board")
    private String domain;
    
    @Schema(description = "참조 ID", example = "123")
    private Long referenceId;
    
    @Schema(description = "업로드 시간")
    private LocalDateTime uploadedAt;
    
    @Schema(description = "업로드한 사용자", example = "user@example.com")
    private String uploadedBy;

    // S3 URL 필드 - 직접 저장
    @Schema(description = "원본 파일 S3 URL", example = "https://bucket.s3.amazonaws.com/domain/uuid_filename.jpg")
    private String originalUrl;
    
    @Schema(description = "썸네일 파일 S3 URL (이미지인 경우)", example = "https://bucket.s3.amazonaws.com/domain/s_uuid_filename.jpg")
    private String thumbnailUrl;

    /**
     * 썸네일 파일의 S3 URL 반환 (이미지인 경우만)
     * thumbnailUrl이 설정되어 있으면 그 값을 사용
     */
    public String getThumbnailUrl() {
        if (!img) {
            return null;
        }
        return thumbnailUrl;
    }
    
    /**
     * 파일 확장자를 반환
     */
    @Schema(description = "파일 확장자", example = "jpg")
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 파일 크기를 MB 단위로 반환
     */
    @Schema(description = "파일 크기 (MB 단위)", example = "1.5")
    public double getFileSizeInMB() {
        if (fileSize == null) return 0.0;
        return fileSize / 1024.0 / 1024.0;
    }
}