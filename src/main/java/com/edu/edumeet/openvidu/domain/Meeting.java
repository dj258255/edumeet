package com.edu.edumeet.openvidu.domain;

import com.edu.edumeet.classroom.domain.ClassRoom;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    /**
     * 세션 형태. 이 값에 따라 정원 제한과 참가 권한이 갈린다. (#2)
     * 기존 세션은 전부 화상강의였으므로 기본값은 INTERACTIVE 다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    @Builder.Default
    private SessionType sessionType = SessionType.INTERACTIVE;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String s3url;

    public void assignTo(ClassRoom classRoom) {
        this.classRoom = classRoom;
        if (classRoom != null) {
            classRoom.getMeetings().add(this);
        }
    }

    public void changeEndTime(LocalDateTime newEndTime) {
        if (newEndTime != null && !newEndTime.isAfter(this.startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.endTime = newEndTime;
    }

    /** 정원 제한을 적용하는 세션인가. */
    public boolean hasParticipantLimit() {
        return this.sessionType.hasParticipantLimit();
    }

    /** 이 세션의 정원. 라이브방송은 제한이 없다. */
    public int participantLimit() {
        return hasParticipantLimit() ? this.classRoom.getParticipantLimit() : Integer.MAX_VALUE;
    }

    public void endNow() {
        changeEndTime(LocalDateTime.now());
    }

    public void changeS3Url(String newS3Url) {
        this.s3url = newS3Url;
    }

    @PrePersist @PreUpdate
    private void validateTimes() {
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }
}
