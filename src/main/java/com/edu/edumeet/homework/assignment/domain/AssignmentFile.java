package com.edu.edumeet.homework.assignment.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssignmentFile {
    private Long id;
    private Long assignmentId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;

    //파일이 이미지인가?
    public boolean isImage() {
        return contentType.startsWith("image/");
    }
}
