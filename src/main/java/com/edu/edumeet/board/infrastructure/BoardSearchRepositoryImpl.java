package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.attachment.presentation.dto.AttchmentDTO;
import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.QReplyJpaEntity;
import com.edu.edumeet.s3.util.S3Uploader;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPQLQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BoardSearchRepository 인터페이스의 구현체
 * QueryDSL을 사용하여 게시글 검색 기능을 구현
 */

@Repository
public class BoardSearchRepositoryImpl extends QuerydslRepositorySupport implements BoardSearchRepository {

    private final S3Uploader s3Uploader;

    public BoardSearchRepositoryImpl(S3Uploader s3Uploader) {
        super(BoardJpaEntity.class);
        this.s3Uploader = s3Uploader;
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
        
        // 삭제되지 않은 게시글만 조회
        query.where(boardJpaEntity.deletedAt.isNull());

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
     * 타입과 키워드로 삭제된 게시글 검색
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    @Override
    public Page<Board> searchAllDeleted(String[] types, String keyword, Pageable pageable) {
        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
        JPQLQuery<BoardJpaEntity> query = from(boardJpaEntity);

        // 검색 조건 적용
        applySearchConditions(query, boardJpaEntity, types, keyword);
        
        // ID > 0 조건 추가
        query.where(boardJpaEntity.id.gt(0L));
        
        // 삭제된 게시글만 조회
        query.where(boardJpaEntity.deletedAt.isNotNull());

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
        
        // 삭제되지 않은 게시글만 조회
        query.where(boardJpaEntity.deletedAt.isNull());

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
     * 삭제된 게시글과 댓글 수를 함께 조회
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    @Override
    public Page<BoardListReplyCountDTO> searchDeletedWithReplyCount(String[] types, String keyword, Pageable pageable) {
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
        
        // 삭제된 게시글만 조회
        query.where(boardJpaEntity.deletedAt.isNotNull());

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

    @Override
    public Page<BoardListAllDTO> searchWithAll(String[] types, String keyword, Long classId, Long categoryId, String boardType, Pageable pageable){

        QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
        QReplyJpaEntity replyJpaEntity = QReplyJpaEntity.replyJpaEntity;

        JPQLQuery<BoardJpaEntity> query = from(boardJpaEntity);
        query.leftJoin(replyJpaEntity).on(replyJpaEntity.board.eq(boardJpaEntity));

        // 검색 조건 빌더
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        // 키워드 검색 조건
        if ((types != null && types.length > 0) && keyword != null) {
            BooleanBuilder keywordBuilder = new BooleanBuilder();

            for (String type : types) {
                switch (type) {
                    case "t":
                        keywordBuilder.or(boardJpaEntity.title.contains(keyword));
                        break;
                    case "c":
                        keywordBuilder.or(boardJpaEntity.content.contains(keyword));
                        break;
                    case "w":
                        keywordBuilder.or(boardJpaEntity.writer.contains(keyword));
                        break;
            }
        }
        booleanBuilder.and(keywordBuilder);
    }

    // classId 조건 추가
    if (classId != null) {
        booleanBuilder.and(boardJpaEntity.classId.eq(classId));
    }
    
    // categoryId 조건 추가
    if (categoryId != null) {
        booleanBuilder.and(boardJpaEntity.categoryId.eq(categoryId));
    }
    
    // boardType 조건 추가
    if (boardType != null && !boardType.isEmpty()) {
        booleanBuilder.and(boardJpaEntity.boardType.stringValue().eq(boardType));
    }

    // 조건이 있으면 where절에 적용
    if (booleanBuilder.hasValue()) {
        query.where(booleanBuilder);
    }
    
    // 삭제되지 않은 게시글만 조회
    query.where(boardJpaEntity.deletedAt.isNull());

    // 그룹화
    query.groupBy(boardJpaEntity);
    
    // 공지사항이 상단에 표시되도록 정렬 (boardType 기준으로 내림차순 정렬)
    // NOTICE(공지사항) > RECOMMENDED(추천) > NORMAL(일반) 순서로 정렬됨
    query.orderBy(boardJpaEntity.boardType.desc(), boardJpaEntity.id.desc());

    // 튜플로 select - 게시글과 댓글 수를 한 번에 조회
    JPQLQuery<Tuple> tupleQuery = query.select(
        boardJpaEntity, 
        replyJpaEntity.countDistinct()
    );

    // 페이징 적용
    this.getQuerydsl().applyPagination(pageable, tupleQuery);

    // 쿼리 실행
    List<Tuple> tupleList = tupleQuery.fetch();

    //튜플을 DTO로 변환
    List<BoardListAllDTO> dtoList = tupleList.stream().map(tuple -> {
        BoardJpaEntity board = tuple.get(boardJpaEntity);
        Long replyCount = tuple.get(1, Long.class);

        // 기본 DTO 생성
        BoardListAllDTO dto = BoardListAllDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .writer(board.getWriter())
                .regDate(board.getRegDate())
                .replyCount(replyCount)
                .classId(board.getClassId())
                .categoryId(board.getCategoryId())
                .boardType(board.getBoardType())
                .view(board.getView())
                .favorite(board.getFavorite())
                .dislike(board.getDislike())
                .build();

        //이미지 정보 변환 (N+1 문제 방지를 위해 batch size 활용)
        if (board.getImageSet() != null && !board.getImageSet().isEmpty()) {
            List<AttchmentDTO> imageDTOs = board.getImageSet().stream()
                    .sorted(Comparator.comparingInt(BoardFileUploadJpaEntity::getOrd))
                    .map(boardImage -> {
                        String uuid = boardImage.getUuid();
                        String fileName = boardImage.getFileName();
                        
                        // S3Uploader의 메서드를 사용하여 URL 생성
                        String s3Url = s3Uploader.getOriginalUrl(uuid, fileName);
                        String s3ThumbnailUrl = s3Uploader.getThumbnailUrl(uuid, fileName);
                        
                        return AttchmentDTO.builder()
                                .uuid(uuid)
                                .fileName(fileName)
                                .ord(boardImage.getOrd())
                                .s3Url(s3Url)
                                .s3ThumbnailUrl(s3ThumbnailUrl)
                                .img(boardImage.isImg())
                                .domain("board")
                                .referenceId(board.getId())
                                .build();
                    })
                    .collect(Collectors.toList());
            
            dto.setBoardImages(imageDTOs);
        }

        return dto;
    }).collect(Collectors.toList());

    // count 쿼리도 같은 조건으로 실행
    long totalCount = query.fetchCount();

    return new PageImpl<>(dtoList, pageable, totalCount);
}

@Override
public Page<BoardListAllDTO> searchDeletedWithAll(String[] types, String keyword, Long classId, Long categoryId, String boardType, Pageable pageable){

    QBoardJpaEntity boardJpaEntity = QBoardJpaEntity.boardJpaEntity;
    QReplyJpaEntity replyJpaEntity = QReplyJpaEntity.replyJpaEntity;

    JPQLQuery<BoardJpaEntity> query = from(boardJpaEntity);
    query.leftJoin(replyJpaEntity).on(replyJpaEntity.board.eq(boardJpaEntity));

    // 검색 조건 빌더
    BooleanBuilder booleanBuilder = new BooleanBuilder();

    // 키워드 검색 조건
    if ((types != null && types.length > 0) && keyword != null) {
        BooleanBuilder keywordBuilder = new BooleanBuilder();

        for (String type : types) {
            switch (type) {
                case "t":
                    keywordBuilder.or(boardJpaEntity.title.contains(keyword));
                    break;
                case "c":
                    keywordBuilder.or(boardJpaEntity.content.contains(keyword));
                    break;
                case "w":
                    keywordBuilder.or(boardJpaEntity.writer.contains(keyword));
                    break;
            }
        }
        booleanBuilder.and(keywordBuilder);
    }

    // classId 조건 추가
    if (classId != null) {
        booleanBuilder.and(boardJpaEntity.classId.eq(classId));
    }
        
    // categoryId 조건 추가
    if (categoryId != null) {
        booleanBuilder.and(boardJpaEntity.categoryId.eq(categoryId));
    }
        
    // boardType 조건 추가
    if (boardType != null && !boardType.isEmpty()) {
        booleanBuilder.and(boardJpaEntity.boardType.stringValue().eq(boardType));
    }

    // 조건이 있으면 where절에 적용
    if (booleanBuilder.hasValue()) {
        query.where(booleanBuilder);
    }
        
    // 삭제된 게시글만 조회
    query.where(boardJpaEntity.deletedAt.isNotNull());

    // 그룹화
    query.groupBy(boardJpaEntity);
        
    // 공지사항이 상단에 표시되도록 정렬 (boardType 기준으로 내림차순 정렬)
    // NOTICE(공지사항) > RECOMMENDED(추천) > NORMAL(일반) 순서로 정렬됨
    query.orderBy(boardJpaEntity.boardType.desc(), boardJpaEntity.id.desc());

    // 튜플로 select - 게시글과 댓글 수를 한 번에 조회
    JPQLQuery<Tuple> tupleQuery = query.select(
        boardJpaEntity, 
        replyJpaEntity.countDistinct()
    );

    // 페이징 적용
    this.getQuerydsl().applyPagination(pageable, tupleQuery);

    // 쿼리 실행
    List<Tuple> tupleList = tupleQuery.fetch();

    //튜플을 DTO로 변환
    List<BoardListAllDTO> dtoList = tupleList.stream().map(tuple -> {
        BoardJpaEntity board = tuple.get(boardJpaEntity);
        Long replyCount = tuple.get(1, Long.class);

        // 기본 DTO 생성
        BoardListAllDTO dto = BoardListAllDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .writer(board.getWriter())
                .regDate(board.getRegDate())
                .replyCount(replyCount)
                .classId(board.getClassId())
                .categoryId(board.getCategoryId())
                .boardType(board.getBoardType())
                .view(board.getView())
                .favorite(board.getFavorite())
                .dislike(board.getDislike())
                .build();

        //이미지 정보 변환 (N+1 문제 방지를 위해 batch size 활용)
        if (board.getImageSet() != null && !board.getImageSet().isEmpty()) {
            List<AttchmentDTO> imageDTOs = board.getImageSet().stream()
                    .sorted(Comparator.comparingInt(BoardFileUploadJpaEntity::getOrd))
                    .map(boardImage -> {
                        String uuid = boardImage.getUuid();
                        String fileName = boardImage.getFileName();
                            
                        // S3Uploader의 메서드를 사용하여 URL 생성
                        String s3Url = s3Uploader.getOriginalUrl(uuid, fileName);
                        String s3ThumbnailUrl = s3Uploader.getThumbnailUrl(uuid, fileName);
                            
                        return AttchmentDTO.builder()
                                .uuid(uuid)
                                .fileName(fileName)
                                .ord(boardImage.getOrd())
                                .s3Url(s3Url)
                                .s3ThumbnailUrl(s3ThumbnailUrl)
                                .img(boardImage.isImg())
                                .domain("board")
                                .referenceId(board.getId())
                                .build();
                    })
                    .collect(Collectors.toList());
                
            dto.setBoardImages(imageDTOs);
        }

        return dto;
    }).collect(Collectors.toList());

    // count 쿼리도 같은 조건으로 실행
    long totalCount = query.fetchCount();

    return new PageImpl<>(dtoList, pageable, totalCount);
}
}