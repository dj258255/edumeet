package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"assignment"})
public class AssignmentFileUploadJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private AssignmentJpaEntity assignment;

    @Column(nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String fileName;

    private int ord;

    private boolean img;

    private Long fileSize;

    private String contentType;

    private String uploadedBy;

    @Column(name = "reference_id")
    private Long referenceId;

    private String domain;

    // Attachment 도메인으로 변환
    public Attachment toFileUpload() {
        return Attachment.builder()
                .uuid(this.uuid)
                .fileName(this.fileName)
                .ord(this.ord)
                .img(this.img)
                .fileSize(this.fileSize)
                .contentType(this.contentType)
                .uploadedBy(this.uploadedBy)
                .referenceId(this.referenceId)
                .domain(this.domain)
                .uploadedAt(this.getRegDate())
                .build();
    }

    // Attachment 도메인에서 엔티티로 변환
    public static AssignmentFileUploadJpaEntity fromFileUpload(Attachment attachment, AssignmentJpaEntity assignment) {
        return AssignmentFileUploadJpaEntity.builder()
                .assignment(assignment)
                .uuid(attachment.getUuid())
                .fileName(attachment.getFileName())
                .ord(attachment.getOrd())
                .img(attachment.isImg())
                .fileSize(attachment.getFileSize())
                .contentType(attachment.getContentType())
                .uploadedBy(attachment.getUploadedBy())
                .referenceId(attachment.getReferenceId())
                .domain(attachment.getDomain())
                .build();
    }
}