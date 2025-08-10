package com.edu.edumeet.homework.domain;

import com.edu.edumeet.upload.domain.FileUpload;
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
    private Long classMemberId;
    private String classMemberName;
    private String content;
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.NOT_SUBMITTED;
    @Builder.Default
    private List<FileUpload> submissionFiles = new ArrayList<>();
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

    // 제출파일 추가
    public Submission addSubmissionFile(FileUpload file) {
        List<FileUpload> newFiles = new ArrayList<>(this.submissionFiles);
        newFiles.add(file);

        return this.toBuilder()
                .submissionFiles(newFiles)
                .modDate(LocalDateTime.now())
                .build();
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