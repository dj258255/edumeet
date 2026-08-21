package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 과제 제출물.
 * 이전 domain.Submission(불변) + infrastructure.Submission 를 통합한 것이다. (#3)
 */
@Entity
@Table(name = "submission")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"submissionFiles"})
public class Submission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "class_member_email")
    private String classMemberEmail;

    @Column(name = "class_member_name")
    private String classMemberName;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.NOT_SUBMITTED;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    @Builder.Default
    private Set<SubmissionFileUpload> submissionFiles = new HashSet<>();

    // ---- 행위 ----

    /** 제출한다. */
    public void submit(String content) {
        this.content = content;
        this.status = SubmissionStatus.SUBMITTED;
    }

    /** 내용을 수정한다. */
    public void update(String content) {
        this.content = content;
    }

    public void addSubmissionFile(Attachment file) {
        this.submissionFiles.add(SubmissionFileUpload.from(file, this));
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isSubmitted() {
        return this.status == SubmissionStatus.SUBMITTED;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
