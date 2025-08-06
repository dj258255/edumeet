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

    /**
     * 왜 JpaEntity에 안넣고 여기에 넣음??
     *  -> 이 메서드는 도메인에 요구되는 요구사항임 -> 그래서 도메인 로직임
     *  그리고 도메인 로직은 도메인 모델이 처리하는게 더 자연스러움
     *  만약 도메인 로직이 영속성 객체에 위치하면 JpaEntity랑 분리하는 의미가 없음
     *  영속성 라이브러리가 JPA에서 MongoDB로 바뀌듯 영속성 객체의 형태가 바뀐다고 해도
     *  도메인 로직이 영향을 받아서는 안된다.
     *  그래서 Change 같은 도메인 로직은 도메인 모델인 Board가 가지고 있는게 더 자연스러움
     *  만약 영속성 객체가 도메인 로직을 가지게 된다면 영속성 코드의 변경이 도메인 로직에 영향을 줄 수 있게 된다는 의미다.
     *  결론적으로 영속성 객체에는 데이터 영속화와 관련된 코드만 들어가야 한다.
     */

    /**
     * 게시글 내용 변경
     * @param title 새 제목
     * @param content 새 내용
     */
    public Board change(String title, String content) {
        return Board.builder()
                .id(this.id)
                .title(title)
                .content(content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(this.boardType)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(new HashSet<>(this.images))
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }


    //클래스 생성자만 공지사항 설정.
    public Board setAsNotice(String requestUser, String classCreator) {
        if (!requestUser.equals(classCreator)) {
            throw new IllegalStateException("공지사항은 클래스 생성자만 설정할 수 있습니다.");
        }

        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(BoardType.NOTICE)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .images(this.images)
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .deletedAt(this.deletedAt)
                .build();
    }


    

    /**
     * 추천 수가 카테고리별 기준값 이상인 경우 추천 게시글로 자동 전환
     * 좋아요와 싫어요를 모두 고려하여 게시글 상태 업데이트
     * 
     * @param newFavorite 새로운 좋아요 수
     * @param newDislike 새로운 싫어요 수
     * @param threshold 추천 게시글 기준값
     * @return 업데이트된 게시글
     */
    private Board checkAndSetRecommended(long newFavorite, long newDislike, int threshold) {
        BoardType newType = this.boardType;

        if (newFavorite >= threshold && this.boardType == BoardType.NORMAL) {
            newType = BoardType.RECOMMENDED;
        } else if (newFavorite < threshold && this.boardType == BoardType.RECOMMENDED) {
            newType = BoardType.NORMAL;
        }

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


    /**
     * 추천 수 증가 (기본 기준값 사용)
     */
    public Board increaseFavorite() {
        long newFavorite = this.favorite;
        if(newFavorite < Long.MAX_VALUE) {
            newFavorite = this.favorite + 1;
        }

        // checkAndSetRecommended 사용하여 일관성 유지
        return checkAndSetRecommended(newFavorite, this.dislike, 10);
    }
    
    /**
     * 추천 수 증가 (카테고리별 기준값 사용)
     * @param threshold 추천 게시글 기준값
     */
    public Board increaseFavorite(int threshold) {
        if(threshold < 0) {
            throw new IllegalArgumentException("추천 기준값은 0 이상이어야 한다.");
        }

        //오버플로우 방지. 맥스값 도달 시 증가 x
        long newFavorite = this.favorite;
        if(newFavorite < Long.MAX_VALUE) {
            newFavorite = this.favorite + 1;
        }

        return checkAndSetRecommended(newFavorite, this.dislike, threshold);
    }

    /**
     * 싫어요 수 증가 (기본 기준값 사용) - 수정
     */
    public Board increaseDislike() {
        long newDislike = this.dislike;
        if(newDislike < Long.MAX_VALUE) {
            newDislike = this.dislike + 1;
        }
    
        // checkAndSetRecommended 사용하여 일관성 유지
        return checkAndSetRecommended(this.favorite, newDislike, 10);
    }

    /**
     * 싫어요 수 증가 (카테고리별 기준값 사용) - 이미 올바름
     */
    public Board increaseDislike(int threshold) {
        if(threshold < 0) {
            throw new IllegalArgumentException("추천 기준값은 0 이상이어야 한다.");
        }

        long newDislike = this.dislike;
        if(newDislike < Long.MAX_VALUE) {
            newDislike = this.dislike + 1;
        }
        
        return checkAndSetRecommended(this.favorite, newDislike, threshold);
    }

    //조회수 증가
    public Board increaseView() {
        //오버플로우 방지
        long newView = this.view;
        if(newView < Long.MAX_VALUE) {
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



    //이미지 추가 - 도메인 로직
    public void addImage(String uuid, String fileName) {
        BoardImage boardImage = BoardImage.builder()
                .uuid(uuid)
                .fileName(fileName)
                .ord(images.size())
                .build();
        images.add(boardImage);
    }
    
    
    //모든 이미지 제거 - 도메인 로직
    public void clearImages() {
        this.images.clear();
    }
    
    /**
     * 게시글이 삭제되었는지 확인
     * @return 삭제 여부
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }


    /**
     * 이미지 변경 (기존 이미지 제거 후 새 이미지 추가)
     * @param fileNames 새로운 파일명 목록
     */
    public Board changeImages(List<String> fileNames) {
        Set<BoardImage> newImages = new HashSet<>();

        if (fileNames != null) {
            for (int i = 0; i < fileNames.size(); i++) {
                String fileName = fileNames.get(i);
                String[] arr = fileName.split("_");
                if (arr.length >= 2) {
                    BoardImage boardImage = BoardImage.builder()
                            .uuid(arr[0])
                            .fileName(arr[1])
                            .ord(i)
                            .build();
                    newImages.add(boardImage);
                }
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

}