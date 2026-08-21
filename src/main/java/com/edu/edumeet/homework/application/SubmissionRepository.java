package com.edu.edumeet.homework.application;

import com.edu.edumeet.homework.domain.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository {
    // 제출물 저장
    Submission save(Submission submission);
    // ID로 제출물 조회
    Optional<Submission> findById(Long id);
    // 과제별 제출물 조회
    List<Submission> findByAssignmentId(Long assignmentId);
    // 과제별 제출물 조회 (제출 파일 포함)
    List<Submission> findByAssignmentIdWithSubmissionFiles(Long assignmentId);
    // 여러 과제의 제출물을 한 번에 조회 (목록 조회의 N+1 방지)
    List<Submission> findByAssignmentIdsWithSubmissionFiles(List<Long> assignmentIds);
    // 학생별 제출물 조회 (최신순)
    List<Submission> findByClassMemberEmailOrderByRegDateDesc(String classMemberEmail);
    // 특정 과제의 특정 학생 제출물 조회
    Optional<Submission> findByAssignmentIdAndClassMemberEmail(Long assignmentId, String classMemberEmail);
    // 첨부파일 포함 제출물 조회
    Optional<Submission> findByIdWithSubmissionFiles(Long id);
    // 제출물 삭제 (논리적 삭제)
    void deleteById(Long id);
    // 삭제된 제출물 복원
    boolean restoreById(Long id);
    // 삭제된 제출물도 포함하여 조회
    Optional<Submission> findByIdIncludeDeleted(Long id);

}
