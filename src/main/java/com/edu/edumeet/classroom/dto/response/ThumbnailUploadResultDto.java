package com.edu.edumeet.classroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailUploadResultDto {
    private String uuid;
    private String fileName;
    private String thumbnailUrl;
    private String originalUrl;
    private boolean isImage;
}
