package com.edu.edumeet.homework.repository;

import com.edu.edumeet.homework.domain.SubmissionFileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionFileUploadRepository extends JpaRepository<SubmissionFileUpload, Long> {

    List<SubmissionFileUpload> findBySubmissionId(Long submissionId);
    
    List<SubmissionFileUpload> findByStudentSubmissionStatusId(Long studentSubmissionStatusId);
    
    @Query("SELECT f FROM SubmissionFileUpload f WHERE f.submission.id = :submissionId ORDER BY f.ord")
    List<SubmissionFileUpload> findBySubmissionIdOrderByOrd(@Param("submissionId") Long submissionId);
    
    @Query("SELECT f FROM SubmissionFileUpload f WHERE f.studentSubmissionStatus.id = :statusId ORDER BY f.ord")
    List<SubmissionFileUpload> findByStudentSubmissionStatusIdOrderByOrd(@Param("statusId") Long statusId);
}
