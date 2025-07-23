package com.edu.edumeet.board.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 페이지네이션 응답 DTO
 * RESTful API에 적합한 페이지네이션 정보를 제공
 * @param <E> 페이지네이션 대상 항목의 타입
 */
@Getter
@ToString
public class PageResponseDTO<E> {

    private int page;
    private int size;
    private int total;

    // 시작 페이지 번호
    private int start;
    // 끝 페이지 번호
    private int end;

    // 이전 페이지의 존재 여부
    private boolean prev;
    // 다음 페이지의 존재 여부
    private boolean next;

    // 페이지 내 항목 목록
    private List<E> dtoList;

    /**
     * PageRequestDTO를 사용하는 생성자 (하위 호환성 유지)
     * @param pageRequestDTO 페이지 요청 정보
     * @param dtoList 페이지 내 항목 목록
     * @param total 전체 항목 수
     */

    @Builder(builderMethodName = "withAll")
    public PageResponseDTO(PageRequestDTO pageRequestDTO, List<E> dtoList, int total) {
        this(pageRequestDTO.getPage(), pageRequestDTO.getSize(), dtoList, total);
    }

    /**
     * 기본 생성자 (RESTful API용)
     * @param page 현재 페이지 번호
     * @param size 페이지 크기
     * @param dtoList 페이지 내 항목 목록
     * @param total 전체 항목 수
     */
    @Builder(builderMethodName = "of")
    public PageResponseDTO(int page, int size, List<E> dtoList, int total) {
        if (total <= 0) {
            return;
        }

        this.page = page;
        this.size = size;
        this.total = total;
        this.dtoList = dtoList;

        // 페이지 네비게이션 계산
        this.end = (int)(Math.ceil(this.page / 10.0)) * 10;
        this.start = this.end - 9;

        int last = (int)(Math.ceil((total / (double)size)));
        this.end = end > last ? last : end;

        this.prev = this.start > 1;
        this.next = total > this.end * this.size;
    }

    /**
     * 페이지네이션 메타데이터를 Map으로 반환 (RESTful API용)
     * @return 페이지네이션 메타데이터
     */
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("page", this.page);
        metadata.put("size", this.size);
        metadata.put("total", this.total);
        metadata.put("start", this.start);
        metadata.put("end", this.end);
        metadata.put("prev", this.prev);
        metadata.put("next", this.next);
        metadata.put("totalPages", (int)Math.ceil((double)total / size));
        
        return metadata;
    }
}