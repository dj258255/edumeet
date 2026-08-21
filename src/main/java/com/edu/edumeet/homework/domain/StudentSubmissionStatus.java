package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 학생별 과제 제출 현황.
 *
 * 이전에는 domain.StudentSubmissionStatus(불변)와
 * infrastructure.StudentSubmissionStatus 가 분리되어 있었다. 통합했다. (#3)
 *
 * 도메인 모델에는 assignmentId(Long)만 있었으나 엔티티는 Assignment 연관을 갖는다.
 * 통합하면서 연관을 유지하고, assignmentId 가 필요한 곳은 getAssignmentId() 로 노출한다.
 */
@Entity
@Table(name = "student_submission_status")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"assignment", "submissionFiles"})
public class StudentSubmissionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name = "student_email", nullable = false)
    private String studentEmail;

    @Column(name = "student_name", nullable = false, length = 50)
    private String studentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.NOT_SUBMITTED;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "studentSubmissionStatus", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<SubmissionFileUpload> submissionFiles = new HashSet<>();

    // ---- 생성 ----

    public static StudentSubmissionStatus notSubmitted(Assignment assignment,
                                                       String studentEmail, String studentName) {
        return StudentSubmissionStatus.builder()
                .assignment(assignment)
                .studentEmail(studentEmail)
                .studentName(studentName)
                .status(SubmissionStatus.NOT_SUBMITTED)
                .build();
    }

    public static StudentSubmissionStatus submitted(Assignment assignment, String studentEmail,
                                                     String studentName, LocalDateTime submittedAt) {
        return StudentSubmissionStatus.builder()
                .assignment(assignment)
                .studentEmail(studentEmail)
                .studentName(studentName)
                .status(SubmissionStatus.SUBMITTED)
                .submittedAt(submittedAt)
                .build();
    }

    // ---- 행위 ----

    /** 제출 처리한다. 첨부가 있으면 함께 등록한다. */
    public void markAsSubmitted(List<Attachment> files) {
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
        if (files != null) {
            files.forEach(f -> this.submissionFiles.add(SubmissionFileUpload.from(f, this)));
        }
    }

    public boolean isSubmitted() {
        return this.status == SubmissionStatus.SUBMITTED;
    }

    /** 연관을 거치지 않고 과제 식별자만 필요한 곳을 위한 접근자. */
    public Long getAssignmentId() {
        return this.assignment != null ? this.assignment.getId() : null;
    }
}
