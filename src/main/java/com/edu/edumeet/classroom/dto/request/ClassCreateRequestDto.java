package com.edu.edumeet.classroom.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class ClassCreateRequestDto {
    private String title;
    private String description;
    private int limit;
    private String thumbnailUrl;
    private List<String> tags;

    @Builder
    public ClassCreateRequestDto(String title, String description, int limit, String thumbnailUrl, List<String> tags) {
        this.title = title;
        this.description = description;
        this.limit = limit;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;
    }
}
