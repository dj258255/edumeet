package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

@Entity
@Table(name = "class_invite")
@Getter
public class ClassInvite extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @ManyToOne
    @JoinColumn(name = "invitee_id")
    private Member invitee;

    @Enumerated(EnumType.STRING)
    private InviteStatus status;

    protected ClassInvite() {}

    @Builder
    private ClassInvite(ClassRoom classRoom, Member invitee, InviteStatus status) {
        this.classRoom = classRoom;
        this.invitee = invitee;
        this.status = status;
    }

    public void changeStatus(InviteStatus newStatus) {
        this.status = newStatus;
    }
}
