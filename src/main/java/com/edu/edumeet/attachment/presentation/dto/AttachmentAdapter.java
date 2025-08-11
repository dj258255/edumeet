package com.edu.edumeet.attachment.presentation.dto;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.s3.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Attachment 도메인 객체와 DTO 간의 변환을 담당하는 어댑터 클래스
 */
@Component
@RequiredArgsConstructor
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
     * @return 변환된 AttchmentDTO 객체
     */
    public AttchmentDTO toFileUploadDTO(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        
        AttchmentDTO dto = AttchmentDTO.builder()
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
            dto.setS3Url(s3Uploader.getDomainOriginalUrl(
                    attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
            
            if (attachment.isImage()) {
                dto.setS3ThumbnailUrl(s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
            }
        } else {
            // 기본 URL 생성
            dto.setS3Url(s3Uploader.getOriginalUrl(attachment.getUuid(), attachment.getFileName()));
            
            if (attachment.isImage()) {
                dto.setS3ThumbnailUrl(s3Uploader.getThumbnailUrl(attachment.getUuid(), attachment.getFileName()));
            }
        }
        
        return dto;
    }
    
    /**
     * FileUploadDTO를 FileUpload로 변환
     * @param attchmentDTO 변환할 AttchmentDTO 객체
     * @return 변환된 Attachment 객체
     */
    public Attachment fromFileUploadDTO(AttchmentDTO attchmentDTO) {
        if (attchmentDTO == null) {
            return null;
        }
        
        return Attachment.builder()
                .uuid(attchmentDTO.getUuid())
                .fileName(attchmentDTO.getFileName())
                .ord(attchmentDTO.getOrd())
                .img(attchmentDTO.isImg())
                .domain(attchmentDTO.getDomain())
                .referenceId(attchmentDTO.getReferenceId())
                .build();
    }
    
    /**
     * Attachment 리스트를 AttchmentDTO 리스트로 변환
     * @param attachments 변환할 Attachment 객체 리스트
     * @return 변환된 AttchmentDTO 객체 리스트
     */
    public List<AttchmentDTO> toFileUploadDTOList(List<Attachment> attachments) {
        if (attachments == null) {
            return null;
        }
        
        return attachments.stream()
                .map(this::toFileUploadDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * AttchmentDTO 리스트를 Attachment 리스트로 변환
     * @param attchmentDTOS 변환할 AttchmentDTO 객체 리스트
     * @return 변환된 Attachment 객체 리스트
     */
    public List<Attachment> fromFileUploadDTOList(List<AttchmentDTO> attchmentDTOS) {
        if (attchmentDTOS == null) {
            return null;
        }
        
        return attchmentDTOS.stream()
                .map(this::fromFileUploadDTO)
                .collect(Collectors.toList());
    }
}