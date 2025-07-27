package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.repository.search.BoardSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardJpaRepository extends JpaRepository<BoardJpaEntity,Long> , BoardSearch {
    
    // 단건 조회용 EntityGraph
    @EntityGraph(attributePaths = {"imageSet"})
    @Query("select b from BoardJpaEntity b where b.id = :id")
    Optional<BoardJpaEntity> findByIdWithImages(Long id);

    /**
     * 테스트 코드 끝나면 삭제하자 아랜
     */
    // N+1 해결 테스트 코드 : Fetch Join (여러 건 조회용)
    @Query("select distinct b from BoardJpaEntity b join fetch b.imageSet where b.id in :ids")
    List<BoardJpaEntity> findByIdsWithImagesFetchJoin(@Param("ids") List<Long> ids);

    // N+1 해결 테스트 코드 : EntityGraph (여러 건 조회용)  
    @EntityGraph(attributePaths = {"imageSet"})
    @Query("select b from BoardJpaEntity b where b.id in :ids")
    List<BoardJpaEntity> findByIdsWithEntityGraph(@Param("ids") List<Long> ids);
}