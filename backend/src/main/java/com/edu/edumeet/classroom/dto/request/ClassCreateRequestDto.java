package com.edu.edumeet.classroom.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "클래스 생성 요청 DTO")
@Getter
public class ClassCreateRequestDto {
    
    @Schema(description = "클래스 제목", example = "자바 프로그래밍 기초")
    private String title;
    
    @Schema(description = "클래스 설명", example = "초보자를 위한 자바 프로그래밍 강의입니다.")
    private String description;
    
    @Schema(description = "참가자 제한 수", example = "30")
    private int limit;
    
    @Schema(description = "미리 업로드된 썸네일 UUID", example = "12345678-1234-1234-1234-123456789012")
    private String thumbnailUuid;  // MultipartFile 대신 UUID 사용
    
    @Schema(description = "클래스 태그 목록", example = "[\"자바\", \"프로그래밍\", \"기초\"]")
    private List<String> tags;

    @Builder
    public ClassCreateRequestDto(String title, String description, int limit, 
                                String thumbnailUuid, List<String> tags) {
        this.title = title;
        this.description = description;
        this.limit = limit;
        this.thumbnailUuid = thumbnailUuid;
        this.tags = tags;
    }
}
