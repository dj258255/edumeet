package com.edu.edumeet.member.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
    boolean existsByEmail(String email);
}
