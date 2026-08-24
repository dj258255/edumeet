package com.edu.edumeet.homework.repository;

import com.edu.edumeet.homework.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByClassMemberEmailOrderByRegDateDesc(String classMemberEmail);

    @Query("SELECT s FROM Submission s WHERE s.assignmentId = :assignmentId AND s.classMemberEmail = :classMemberEmail AND s.deletedAt IS NULL")
    Optional<Submission> findByAssignmentIdAndClassMemberEmailAndDeletedAtIsNull(@Param("assignmentId") Long assignmentId, @Param("classMemberEmail") String classMemberEmail);

    @Query("SELECT s FROM Submission s WHERE s.assignmentId = :assignmentId AND s.deletedAt IS NULL")
    List<Submission> findByAssignmentIdAndDeletedAtIsNull(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM Submission s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Submission> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.submissionFiles WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Submission> findByIdWithSubmissionFiles(@Param("id") Long id);

    // N+1 문제 해결을 위한 FetchJoin 쿼리들
    @Query("SELECT DISTINCT s FROM Submission s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.assignmentId = :assignmentId AND s.deletedAt IS NULL")
    List<Submission> findByAssignmentIdWithSubmissionFiles(@Param("assignmentId") Long assignmentId);

    /**
     * 여러 과제의 제출물을 한 번에 조회한다.
     * 목록 조회에서 과제마다 findByAssignmentIdWithSubmissionFiles 를 반복 호출하면
     * 과제 수에 비례해 쿼리가 늘어나므로(N+1) IN 절로 한 번에 가져온다.
     */
    @Query("SELECT DISTINCT s FROM Submission s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.assignmentId IN :assignmentIds AND s.deletedAt IS NULL")
    List<Submission> findByAssignmentIdsWithSubmissionFiles(@Param("assignmentIds") List<Long> assignmentIds);

    @Query("SELECT DISTINCT s FROM Submission s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.classMemberEmail = :classMemberEmail " +
           "ORDER BY s.regDate DESC")
    List<Submission> findByClassMemberEmailWithSubmissionFilesOrderByRegDateDesc(@Param("classMemberEmail") String classMemberEmail);

    @Query("SELECT DISTINCT s FROM Submission s " +
           "LEFT JOIN FETCH s.submissionFiles " +
           "WHERE s.assignmentId = :assignmentId AND s.classMemberEmail = :classMemberEmail AND s.deletedAt IS NULL")
    Optional<Submission> findByAssignmentIdAndClassMemberEmailWithSubmissionFiles(@Param("assignmentId") Long assignmentId, @Param("classMemberEmail") String classMemberEmail);

}
