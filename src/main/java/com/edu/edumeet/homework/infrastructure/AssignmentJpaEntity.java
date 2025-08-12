package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.homework.domain.Assignment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Table(name = "assignment")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"attachmentFiles", "studentSubmissionStatuses"})
public class AssignmentJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 첨부파일 관계 (과제와 파일 업로드 간의 연관관계)
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20) // N+1 문제 해결
    @Builder.Default
    private Set<AssignmentFileUploadJpaEntity> attachmentFiles = new HashSet<>();

    // 학생별 제출 현황
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 30) // 클래스당 평균 학생 수 고려하여 30으로 설정
    @Builder.Default
    private Set<StudentSubmissionStatusJpaEntity> studentSubmissionStatuses = new HashSet<>();

    // 도메인 모델로 변환
    public Assignment toDomain() {
        return Assignment.builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .classId(this.classId)
                .createdById(this.createdById)
                .createdByName(this.createdByName)
                .attachmentFiles(this.attachmentFiles.stream()
                        .map(AssignmentFileUploadJpaEntity::toFileUpload)
                        .collect(Collectors.toList()))
                .studentSubmissionStatuses(this.studentSubmissionStatuses.stream()
                        .map(StudentSubmissionStatusJpaEntity::toDomain)
                        .collect(Collectors.toList()))
                .regDate(this.getRegDate())
                .modDate(this.getModDate())
                .deletedAt(this.deletedAt)
                .build();
    }

    // 도메인 모델에서 JPA 엔티티로 변환
    public static AssignmentJpaEntity fromDomain(Assignment assignment) {
        AssignmentJpaEntity entity = AssignmentJpaEntity.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classId(assignment.getClassId())
                .createdById(assignment.getCreatedById())
                .createdByName(assignment.getCreatedByName())
                .deletedAt(assignment.getDeletedAt())
                .build();

        // 첨부파일 변환
        if (assignment.getAttachmentFiles() != null) {
            Set<AssignmentFileUploadJpaEntity> fileEntities = assignment.getAttachmentFiles().stream()
                    .map(fileUpload -> AssignmentFileUploadJpaEntity.fromFileUpload(fileUpload, entity))
                    .collect(Collectors.toSet());
            entity.attachmentFiles = fileEntities;
        }

        // 학생 제출 현황 변환
        if (assignment.getStudentSubmissionStatuses() != null) {
            Set<StudentSubmissionStatusJpaEntity> statusEntities = assignment.getStudentSubmissionStatuses().stream()
                    .map(status -> StudentSubmissionStatusJpaEntity.fromDomain(status, entity))
                    .collect(Collectors.toSet());
            entity.studentSubmissionStatuses = statusEntities;
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