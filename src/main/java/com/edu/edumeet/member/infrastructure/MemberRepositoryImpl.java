package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.application.repository.MemberRepository;
import com.edu.edumeet.member.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
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
        log.info("Member 저장 시작 - email: {}, 기존 id: {}", member.getEmail(), member.getId());

        MemberJpaEntity entity = MemberJpaEntity.from(member);
        MemberJpaEntity savedEntity = memberJpaRepository.save(entity);

        log.info("Member 저장 완료 - 생성된 id: {}, email: {}",
                savedEntity.getId(), savedEntity.getEmail());

        Member savedMember = savedEntity.toDomain();

        log.info("반환할 Member 도메인 객체 - id: {}, email: {}",
                savedMember.getId(), savedMember.getEmail());

        return savedMember;
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        log.debug("Member 조회 by email: {}", email);

        return memberJpaRepository.findByEmail(email)
                .map(entity -> {
                    Member member = entity.toDomain();
                    log.debug("Member 조회 성공 - id: {}, email: {}", member.getId(), member.getEmail());
                    return member;
                });
    }
}