package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize(builder = StudentSubmissionStatus.StudentSubmissionStatusBuilder.class)
public class StudentSubmissionStatus {
    private Long assignmentId;
    private String studentEmail; // classMemberEmail
    private String studentName; // class MemberName
    private SubmissionStatus status;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime submittedAt;
    
    // 제출된 파일들 (선생님이 볼 수 있도록)
    @Builder.Default
    private List<Attachment> submissionFiles = new ArrayList<>();

    // 미제출 상태로 생성
    public static StudentSubmissionStatus notSubmitted(Long assignmentId, String classMemberEmail, String classMemberName) {
        return StudentSubmissionStatus.builder()
                .assignmentId(assignmentId)
                .studentEmail(classMemberEmail)
                .studentName(classMemberName)
                .status(SubmissionStatus.NOT_SUBMITTED)
                .build();
    }

    // 제출 완료로 변경
    public StudentSubmissionStatus markAsSubmitted() {
        return StudentSubmissionStatus.builder()
                .assignmentId(this.assignmentId)
                .studentEmail(this.studentEmail)
                .studentName(this.studentName)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .submissionFiles(this.submissionFiles)
                .build();
    }

    // 제출 완료로 변경 (제출 파일 포함)
    public StudentSubmissionStatus markAsSubmitted(List<Attachment> submissionFiles) {
        return StudentSubmissionStatus.builder()
                .assignmentId(this.assignmentId)
                .studentEmail(this.studentEmail)
                .studentName(this.studentName)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .submissionFiles(submissionFiles != null ? submissionFiles : new ArrayList<>())
                .build();
    }

    // 제출 완료 상태로 생성 (제출 파일 포함)
    public static StudentSubmissionStatus submitted(Long assignmentId, String classMemberEmail, String classMemberName, 
                                                   List<Attachment> submissionFiles, LocalDateTime submittedAt) {
        return StudentSubmissionStatus.builder()
                .assignmentId(assignmentId)
                .studentEmail(classMemberEmail)
                .studentName(classMemberName)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(submittedAt)
                .submissionFiles(submissionFiles != null ? submissionFiles : new ArrayList<>())
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