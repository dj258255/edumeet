package com.edu.edumeet.homework.submission.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmissionFile {
    private Long id;
    private Long submissionId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;


    //파일이 이미지인가
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
