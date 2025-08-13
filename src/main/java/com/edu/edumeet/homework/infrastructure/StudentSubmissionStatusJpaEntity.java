package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "student_submission_status")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"assignment"})
public class StudentSubmissionStatusJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private AssignmentJpaEntity assignment;

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

    // 제출된 파일들
    @OneToMany(mappedBy = "studentSubmissionStatus", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<SubmissionFileUploadJpaEntity> submissionFiles = new HashSet<>();

    // 도메인 모델로 변환
    public StudentSubmissionStatus toDomain() {
        List<com.edu.edumeet.attachment.domain.Attachment> attachments = new ArrayList<>();
        if (this.submissionFiles != null) {
            attachments = this.submissionFiles.stream()
                    .map(SubmissionFileUploadJpaEntity::toFileUpload)
                    .toList();
        }

        return StudentSubmissionStatus.builder()
                .assignmentId(this.assignment.getId())
                .studentEmail(this.studentEmail)
                .studentName(this.studentName)
                .status(this.status)
                .submittedAt(this.submittedAt)
                .submissionFiles(attachments)
                .build();
    }

    // 도메인 모델에서 JPA 엔티티로 변환
    public static StudentSubmissionStatusJpaEntity fromDomain(StudentSubmissionStatus status, AssignmentJpaEntity assignment) {
        StudentSubmissionStatusJpaEntity entity = StudentSubmissionStatusJpaEntity.builder()
                .assignment(assignment)
                .studentEmail(status.getStudentEmail())
                .studentName(status.getStudentName())
                .status(status.getStatus())
                .submittedAt(status.getSubmittedAt())
                .build();

        // submissionFiles 처리
        if (status.getSubmissionFiles() != null && !status.getSubmissionFiles().isEmpty()) {
            Set<SubmissionFileUploadJpaEntity> fileEntities = new HashSet<>();
            for (com.edu.edumeet.attachment.domain.Attachment attachment : status.getSubmissionFiles()) {
                SubmissionFileUploadJpaEntity fileEntity = SubmissionFileUploadJpaEntity.fromAttachment(attachment, entity);
                fileEntities.add(fileEntity);
            }
            entity.submissionFiles = fileEntities;
        }

        return entity;
    }
}