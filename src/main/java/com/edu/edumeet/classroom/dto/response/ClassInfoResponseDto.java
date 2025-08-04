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
    private int participantLimit;

    @Builder
    public ClassInfoResponseDto(String title, String description, String thumbnailUrl, List<String> tags, int participantLimit) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;
        this.participantLimit = participantLimit;
    }
}
