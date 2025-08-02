package com.edu.edumeet.classroom.dto.response;

import com.edu.edumeet.classroom.domain.Tag;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class ClassInfoResponseDto {
    private String title;
    private String description;
    private String thumbnailUrl;
    private List<String> tags;

    @Builder
    public ClassInfoResponseDto(String title, String description, String thumbnailUrl, List<String> tags) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;
    }
}
