package com.edu.edumeet.attachment.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class Attachment implements Comparable<Attachment> {
    private Long id;
    private String uuid;
    private String fileName;
    private int ord; //순서
    private boolean img; //이미지 여부 썸네일 있으면 true
    private Long fileSize;
    private String contentType;
    private String domain; // board classroom 등등
    private Long referenceId; //참조 id
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    public double getFileSizeInMB() {
        if (fileSize == null) return 0.0;
        return fileSize / 1024.0 / 1024.0;
    }

    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    public boolean isImage() {
        return img;
    }

    @Override
    public int compareTo(Attachment other) {
        return this.ord - other.ord;
    }

}
