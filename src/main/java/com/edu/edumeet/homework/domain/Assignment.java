package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.classroom.domain.ClassMember;
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
    private String createdByEmail;
    private String createdByName;

    @Builder.Default
    private List<Attachment> attachmentFiles = new ArrayList<>();

    @Builder.Default
    private List<StudentSubmissionStatus> studentSubmissionStatuses = new ArrayList<>();

    private LocalDateTime regDate;
    private LocalDateTime modDate;
    private LocalDateTime deletedAt;

    // 과제 수정
    public Assignment update(String title, String description) {
        return Assignment.builder()
                .id(this.id)
                .title(title)
                .description(description)
                .classId(this.classId)
                .createdByEmail(this.createdByEmail)
                .createdByName(this.createdByName)
                .attachmentFiles(this.attachmentFiles)
                .studentSubmissionStatuses(this.studentSubmissionStatuses)
                .regDate(this.regDate)
                .modDate(LocalDateTime.now())
                .deletedAt(this.deletedAt)
                .build();
    }

    // 첨부파일 추가
    public Assignment addAttachmentFile(Attachment file) {
        List<Attachment> newFiles = new ArrayList<>(this.attachmentFiles);
        newFiles.add(file);

        return Assignment.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .classId(this.classId)
                .createdByEmail(this.createdByEmail)
                .createdByName(this.createdByName)
                .attachmentFiles(newFiles)
                .studentSubmissionStatuses(this.studentSubmissionStatuses)
                .regDate(this.regDate)
                .modDate(this.modDate)
                .deletedAt(this.deletedAt)
                .build();
    }

    // 첨부파일 업데이트 (기존 파일들을 새로운 파일 목록으로 대체)
    // 클래스 멤버들의 제출 현황 초기화 (과제 생성 시점에 호출)
    public Assignment initializeStudentStatuses(List<ClassMember> classMembers) {
        List<StudentSubmissionStatus> statuses = new ArrayList<>();
        for (ClassMember classMember : classMembers) {
            // 생성자는 제외하고 클래스 멤버들만 추가
            // createdByEmail이 null이면 모든 멤버를 포함
            if (this.createdByEmail == null || !classMember.getMember().getEmail().equals(this.createdByEmail)) {
                statuses.add(StudentSubmissionStatus.notSubmitted(
                        this.id,
                        classMember.getMember().getEmail(),
                        classMember.getMember().getNickname()
                ));
            }
        }

        return Assignment.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .classId(this.classId)
                .createdByEmail(this.createdByEmail)
                .createdByName(this.createdByName)
                .attachmentFiles(this.attachmentFiles)
                .studentSubmissionStatuses(statuses)
                .regDate(this.regDate)
                .modDate(this.modDate)
                .deletedAt(this.deletedAt)
                .build();
    }


    // 클래스 멤버 제출 시 상태 업데이트 (email 기준)
    // 생성자인지 확인 (이메일로 확인)
    // 삭제 여부
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}