package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.member.domain.Member;

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

    @ManyToOne
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    protected ClassMember() {}

    @Builder
    private ClassMember(ClassRoom classRoom, Member member) {
        this.classRoom = classRoom;
        this.member = member;
    }
}