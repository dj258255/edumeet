package com.edu.edumeet.classroom.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class EvictionRequestDto {
    private Long classId;
    private String email;

    @Builder
    public EvictionRequestDto(Long classId, String email) {
        this.classId = classId;
        this.email = email;
    }
}
