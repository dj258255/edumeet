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


}