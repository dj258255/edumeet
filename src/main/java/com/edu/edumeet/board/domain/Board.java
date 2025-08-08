package com.edu.edumeet.board.domain;

import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 게시판 도메인 모델
 * DDD의 애그리게이트 루트 역할을 하는 도메인 객체
 */
@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class Board {
    private Long id;            // 게시글 ID
    private String title;        // 제목
    private String content;      // 내용
    private String writer;       // 작성자

    private Long classId;        // 클래스 아이디
    private Long categoryId;     // 카테고리 ID

    @Builder.Default
    private BoardType boardType = BoardType.NORMAL; // 게시글 타입

    /**
     * -- SETTER --
     *  이미지 세트 설정
     *
     * @param images 이미지 세트
     */
    @Setter
    @Builder.Default
    private Set<BoardImage> images = new HashSet<>();

    private LocalDateTime regDate;  // 등록일시
    private LocalDateTime modDate;  // 수정일시
    // getter 추가
    // setter 또는 빌더에서 사용할 수 있도록
    @Setter
    private LocalDateTime deletedAt; // 삭제일시

    private long view;
    @Builder.Default
    private long favorite = 0L;
    @Builder.Default
    private long dislike = 0L;

    //게시글 내용과 타입 모두 바꿈
    public Board changeAll(String title, String content, BoardType newBoardType) {
        // 동일한 타입으로의 전환은 허용 (내용만 변경하는 경우)
        if (newBoardType != null && newBoardType != this.boardType && !this.boardType.canTransitionTo(newBoardType)) {
            throw new IllegalStateException(
                String.format("%s에서 %s로 전환할 수 없습니다.", 
                this.boardType.getDescription(), 
                newBoardType.getDescription())
            );
        }

        BoardType finalBoardType = newBoardType != null ? newBoardType : this.boardType;

        return Board.builder()
                .id(this.id)
                .title(title)
                .content(content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(finalBoardType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(new HashSet<>(this.images))
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }

    //게시글 내용 변경
    public Board change(String title, String content) {
        return changeAll(title, content, this.boardType);
    }

    //게시글 타입 변경 , 요청 사용자 , 클래스 생성자
    public Board changeBoardType(BoardType newBoardType, String requestUser, String classCreator) {
        if (newBoardType == null) {
            throw new IllegalArgumentException("게시글 타입은 null일 수 없습니다.");
        }

        // enum의 전환 규칙 검증
        if (!this.boardType.canTransitionTo(newBoardType)) {
            throw new IllegalStateException(
                String.format("%s에서 %s로 전환할 수 없습니다.", 
                this.boardType.getDescription(), 
                newBoardType.getDescription())
            );
        }

        // 공지사항으로 전환 시 추가 검증
        if (newBoardType == BoardType.NOTICE) {
            validateNoticePermission(requestUser, classCreator);
        }

        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(newBoardType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(new HashSet<>(this.images))
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }


    //게시글 타입 변경 . 권한 검증 x
    public Board changeBoardType(BoardType newBoardType) {
        return changeBoardType(newBoardType, null, null);
    }



    //공지사항 권한 검증
    private void validateNoticePermission(String requestUser, String classCreator) {
        if (requestUser == null || classCreator == null) {
            return; // 권한 검증을 하지 않는 경우 (내부 로직에서 호출 시)
        }
        
        if (!requestUser.equals(classCreator)) {
            throw new IllegalStateException("공지사항은 클래스 생성자만 설정할 수 있습니다.");
        }
    }

    // 추천 수가 카테고리별 기준값 이상인 경우 추천 게시글로 자동 전환
    // 싫어요 수는 더 이상 게시글 타입 변경에 영향을 주지 않음
    private Board checkAndSetRecommended(long newFavorite, long newDislike, int threshold) {
        // enum의 비즈니스 로직 활용 - 좋아요 수만 고려
        BoardType newType = this.boardType.getRecommendedType(newFavorite, threshold);

        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(newType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(this.images)
                .view(this.view)
                .favorite(newFavorite)
                .dislike(newDislike)
                .deletedAt(this.deletedAt)
                .build();
    }

    //추천 수 증가. 기본 기준값
    public Board increaseFavorite() {
        long newFavorite = this.favorite;
        if (newFavorite < Long.MAX_VALUE) {
            newFavorite = this.favorite + 1;
        }

        return checkAndSetRecommended(newFavorite, this.dislike, 10);
    }
    
    //추천 수 증가. 카테고리별 기준 값
    public Board increaseFavorite(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("추천 기준값은 0 이상이어야 한다.");
        }

        long newFavorite = this.favorite;
        if (newFavorite < Long.MAX_VALUE) {
            newFavorite = this.favorite + 1;
        }

        return checkAndSetRecommended(newFavorite, this.dislike, threshold);
    }

    /**
     * 싫어요 수 증가 (게시글 타입 변경 없음)
     * @return 싫어요 수가 증가된 새로운 Board 객체
     */
    public Board increaseDislike() {
        long newDislike = this.dislike;
        if (newDislike < Long.MAX_VALUE) {
            newDislike = this.dislike + 1;
        }
    
        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(this.boardType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(this.images)
                .view(this.view)
                .favorite(this.favorite)
                .dislike(newDislike)
                .deletedAt(this.deletedAt)
                .build();
    }


    //조회수 증가
    public Board increaseView() {
        long newView = this.view;
        if (newView < Long.MAX_VALUE) {
            newView = this.view + 1;
        }

        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(this.boardType)
                .regDate(this.regDate)
                .modDate(this.modDate)
                .images(this.images)
                .view(newView)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }

    //이미지 추가
    public void addImage(String uuid, String fileName) {
        BoardImage boardImage = BoardImage.builder()
                .uuid(uuid)
                .fileName(fileName)
                .ord(images.size())
                .build();
        images.add(boardImage);
    }
    
    ///모든 이미지 제거
    public void clearImages() {
        this.images.clear();
    }
    
    // 게시글 삭제 확인
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    
    /**
     * 기존 이미지 제거 후 BoardImageDTO 목록으로 새 이미지 추가
     * @param boardImages BoardImageDTO 목록
     * @return 이미지가 변경된 새로운 Board 객체
     */
    public Board changeBoardImages(List<com.edu.edumeet.board.presentation.dto.BoardImageDTO> boardImages) {
        Set<BoardImage> newImages = new HashSet<>();

        if (boardImages != null) {
            for (int i = 0; i < boardImages.size(); i++) {
                com.edu.edumeet.board.presentation.dto.BoardImageDTO imageDTO = boardImages.get(i);
                BoardImage boardImage = BoardImage.builder()
                        .uuid(imageDTO.getUuid())
                        .fileName(imageDTO.getFileName())
                        .ord(imageDTO.getOrd())
                        .build();
                newImages.add(boardImage);
            }
        }

        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(this.boardType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(newImages)
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }

    /**
     * 게시글 타입 문자열로부터 안전하게 변경
     * @param boardTypeString 게시글 타입 문자열
     * @return 변경된 게시글 (변환 실패 시 기존 타입 유지)
     */
    public Board changeBoardTypeFromString(String boardTypeString) {
        BoardType newBoardType = BoardType.safeValueOf(boardTypeString, this.boardType);
        
        // 같은 타입이면 변경하지 않음
        if (newBoardType == this.boardType) {
            return this;
        }
        
        return changeBoardType(newBoardType);
    }
}