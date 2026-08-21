package com.edu.edumeet.attachment.service;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.dto.AttachmentResultDTO;
import com.edu.edumeet.s3.util.LocalUploader;
import com.edu.edumeet.s3.util.S3Uploader;
import com.edu.edumeet.attachment.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class AttachmentService {
    
    // ... 기존 필드들
    
    private final LocalUploader localUploader;
    private final S3Uploader s3Uploader;
    public List<Attachment> uploadFiles(List<MultipartFile> files, String domain, Long referenceId) {
        // ... 기존 업로드 로직 유지
        
        // 기존 코드 그대로 사용하되, 로그에 S3 URL 추가
        List<Attachment> uploadedFiles = new ArrayList<>();
        int order = 0;

        for (MultipartFile multipartFile : files) {
            List<String> localPaths = localUploader.uploadLocal(multipartFile);

            if (localPaths != null && !localPaths.isEmpty()) {
                String originalName = multipartFile.getOriginalFilename();
                String uuid = extractUuidFromPath(localPaths.get(0));

                // S3로 업로드
                List<String> s3Urls = localPaths.stream()
                        .map(path -> domain != null ? s3Uploader.upload(path, domain) : s3Uploader.upload(path))
                        .collect(Collectors.toList());

                boolean isImage = s3Urls.size() > 1;
                
                log.debug("파일 업로드 처리 - 파일명: {}, 로컬경로수: {}, S3URL수: {}, 이미지여부: {}", 
                        originalName, localPaths.size(), s3Urls.size(), isImage);

                Attachment attachment = Attachment.builder()
                        .uuid(uuid)
                        .fileName(originalName)
                        .ord(order++)
                        .img(isImage)
                        .fileSize(multipartFile.getSize())
                        .contentType(multipartFile.getContentType())
                        .domain(domain)
                        .referenceId(referenceId)
                        .uploadedAt(LocalDateTime.now())
                        .build();

                // S3 URL 정보 로그 추가
                String originalUrl = domain != null ? 
                        s3Uploader.getDomainOriginalUrl(domain, uuid, originalName) :
                        s3Uploader.getOriginalUrl(uuid, originalName);
                String thumbnailUrl = isImage ? 
                        (domain != null ? 
                                s3Uploader.getDomainThumbnailUrl(domain, uuid, originalName) :
                                s3Uploader.getThumbnailUrl(uuid, originalName)) : null;
                
                log.info("파일 업로드 완료 - UUID: {}, 이미지: {}, 원본URL: {}, 썸네일URL: {}", 
                        uuid, isImage, originalUrl, thumbnailUrl);

                uploadedFiles.add(attachment);
            }
        }

        return uploadedFiles;
    }
    
    //  Attachment → AttachmentResultDTO 변환 메서드 추가
    public List<AttachmentResultDTO> convertToUploadResultDTOs(List<Attachment> attachments) {
        return attachments.stream()
                .map(this::convertToUploadResultDTO)
                .collect(Collectors.toList());
    }
    
    private AttachmentResultDTO convertToUploadResultDTO(Attachment attachment) {
        String originalUrl = s3Uploader.getDomainOriginalUrl(
                attachment.getDomain(),
                attachment.getUuid(),
                attachment.getFileName()
        );
        
        String thumbnailUrl = attachment.isImg() ?
                s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(),
                        attachment.getUuid(),
                        attachment.getFileName()
                ) : null;
        
        return AttachmentResultDTO.builder()
                .uuid(attachment.getUuid())
                .fileName(attachment.getFileName())
                .img(attachment.isImg())
                .fileSize(attachment.getFileSize())
                .contentType(attachment.getContentType())
                .domain(attachment.getDomain())
                .referenceId(attachment.getReferenceId())
                .uploadedAt(attachment.getUploadedAt())
                .uploadedBy("system") // 또는 현재 사용자
                .originalUrl(originalUrl)      //  S3 URL 설정!
                .thumbnailUrl(thumbnailUrl)    //  S3 URL 설정!
                .build();
    }
    
    // ... 기존 메서드들
    public Map<String, Object> getFileInfo(String fileName) {
        try {
            // S3 URL 생성
            String s3Url = getS3Url(fileName);
            log.info("생성된 S3 URL: {}", s3Url);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("url", s3Url);
            resultMap.put("fileName", fileName);

            return resultMap;
        } catch (Exception e) {
            log.error("파일 정보 조회 실패: {} - {}", fileName, e.getMessage());
            throw new RuntimeException("파일 정보 조회 실패", e);
        }
    }
    public boolean removeFile(String fileName) {
        try {
            // S3에서 파일 삭제
            s3Uploader.removeS3File(fileName);

            // 이미지인 경우 섬네일 삭제
            if (isImageFile(fileName)) {
                // UUID_파일명 형식에서 UUID와 원본 파일명 추출
                if (fileName.contains("_")) {
                    String uuid = fileName.substring(0, fileName.indexOf('_'));
                    String originalFileName = fileName.substring(fileName.indexOf('_') + 1);
                    String thumbnailFileName = s3Uploader.createThumbnailFileName(uuid, originalFileName);
                    s3Uploader.removeS3File(thumbnailFileName);
                } else {
                    // 기존 방식 유지 (하위 호환성)
                    String thumbnailFileName = "s_" + fileName;
                    s3Uploader.removeS3File(thumbnailFileName);
                }
            }

            return true;
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {}", e.getMessage());
            return false;
        }
    }
    public List<Attachment> getFilesByReference(String domain, Long referenceId) {
        // 이 메서드는 실제 구현에서는 데이터베이스에서 조회해야 함
        // 현재는 스켈레톤 코드만 제공
        log.info("도메인 {} 및 참조 ID {}에 대한 파일 조회 요청", domain, referenceId);
        return Collections.emptyList();
    }

    // 파일 이미지인지 확인
    private boolean isImageFile(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension);
    }

    // 파일 경로에서 UUID 추출하는 헬퍼 메서드
    private String extractUuidFromPath(String filePath) {
        String fileName = new File(filePath).getName();
        int underscoreIndex = fileName.indexOf('_');
        return underscoreIndex > 0 ? fileName.substring(0, underscoreIndex) : UUID.randomUUID().toString();
    }
    
    // S3 URL 생성 헬퍼 메서드 - S3Uploader 활용
    private String getS3Url(String fileName) {
        // UUID_파일명 형식인지 확인
        if (fileName.contains("_")) {
            String uuid = fileName.substring(0, fileName.indexOf('_'));
            String originalFileName = fileName.substring(fileName.indexOf('_') + 1);
            return s3Uploader.getOriginalUrl(uuid, originalFileName);
        } else {
            // 단순 파일명인 경우 - S3Uploader의 기존 로직을 활용하여 URL 생성
            // UUID가 없는 파일에 대해서는 S3Uploader에 위임
            throw new RuntimeException("파일명에 UUID가 없습니다. 올바른 형식의 파일명을 사용해주세요: " + fileName);
        }
    }
}