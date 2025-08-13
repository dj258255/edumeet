package com.edu.edumeet.homework.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize(builder = StudentSubmissionStatus.StudentSubmissionStatusBuilder.class)
public class StudentSubmissionStatus {
    private Long assignmentId;
    private Long studentId; // classMemberId
    private String studentName; // class MemberName
    private SubmissionStatus status;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
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

    // Jackson Builder 설정
    @JsonPOJOBuilder(withPrefix = "")
    public static class StudentSubmissionStatusBuilder {
    }
}