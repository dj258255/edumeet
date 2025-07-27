package com.edu.edumeet.member.application.repository;

import com.edu.edumeet.member.domain.Member;

public interface MemberRepository {
    boolean existsByEmail(String email);
    Member save(Member member);
}
