package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Table(name = "submission")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"submissionFiles"})
public class SubmissionJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "class_member_id", nullable = false)
    private Long classMemberId;

    @Column(name = "class_member_name", nullable = false, length = 50)
    private String classMemberName;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 제출물 파일들
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10) // 제출물당 첨부파일은 보통 적으므로 10으로 설정
    @Builder.Default
    private Set<SubmissionFileUploadJpaEntity> submissionFiles = new HashSet<>();

    // 도메인 모델로 변환
    public Submission toDomain() {
        return Submission.builder()
                .id(this.id)
                .assignmentId(this.assignmentId)
                .classMemberId(this.classMemberId)
                .classMemberName(this.classMemberName)
                .content(this.content)
                .status(this.status)
                .submissionFiles(this.submissionFiles.stream()
                        .map(SubmissionFileUploadJpaEntity::toFileUpload)
                        .collect(Collectors.toList()))
                .regDate(this.getRegDate())
                .modDate(this.getModDate())
                .deletedAt(this.deletedAt)
                .build();
    }

    // 도메인 모델에서 JPA 엔티티로 변환
    public static SubmissionJpaEntity fromDomain(Submission submission) {
        SubmissionJpaEntity entity = SubmissionJpaEntity.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignmentId())
                .classMemberId(submission.getClassMemberId())
                .classMemberName(submission.getClassMemberName())
                .content(submission.getContent())
                .status(submission.getStatus())
                .deletedAt(submission.getDeletedAt())
                .build();

        // 제출물 파일 변환
        if (submission.getSubmissionFiles() != null) {
            Set<SubmissionFileUploadJpaEntity> fileEntities = submission.getSubmissionFiles().stream()
                    .map(fileUpload -> SubmissionFileUploadJpaEntity.fromFileUpload(fileUpload, entity))
                    .collect(Collectors.toSet());
            entity.submissionFiles = fileEntities;
        }

        return entity;
    }

    // 논리적 삭제
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    // 복원
    public void restore() {
        this.deletedAt = null;
    }

    // 삭제 여부 확인
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}