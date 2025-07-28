package com.edu.edumeet.member.application.repository;

import com.edu.edumeet.member.domain.Member;

import java.util.Optional;

public interface MemberRepository {
    boolean existsByEmail(String email);
    Member save(Member member);
    Optional<Member> findByEmail(String email);
}
