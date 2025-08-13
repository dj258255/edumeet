package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
public class Submission {
    private Long id;
    private Long assignmentId;
    private String classMemberEmail;
    private String classMemberName;
    private String content;
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.NOT_SUBMITTED;
    @Builder.Default
    private List<Attachment> submissionFiles = new ArrayList<>();
    private LocalDateTime regDate;
    private LocalDateTime modDate;
    private LocalDateTime deletedAt;

    // 제출
    public Submission submit(String content) {
        return this.toBuilder()
                .content(content)
                .status(SubmissionStatus.SUBMITTED)
                .modDate(LocalDateTime.now())
                .build();
    }

    // 제출물 수정
    public Submission update(String content) {
        return this.toBuilder()
                .content(content)
                .modDate(LocalDateTime.now())
                .build();
    }

    // 제출파일 추가
    public Submission addSubmissionFile(Attachment file) {
        List<Attachment> newFiles = new ArrayList<>(this.submissionFiles);
        newFiles.add(file);

        return this.toBuilder()
                .submissionFiles(newFiles)
                .modDate(LocalDateTime.now())
                .build();
    }

    // 제출파일 업데이트 (기존 파일들을 새로운 파일 목록으로 대체)
    public Submission updateSubmissionFiles(List<Attachment> newFiles) {
        return this.toBuilder()
                .submissionFiles(new ArrayList<>(newFiles))
                .modDate(LocalDateTime.now())
                .build();
    }

    // 제출자인지 확인 (이메일 기준)
    public boolean isSubmittedBy(String memberEmail) {
        return this.classMemberEmail != null && this.classMemberEmail.equals(memberEmail);
    }

    // 제출 여부 확인
    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }

    // 삭제 여부
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}