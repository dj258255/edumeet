package com.edu.edumeet.board.repository;

import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardImageJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BoardRepositoryTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardSearchRepository boardSearchRepository;

    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    private Long testBoardId;

    @BeforeAll
    public void 테스트_더미데이터_생성() {
        // 기존 데이터 삭제
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();

        log.info("테스트 데이터 생성 시작");

        for (int i = 1; i <= 100; i++) {
            Board.BoardBuilder boardBuilder = Board.builder()
                    .title("title..." + i)
                    .content("content..." + i)
                    .writer("user" + (i % 10))
                    .classId(1L);

            Board board = boardBuilder.build();

            // 도메인로직으로 이미지 추가
            for (int j = 0; j < 3; j++) {
                if (i % 5 == 0) {
                    continue;
                }
                board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }

            // 도메인 모델을 jpa 엔티티로 변환 후 저장
            BoardJpaEntity savedEntity = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));

            if (i == 1) {
                testBoardId = savedEntity.getId();
            }
        }

        // 클래스 2 데이터 추가 (검색 테스트용)
        for (int i = 101; i <= 110; i++) {
            Board board = Board.builder()
                    .title("클래스2_title..." + i)
                    .content("클래스2_content..." + i)
                    .writer("클래스2_user" + (i % 5))
                    .classId(2L)
                    .build();

            // 이미지도 추가
            if (i % 3 != 0) {
                board.addImage(UUID.randomUUID().toString(), "class2_file" + i + ".jpg");
            }

            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }

        log.info("테스트 데이터 생성 완료 : 첫 번째 게시글 ID : " + testBoardId);
    }

    @Test
    public void 테스트_게시글정보보기() {
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        log.info("게시글 정보 : " + boardJpaEntity);
        log.info("이미지 개수 : " + boardJpaEntity.getImageSet().size());
        
        assertThat(boardJpaEntity).isNotNull();
        assertThat(boardJpaEntity.getTitle()).contains("title");
    }

    @Test
    public void 테스트_게시글검색_및_댓글개수() {
        String[] types = {"t", "c", "w"};
        String keyword = "1";
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        Page<BoardListReplyCountDTO> result = boardSearchRepository
                .searchWithReplyCount(types, keyword, pageable);

        log.info("Total Pages: " + result.getTotalPages());
        log.info("Page Size: " + result.getSize());
        log.info("Page Number: " + result.getNumber());
        log.info("Has Previous: " + result.hasPrevious() + " Has Next: " + result.hasNext());

        assertThat(result.getContent()).isNotEmpty();
        
        result.getContent().forEach(board -> {
            log.info("Board ID: " + board.getId());
            log.info("Title: " + board.getTitle());
            log.info("Writer: " + board.getWriter());
            log.info("Reply Count: " + board.getReplyCount());
            log.info("-------------------");
        });
    }

    @Test
    public void 테스트_이미지와함께_게시글읽기() {
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(testBoardId);
        
        BoardJpaEntity boardJpaEntity = result.orElseThrow(() ->
                new RuntimeException("게시글을 찾을 수 없습니다. ID: " + testBoardId));

        log.info(boardJpaEntity);
        log.info("------------------");

        assertThat(boardJpaEntity).isNotNull();
        
        for (BoardImageJpaEntity boardImageJpaEntity : boardJpaEntity.getImageSet()) {
            log.info(boardImageJpaEntity);
        }
    }

    @Test
    public void 테스트_검색_이미지_댓글개수() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        // classId, categoryId, boardType 파라미터 추가 (null = 전체 조회)
        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null, null, null, null, null, pageable);

        log.info("조회된 게시글 수: " + result.getContent().size());
        log.info("전체 게시글 수 : " + result.getTotalElements());

        assertThat(result.getContent()).isNotEmpty();
        
        result.getContent().forEach(dto -> {
            log.info("ID: {}, 제목: {}, 클래스: {}, 댓글수: {}, 조회수: {}, 좋아요: {}, 이미지수: {}",
                    dto.getId(), dto.getTitle(), dto.getClassId(), dto.getReplyCount(),
                    dto.getView(), dto.getFavorite(),
                    dto.getBoardImages() != null ? dto.getBoardImages().size() : 0);
            
            // 조회수와 좋아요 필드가 포함되어 있는지 확인
            assertThat(dto.getView()).isNotNull();
            assertThat(dto.getFavorite()).isNotNull();
        });
    }

    @Test
    public void 테스트_클래스별_검색() {
        Pageable pageable = PageRequest.of(0, 5, Sort.by("id").descending());

        // 클래스 1만 조회
        Page<BoardListAllDTO> result1 = boardSearchRepository
                .searchWithAll(null, null, 1L, null, null, pageable);

        // 클래스 2만 조회
        Page<BoardListAllDTO> result2 = boardSearchRepository
                .searchWithAll(null, null, 2L, null, null, pageable);

        log.info("클래스 1 게시글 수: {}", result1.getTotalElements());
        log.info("클래스 2 게시글 수: {}", result2.getTotalElements());

        // 검증
        assertThat(result1.getContent()).isNotEmpty();
        assertThat(result2.getContent()).isNotEmpty();
        
        result1.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(1L);
            log.info("클래스1 - ID: {}, 제목: {}, 댓글: {}, 조회수: {}, 좋아요: {}",
                    dto.getId(), dto.getTitle(), dto.getReplyCount(), dto.getView(), dto.getFavorite());
        });

        result2.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(2L);
            log.info("클래스2 - ID: {}, 제목: {}, 댓글: {}, 조회수: {}, 좋아요: {}",
                    dto.getId(), dto.getTitle(), dto.getReplyCount(), dto.getView(), dto.getFavorite());
        });
        
        // 키워드 검색 테스트
        Page<BoardListAllDTO> keywordResult = boardSearchRepository
                .searchWithAll(new String[]{"t"}, "title", 1L, null, null, pageable);
                
        log.info("클래스 1에서 'title' 검색 결과: {}건", keywordResult.getTotalElements());
        
        assertThat(keywordResult.getContent()).isNotEmpty();
        keywordResult.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(1L);
            assertThat(dto.getTitle().toLowerCase()).contains("title");
        });
    }
}