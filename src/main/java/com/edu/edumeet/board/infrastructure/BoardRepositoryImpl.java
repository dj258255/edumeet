package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.BoardRepository;
import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 게시판 도메인 레포지토리 구현체
 * DDD의 레포지토리 패턴을 구현
 */
@Repository
@RequiredArgsConstructor
public class BoardRepositoryImpl implements BoardRepository {

    private final BoardJpaRepository boardJpaRepository;
    private final BoardSearchRepository boardSearchRepository;

    /**
     * 게시글 저장
     * @param board 저장할 게시글 도메인 객체
     * @return 저장된 게시글의 ID, 업데이트할 게시글이 존재하지 않을 경우 null
     */
    @Override
    public Long save(Board board) {
        BoardJpaEntity boardJpaEntity;

        if(board.getId() != null) {
            //기존 게시글 업데이트
            Optional<BoardJpaEntity> existingEntity = boardJpaRepository.findById(board.getId());
            if(existingEntity.isPresent()){
                //기존 엔티티 업데이트
                boardJpaEntity = existingEntity.get();
                boardJpaEntity.updateFromDomain(board);
            }else{
                // 예외를 던지지 않고 null 반환하여 서비스에서 처리하도록 함
                return null;
            }
        }else{
            //새로운 게시글 생성
            boardJpaEntity = BoardJpaEntity.fromDomain(board);
        }

        //도메인 -> JPA 엔티티 변환 후 저장
        BoardJpaEntity savedEntity = boardJpaRepository.save(boardJpaEntity);
        return savedEntity.getId();
    }

    @Override
    public Optional<Board> findById(Long id) {
        // JPA 레포지토리를 통해 엔티티 조회
        Optional<BoardJpaEntity> entityOptional = boardJpaRepository.findById(id);
        
        // 삭제된 엔티티는 제외
        if (entityOptional.isPresent() && entityOptional.get().isDeleted()) {
            return Optional.empty();
        }
        
        // 엔티티를 도메인 모델로 변환하여 반환
        return entityOptional.map(BoardJpaEntity::toModel);
    }

    @Override
    public void deleteById(Long id) {
        // 물리적 삭제 대신 논리적 삭제 수행
        Optional<BoardJpaEntity> entityOptional = boardJpaRepository.findById(id);
        if (entityOptional.isPresent()) {
            BoardJpaEntity entity = entityOptional.get();
            entity.markDeleted(); // BaseEntity의 markDeleted 메서드 호출
            boardJpaRepository.save(entity);
        }
        // 게시글이 존재하지 않아도 예외를 던지지 않음 (서비스에서 처리)
    }
    
    /**
     * 삭제된 게시글 복원
     * @param id 복원할 게시글 ID
     * @return 복원 성공 여부
     */
    @Override
    public boolean restoreById(Long id) {
        // 삭제된 게시글 포함하여 조회
        Optional<BoardJpaEntity> entityOptional = boardJpaRepository.findById(id);
        if (entityOptional.isPresent()) {
            BoardJpaEntity entity = entityOptional.get();
            // deletedAt 필드를 null로 설정하여 복원
            entity.restoreDeleted();
            boardJpaRepository.save(entity);
            return true;
        }
        return false; // 게시글이 존재하지 않음을 서비스에 알림
    }
    
    @Override
    public Optional<Board> findByIdIncludeDeleted(Long id) {
        // JPA 레포지토리를 통해 엔티티 조회 (삭제 여부 관계없이)
        Optional<BoardJpaEntity> entityOptional = boardJpaRepository.findById(id);
        // 엔티티를 도메인 모델로 변환하여 반환
        return entityOptional.map(BoardJpaEntity::toModel);
    }

    @Override
    public Page<Board> searchAll(String[] types, String keyword, Pageable pageable) {
        // BoardSearchRepository 인터페이스의 searchAll 메소드 호출
        return boardSearchRepository.searchAll(types, keyword, pageable);
    }

    @Override
    public Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable) {
        // BoardSearchRepository 인터페이스의 searchWithReplyCount 메소드 호출
        return boardSearchRepository.searchWithReplyCount(types, keyword, pageable);
    }

    @Override
    public Page<BoardListAllDTO> searchWithAll(String[] types, String keyword, Long classId, Long categoryId, String boardType, Pageable pageable){
        //BoardSearchRepository 인터페이스의 searchWithAll 메소드 호출 (JPA Repository에 위임)
        return boardSearchRepository.searchWithAll(types, keyword, classId, categoryId, boardType, pageable);
    }
}