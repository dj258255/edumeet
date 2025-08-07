package com.edu.edumeet.board.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BoardJpaRepository extends JpaRepository<BoardJpaEntity,Long>  {
    
    // 단건 조회용 EntityGraph (삭제되지 않은 엔티티만)
    @EntityGraph(attributePaths = {"imageSet"})
    @Query("select b from BoardJpaEntity b where b.id = :id and b.deletedAt is null")
    Optional<BoardJpaEntity> findByIdWithImages(Long id);
    
    // 삭제되지 않은 엔티티만 조회
    @Query("select b from BoardJpaEntity b where b.id = :id and b.deletedAt is null")
    Optional<BoardJpaEntity> findByIdNotDeleted(Long id);
    
    // 클래스별 삭제되지 않은 게시글 조회
    @Query("select b from BoardJpaEntity b where b.classId = :classId and b.deletedAt is null")
    List<BoardJpaEntity> findByClassIdNotDeleted(Long classId);
    
    // 카테고리별 삭제되지 않은 게시글 조회
    @Query("select b from BoardJpaEntity b where b.categoryId = :categoryId and b.deletedAt is null")
    List<BoardJpaEntity> findByCategoryIdNotDeleted(Long categoryId);
    
    // 페이징 처리된 삭제되지 않은 게시글 조회
    @Query("select b from BoardJpaEntity b where b.deletedAt is null")
    Page<BoardJpaEntity> findAllNotDeleted(Pageable pageable);

    //아래는 실험용 코드

//    // N+1 해결 테스트 코드 : Fetch Join (여러 건 조회용)
//    @Query("select distinct b from BoardJpaEntity b left join fetch b.imageSet where b.id > 0")
//    Page<BoardJpaEntity> searchWithAllFetchJoin(Pageable pageable);

    // EntityGraph를 사용한 N+1 해결 (여러 건 조회용)
//    @EntityGraph(attributePaths = {"imageSet"})
//    @Query("select distinct b from BoardJpaEntity b where b.id > 0")
//    Page<BoardJpaEntity> searchWithAllEntityGraph(Pageable pageable);

//    // SUBSELECT를 사용한 N+1 해결 (여러 건 조회용)
//    @Query("select distinct b from BoardJpaEntity b where b.id > 0")
//    Page<BoardJpaEntity> searchWithAllSubSelect(Pageable pageable);

}