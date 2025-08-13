package com.edu.edumeet.homework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentSubmissionStatusJpaRepository extends JpaRepository<StudentSubmissionStatusJpaEntity, Long> {

    @Query("SELECT s FROM StudentSubmissionStatusJpaEntity s WHERE s.assignment.id = :assignmentId")
    List<StudentSubmissionStatusJpaEntity> findByAssignmentId(@Param("assignmentId") Long assignmentId);
    
    @Query("SELECT s FROM StudentSubmissionStatusJpaEntity s WHERE s.assignment.id = :assignmentId AND s.studentEmail = :studentEmail")
    Optional<StudentSubmissionStatusJpaEntity> findByAssignmentIdAndStudentEmail(@Param("assignmentId") Long assignmentId, @Param("studentEmail") String studentEmail);
    
    @Query("SELECT s FROM StudentSubmissionStatusJpaEntity s LEFT JOIN FETCH s.submissionFiles WHERE s.assignment.id = :assignmentId")
    List<StudentSubmissionStatusJpaEntity> findByAssignmentIdWithSubmissionFiles(@Param("assignmentId") Long assignmentId);
    
    @Query("SELECT s FROM StudentSubmissionStatusJpaEntity s LEFT JOIN FETCH s.submissionFiles WHERE s.assignment.id = :assignmentId AND s.studentEmail = :studentEmail")
    Optional<StudentSubmissionStatusJpaEntity> findByAssignmentIdAndStudentEmailWithSubmissionFiles(@Param("assignmentId") Long assignmentId, @Param("studentEmail") String studentEmail);
}
