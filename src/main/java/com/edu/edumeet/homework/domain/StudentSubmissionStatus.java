package com.edu.edumeet.homework.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class StudentSubmissionStatus {
    private Long assignmentId;
    private Long studentId; // classMemberId
    private String studentName; // class MemberName
    private SubmissionStatus status;
    private LocalDateTime submittedAt;

    // 미제출 상태로 생성
    public static StudentSubmissionStatus notSubmitted(Long assignmentId, Long classMemberId, String classMemberName) {
        return StudentSubmissionStatus.builder()
                .assignmentId(assignmentId)
                .studentId(classMemberId)
                .studentName(classMemberName)
                .status(SubmissionStatus.NOT_SUBMITTED)
                .build();
    }

    // 제출 완료로 변경
    public StudentSubmissionStatus markAsSubmitted() {
        return StudentSubmissionStatus.builder()
                .assignmentId(this.assignmentId)
                .studentId(this.studentId)
                .studentName(this.studentName)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    // 제출 여부 확인
    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }
}