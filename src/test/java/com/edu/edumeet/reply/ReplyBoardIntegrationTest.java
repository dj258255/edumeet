package com.edu.edumeet.reply;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.reply.presentation.ReplyService;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test class focuses specifically on the integration between Reply and Board entities,
 * ensuring that the necessary Board entities exist before testing Reply functionality.
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyBoardIntegrationTest {

    @Autowired
    private ReplyService replyService;
    
    @Autowired
    private BoardJpaRepository boardJpaRepository;
    
    private Long boardId;
    
    @BeforeEach
    public void setUp() {
        // Clear all existing boards
        boardJpaRepository.deleteAll();
        
        // Create a test board
        Board board = Board.builder()
                .title("Test Board for Replies")
                .content("Test Content")
                .writer("testUser")
                .classId(1L)
                .build();
        
        BoardJpaEntity savedBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        boardId = savedBoard.getId();
        
        log.info("Created test board with ID: {}", boardId);
    }
    
    @Test
    @DisplayName("댓글 등록 테스트 - 게시글 존재 확인")
    public void testRegisterReply() {
        // Create a reply for the board
        ReplyDTO replyDTO = ReplyDTO.builder()
                .boardId(boardId)
                .replyText("Test Reply")
                .replayer("tester")
                .build();
        
        // Register the reply
        Long replyId = replyService.register(replyDTO);
        
        // Verify the reply was created
        assertThat(replyId).isNotNull();
        
        // Read the reply
        ReplyDTO savedReply = replyService.read(replyId);
        assertThat(savedReply).isNotNull();
        assertThat(savedReply.getBoardId()).isEqualTo(boardId);
        assertThat(savedReply.getReplyText()).isEqualTo("Test Reply");
        
        log.info("Successfully registered and verified reply with ID: {} for board ID: {}", 
                replyId, boardId);
    }
    
    @Test
    @DisplayName("대댓글 등록 테스트")
    public void testRegisterChildReply() {
        // Create a parent reply
        ReplyDTO parentReply = ReplyDTO.builder()
                .boardId(boardId)
                .replyText("Parent Reply")
                .replayer("tester")
                .build();
        
        Long parentReplyId = replyService.register(parentReply);
        
        // Create a child reply
        ReplyDTO childReply = ReplyDTO.builder()
                .boardId(boardId)
                .replyText("Child Reply")
                .replayer("tester")
                .parentReplyId(parentReplyId)
                .build();
        
        Long childReplyId = replyService.register(childReply);
        
        // Verify the child reply was created
        ReplyDTO savedChildReply = replyService.read(childReplyId);
        assertThat(savedChildReply).isNotNull();
        assertThat(savedChildReply.getParentReplyId()).isEqualTo(parentReplyId);
        
        log.info("Successfully registered and verified child reply with ID: {} for parent reply ID: {}", 
                childReplyId, parentReplyId);
    }
}