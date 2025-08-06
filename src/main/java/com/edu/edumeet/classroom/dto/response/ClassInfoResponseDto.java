package com.edu.edumeet.classroom.dto.response;

import com.edu.edumeet.classroom.domain.Tag;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class ClassInfoResponseDto {
    private Long classId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private List<String> tags;
    private int participantLimit;

    @Builder
    public ClassInfoResponseDto(Long classId, String title, String description, String thumbnailUrl, List<String> tags, int participantLimit) {
        this.classId = classId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;
        this.participantLimit = participantLimit;
    }
}
