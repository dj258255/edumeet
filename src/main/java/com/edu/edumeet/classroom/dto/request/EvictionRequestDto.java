package com.edu.edumeet.classroom.dto.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class EvictionRequestDto {
    private Long classId;
    private Long studentId;

    @Builder
    public EvictionRequestDto(Long classId, Long studentId) {
        this.classId = classId;
        this.studentId = studentId;
    }
}
