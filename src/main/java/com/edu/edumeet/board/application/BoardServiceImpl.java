package com.edu.edumeet.board.application;

import com.edu.edumeet.board.application.repository.BoardRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
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

    /**
     * 게시글 등록
     * @param boardDTO 등록할 게시글 정보
     * @return 등록된 게시글의 ID
     */
    @Override
    public Long register(BoardDTO boardDTO) {
        // DTO를 도메인 모델로 변환
        Board board = modelMapper.map(boardDTO, Board.class);
        // 레포지토리를 통해 저장하고 ID 반환
        return boardRepository.save(board);
    }

    /**
     * 게시글 조회
     * @param id 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    @Override
    public BoardDTO readOne(Long id) {
        // 레포지토리를 통해 도메인 모델 조회
        Optional<Board> result = boardRepository.findById(id);
        // 없으면 예외 발생
        Board board = result.orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id));
        // 도메인 모델을 DTO로 변환하여 반환
        return modelMapper.map(board, BoardDTO.class);
    }

    /**
     * 게시글 수정
     * @param boardDTO 수정할 게시글 정보
     */
    @Override
    public void modify(BoardDTO boardDTO) {
        // 레포지토리를 통해 도메인 모델 조회
        Optional<Board> result = boardRepository.findById(boardDTO.getId());
        // 없으면 예외 발생
        Board board = result.orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + boardDTO.getId()));
        // 도메인 모델 수정
        board.change(boardDTO.getTitle(), boardDTO.getContent());
        // 레포지토리를 통해 저장
        boardRepository.save(board);
    }

    /**
     * 게시글 삭제
     * @param id 삭제할 게시글 ID
     */
    @Override
    public void remove(Long id) {
        // 레포지토리를 통해 삭제
        boardRepository.deleteById(id);
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
        Pageable pageable = pageRequestDTO.getPageable("id");

        // 레포지토리를 통해 검색
        Page<Board> result = boardRepository.searchAll(types, keyword, pageable);

        // 도메인 모델을 DTO로 변환
        List<BoardDTO> dtoList = result.getContent().stream()
                .map(board -> modelMapper.map(board, BoardDTO.class))
                .collect(Collectors.toList());

        // 페이징 응답 생성
        return PageResponseDTO.<BoardDTO>of()
                .page(pageRequestDTO.getPage())
                .size(pageRequestDTO.getSize())
                .dtoList(dtoList)
                .total((int)result.getTotalElements())
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
}
