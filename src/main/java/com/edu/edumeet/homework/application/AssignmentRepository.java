package com.edu.edumeet.homework.application;

import com.edu.edumeet.homework.domain.Assignment;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository {
    //과제 저장
    Assignment save(Assignment assignment);
    //ID로 과제 조회
    Optional<Assignment> findById(Long id);
    //클래스별 과제 조회 (최신순)
    List<Assignment> findByClassIdOrderByRegDateDesc(Long classId);
    //첨부파일 포함 과제 조회
    Optional<Assignment> findByIdWithAttachmentFiles(Long id);
    //제출 현황 포함 과제 조회
    Optional<Assignment> findByIdWithSubmissionStatuses(Long id);
    // 과제 삭제 (논리적 삭제)
    void deleteById(Long id);
    // 삭제된 과제 복원
    boolean restoreById(Long id);
    // 삭제된 과제도 포함하여 조회
    Optional<Assignment> findByIdIncludeDeleted(Long id);
}
