package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassInvite;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassInviteRepository extends JpaRepository<ClassInvite, Long> {
    boolean existsByClassRoomAndInvitee(ClassRoom classRoom, MemberJpaEntity invitee);

    List<ClassInvite> findByInviteeId(Long inviteeId);

    Optional<ClassInvite> findByClassRoomIdAndInviteeId(Long classId, Long inviteeId);

    void delete(ClassInvite invite);
}
