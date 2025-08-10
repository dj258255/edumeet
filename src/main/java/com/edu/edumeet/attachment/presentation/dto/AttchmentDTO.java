package com.edu.edumeet.attachment.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 파일 업로드 정보를 위한 DTO
 * BoardImageDTO를 대체하는 클래스
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttchmentDTO {

    private String uuid;
    private String fileName;
    private int ord;
    private String s3Url;
    private String s3ThumbnailUrl;
    private boolean img;
    private String domain;
    private Long referenceId;
}