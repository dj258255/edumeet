//package com.edu.edumeet.homework.domain;
//
//import lombok.Builder;
//import lombok.Getter;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Getter
//@Builder
//public class Submission { //제출물 도메인
//    private Long id;
//    private Long assignmentId;
//    private Long classMemberId;
//    private String classMemberName;
//    private String content;
//
//    @Builder.Default
//    private SubmissionStatus status = SubmissionStatus.SUBMITTED;
//    @Builder.Default
//    private List<SubmissionFile> submissionFiles = new ArrayList<>();
//
//    private LocalDateTime regDate;
//    private LocalDateTime modDate;
//    private LocalDateTime deletedAt;
//
//    // 제출물 수정
//    public Submission update(String content) {
//        return Submission.builder()
//                .id(this.id)
//                .assignmentId(this.assignmentId)
//                .classMemberId(this.classMemberId)
//                .classMemberName(this.classMemberName)
//                .content(content)
//                .status(this.status)
//                .submissionFiles(this.submissionFiles)
//                .regDate(this.regDate)
//                .modDate(LocalDateTime.now())
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 제출파일 추가
//    public Submission addSubmissionFile(SubmissionFile file) {
//        List<SubmissionFile> newFiles = new ArrayList<>(this.submissionFiles);
//        newFiles.add(file);
//
//        return Submission.builder()
//                .id(this.id)
//                .assignmentId(this.assignmentId)
//                .classMemberId(this.classMemberId)
//                .classMemberName(this.classMemberName)
//                .content(this.content)
//                .status(this.status)
//                .submissionFiles(newFiles)
//                .regDate(this.regDate)
//                .modDate(this.modDate)
//                .deletedAt(this.deletedAt)
//                .build();
//    }
//
//    // 제출파일 존재 여부
//    public boolean hasSubmissionFiles() {
//        return !this.submissionFiles.isEmpty();
//    }
//
//    // 내용이 있는지 확인
//    public boolean hasContent() {
//        return content != null && !content.trim().isEmpty();
//    }
//
//    // 제출물이 비어있는지 확인
//    public boolean isEmpty() {
//        return !hasContent() && !hasSubmissionFiles();
//    }
//
//    // 클래스 멤버가 작성한 것인지 확인
//    public boolean isSubmittedBy(Long classMemberId) {
//        return this.classMemberId.equals(classMemberId);
//    }
//
//    // 삭제 여부
//    public boolean isDeleted() {
//        return this.deletedAt != null;
//    }
//
//    // 제출 후 경과시간 계산
//    public long getHoursSinceSubmission() {
//        if (regDate == null) return 0;
//        return java.time.Duration.between(regDate, LocalDateTime.now()).toHours();
//    }
//}
