package com.edu.edumeet.classroom.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ClassCreateResponseDto {
    private String title;
    private String description;

    @Builder
    public ClassCreateResponseDto(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
