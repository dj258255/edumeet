package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.*;
import com.edu.edumeet.reply.application.ReplyRepository;
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

    // BoardJpaRepository 대신 BoardRepository 인터페이스 사용
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final ReplyRepository replyRepository;
    
    /**
     * BoardListAllDTO를 BoardDTO로 변환하는 메서드
     */
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
                .build();
                
        // 이미지 정보 설정
        if (dto.getBoardImages() != null && !dto.getBoardImages().isEmpty()) {
            List<String> fileNames = dto.getBoardImages().stream()
                    .map(image -> image.getUuid() + "_" + image.getFileName())
                    .collect(Collectors.toList());
            boardDTO.setFileNames(fileNames);
        }
        
        return boardDTO;
    }

    /**
     * 게시글 등록
     * @param boardDTO 등록할 게시글 정보
     * @return 등록된 게시글의 ID
     */
    @Override
    public Long register(BoardDTO boardDTO) {
        Board board = dtoToDomain(boardDTO);

        Long id = boardRepository.save(board);

        return id;
    }

    @Override
    public void addImageToBoard(Long boardId, String uuid, String fileName){
        Optional<Board> result = boardRepository.findById(boardId);
        Board board = result.orElseThrow();
        board.addImage(uuid, fileName);
        boardRepository.save(board);
    }

    /**
     * 게시글 조회
     * @param id 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    @Override
    @Transactional
    public BoardDTO readOne(Long id){
        //board_image까지 조인 처리되는 findByWithImages()를 이용
        Optional<Board> result = boardRepository.findById(id);
        Board board = result.orElseThrow();
        
        // 조회수 증가 (불변 객체 패턴 사용)
        Board updatedBoard = board.increaseView();
        boardRepository.save(updatedBoard);

        return domainToDto(updatedBoard);
    }

    /**
     * 게시글 수정
     * @param boardDTO 수정할 게시글 정보
     */
    @Override
    public void modify(BoardDTO boardDTO) {
        Optional<Board> result = boardRepository.findById(boardDTO.getId());
        Board board = result.orElseThrow();

        // 도메인 로직을 통한 변경사항 적용
        Board changeBoard = board.change(boardDTO.getTitle(), boardDTO.getContent());
        
        // 이미지 변경 적용
        Board finalBoard = changeBoard.changeImages(boardDTO.getFileNames());

        // 수정된 게시글 저장
        boardRepository.save(finalBoard);
    }

    /**
     * 게시글 삭제 (논리적 삭제)
     * @param id 삭제할 게시글 ID
     */
    @Override
    public void remove(Long id) {
        // 댓글도 논리적 삭제 처리 (댓글 리포지토리에 해당 기능이 구현되어 있다고 가정)
        // replyRepository.markDeletedByBoardId(id);
        
        // 게시글 논리적 삭제
        boardRepository.deleteById(id);
    }
    
    /**
     * 삭제된 게시글 복원
     * @param id 복원할 게시글 ID
     */
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
        boardRepository.restoreById(id);
        
        // 관련 댓글도 복원 (댓글 리포지토리에 해당 기능이 구현되어 있다고 가정)
        // replyRepository.restoreByBoardId(id);
    }

    /**
     * 게시글 목록 조회
     * @param pageRequestDTO 페이징 및 검색 조건
     * @return 게시글 목록 및 페이징 정보
     */
    @Override
    public PageResponseDTO<BoardDTO> list(PageRequestDTO pageRequestDTO) {
        // 검색 조건 추출
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Long classId = pageRequestDTO.getClassId();
        Long categoryId = pageRequestDTO.getCategoryId();
        String boardType = pageRequestDTO.getBoardType();
        Pageable pageable = pageRequestDTO.getPageable("id");

        // 항상 searchWithAll 사용하여 카테고리 필터링 지원
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
        
    /**
     * 댓글 수가 포함된 게시글 목록 조회
     * @param pageRequestDTO 페이징 및 검색 조건
     * @return 게시글 목록, 댓글 수 및 페이징 정보
     */
    @Override
    public PageResponseDTO<BoardListReplyCountDTO> listWithReplyCount(PageRequestDTO pageRequestDTO) {
        // 검색 조건 추출
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("id");

        // 레포지토리를 통해 댓글 수가 포함된 게시글 검색
        Page<BoardListReplyCountDTO> result = boardRepository.searchWithReplyCount(types, keyword, pageable);

        // 페이징 응답 생성
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
    
    /**
     * 게시글 좋아요 토글
     * 이미 좋아요가 되어 있으면 좋아요 취소, 아니면 좋아요 추가
     * 카테고리별 추천 기준값을 적용하여 추천 게시글 여부 결정
     * @param id 좋아요 토글할 게시글 ID
     * @return 토글 후 좋아요 수
     */

    @Override
    @Transactional
    public long toggleFavorite(Long id) {
        Optional<Board> result = boardRepository.findById(id);
        Board board = result.orElseThrow();

        log.info(" 좋아요 토글 전 - ID: {}, 현재 좋아요: {}", id, board.getFavorite());

        // 카테고리 정보 조회하여 추천 기준값 가져오기
        int recommendThreshold = 10; // 기본값

        // 게시글에 카테고리 ID가 있는 경우 해당 카테고리의 추천 기준값 사용
        if (board.getCategoryId() != null) {
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(board.getCategoryId());
            if (categoryResult.isPresent()) {
                recommendThreshold = categoryResult.get().getRecommendThreshold();
            }
        }

        Board updatedBoard;

        // 좋아요 추가 (좋아요 취소 기능 제거)
        updatedBoard = board.increaseFavorite(recommendThreshold);

        log.info("🔍 좋아요 토글 후 - 새로운 좋아요: {}", updatedBoard.getFavorite());

        Long savedId = boardRepository.save(updatedBoard);
        log.info("🔍 저장 완료 - 저장된 ID: {}", savedId);

        return updatedBoard.getFavorite();
    }

    @Override
    @Transactional
    public long toggleDislike(Long id) {
        Optional<Board> result = boardRepository.findById(id);
        Board board = result.orElseThrow();

        log.info(" 싫어요 토글 전 - ID: {}, 현재 싫어요: {}", id, board.getDislike());

        // 카테고리 정보 조회하여 추천 기준값 가져오기
        int recommendThreshold = 10; // 기본값

        if (board.getCategoryId() != null) {
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(board.getCategoryId());
            if (categoryResult.isPresent()) {
                recommendThreshold = categoryResult.get().getRecommendThreshold();
            }
        }

        Board updatedBoard = board.increaseDislike(recommendThreshold);

        log.info("🔍 싫어요 토글 후 - 새로운 싫어요: {}", updatedBoard.getDislike());

        Long savedId = boardRepository.save(updatedBoard);
        log.info("🔍 저장 완료 - 저장된 ID: {}", savedId);

        // 저장 후 최신 상태의 게시글을 다시 조회하여 반환
        Optional<Board> refreshedResult = boardRepository.findById(id);
        Board refreshedBoard = refreshedResult.orElseThrow();
        return refreshedBoard.getDislike();
    }
}