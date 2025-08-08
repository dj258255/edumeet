package com.edu.edumeet.homework.assignment.domain;

import com.edu.edumeet.homework.submission.domain.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Assignment {
    private Long id;
    private String title;
    private String description;
    private Long classId;
    private Long teacherId;
    private String teacherName;

    @Builder.Default
    private List<AssignmentFile> attachmentFiles = new ArrayList<>();

    private List<Submission> submissions = new ArrayList<>();

    private LocalDateTime regDate;
    private LocalDateTime modDate;
    private LocalDateTime deletedAt;


    //숙제 수정
    public Assignment update(String title, String description) {
        return Assignment.builder()
                .id(this.id)
                .title(title)
                .description(description)
                .classId(this.classId)
                .teacherId(this.teacherId)
                .teacherName(this.teacherName)
                .attachmentFiles(this.attachmentFiles)
                .submissions(this.submissions)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .deletedAt(this.deletedAt)
                .build();
    }

    //첨부파일 추가.
    public Assignment addAttachmentFile(AssignmentFile file) {
        List<AssignmentFile> newFiles = new ArrayList<>(this.attachmentFiles);
        newFiles.add(file);

        return Assignment.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .classId(this.classId)
                .teacherId(this.teacherId)
                .teacherName(this.teacherName)
                .attachmentFiles(newFiles)
                .submissions(this.submissions)
                .regDate(this.regDate)
                .modDate(this.modDate)
                .deletedAt(this.deletedAt)
                .build();
    }

    //첨부파일 존재 여부
    public boolean hasAttachmentFiles() {
        return !this.attachmentFiles.isEmpty();
    }

    //삭제 여부
    public boolean isDeleted() {
        return this.deletedAt != null;
    }



}
