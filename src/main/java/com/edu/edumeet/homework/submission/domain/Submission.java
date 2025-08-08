package com.edu.edumeet.homework.submission.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Submission {
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String studentName;
    private String content;

    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Builder.Default
    private List<SubmissionFile> submissionFiles = new ArrayList<>();

    private LocalDateTime regDate;
    private LocalDateTime modDate;
    private LocalDateTime deletedAt;

    //제출물 수정

    public Submission update(String content){
        return Submission.builder()
                .id(this.id)
                .assignmentId(this.assignmentId)
                .studentId(this.studentId)
                .studentName(this.studentName)
                .content(content)
                .status(this.status)
                .submissionFiles(this.submissionFiles)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .deletedAt(this.deletedAt)
                .build();
    }

    //제출파일 추가
    public Submission addSubmissionFile(SubmissionFile file) {
        List<SubmissionFile> newFiles = new ArrayList<>(this.submissionFiles);
        newFiles.add(file);

        return Submission.builder()
                .id(this.id)
                .assignmentId(this.assignmentId)
                .studentId(this.studentId)
                .studentName(this.studentName)
                .content(this.content)
                .status(this.status)
                .submissionFiles(newFiles)
                .regDate(this.regDate)
                .modDate(this.modDate)
                .deletedAt(this.deletedAt)
                .build();
    }

    //제출파일 존재 여부

    public boolean hasSubmissionFiles() {
        return !this.submissionFiles.isEmpty();
    }


    //삭제 여부
    public boolean isDeleted(){
        return this.deletedAt != null;
    }
}
