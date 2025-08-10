package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_name", nullable = false, length = 50)
    private String studentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.NOT_SUBMITTED;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // 도메인 모델로 변환
    public StudentSubmissionStatus toDomain() {
        return StudentSubmissionStatus.builder()
                .assignmentId(this.assignment.getId())
                .studentId(this.studentId)
                .studentName(this.studentName)
                .status(this.status)
                .submittedAt(this.submittedAt)
                .build();
    }

    // 도메인 모델에서 JPA 엔티티로 변환
    public static StudentSubmissionStatusJpaEntity fromDomain(StudentSubmissionStatus status, AssignmentJpaEntity assignment) {
        return StudentSubmissionStatusJpaEntity.builder()
                .assignment(assignment)
                .studentId(status.getStudentId())
                .studentName(status.getStudentName())
                .status(status.getStatus())
                .submittedAt(status.getSubmittedAt())
                .build();
    }
}