package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.upload.domain.FileUpload;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"submission"})
public class SubmissionFileUploadJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private SubmissionJpaEntity submission;

    @Column(nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String fileName;

    private int ord;

    @Column(nullable = false)
    private boolean img;

    private Long fileSize;

    private String contentType;

    private String uploadedBy;

    @Column(name = "reference_id")
    private Long referenceId;

    // FileUpload 도메인으로 변환
    public FileUpload toFileUpload() {
        return FileUpload.builder()
                .uuid(this.uuid)
                .fileName(this.fileName)
                .ord(this.ord)
                .img(this.img)
                .fileSize(this.fileSize)
                .contentType(this.contentType)
                .uploadedBy(this.uploadedBy)
                .referenceId(this.referenceId)
                .uploadedAt(this.getRegDate())
                .build();
    }

    // FileUpload 도메인에서 엔티티로 변환
    public static SubmissionFileUploadJpaEntity fromFileUpload(FileUpload fileUpload, SubmissionJpaEntity submission) {
        return SubmissionFileUploadJpaEntity.builder()
                .submission(submission)
                .uuid(fileUpload.getUuid())
                .fileName(fileUpload.getFileName())
                .ord(fileUpload.getOrd())
                .img(fileUpload.isImg())
                .fileSize(fileUpload.getFileSize())
                .contentType(fileUpload.getContentType())
                .uploadedBy(fileUpload.getUploadedBy())
                .referenceId(fileUpload.getReferenceId())
                .build();
    }
}