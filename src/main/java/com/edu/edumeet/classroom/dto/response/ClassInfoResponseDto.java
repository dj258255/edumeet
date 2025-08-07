package com.edu.edumeet.classroom.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "클래스 정보 응답 DTO")
@Getter
public class ClassInfoResponseDto {
    
    @Schema(description = "클래스 ID", example = "1")
    private Long classId;
    
    @Schema(description = "클래스 제목", example = "자바 프로그래밍 기초")
    private String title;
    
    @Schema(description = "클래스 설명", example = "초보자를 위한 자바 프로그래밍 강의입니다.")
    private String description;
    
    @Schema(description = "썸네일 이미지 URL", example = "https://bucket-name.s3.amazonaws.com/s_uuid_thumbnail.jpg")
    private String thumbnailUrl;
    
    @Schema(description = "클래스 태그 목록", example = "[\"자바\", \"프로그래밍\", \"기초\"]")
    private List<String> tags;
    
    @Schema(description = "참가자 제한 수", example = "30")
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
