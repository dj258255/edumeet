package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.repository.search.BoardSearch;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
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