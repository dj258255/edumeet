package com.edu.edumeet.homework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentJpaRepository extends JpaRepository<AssignmentJpaEntity, Long> {

    List<AssignmentJpaEntity> findByClassIdOrderByRegDateDesc(Long classId);

    Optional<AssignmentJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT a FROM AssignmentJpaEntity a LEFT JOIN FETCH a.attachmentFiles WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AssignmentJpaEntity> findByIdWithAttachmentFiles(@Param("id") Long id);

    @Query("SELECT a FROM AssignmentJpaEntity a LEFT JOIN FETCH a.studentSubmissionStatuses WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AssignmentJpaEntity> findByIdWithSubmissionStatuses(@Param("id") Long id);

    // N+1 문제 해결을 위한 FetchJoin 쿼리들
    /**
     * 과제 상세 조회.
     *
     * 이전에는 attachmentFiles 와 studentSubmissionStatuses 를 동시에 LEFT JOIN FETCH 했다.
     * Set 매핑이라 MultipleBagFetchException 은 나지 않지만,
     * DB 는 첨부 M건 x 제출현황 N건 = M x N 행을 만들어 보낸다.
     * (첨부 5 / 제출현황 20 기준 100행. 나눠 조회하면 26행 - CartesianProductTest 참조)
     *
     * 컬렉션은 hibernate.default_batch_fetch_size 로 일괄 로딩되므로
     * 여기서 조인하지 않는다.
     */
    @Query("SELECT a FROM AssignmentJpaEntity a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AssignmentJpaEntity> findByIdWithAllDetails(@Param("id") Long id);

}