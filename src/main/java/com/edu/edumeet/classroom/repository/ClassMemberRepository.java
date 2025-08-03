package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {
    List<ClassMember> findAllByMemberId(Long memberId);
}
