package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardImageDTO;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.QReplyJpaEntity;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPQLQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BoardSearchRepository 인터페이스의 구현체
 * QueryDSL을 사용하여 게시글 검색 기능을 구현
 */

@Repository
public class BoardSearchRepositoryImpl extends QuerydslRepositorySupport implements BoardSearchRepository {

    public BoardSearchRepositoryImpl() {
        super(BoardJpaEntity.class);
    }

    /**
     * 타입과 키워드로 게시글 검색
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    @Override
    public Page<Board> searchAll(String[] types, String keyword, Pageable pageable) {
        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
        JPQLQuery<BoardJpaEntity> query = from(boardJpaEntity);

        // 검색 조건 적용
        applySearchConditions(query, boardJpaEntity, types, keyword);
        
        // ID > 0 조건 추가
        query.where(boardJpaEntity.id.gt(0L));

        // 페이징 적용
        this.getQuerydsl().applyPagination(pageable, query);

        // 쿼리 실행
        List<BoardJpaEntity> list = query.fetch();
        long count = query.fetchCount();

        // JPA 엔티티를 도메인 모델로 변환
        List<Board> boardList = list.stream()
                .map(BoardJpaEntity::toModel)
                .collect(Collectors.toList());

        return new PageImpl<>(boardList, pageable, count);
    }

    /**
     * 게시글과 댓글 수를 함께 조회
     * 게시글에 댓글이 없는 경우도 처리하기 위해 LEFT OUTER JOIN 사용
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    @Override
    public Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable) {
        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
        QReplyJpaEntity replyJpaEntity = QReplyJpaEntity.replyJpaEntity;

        // 기본 쿼리 생성
        JPQLQuery<BoardJpaEntity> query = from(boardJpaEntity);
        
        // LEFT OUTER JOIN으로 댓글 테이블 연결
        query.leftJoin(replyJpaEntity).on(replyJpaEntity.board.eq(boardJpaEntity));
        
        // 게시글별 그룹화
        query.groupBy(boardJpaEntity);

        // 검색 조건 적용
        applySearchConditions(query, boardJpaEntity, types, keyword);
        
        // ID > 0 조건 추가
        query.where(boardJpaEntity.id.gt(0L));

        // DTO로 직접 매핑하는 Projection 쿼리 생성
        JPQLQuery<BoardListReplyCountDTO> dtoQuery = query.select(Projections.bean(BoardListReplyCountDTO.class,
                boardJpaEntity.id,
                boardJpaEntity.title,
                boardJpaEntity.writer,
                boardJpaEntity.regDate,
                replyJpaEntity.count().as("replyCount")
        ));

        // 페이징 적용
        this.getQuerydsl().applyPagination(pageable, dtoQuery);

        // 쿼리 실행
        List<BoardListReplyCountDTO> dtoList = dtoQuery.fetch();
        long count = dtoQuery.fetchCount();

        return new PageImpl<>(dtoList, pageable, count);
    }
    
    /**
     * 검색 조건을 쿼리에 적용하는 공통 메서드
     * @param query 적용할 쿼리
     * @param boardJpaEntity 게시글 엔티티
     * @param types 검색 타입 배열
     * @param keyword 검색 키워드
     */
    private void applySearchConditions(JPQLQuery<?> query, QBoardJpaEntity boardJpaEntity,
                                      String[] types, String keyword) {
        if ((types != null && types.length > 0) && keyword != null) {
            BooleanBuilder booleanBuilder = new BooleanBuilder();

            for (String type : types) {
                switch (type) {
                    case "t":
                        booleanBuilder.or(boardJpaEntity.title.contains(keyword));
                        break;
                    case "c":
                        booleanBuilder.or(boardJpaEntity.content.contains(keyword));
                        break;
                    case "w":
                        booleanBuilder.or(boardJpaEntity.writer.contains(keyword));
                        break;
                }
            }
            
            query.where(booleanBuilder);
        }
    }

//    @Override
//    public Page<BoardListReplyCountDTO> searchWithAll(String[] types,
//                                                      String keyword,
//                                                      Pageable pageable){
//
//        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
//        QReplyJpaEntity replyJpaEntity = QReplyJpaEntity.replyJpaEntity;
//
//        JPQLQuery<BoardJpaEntity>  boardJpaEntityJPQLQuery = from(boardJpaEntity);
//        boardJpaEntityJPQLQuery.leftJoin(replyJpaEntity).on(replyJpaEntity.board.eq(boardJpaEntity)); // 레프트 조인
//
//        getQuerydsl().applyPagination(pageable, boardJpaEntityJPQLQuery); // 페이징
//
//        List<BoardJpaEntity> boardList = boardJpaEntityJPQLQuery.fetch();
//
//        boardList.forEach(boardJpaEntity1 ->{
//            System.out.println(boardJpaEntity1.getId());
//            System.out.println(boardJpaEntity1.getImageSet());
//            System.out.println("---------------");
//        });
//
//
//        return null;
//    }

    @Override
    public Page<BoardListAllDTO> searchWithAll(String[] types, String keyword, Pageable pageable){

        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
        QReplyJpaEntity replyJpaEntity = QReplyJpaEntity.replyJpaEntity;

        JPQLQuery<BoardJpaEntity> boardJpaEntityJPQLQuery = from(boardJpaEntity);
        boardJpaEntityJPQLQuery.leftJoin(replyJpaEntity).on(replyJpaEntity.board.eq(boardJpaEntity)); // 레프트 조인

        //검색 조건 추가
        if( ( types != null && types.length > 0 ) && keyword != null ){
            BooleanBuilder booleanBuilder = new BooleanBuilder();

            for(String type : types){
                switch(type){
                    case "t":
                        booleanBuilder.or(boardJpaEntity.title.contains(keyword));
                        break;
                    case "c":
                        booleanBuilder.or(boardJpaEntity.content.contains(keyword));
                        break;
                    case "w":
                        booleanBuilder.or(boardJpaEntity.writer.contains(keyword));
                        break;
                }
            }
            boardJpaEntityJPQLQuery.where(booleanBuilder);
        }


        boardJpaEntityJPQLQuery.groupBy(boardJpaEntity);

        getQuerydsl().applyPagination(pageable, boardJpaEntityJPQLQuery); //페이징

        JPQLQuery<Tuple> tupleJPQLQuery = boardJpaEntityJPQLQuery.select(boardJpaEntity, replyJpaEntity.countDistinct());

        List<Tuple> tupleList = tupleJPQLQuery.fetch();

        List<BoardListAllDTO> dtoList = tupleList.stream().map(tuple ->{

            BoardJpaEntity board1 = (BoardJpaEntity) tuple.get(boardJpaEntity);
            long replyCount = tuple.get(1,Long.class);

            BoardListAllDTO dto = BoardListAllDTO.builder()
                    .id(board1.getId())
                    .title(board1.getTitle())
                    .writer(board1.getWriter())
                    .regDate(board1.getRegDate())
                    .replyCount(replyCount)
                    .build();

            //BoardImage를 BoardImageDTO 처리할 부분
            List<BoardImageDTO> imageDTOS = board1.getImageSet().stream().sorted()
                    .map(boardImageJpaEntity -> BoardImageDTO.builder()
                            .uuid(boardImageJpaEntity.getUuid())
                            .fileName(boardImageJpaEntity.getFilename())
                            .ord(boardImageJpaEntity.getOrd())
                            .build()
                    ).collect(Collectors.toList());

            dto.setBoardImages(imageDTOS); // 처리된 BoardImageDTO들을 추가


            return dto;
        }).collect(Collectors.toList());

        long totalCount = boardJpaEntityJPQLQuery.fetchCount();


        return new PageImpl<>(dtoList, pageable, totalCount);

    }

}
