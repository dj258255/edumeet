package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @OneToOne(mappedBy = "classRoom", fetch = FetchType.LAZY)
    private ClassMember classMember;

    private String title;

    private String description;

    @Column(name = "participant_limit")
    private int participantLimit;

    protected ClassRoom() {}

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