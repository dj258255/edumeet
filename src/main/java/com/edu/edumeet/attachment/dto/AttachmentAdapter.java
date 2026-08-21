package com.edu.edumeet.attachment.dto;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.s3.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Attachment 도메인 객체와 DTO 간의 변환을 담당하는 어댑터 클래스
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class AttachmentAdapter {

    private final S3Uploader s3Uploader;

    /**
     * Attachment 도메인 객체를 UploadResultDTO로 변환
     */
    public AttachmentResultDTO toDto(Attachment attachment) {
        if (attachment == null) {
            return null;
        }

        AttachmentResultDTO dto = AttachmentResultDTO.builder()
                .uuid(attachment.getUuid())
                .fileName(attachment.getFileName())
                .img(attachment.isImage())
                .fileSize(attachment.getFileSize())
                .contentType(attachment.getContentType())
                .domain(attachment.getDomain())
                .referenceId(attachment.getReferenceId())
                .uploadedAt(attachment.getUploadedAt())
                .uploadedBy(attachment.getUploadedBy())
                .build();

        // S3 URL 설정
        if (attachment.getDomain() != null && !attachment.getDomain().isEmpty()) {
            // 도메인별 URL 생성
            dto.setOriginalUrl(s3Uploader.getDomainOriginalUrl(
                    attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
            
            if (attachment.isImage()) {
                dto.setThumbnailUrl(s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
            }
        } else {
            // 기본 URL 생성
            dto.setOriginalUrl(s3Uploader.getOriginalUrl(attachment.getUuid(), attachment.getFileName()));
            
            if (attachment.isImage()) {
                dto.setThumbnailUrl(s3Uploader.getThumbnailUrl(attachment.getUuid(), attachment.getFileName()));
            }
        }

        return dto;
    }
    
    /**
     * FileUpload를 FileUploadDTO로 변환
     * @param attachment 변환할 Attachment 객체
     * @return 변환된 AttachmentDTO 객체
     */
    public AttachmentDTO toFileUploadDTO(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        
        AttachmentDTO dto = AttachmentDTO.builder()
                .uuid(attachment.getUuid())
                .fileName(attachment.getFileName())
                .ord(attachment.getOrd())
                .img(attachment.isImage())
                .domain(attachment.getDomain())
                .referenceId(attachment.getReferenceId())
                .build();
        
        // S3 URL 설정
        if (attachment.getDomain() != null && !attachment.getDomain().isEmpty()) {
            // 도메인별 URL 생성
            String originalUrl = s3Uploader.getDomainOriginalUrl(
                    attachment.getDomain(), attachment.getUuid(), attachment.getFileName());
            dto.setS3Url(originalUrl);
            
            if (attachment.isImage()) {
                String thumbnailUrl = s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(), attachment.getUuid(), attachment.getFileName());
                dto.setS3ThumbnailUrl(thumbnailUrl);
                log.debug("도메인별 썸네일 URL 설정 - UUID: {}, 도메인: {}, 이미지: {}, 썸네일URL: {}", 
                        attachment.getUuid(), attachment.getDomain(), attachment.isImage(), thumbnailUrl);
            }
        } else {
            // 기본 URL 생성
            String originalUrl = s3Uploader.getOriginalUrl(attachment.getUuid(), attachment.getFileName());
            dto.setS3Url(originalUrl);
            
            if (attachment.isImage()) {
                String thumbnailUrl = s3Uploader.getThumbnailUrl(attachment.getUuid(), attachment.getFileName());
                dto.setS3ThumbnailUrl(thumbnailUrl);
                log.debug("기본 썸네일 URL 설정 - UUID: {}, 이미지: {}, 썸네일URL: {}", 
                        attachment.getUuid(), attachment.isImage(), thumbnailUrl);
            }
        }
        
        return dto;
    }
    
    /**
     * FileUploadDTO를 FileUpload로 변환
     * @param attachmentDTO 변환할 AttachmentDTO 객체
     * @return 변환된 Attachment 객체
     */
    public Attachment fromFileUploadDTO(AttachmentDTO attachmentDTO) {
        if (attachmentDTO == null) {
            return null;
        }
        
        return Attachment.builder()
                .uuid(attachmentDTO.getUuid())
                .fileName(attachmentDTO.getFileName())
                .ord(attachmentDTO.getOrd())
                .img(attachmentDTO.isImg())
                .domain(attachmentDTO.getDomain())
                .referenceId(attachmentDTO.getReferenceId())
                .build();

    }
    
    /**
     * Attachment 리스트를 AttachmentDTO 리스트로 변환
     * @param attachments 변환할 Attachment 객체 리스트
     * @return 변환된 AttachmentDTO 객체 리스트
     */
    public List<AttachmentDTO> toFileUploadDTOList(List<Attachment> attachments) {
        if (attachments == null) {
            return null;
        }
        
        return attachments.stream()
                .map(this::toFileUploadDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * AttachmentDTO 리스트를 Attachment 리스트로 변환
     * @param attachmentDTOS 변환할 AttachmentDTO 객체 리스트
     * @return 변환된 Attachment 객체 리스트
     */
    public List<Attachment> fromFileUploadDTOList(List<AttachmentDTO> attachmentDTOS) {
        if (attachmentDTOS == null) {
            return null;
        }
        
        return attachmentDTOS.stream()
                .map(this::fromFileUploadDTO)
                .collect(Collectors.toList());
    }
}