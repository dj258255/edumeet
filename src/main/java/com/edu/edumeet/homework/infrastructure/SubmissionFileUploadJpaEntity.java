package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "submission_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"submission", "studentSubmissionStatus"})
public class SubmissionFileUploadJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private SubmissionJpaEntity submission;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_submission_status_id")
    private StudentSubmissionStatusJpaEntity studentSubmissionStatus;

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

    // Attachment 도메인에서 엔티티로 변환 (Submission용)
    public static SubmissionFileUploadJpaEntity fromFileUpload(Attachment attachment, SubmissionJpaEntity submission) {
        return SubmissionFileUploadJpaEntity.builder()
                .submission(submission)
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
    
    // Attachment 도메인에서 엔티티로 변환 (StudentSubmissionStatus용)
    public static SubmissionFileUploadJpaEntity fromAttachment(Attachment attachment, StudentSubmissionStatusJpaEntity studentSubmissionStatus) {
        return SubmissionFileUploadJpaEntity.builder()
                .studentSubmissionStatus(studentSubmissionStatus)
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