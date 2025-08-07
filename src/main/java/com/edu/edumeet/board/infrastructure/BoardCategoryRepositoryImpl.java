package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.BoardCategoryRepository;
import com.edu.edumeet.board.domain.BoardCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 게시판 카테고리 도메인 레포지토리 구현체
 * DDD의 레포지토리 패턴을 구현
 */
@Repository
@RequiredArgsConstructor
public class BoardCategoryRepositoryImpl implements BoardCategoryRepository {

    private final BoardCategoryJpaRepository boardCategoryJpaRepository;

    /**
     * 카테고리 저장
     * @param boardCategory 저장할 카테고리 도메인 객체
     * @return 저장된 카테고리의 ID
     */
    @Override
    public Long save(BoardCategory boardCategory) {
        BoardCategoryJpaEntity entity = BoardCategoryJpaEntity.fromDomain(boardCategory);
        BoardCategoryJpaEntity savedEntity = boardCategoryJpaRepository.save(entity);
        return savedEntity.getId();
    }

    /**
     * ID로 카테고리 조회
     * @param id 조회할 카테고리 ID
     * @return 조회된 카테고리, 없으면 빈 Optional 반환
     */
    @Override
    public Optional<BoardCategory> findById(Long id) {
        return boardCategoryJpaRepository.findById(id)
                .map(BoardCategoryJpaEntity::toModel);
    }

    /**
     * 클래스 ID로 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 해당 클래스의 카테고리 목록
     */
    @Override
    public List<BoardCategory> findByClassId(Long classId) {
        return boardCategoryJpaRepository.findByClassId(classId).stream()
                .map(BoardCategoryJpaEntity::toModel)
                .collect(Collectors.toList());
    }

    /**
     * 부모 카테고리 ID로 하위 카테고리 목록 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    @Override
    public List<BoardCategory> findByParentId(Long parentId) {
        return boardCategoryJpaRepository.findByParentId(parentId).stream()
                .map(BoardCategoryJpaEntity::toModel)
                .collect(Collectors.toList());
    }

    /**
     * 루트 카테고리 목록 조회 (부모가 없는 카테고리)
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    @Override
    public List<BoardCategory> findRootCategories(Long classId) {
        return boardCategoryJpaRepository.findByClassIdAndParentIdIsNull(classId).stream()
                .map(BoardCategoryJpaEntity::toModel)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리 삭제
     * @param id 삭제할 카테고리 ID
     */
    @Override
    public void deleteById(Long id) {
        boardCategoryJpaRepository.deleteById(id);
    }
}