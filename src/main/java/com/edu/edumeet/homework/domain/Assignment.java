//package com.edu.edumeet.homework.assignment.domain;
//
//import com.edu.edumeet.classroom.domain.ClassMember;
//import com.edu.edumeet.homework.domain.AssignmentFile;
//import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
//import lombok.Builder;
//import lombok.Getter;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Getter
//@Builder
//public class Assignment {
//    private Long id;
//    private String title;
//    private String description;
//    private Long classId;
//    private Long createdById;
//    private String createdByName;
//
//    @Builder.Default
//    private List<AssignmentFile> attachmentFiles = new ArrayList<>();
//
//    @Builder.Default
//    private List<StudentSubmissionStatus> studentSubmissionStatuses = new ArrayList<>();
//
//    private LocalDateTime regDate;
//    private LocalDateTime modDate;
//    private LocalDateTime deletedAt;
//
//    // 숙제 수정
//    public Assignment update(String title, String description) {
//        return Assignment.builder()
//                .id(this.id)
//                .title(title)
//                .description(description)
//                .classId(this.classId)
//                .createdById(this.createdById)
//                .createdByName(this.createdByName)
//                .attachmentFiles(this.attachmentFiles)
//                .studentSubmissionStatuses(this.studentSubmissionStatuses)
//                .regDate(this.regDate)
//                .modDate(LocalDateTime.now())
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 첨부파일 추가
//    public Assignment addAttachmentFile(AssignmentFile file) {
//        List<AssignmentFile> newFiles = new ArrayList<>(this.attachmentFiles);
//        newFiles.add(file);
//
//        return Assignment.builder()
//                .id(this.id)
//                .title(this.title)
//                .description(this.description)
//                .classId(this.classId)
//                .createdById(this.createdById)
//                .createdByName(this.createdByName)
//                .attachmentFiles(newFiles)
//                .studentSubmissionStatuses(this.studentSubmissionStatuses)
//                .regDate(this.regDate)
//                .modDate(this.modDate)
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 클래스 멤버들의 제출 현황 초기화 (숙제 생성 시점에 호출)
//    public Assignment initializeStudentStatuses(List<ClassMember> classMembers) {
//        List<StudentSubmissionStatus> statuses = new ArrayList<>();
//        for (ClassMember classMember : classMembers) {
//            // 생성자는 제외하고 클래스 멤버들만 추가
//            if (!classMember.getMember().getId().equals(this.createdById)) {
//                statuses.add(StudentSubmissionStatus.notSubmitted(
//                        this.id,
//                        classMember.getMember().getId(),
//                        classMember.getMember().getNickname()
//                ));
//            }
//        }
//
//        return Assignment.builder()
//                .id(this.id)
//                .title(this.title)
//                .description(this.description)
//                .classId(this.classId)
//                .createdById(this.createdById)
//                .createdByName(this.createdByName)
//                .attachmentFiles(this.attachmentFiles)
//                .studentSubmissionStatuses(statuses)
//                .regDate(this.regDate)
//                .modDate(this.modDate)
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 클래스 멤버 제출 시 상태 업데이트
//    public Assignment updateSubmissionStatus(Long classMemberId) {
//        List<StudentSubmissionStatus> updatedStatuses = this.studentSubmissionStatuses.stream()
//                .map(status -> status.getStudentId().equals(classMemberId) ?
//                        status.markAsSubmitted() : status)
//                .toList();
//
//        return Assignment.builder()
//                .id(this.id)
//                .title(this.title)
//                .description(this.description)
//                .classId(this.classId)
//                .createdById(this.createdById)
//                .createdByName(this.createdByName)
//                .attachmentFiles(this.attachmentFiles)
//                .studentSubmissionStatuses(updatedStatuses)
//                .regDate(this.regDate)
//                .modDate(LocalDateTime.now())
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 첨부파일 존재 여부
//    public boolean hasAttachmentFiles() {
//        return !this.attachmentFiles.isEmpty();
//    }
//
//    // 제출 완료된 클래스 멤버 수
//    public long getSubmittedCount() {
//        return studentSubmissionStatuses.stream()
//                .filter(status -> status.getStatus() == SubmissionStatus.SUBMITTED)
//                .count();
//    }
//
//    // 전체 클래스 멤버 수
//    public int getTotalStudentCount() {
//        return studentSubmissionStatuses.size();
//    }
//
//    // 제출률 계산
//    public double getSubmissionRate() {
//        if (getTotalStudentCount() == 0) return 0.0;
//        return (double) getSubmittedCount() / getTotalStudentCount() * 100;
//    }
//
//    // 특정 클래스 멤버의 제출 상태 조회
//    public StudentSubmissionStatus getStudentSubmissionStatus(Long classMemberId) {
//        return studentSubmissionStatuses.stream()
//                .filter(status -> status.getStudentId().equals(classMemberId))
//                .findFirst()
//                .orElse(null);
//    }
//
//    // 클래스 멤버가 제출할 수 있는지 확인 (해당 클래스 멤버인지)
//    public boolean canClassMemberSubmit(Long classMemberId) {
//        return studentSubmissionStatuses.stream()
//                .anyMatch(status -> status.getStudentId().equals(classMemberId));
//    }
//
//    // 생성자인지 확인
//    public boolean isCreatedBy(Long memberId) {
//        return this.createdById.equals(memberId);
//    }
//
//    // 삭제 여부
//    public boolean isDeleted() {
//        return this.deletedAt != null;
//    }
//}