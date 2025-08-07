package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.reply.domain.Reply;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReplyRepositoryTests {

    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    // 테스트용 게시글 ID를 저장할 변수
    private Long testBoardId;

    @BeforeAll
    public void 테스트_더미데이터_생성() {
        // 기존 데이터 삭제
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();

        log.info("테스트 데이터 생성 시작");

        // 테스트용 게시글 생성 (필요한 최소한의 게시글만 생성)
        for (int i = 1; i <= 10; i++) {
            Board board = Board.builder()
                    .title("제목..." + i)
                    .content("내용..." + i)
                    .writer("user" + (i % 5))
                    .classId(1L)
                    .build();

            // 일부 게시글에만 이미지 추가
            if (i % 3 != 0) {
                board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }

            // 도메인 모델을 jpa 엔티티로 변환 후 저장
            BoardJpaEntity saved = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));

            if (i == 1) {
                testBoardId = saved.getId();
            }
        }

        log.info("테스트 데이터 생성 완료 : 첫 번째 게시글 ID : " + testBoardId);
    }

    @Test
    public void 테스트_댓글_삽입_및_조회() {
        // 실제 DB에 있는 board_id
        Reply reply = Reply.builder()
                .boardId(testBoardId)
                .replyText("댓글.....")
                .replayer("replyer1")
                .build();

        ReplyJpaEntity savedReply = replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));

        log.info("저장된 댓글 ID: " + savedReply.getId());
        log.info("댓글 내용: " + savedReply.getReplyText());
        log.info("등록일시: " + savedReply.getRegDate());
        log.info("수정일시: " + savedReply.getModDate());
        
        // 날짜 필드가 자동으로 채워졌는지 확인
        assertThat(savedReply.getRegDate()).isNotNull();
        assertThat(savedReply.getModDate()).isNotNull();
        assertThat(savedReply.getReplyText()).isEqualTo("댓글.....");
        assertThat(savedReply.getReplayer()).isEqualTo("replyer1");
        assertThat(savedReply.getBoard().getId()).isEqualTo(testBoardId);
    }
}