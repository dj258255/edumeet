package com.edu.edumeet.homework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionJpaRepository extends JpaRepository<SubmissionJpaEntity, Long> {

    List<SubmissionJpaEntity> findByClassMemberEmailOrderByRegDateDesc(String classMemberEmail);

    @Query("SELECT s FROM SubmissionJpaEntity s WHERE s.assignmentId = :assignmentId AND s.classMemberEmail = :classMemberEmail AND s.deletedAt IS NULL")
    Optional<SubmissionJpaEntity> findByAssignmentIdAndClassMemberEmailAndDeletedAtIsNull(@Param("assignmentId") Long assignmentId, @Param("classMemberEmail") String classMemberEmail);

    @Query("SELECT s FROM SubmissionJpaEntity s WHERE s.assignmentId = :assignmentId AND s.deletedAt IS NULL")
    List<SubmissionJpaEntity> findByAssignmentIdAndDeletedAtIsNull(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM SubmissionJpaEntity s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<SubmissionJpaEntity> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT s FROM SubmissionJpaEntity s LEFT JOIN FETCH s.submissionFiles WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<SubmissionJpaEntity> findByIdWithSubmissionFiles(@Param("id") Long id);

    // N+1 문제 해결을 위한 FetchJoin 쿼리들
    @Query("SELECT DISTINCT s FROM SubmissionJpaEntity s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.assignmentId = :assignmentId AND s.deletedAt IS NULL")
    List<SubmissionJpaEntity> findByAssignmentIdWithSubmissionFiles(@Param("assignmentId") Long assignmentId);

    @Query("SELECT DISTINCT s FROM SubmissionJpaEntity s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.classMemberEmail = :classMemberEmail " +
           "ORDER BY s.regDate DESC")
    List<SubmissionJpaEntity> findByClassMemberEmailWithSubmissionFilesOrderByRegDateDesc(@Param("classMemberEmail") String classMemberEmail);

    @Query("SELECT DISTINCT s FROM SubmissionJpaEntity s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.assignmentId = :assignmentId AND s.classMemberEmail = :classMemberEmail AND s.deletedAt IS NULL")
    Optional<SubmissionJpaEntity> findByAssignmentIdAndClassMemberEmailWithSubmissionFiles(@Param("assignmentId") Long assignmentId, @Param("classMemberEmail") String classMemberEmail);

}
