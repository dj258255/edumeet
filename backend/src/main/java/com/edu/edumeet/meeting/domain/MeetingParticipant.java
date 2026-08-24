package com.edu.edumeet.meeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 세션 참가자.
 *
 * 화상강의(INTERACTIVE)는 SFU 의 CPU 한계 때문에 정원을 넘길 수 없다.
 * 정원을 강제하려면 "지금 몇 명이 들어와 있는가"를 알아야 하므로 참가를 기록한다.
 *
 * 라이브방송(BROADCAST)은 정원 제한이 없지만 시청자 집계를 위해 동일하게 기록한다.
 */
@Entity
@Table(
    name = "meeting_participant",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_meeting_participant",
        columnNames = {"meeting_id", "participant_email"}
    )
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "meeting")
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(name = "participant_email", nullable = false, length = 100)
    private String participantEmail;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    public static MeetingParticipant join(Meeting meeting, String email) {
        return MeetingParticipant.builder()
                .meeting(meeting)
                .participantEmail(email)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public void leave() {
        this.leftAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.leftAt == null;
    }
}
