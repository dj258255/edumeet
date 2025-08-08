package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.*;
import com.edu.edumeet.reply.application.ReplyRepository;
import com.edu.edumeet.util.S3Uploader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 게시판 서비스 구현체
 * DDD의 애플리케이션 계층에서 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class BoardServiceImpl implements BoardService {

    private final ModelMapper modelMapper;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final ReplyRepository replyRepository;
    private final S3Uploader s3Uploader;
    
    private BoardDTO convertToBoardDTO(BoardListAllDTO dto) {
        BoardDTO boardDTO = BoardDTO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .writer(dto.getWriter())
                .regDate(dto.getRegDate())
                .classId(dto.getClassId())
                .categoryId(dto.getCategoryId())
                .boardType(dto.getBoardType() != null ? dto.getBoardType().name() : null)
                .view(dto.getView())
                .favorite(dto.getFavorite())
                .dislike(dto.getDislike())
                .boardImages(dto.getBoardImages())
                .build();
        
        return boardDTO;
    }

    @Override
    public Long register(BoardDTO boardDTO) {
        validateBoardData(boardDTO);
        validateCategoryExists(boardDTO.getCategoryId());

        Board board = dtoToDomain(boardDTO);
        Long savedId = boardRepository.save(board);
        
        // 서비스 계층에서 예외 처리
        if (savedId == null) {
            throw new IllegalArgumentException("게시글 저장에 실패했습니다.");
        }
        
        return savedId;
    }

    @Override
    public void addImageToBoard(Long boardId, String uuid, String fileName){
        Optional<Board> result = boardRepository.findById(boardId);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + boardId);
        }
        
        Board board = result.get();
        board.addImage(uuid, fileName);
        
        Long savedId = boardRepository.save(board);
        if (savedId == null) {
            throw new IllegalArgumentException("게시글 업데이트에 실패했습니다: " + boardId);
        }
    }

    @Override
    @Transactional
    public BoardDTO readOne(Long id){
        Optional<Board> result = boardRepository.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        
        Board board = result.get();
        
        // 조회수 증가 (불변 객체 패턴 사용)
        Board updatedBoard = board.increaseView();
        Long savedId = boardRepository.save(updatedBoard);
        
        if (savedId == null) {
            throw new IllegalArgumentException("게시글 조회수 업데이트에 실패했습니다: " + id);
        }

        return domainToDto(updatedBoard, s3Uploader);
    }

    @Override
    public void modify(BoardDTO boardDTO) {
        Optional<Board> result = boardRepository.findById(boardDTO.getId());
        if (result.isEmpty()) {
            throw new IllegalArgumentException("수정할 게시글을 찾을 수 없습니다: " + boardDTO.getId());
        }
        
        Board board = result.get();

        // 유효성 검증
        validateBoardData(boardDTO);

        // 카테고리 변경 시 존재 여부 검증
        if (boardDTO.getCategoryId() != null &&
                !boardDTO.getCategoryId().equals(board.getCategoryId())) {
            validateCategoryExists(boardDTO.getCategoryId());
        }

        // 체이닝을 통한 일관된 변경 처리
        Board modifiedBoard = board
            .changeBoardTypeFromString(boardDTO.getBoardType())
            .change(boardDTO.getTitle(), boardDTO.getContent())
            .changeBoardImages(boardDTO.getBoardImages());

        // 수정된 게시글 저장
        Long savedId = boardRepository.save(modifiedBoard);
        if (savedId == null) {
            throw new IllegalArgumentException("게시글 수정에 실패했습니다: " + boardDTO.getId());
        }
    }

    @Override
    public void remove(Long id) {
        // 게시글 존재 여부 확인
        Optional<Board> result = boardRepository.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("삭제할 게시글을 찾을 수 없습니다: " + id);
        }
        
        // 게시글 논리적 삭제
        boardRepository.deleteById(id);
    }
    
    @Override
    @Transactional
    public void restore(Long id) {
        // 삭제된 게시글 포함하여 조회
        Optional<Board> result = boardRepository.findByIdIncludeDeleted(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        
        Board board = result.get();
        // 이미 삭제되지 않은 상태인지 확인
        if (!board.isDeleted()) {
            throw new IllegalStateException("이미 복원된 게시글입니다: " + id);
        }
        
        // 게시글 복원
        boolean restored = boardRepository.restoreById(id);
        if (!restored) {
            throw new IllegalArgumentException("게시글 복원에 실패했습니다: " + id);
        }
    }

    @Override
    public PageResponseDTO<BoardDTO> list(PageRequestDTO pageRequestDTO) {
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Long classId = pageRequestDTO.getClassId();
        Long categoryId = pageRequestDTO.getCategoryId();
        String boardType = pageRequestDTO.getBoardType();
        Pageable pageable = pageRequestDTO.getPageable("id");

        Page<BoardListAllDTO> allResult = boardRepository.searchWithAll(types, keyword, classId, categoryId, boardType, pageable);
        
        List<BoardDTO> dtoList = allResult.getContent().stream()
                .map(this::convertToBoardDTO)
                .collect(Collectors.toList());
        
        return PageResponseDTO.<BoardDTO>of()
                .page(pageRequestDTO.getPage())
                .size(pageRequestDTO.getSize())
                .dtoList(dtoList)
                .total((int)allResult.getTotalElements())
                .build();
    }

    @Override
    public PageResponseDTO<BoardListReplyCountDTO> listWithReplyCount(PageRequestDTO pageRequestDTO) {
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("id");

        Page<BoardListReplyCountDTO> result = boardRepository.searchWithReplyCount(types, keyword, pageable);

        return PageResponseDTO.<BoardListReplyCountDTO>of()
                .page(pageRequestDTO.getPage())
                .size(pageRequestDTO.getSize())
                .dtoList(result.getContent())
                .total((int)result.getTotalElements())
                .build();
    }

    @Override
    public PageResponseDTO<BoardListAllDTO> listWithAll(PageRequestDTO pageRequestDTO){
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("id");
        Long classId = pageRequestDTO.getClassId();
        Long categoryId = pageRequestDTO.getCategoryId();
        String boardType = pageRequestDTO.getBoardType();

        Page<BoardListAllDTO> result = boardRepository.searchWithAll(types, keyword, classId, categoryId, boardType, pageable);

        return PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(pageRequestDTO)
                .dtoList(result.getContent())
                .total((int)result.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public long toggleFavorite(Long id) {
        Optional<Board> result = boardRepository.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        
        Board board = result.get();
        int recommendThreshold = 10;
        
        if (board.getCategoryId() != null) {
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(board.getCategoryId());
            if (categoryResult.isPresent()) {
                recommendThreshold = categoryResult.get().getRecommendThreshold();
            }
        }
        
        Board updatedBoard = board.increaseFavorite(recommendThreshold);
        Long savedId = boardRepository.save(updatedBoard);
        
        if (savedId == null) {
            throw new IllegalArgumentException("좋아요 업데이트에 실패했습니다: " + id);
        }
        
        return updatedBoard.getFavorite();
    }

    @Override
    @Transactional
    public long toggleDislike(Long id) {
        Optional<Board> result = boardRepository.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        
        Board board = result.get();

        Board updatedBoard = board.increaseDislike();

        Long savedId = boardRepository.save(updatedBoard);
        if (savedId == null) {
            throw new IllegalArgumentException("싫어요 업데이트에 실패했습니다: " + id);
        }

        // 저장 후 최신 상태의 게시글을 다시 조회하여 반환
        Optional<Board> refreshedResult = boardRepository.findById(id);
        Board refreshedBoard = refreshedResult.orElseThrow(() -> 
            new IllegalArgumentException("업데이트된 게시글을 다시 조회할 수 없습니다: " + id));
        
        return refreshedBoard.getDislike();
    }

    private void validateCategoryExists(Long categoryId){
        if(categoryId != null){
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(categoryId);
            if(categoryResult.isEmpty()){
                throw new IllegalArgumentException("존재하지 않는 카테고리입니다: " + categoryId);
            }
        }
    }

    private void validateBoardData(BoardDTO boardDTO) {
        if (boardDTO.getTitle() == null || boardDTO.getTitle().isEmpty()) {
            throw new IllegalArgumentException("제목은 필수입니다");
        }

        if (boardDTO.getTitle().length() > 50){
            throw new IllegalArgumentException("제목이 50자를 초과합니다");
        }
    }
}