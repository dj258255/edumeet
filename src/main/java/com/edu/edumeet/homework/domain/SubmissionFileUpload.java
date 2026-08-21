package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** 제출물 첨부파일. 이전 infrastructure.SubmissionFileUpload 를 통합한 것이다. (#3) */
@Entity
@Table(name = "submission_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"submission", "studentSubmissionStatus"})
public class SubmissionFileUpload extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_submission_status_id")
    private StudentSubmissionStatus studentSubmissionStatus;

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

    public Attachment toAttachment() {
        return Attachment.builder()
                .uuid(this.uuid).fileName(this.fileName).ord(this.ord).img(this.img)
                .fileSize(this.fileSize).contentType(this.contentType)
                .uploadedBy(this.uploadedBy).referenceId(this.referenceId)
                .domain(this.domain).uploadedAt(this.getRegDate())
                .build();
    }

    public static SubmissionFileUpload from(Attachment attachment, Submission submission) {
        return base(attachment).submission(submission).build();
    }

    public static SubmissionFileUpload from(Attachment attachment, StudentSubmissionStatus status) {
        return base(attachment).studentSubmissionStatus(status).build();
    }

    private static SubmissionFileUploadBuilder base(Attachment a) {
        return SubmissionFileUpload.builder()
                .uuid(a.getUuid()).fileName(a.getFileName()).ord(a.getOrd()).img(a.isImg())
                .fileSize(a.getFileSize()).contentType(a.getContentType())
                .uploadedBy(a.getUploadedBy()).referenceId(a.getReferenceId())
                .domain(a.getDomain());
    }
}
