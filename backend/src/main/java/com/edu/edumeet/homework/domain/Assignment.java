package com.edu.edumeet.homework.domain;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.classroom.domain.ClassMember;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 과제.
 *
 * 이전에는 domain.Assignment(불변 도메인 모델)와
 * infrastructure.Assignment(JPA 엔티티)가 분리되어 있었고
 * toDomain() / fromDomain() 이 둘을 오갔다.
 *
 * 분리를 유지할 근거가 없다고 판단해 통합했다. (#3)
 *  - 도메인 행위 메서드 7개 중 5개가 호출되지 않는 죽은 코드였다
 *  - 매핑 계층 때문에 목록 조회에서 DTO 프로젝션을 쓸 수 없었다
 *  - Port 가 노출하지 않은 최적화 쿼리가 죽은 코드가 되고 있었다 (#4)
 *
 * JPA 엔티티는 가변이어야 하므로 행위 메서드는 새 인스턴스를 반환하지 않고
 * 자신의 상태를 바꾼다. 영속 상태에서는 변경 감지로 반영된다.
 */
@Entity
@Table(name = "assignment")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"attachmentFiles", "studentSubmissionStatuses"})
public class Assignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @Builder.Default
    private Set<AssignmentFileUpload> attachmentFiles = new HashSet<>();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 30)
    @Builder.Default
    private Set<StudentSubmissionStatus> studentSubmissionStatuses = new HashSet<>();

    // ---- 행위 ----

    /** 첨부파일을 추가한다. */
    public void addAttachmentFile(Attachment file) {
        this.attachmentFiles.add(AssignmentFileUpload.from(file, this));
    }

    /**
     * 클래스 멤버들의 제출 현황을 초기화한다. (과제 생성 시점)
     * 과제 생성자는 제외한다. createdByEmail 이 null 이면 모든 멤버를 포함한다.
     */
    public void initializeStudentStatuses(List<ClassMember> classMembers) {
        for (ClassMember classMember : classMembers) {
            String email = classMember.getMember().getEmail();
            if (this.createdByEmail == null || !email.equals(this.createdByEmail)) {
                this.studentSubmissionStatuses.add(
                        StudentSubmissionStatus.notSubmitted(
                                this, email, classMember.getMember().getNickname()));
            }
        }
    }

    /** 논리적 삭제 */
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 복원 */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
