package com.edu.edumeet.member.application.repository;

import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    boolean existsByEmail(String email);
    Member save(Member member);
    Optional<Member> findByEmail(String email);
    List<Member> findByEmailContainingIgnoreCase(String keyword);
}
