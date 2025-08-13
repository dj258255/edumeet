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

    public void endNow() {
        changeEndTime(LocalDateTime.now());
    }

    @PrePersist @PreUpdate
    private void validateTimes() {
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }
}
