package com.edu.edumeet.board.infrastructure;


import com.edu.edumeet.board.application.repository.search.BoardSearch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BoardJpaRepository extends JpaRepository<BoardJpaEntity,Long> , BoardSearch {

    @EntityGraph(attributePaths = {"imageSet"})
    @Query("select b from BoardJpaEntity b where b.id = :id")
    Optional<BoardJpaEntity> findByIdWithImages(Long id);
}
