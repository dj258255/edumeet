package com.edu.edumeet.board.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 페이지 응답 DTO
 * 페이징 처리된 결과를 반환하기 위한 데이터 객체
 * @param <E> 페이징 대상 DTO 타입
 */
@Schema(description = "페이지 응답 정보")
@Getter
@ToString
public class PageResponseDTO<E> {

    @Schema(description = "현재 페이지 번호", example = "1")
    private int page;
    
    @Schema(description = "페이지 크기", example = "10")
    private int size;
    
    @Schema(description = "전체 항목 수", example = "123")
    private int total;

    @Schema(description = "시작 페이지 번호", example = "1")
    private int start; // 시작 페이지 번호
    
    @Schema(description = "끝 페이지 번호", example = "10")
    private int end; // 끝 페이지 번호

    @Schema(description = "이전 페이지 존재 여부", example = "false")
    private boolean prev; // 이전 페이지의 존재 여부
    
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private boolean next; // 다음 페이지의 존재 여부

    @Schema(description = "데이터 목록")
    private List<E> dtoList;

    /**
     * 페이지 응답 DTO 생성자
     * @param pageRequestDTO 페이지 요청 정보
     * @param dtoList 데이터 목록
     * @param total 전체 항목 수
     */
    @Builder(builderMethodName = "withAll")
    public PageResponseDTO(PageRequestDTO pageRequestDTO, List<E> dtoList, int total){
        if(total <= 0){
            return;
        }

        this.page = pageRequestDTO.getPage();
        this.size = pageRequestDTO.getSize();
        this.total = total;
        this.dtoList = dtoList;

        // 페이지 네비게이션 계산
        this.end = (int)(Math.ceil(this.page / 10.0)) * 10;
        this.start = this.end - 9;

        // 마지막 페이지 계산
        int last = (int)(Math.ceil((total/(double)size)));
        this.end = end > last ? last : end;

        // 이전/다음 페이지 존재 여부 계산
        this.prev = this.start > 1;
        this.next = total > this.end * this.size;
    }
    
    /**
     * 빌더 패턴을 위한 정적 메서드
     * @param <E> 페이징 대상 DTO 타입
     * @return 빌더 객체
     */
    public static <E> PageResponseDTOBuilder<E> of() {
        return new PageResponseDTOBuilder<>();
    }
    
    /**
     * 페이지 응답 DTO 빌더 클래스
     * @param <E> 페이징 대상 DTO 타입
     */
    public static class PageResponseDTOBuilder<E> {
        private int page;
        private int size;
        private List<E> dtoList;
        private int total;
        
        public PageResponseDTOBuilder<E> page(int page) {
            this.page = page;
            return this;
        }
        
        public PageResponseDTOBuilder<E> size(int size) {
            this.size = size;
            return this;
        }
        
        public PageResponseDTOBuilder<E> dtoList(List<E> dtoList) {
            this.dtoList = dtoList;
            return this;
        }
        
        public PageResponseDTOBuilder<E> total(int total) {
            this.total = total;
            return this;
        }
        
        public PageResponseDTO<E> build() {
            PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                    .page(this.page)
                    .size(this.size)
                    .build();
            return new PageResponseDTO<>(pageRequestDTO, this.dtoList, this.total);
        }
    }
}