package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** 과제 첨부파일. 이전 infrastructure.AssignmentFileUpload 를 통합한 것이다. (#3) */
@Entity
@Table(name = "assignment_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"assignment"})
public class AssignmentFileUpload extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

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

    /** 첨부 공용 도메인 타입으로 변환한다. DTO 로 내보낼 때 쓴다. */
    public Attachment toAttachment() {
        return Attachment.builder()
                .uuid(this.uuid).fileName(this.fileName).ord(this.ord).img(this.img)
                .fileSize(this.fileSize).contentType(this.contentType)
                .uploadedBy(this.uploadedBy).referenceId(this.referenceId)
                .domain(this.domain).uploadedAt(this.getRegDate())
                .build();
    }

    public static AssignmentFileUpload from(Attachment attachment, Assignment assignment) {
        return AssignmentFileUpload.builder()
                .assignment(assignment)
                .uuid(attachment.getUuid()).fileName(attachment.getFileName())
                .ord(attachment.getOrd()).img(attachment.isImg())
                .fileSize(attachment.getFileSize()).contentType(attachment.getContentType())
                .uploadedBy(attachment.getUploadedBy()).referenceId(attachment.getReferenceId())
                .domain(attachment.getDomain())
                .build();
    }
}
