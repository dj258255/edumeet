package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassInvite;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassInviteRepository extends JpaRepository<ClassInvite, Long> {
    boolean existsByClassRoomAndInvitee(ClassRoom classRoom, MemberJpaEntity invitee);

    List<ClassInvite> findByInviteeId(Long inviteeId);
}
