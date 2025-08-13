package com.edu.edumeet.homework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionFileUploadJpaRepository extends JpaRepository<SubmissionFileUploadJpaEntity, Long> {

    List<SubmissionFileUploadJpaEntity> findBySubmissionId(Long submissionId);
    
    List<SubmissionFileUploadJpaEntity> findByStudentSubmissionStatusId(Long studentSubmissionStatusId);
    
    @Query("SELECT f FROM SubmissionFileUploadJpaEntity f WHERE f.submission.id = :submissionId ORDER BY f.ord")
    List<SubmissionFileUploadJpaEntity> findBySubmissionIdOrderByOrd(@Param("submissionId") Long submissionId);
    
    @Query("SELECT f FROM SubmissionFileUploadJpaEntity f WHERE f.studentSubmissionStatus.id = :statusId ORDER BY f.ord")
    List<SubmissionFileUploadJpaEntity> findByStudentSubmissionStatusIdOrderByOrd(@Param("statusId") Long statusId);
}
