package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.openvidu.domain.Meeting;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "class_room")
@Getter
public class ClassRoom extends BaseEntity {
    @Id
    @GeneratedValue
    @Column(name = "class_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberJpaEntity member;

    @OneToMany(mappedBy = "classRoom", fetch = FetchType.LAZY)
    private List<ClassMember> classMember;

    @OneToOne(mappedBy = "classRoom", fetch = FetchType.LAZY)
    private Thumbnail thumbnail;

    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Tag> tags;

    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Meeting> meetings;

    private String title;

    private String description;

    @Column(name = "participant_limit")
    private int participantLimit;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isDeleted = false;

    protected ClassRoom() {}

    public void markAsDeleted() {
        this.isDeleted = true;
    }

    public void addMeeting(com.edu.edumeet.openvidu.domain.Meeting meeting) {
        meetings.add(meeting);
        meeting.assignTo(this);
    }

    @Builder
    private ClassRoom(MemberJpaEntity member, String title, String description, int participantLimit) {
        this.member = member;
        this.title = title;
        this.description = description;
        this.participantLimit = participantLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassRoom classRoom = (ClassRoom) o;
        return Objects.equals(id, classRoom.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}