package com.edu.edumeet.attachment.dto;

import java.time.Duration;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.s3.service.S3Uploader;
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

    /** 읽기 링크 유효 시간. 페이지를 보는 동안 유지되면 충분하고, 그 뒤에는 닫혀야 한다. */
    private static final int READ_LINK_TTL_MINUTES = 15;
    private static final Duration READ_LINK_TTL = Duration.ofMinutes(READ_LINK_TTL_MINUTES);

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
            dto.setOriginalUrl(readLink(s3Uploader.getDomainOriginalUrl(
                    attachment.getDomain(), attachment.getUuid(), attachment.getFileName())));
            
            if (attachment.isImage()) {
                dto.setThumbnailUrl(readLink(s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(), attachment.getUuid(), attachment.getFileName())));
            }
        } else {
            // 기본 URL 생성
            dto.setOriginalUrl(readLink(s3Uploader.getOriginalUrl(attachment.getUuid(), attachment.getFileName())));
            
            if (attachment.isImage()) {
                dto.setThumbnailUrl(readLink(s3Uploader.getThumbnailUrl(attachment.getUuid(), attachment.getFileName())));
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
            String originalUrl = readLink(s3Uploader.getDomainOriginalUrl(
                    attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
            dto.setS3Url(originalUrl);
            
            if (attachment.isImage()) {
                String thumbnailUrl = readLink(s3Uploader.getDomainThumbnailUrl(
                        attachment.getDomain(), attachment.getUuid(), attachment.getFileName()));
                dto.setS3ThumbnailUrl(thumbnailUrl);
                log.debug("도메인별 썸네일 URL 설정 - UUID: {}, 도메인: {}, 이미지: {}, 썸네일URL: {}", 
                        attachment.getUuid(), attachment.getDomain(), attachment.isImage(), thumbnailUrl);
            }
        } else {
            // 기본 URL 생성
            String originalUrl = readLink(s3Uploader.getOriginalUrl(attachment.getUuid(), attachment.getFileName()));
            dto.setS3Url(originalUrl);
            
            if (attachment.isImage()) {
                String thumbnailUrl = readLink(s3Uploader.getThumbnailUrl(attachment.getUuid(), attachment.getFileName()));
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

    /**
     * 파일을 <b>기간이 정해진 링크</b>로 내보낸다. (#64)
     *
     * <p>예전에는 여기서 공개 주소를 만들어 그대로 내려줬다.
     * 업로드가 {@code PUBLIC_READ} 였기 때문에 <b>주소를 아는 사람이 곧 볼 자격이 있는 사람</b>이었다.
     * 과제 제출물이 그렇게 열려 있었다.
     *
     * <p>이제 파일은 비공개고, 이 응답을 받을 자격이 확인된 뒤에만 링크가 나간다.
     * 링크는 {@value #READ_LINK_TTL_MINUTES}분 뒤 만료된다 —
     * 유출되더라도 창이 닫힌다.
     */
    private String readLink(String objectUrl) {
        return s3Uploader.presignedGetUrl(objectUrl, READ_LINK_TTL);
    }
}
