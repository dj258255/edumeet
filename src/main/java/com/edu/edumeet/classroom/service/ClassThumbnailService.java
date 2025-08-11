package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.dto.response.ThumbnailUploadResultDto;
import com.edu.edumeet.s3.util.LocalUploader;
import com.edu.edumeet.s3.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 간소화된 썸네일 서비스
 * - 별도 데이터베이스 테이블 없음
 * - 임시 저장소 사용 (실제 프로덕션에서는 Redis 권장)
 * - 클래스 생성 시까지만 임시 보관
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class ClassThumbnailService {
    
    private final LocalUploader localUploader;
    private final S3Uploader s3Uploader;
    
    // 임시 저장소 (실제 프로덕션에서는 Redis 등 사용 권장)
    private final Map<String, ThumbnailUploadResultDto> temporaryThumbnailStorage = new ConcurrentHashMap<>();
    
    // 허용되는 이미지 파일 확장자
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = 
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");
    
    // 최대 파일 크기 (5MB)
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 썸네일 업로드 (임시 저장)
     * 클래스 생성 시까지 메모리에 보관
     */
    public ThumbnailUploadResultDto uploadThumbnail(MultipartFile file) {
        log.info("썸네일 업로드 시작 - 파일명: {}, 크기: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        // 1. 파일 검증
        validateFile(file);

        try {
            // 2. 로컬 업로드 + 썸네일 생성
            List<String> localPaths = localUploader.uploadLocal(file);
            
            if (localPaths == null || localPaths.isEmpty()) {
                throw new RuntimeException("로컬 업로드에 실패했습니다.");
            }

            String uuid = extractUuidFromPath(localPaths.get(0));
            String originalFileName = file.getOriginalFilename();
            
            log.info("로컬 업로드 완료 - UUID: {}, 생성된 파일 수: {}", uuid, localPaths.size());

            // 3. S3 업로드
            String originalUrl = s3Uploader.upload(localPaths.get(0)); // 원본
            String thumbnailUrl = originalUrl; // 기본값
            
            if (localPaths.size() > 1) {
                thumbnailUrl = s3Uploader.upload(localPaths.get(1)); // 썸네일
                log.info("썸네일 S3 업로드 완료 - URL: {}", thumbnailUrl);
            }
            
            log.info("원본 S3 업로드 완료 - URL: {}", originalUrl);

            // 4. 임시 저장소에 보관
            ThumbnailUploadResultDto result = ThumbnailUploadResultDto.builder()
                    .uuid(uuid)
                    .fileName(originalFileName)
                    .originalUrl(originalUrl)
                    .thumbnailUrl(thumbnailUrl)
                    .isImage(true)
                    .build();
            
            temporaryThumbnailStorage.put(uuid, result);
            log.info("썸네일 임시 저장 완료 - UUID: {}", uuid);
            
            // 5. 30분 후 자동 정리 스케줄링
            scheduleCleanup(uuid);
            
            return result;

        } catch (Exception e) {
            log.error("썸네일 업로드 실패 - 파일: {}, 오류: {}", file.getOriginalFilename(), e.getMessage());
            throw new RuntimeException("썸네일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * UUID로 썸네일 정보 조회
     */
    public Optional<ThumbnailUploadResultDto> getThumbnailInfo(String uuid) {
        ThumbnailUploadResultDto result = temporaryThumbnailStorage.get(uuid);
        if (result != null) {
            log.info("썸네일 정보 조회 성공 - UUID: {}", uuid);
        } else {
            log.warn("썸네일 정보를 찾을 수 없습니다 - UUID: {}", uuid);
        }
        return Optional.ofNullable(result);
    }
    
    /**
     * 썸네일 사용 완료 처리 (임시 저장소에서 제거)
     */
    public void markThumbnailAsUsed(String uuid) {
        ThumbnailUploadResultDto removed = temporaryThumbnailStorage.remove(uuid);
        if (removed != null) {
            log.info("썸네일 사용 완료 처리 - UUID: {}, 파일명: {}", uuid, removed.getFileName());
        }
    }
    
    /**
     * 썸네일 삭제 (S3에서 파일 삭제 + 임시 저장소에서 제거)
     */
    public boolean deleteThumbnail(String uuid) {
        log.info("썸네일 삭제 시작 - UUID: {}", uuid);
        
        try {
            ThumbnailUploadResultDto thumbnailInfo = temporaryThumbnailStorage.get(uuid);
            if (thumbnailInfo == null) {
                log.warn("삭제할 썸네일 정보가 없습니다 - UUID: {}", uuid);
                return false;
            }
            
            // S3에서 파일 삭제
            String fileName = thumbnailInfo.getFileName();
            String originalFileName = uuid + "_" + fileName;
            String thumbnailFileName = "s_" + uuid + "_" + fileName;
            
            try {
                s3Uploader.removeS3File(originalFileName);
                s3Uploader.removeS3File(thumbnailFileName);
                log.info("S3 파일 삭제 완료 - 원본: {}, 썸네일: {}", originalFileName, thumbnailFileName);
            } catch (Exception s3Exception) {
                log.warn("S3 파일 삭제 중 오류 (계속 진행): {}", s3Exception.getMessage());
            }
            
            // 임시 저장소에서 제거
            temporaryThumbnailStorage.remove(uuid);
            log.info("썸네일 삭제 완료 - UUID: {}", uuid);
            
            return true;
            
        } catch (Exception e) {
            log.error("썸네일 삭제 실패 - UUID: {}, 오류: {}", uuid, e.getMessage());
            return false;
        }
    }
    
    /**
     * 임시 저장소 상태 확인 (디버깅용)
     */
    public Map<String, ThumbnailUploadResultDto> getTemporaryStorage() {
        return Map.copyOf(temporaryThumbnailStorage);
    }
    
    /**
     * 미사용 썸네일 자동 정리 스케줄링
     */
    private void scheduleCleanup(String uuid) {
        CompletableFuture.delayedExecutor(30, TimeUnit.MINUTES).execute(() -> {
            if (temporaryThumbnailStorage.containsKey(uuid)) {
                log.info("미사용 썸네일 자동 정리 - UUID: {}", uuid);
                deleteThumbnail(uuid);
            }
        });
    }

    /**
     * 파일 검증
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 없습니다.");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }
        
        if (!isImageFile(file)) {
            throw new IllegalArgumentException(
                "이미지 파일만 업로드할 수 있습니다. 허용된 확장자: " + ALLOWED_IMAGE_EXTENSIONS);
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("파일 크기가 너무 큽니다. 최대: %.1fMB, 현재: %.1fMB",
                        MAX_FILE_SIZE / (1024.0 * 1024.0), 
                        file.getSize() / (1024.0 * 1024.0)));
        }
    }

    private boolean isImageFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) return false;
        
        String extension = getFileExtension(originalFilename);
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String extractUuidFromPath(String filePath) {
        String fileName = new File(filePath).getName();
        int underscoreIndex = fileName.indexOf('_');
        return underscoreIndex > 0 ? fileName.substring(0, underscoreIndex) : UUID.randomUUID().toString();
    }
}
