package com.edu.edumeet.board.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;


@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageRequestDTO {

    @Builder.Default
    private int page = 1;

    @Builder.Default
    private int size = 10;

    //제목 , 내용 , 글쓴이
    private String type; // 검색의 종류 t,c, w, tc,tw, twc

    private String keyword;

    /**
     * 검색 타입을 배열로 반환
     * @return 검색 타입 배열 (t: 제목, c: 내용, w: 글쓴이)
     */
    public String[] getTypes(){
        if(type == null || type.isEmpty()){
            return null;
        }
        return type.split("");
    }

    /**
     * Spring Data JPA의 Pageable 객체 생성 (내부 구현용)
     * @param props 정렬 속성
     * @return Pageable 객체
     */
    public Pageable getPageable(String...props) {
        return PageRequest.of(this.page -1, this.size, Sort.by(props).descending());
    }

    /**
     * 페이지네이션 파라미터를 Map으로 반환 (RESTful API용)
     * @return 페이지네이션 파라미터 Map
     */
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        
        params.put("page", this.page);
        params.put("size", this.size);
        
        if(type != null && !type.isEmpty()) {
            params.put("type", type);
        }
        
        if(keyword != null && !keyword.isEmpty()) {
            params.put("keyword", keyword);
        }
        
        return params;
    }
}