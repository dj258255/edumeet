package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {
    List<ClassMember> findAllByMemberId(Long memberId);

    ClassMember save(ClassMember classMember);

    List<ClassMember> findAllByClassRoomId(Long classId);

    boolean existsByClassRoomIdAndMemberId(Long classId, Long memberId);

    Optional<ClassMember> findByClassRoomIdAndMemberId(Long classId, Long studentId);
}
