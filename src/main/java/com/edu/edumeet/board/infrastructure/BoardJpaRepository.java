package com.edu.edumeet.board.infrastructure;


import com.edu.edumeet.board.application.repository.search.BoardSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BoardJpaRepository extends JpaRepository<BoardJpaEntity,Long> , BoardSearch {

    @Query(value = "select now()" , nativeQuery = true)
    String getTime();
}
