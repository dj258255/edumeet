package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaRepository;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "class_member")
@Getter
public class ClassMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id", unique = true)
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberJpaEntity member;

    protected ClassMember() {}

    @Builder
    private ClassMember(ClassRoom classRoom, MemberJpaEntity member) {
        this.classRoom = classRoom;
        this.member = member;
    }
}