package com.edu.edumeet.classroom.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ClassCreateRequestDto {
    private String title;
    private String description;
    private int limit;

    @Builder
    public ClassCreateRequestDto(String title, String description, int limit) {
        this.title = title;
        this.description = description;
        this.limit = limit;
    }
}
