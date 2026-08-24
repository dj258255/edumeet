package com.edu.edumeet.homework.repository;

import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentSubmissionStatusRepository extends JpaRepository<StudentSubmissionStatus, Long> {

    @Query("SELECT s FROM StudentSubmissionStatus s WHERE s.assignment.id = :assignmentId")
    List<StudentSubmissionStatus> findByAssignmentId(@Param("assignmentId") Long assignmentId);
    
    @Query("SELECT s FROM StudentSubmissionStatus s WHERE s.assignment.id = :assignmentId AND s.studentEmail = :studentEmail")
    Optional<StudentSubmissionStatus> findByAssignmentIdAndStudentEmail(@Param("assignmentId") Long assignmentId, @Param("studentEmail") String studentEmail);
    
    @Query("SELECT s FROM StudentSubmissionStatus s LEFT JOIN FETCH s.submissionFiles WHERE s.assignment.id = :assignmentId")
    List<StudentSubmissionStatus> findByAssignmentIdWithSubmissionFiles(@Param("assignmentId") Long assignmentId);
    
    @Query("SELECT s FROM StudentSubmissionStatus s LEFT JOIN FETCH s.submissionFiles WHERE s.assignment.id = :assignmentId AND s.studentEmail = :studentEmail")
    Optional<StudentSubmissionStatus> findByAssignmentIdAndStudentEmailWithSubmissionFiles(@Param("assignmentId") Long assignmentId, @Param("studentEmail") String studentEmail);
}
