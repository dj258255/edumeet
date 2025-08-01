package com.edu.edumeet.classroom.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ClassInfoResponseDto {
    private String title;
    private String description;

    @Builder
    public ClassInfoResponseDto(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
