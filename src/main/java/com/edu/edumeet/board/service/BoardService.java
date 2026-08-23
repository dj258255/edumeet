package com.edu.edumeet.board.service;

import com.edu.edumeet.board.repository.BoardCategoryRepository;
import com.edu.edumeet.board.dto.BoardDTO;
import com.edu.edumeet.board.dto.BoardListAllDTO;
import com.edu.edumeet.board.dto.BoardListReplyCountDTO;
import com.edu.edumeet.board.repository.BoardRepository;
import com.edu.edumeet.board.repository.BoardRepositoryImpl;
import com.edu.edumeet.board.dto.PageRequestDTO;
import com.edu.edumeet.board.dto.PageResponseDTO;
import com.edu.edumeet.attachment.dto.AttachmentDTO;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.service.BoardService;
import com.edu.edumeet.board.dto.*;
import com.edu.edumeet.reply.repository.ReplyRepository;
import com.edu.edumeet.s3.service.S3Uploader;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.dto.AttachmentAdapter;
import com.edu.edumeet.attachment.service.AttachmentService;
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
public class BoardService {

    private final ModelMapper modelMapper;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final ReplyRepository replyRepository;
    private final S3Uploader s3Uploader;
    private final AttachmentAdapter attachmentAdapter;
    private final AttachmentService attachmentService;
    
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
    public void addImageToBoard(Long boardId, String uuid, String fileName){
        Board board = findBoardByIdOrThrow(boardId);
        board.addImage(uuid, fileName);
        
        // Board 도메인 객체 저장
        Long savedId = boardRepository.save(board);
        validateSaveResult(savedId, "게시글 업데이트");
        
        // Attachment 도메인 객체 생성 및 저장
        Attachment attachment = Attachment.builder()
                .uuid(uuid)
                .fileName(fileName)
                .ord(board.getImages().size() - 1) // 방금 추가된 이미지의 순서
                .img(true) // 게시판 이미지는 항상 이미지로 간주
                .domain("board")
                .referenceId(boardId)
                .build();
        
        // Attachment 정보는 BoardRepositoryImpl에서 BoardFileUploadJpaEntity로 저장됨
        
        log.info("게시글 {}에 이미지 추가 완료: UUID={}, fileName={}", boardId, uuid, fileName);
    }
    @Transactional
    public BoardDTO readOne(Long id){
        Board board = findBoardByIdOrThrow(id);
        
        // 조회수 증가 (불변 객체 패턴 사용)
        Board updatedBoard = board.increaseView();
        Long savedId = boardRepository.save(updatedBoard);
        validateSaveResult(savedId, "게시글 조회수 업데이트");

        return domainToDto(updatedBoard, s3Uploader);
    }
    public void modify(BoardDTO boardDTO) {
        Board board = findBoardByIdOrThrow(boardDTO.getId());

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
        validateSaveResult(savedId, "게시글 수정");
        
        // 파일 업로드 처리
        handleFileOperations(boardDTO);
    }
    public void remove(Long id) {
        // 게시글 존재 여부 확인
        findBoardByIdOrThrow(id);
        
        // 게시글 논리적 삭제
        boardRepository.deleteById(id);
        
        // 관련 Attachment 정보는 유지 (게시글 복원 시 필요할 수 있음)
        // 실제 S3 파일은 삭제하지 않음 (다른 곳에서 참조할 수 있음)
        log.info("게시글 {} 삭제 완료 (관련 파일 정보는 유지)", id);
    }
    @Transactional
    public void restore(Long id) {
        // 삭제된 게시글 포함하여 조회
        Board board = findBoardByIdIncludeDeletedOrThrow(id);
        
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
        
        return buildPageResponse(pageRequestDTO, dtoList, (int)allResult.getTotalElements());
    }
    public PageResponseDTO<BoardListReplyCountDTO> listWithReplyCount(PageRequestDTO pageRequestDTO) {
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("id");

        Page<BoardListReplyCountDTO> result = boardRepository.searchWithReplyCount(types, keyword, pageable);

        return buildPageResponse(pageRequestDTO, result.getContent(), (int)result.getTotalElements());
    }
    public PageResponseDTO<BoardListAllDTO> listWithAll(PageRequestDTO pageRequestDTO){
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("id");
        Long classId = pageRequestDTO.getClassId();
        Long categoryId = pageRequestDTO.getCategoryId();
        String boardType = pageRequestDTO.getBoardType();

        Page<BoardListAllDTO> result = boardRepository.searchWithAll(types, keyword, classId, categoryId, boardType, pageable);

        return buildPageResponseWithAll(pageRequestDTO, result.getContent(), (int)result.getTotalElements());
    }
    @Transactional
    public long toggleFavorite(Long id) {
        Board board = findBoardByIdOrThrow(id);
        int recommendThreshold = getRecommendThreshold(board);

        Board updatedBoard = board.increaseFavorite(recommendThreshold);
        Long savedId = boardRepository.save(updatedBoard);
        validateSaveResult(savedId, "좋아요 업데이트");

        return updatedBoard.getFavorite();
    }
    @Transactional
    public long toggleDislike(Long id) {
        Board board = findBoardByIdOrThrow(id);

        Board updatedBoard = board.increaseDislike();

        Long savedId = boardRepository.save(updatedBoard);
        validateSaveResult(savedId, "싫어요 업데이트");
        
        return updatedBoard.getDislike();
    }

    private void validateCategoryExists(Long categoryId){
        if(categoryId != null){
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(categoryId);
            if(categoryResult.isEmpty()){
                throw new IllegalArgumentException("존재하지 않는 카테고리입니다: " + categoryId);
            }
        }
    }

    private Board findBoardByIdOrThrow(Long id) {
        Optional<Board> result = boardRepository.findById(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        return result.get();
    }

    private Board findBoardByIdIncludeDeletedOrThrow(Long id) {
        Optional<Board> result = boardRepository.findByIdIncludeDeleted(id);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id);
        }
        return result.get();
    }

    private void validateSaveResult(Long savedId, String operation) {
        if (savedId == null) {
            throw new IllegalArgumentException(operation + "에 실패했습니다");
        }
    }

    private int getRecommendThreshold(Board board) {
        int recommendThreshold = 10; // 기본값
        
        if (board.getCategoryId() != null) {
            Optional<BoardCategory> categoryResult = boardCategoryRepository.findById(board.getCategoryId());
            if (categoryResult.isPresent()) {
                recommendThreshold = categoryResult.get().getRecommendThreshold();
            }
        }
        
        return recommendThreshold;
    }

    private void handleFileOperations(BoardDTO boardDTO) {
        // 파일 정보는 BoardRepositoryImpl.save()에서 BoardFileUploadJpaEntity로 처리됨
        // Board 도메인 객체의 images 컬렉션을 통해 파일 정보가 저장됨
        
        if (boardDTO.getBoardImages() != null && !boardDTO.getBoardImages().isEmpty()) {
            log.info("게시글 {}의 이미지 {}개가 BoardRepositoryImpl에서 처리됨", 
                    boardDTO.getId(), boardDTO.getBoardImages().size());
        }
    }

    private <T> PageResponseDTO<T> buildPageResponse(PageRequestDTO pageRequestDTO, List<T> dtoList, int total) {
        return PageResponseDTO.<T>of()
                .page(pageRequestDTO.getPage())
                .size(pageRequestDTO.getSize())
                .dtoList(dtoList)
                .total(total)
                .build();
    }

    private <T> PageResponseDTO<T> buildPageResponseWithAll(PageRequestDTO pageRequestDTO, List<T> dtoList, int total) {
        return PageResponseDTO.<T>of()
                .page(pageRequestDTO.getPage())
                .size(pageRequestDTO.getSize())
                .dtoList(dtoList)
                .total(total)
                .build();
    }

    private void validateBoardData(BoardDTO boardDTO) {
        if (boardDTO.getTitle() == null || boardDTO.getTitle().isEmpty()) {
            throw new IllegalArgumentException("제목은 필수입니다");
        }

        if (boardDTO.getTitle().length() > 50){
            throw new IllegalArgumentException("제목이 50자를 초과합니다");
        }
    }

    // ---- 이전에 서비스 인터페이스의 default 메서드였던 변환 ----
    // 인터페이스의 default 메서드였으므로 가시성(public)을 그대로 유지한다.

    public Board dtoToDomain(BoardDTO boardDTO){
        // BoardType 설정
        BoardType boardTypeEnum = BoardType.NORMAL; // 기본값
        if (boardDTO.getBoardType() != null && !boardDTO.getBoardType().isEmpty()) {
            try {
                boardTypeEnum = BoardType.valueOf(boardDTO.getBoardType());
            } catch (IllegalArgumentException e) {
                // 잘못된 boardType이면 기본값 사용
                // 로그 추가 가능
            }
        }
        
        // 기본 Board 객체 생성
        Board board = Board.builder()
                .id(boardDTO.getId())
                .title(boardDTO.getTitle())
                .content(boardDTO.getContent())
                .writer(boardDTO.getWriter())
                .classId(boardDTO.getClassId())
                .categoryId(boardDTO.getCategoryId())
                .boardType(boardTypeEnum)
                .regDate(boardDTO.getRegDate())
                .modDate(boardDTO.getModDate())
                .view(boardDTO.getView())
                .favorite(boardDTO.getFavorite())
                .dislike(boardDTO.getDislike())
                .build();

        // AttchmentDTO 정보를 도메인 이미지로 변환
        if(boardDTO.getBoardImages() != null){
            for (AttachmentDTO imageDTO : boardDTO.getBoardImages()) {
                board.addImage(imageDTO.getUuid(), imageDTO.getFileName());
            }
        }
        
        return board;
    }
    /**
     * Board 도메인 객체를 BoardDTO로 변환
     * @param board 변환할 Board 도메인 객체
     * @param s3Uploader S3 URL 생성을 위한 S3Uploader
     * @return 변환된 BoardDTO 객체
     */
    public BoardDTO domainToDto(Board board, S3Uploader s3Uploader){
        BoardDTO boardDTO = BoardDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .classId(board.getClassId())
                .categoryId(board.getCategoryId())
                .boardType(board.getBoardType() != null ? board.getBoardType().name() : null)
                .regDate(board.getRegDate())
                .modDate(board.getModDate())
                .view(board.getView())
                .favorite(board.getFavorite())
                .dislike(board.getDislike())
                .build();

        // 도메인 이미지를 FileUploadDTO로 변환
        if (board.getImages() != null && !board.getImages().isEmpty()) {
            List<AttachmentDTO> boardImages = board.getImages().stream()
                    .sorted()
                    .map(img -> {
                        String uuid = img.getUuid();
                        String fileName = img.getFileName();
                        
                        // S3Uploader를 사용하여 도메인 경로 포함된 URL 생성
                        String s3Url = s3Uploader.getDomainOriginalUrl(img.getDomain(), uuid, fileName);
                        String s3ThumbnailUrl = s3Uploader.getDomainThumbnailUrl(img.getDomain(), uuid, fileName);
                        
                        return AttachmentDTO.builder()
                                .uuid(uuid)
                                .fileName(fileName)
                                .ord(img.getOrd())
                                .s3Url(s3Url)
                                .s3ThumbnailUrl(s3ThumbnailUrl)
                                .img(true)
                                .domain("board")
                                .referenceId(img.getReferenceId())
                                .build();
                    })
                    .collect(Collectors.toList());
            boardDTO.setBoardImages(boardImages);
        }

        return boardDTO;
    }
}
