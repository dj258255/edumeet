package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.application.repository.MemberRepository;
import com.edu.edumeet.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public boolean existsByEmail(String email) {
        return memberJpaRepository.existsByEmail(email);
    }

    @Override
    public Member save(Member member) {
        MemberJpaEntity entity = MemberJpaEntity.from(member);
        memberJpaRepository.save(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return memberJpaRepository.findByEmail(email)
                .map(MemberJpaEntity::toDomain);
    }
}
