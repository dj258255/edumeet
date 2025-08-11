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
    @Query("SELECT DISTINCT a FROM AssignmentJpaEntity a " +
           "LEFT JOIN FETCH a.attachmentFiles " +
           "LEFT JOIN FETCH a.studentSubmissionStatuses " +
           "WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AssignmentJpaEntity> findByIdWithAllDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT a FROM AssignmentJpaEntity a " +
           "LEFT JOIN FETCH a.attachmentFiles " +
           "WHERE a.classId = :classId AND a.deletedAt IS NULL " +
           "ORDER BY a.regDate DESC")
    List<AssignmentJpaEntity> findByClassIdWithAttachmentFilesOrderByRegDateDesc(@Param("classId") Long classId);

    @Query("SELECT DISTINCT a FROM AssignmentJpaEntity a " +
           "LEFT JOIN FETCH a.studentSubmissionStatuses " +
           "WHERE a.classId = :classId AND a.deletedAt IS NULL " +
           "ORDER BY a.regDate DESC")
    List<AssignmentJpaEntity> findByClassIdWithSubmissionStatusesOrderByRegDateDesc(@Param("classId") Long classId);

}