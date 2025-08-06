package com.edu.edumeet.board.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 페이지 요청 DTO
 * 페이징 및 검색 조건을 포함하는 요청 데이터 객체
 */
@Schema(description = "페이지 요청 정보")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageRequestDTO {

    @Schema(description = "페이지 번호 (1부터 시작)", example = "1", defaultValue = "1")
    @Builder.Default
    private int page = 1;

    @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
    @Builder.Default
    private int size = 10;

    @Schema(description = "검색 유형 (t: 제목, c: 내용, w: 작성자, tc: 제목+내용, tw: 제목+작성자, twc: 제목+작성자+내용)", example = "tc")
    private String type; // 검색의 종류 t,c, w, tc,tw, twc

    @Schema(description = "검색 키워드", example = "title")
    private String keyword;

    @Schema(description = "클래스 ID (특정 클래스의 게시글만 조회)" , example = "1")
    private Long classId;
    
    @Schema(description = "카테고리 ID (특정 카테고리의 게시글만 조회)", example = "1")
    private Long categoryId;
    
    @Schema(description = "게시글 타입 (NORMAL: 일반, NOTICE: 공지사항, RECOMMENDED: 추천게시글)", example = "NOTICE")
    private String boardType;

    /**
     * 검색 유형을 배열로 변환
     * @return 검색 유형 배열 (null이면 검색하지 않음)
     */

    @Schema(hidden = true)
    public String[] getTypes(){
        if(type == null || type.isEmpty()){
            return null;
        }
        return type.split("");
    }

    /**
     * 페이징 정보 생성
     * @param props 정렬 기준 필드
     * @return Pageable 객체
     */

    @Schema(hidden = true)
    public Pageable getPageable(String...props) {
        return PageRequest.of(this.page -1, this.size, Sort.by(props).descending());
    }

}