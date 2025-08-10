package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.homework.application.SubmissionRepository;
import com.edu.edumeet.homework.domain.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class SubmissionRepositoryImpl implements SubmissionRepository {

    private final SubmissionJpaRepository submissionJpaRepository;

    @Override
    @Transactional
    public Submission save(Submission submission) {
        log.debug("제출물 저장 시작: assignmentId={}, classMemberId={}", 
                submission.getAssignmentId(), submission.getClassMemberId());
        
        SubmissionJpaEntity entity = SubmissionJpaEntity.fromDomain(submission);
        SubmissionJpaEntity savedEntity = submissionJpaRepository.save(entity);
        
        log.debug("제출물 저장 완료: ID={}", savedEntity.getId());
        return savedEntity.toDomain();
    }

    @Override
    public Optional<Submission> findById(Long id) {
        log.debug("제출물 조회 시작: ID={}", id);
        
        return submissionJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(SubmissionJpaEntity::toDomain);
    }

    @Override
    public List<Submission> findByAssignmentId(Long assignmentId) {
        log.debug("과제별 제출물 조회 시작: assignmentId={}", assignmentId);
        
        return submissionJpaRepository.findByAssignmentIdAndDeletedAtIsNull(assignmentId)
                .stream()
                .map(SubmissionJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Submission> findByClassMemberIdOrderByRegDateDesc(Long classMemberId) {
        log.debug("학생별 제출물 조회 시작: classMemberId={}", classMemberId);
        
        return submissionJpaRepository.findByClassMemberIdOrderByRegDateDesc(classMemberId)
                .stream()
                .filter(entity -> entity.getDeletedAt() == null)
                .map(SubmissionJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Submission> findByAssignmentIdAndClassMemberId(Long assignmentId, Long classMemberId) {
        log.debug("특정 과제의 특정 학생 제출물 조회 시작: assignmentId={}, classMemberId={}", assignmentId, classMemberId);
        
        return submissionJpaRepository.findByAssignmentIdAndClassMemberIdAndDeletedAtIsNull(assignmentId, classMemberId)
                .map(SubmissionJpaEntity::toDomain);
    }

    @Override
    public Optional<Submission> findByIdWithSubmissionFiles(Long id) {
        log.debug("첨부파일 포함 제출물 조회 시작: ID={}", id);
        
        return submissionJpaRepository.findByIdWithSubmissionFiles(id)
                .map(SubmissionJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("제출물 삭제 시작: ID={}", id);
        
        submissionJpaRepository.findById(id)
                .ifPresent(entity -> {
                    entity.delete();
                    submissionJpaRepository.save(entity);
                    log.debug("제출물 삭제 완료: ID={}", id);
                });
    }

    @Override
    @Transactional
    public boolean restoreById(Long id) {
        log.debug("제출물 복원 시작: ID={}", id);
        
        Optional<SubmissionJpaEntity> entityOpt = submissionJpaRepository.findById(id);
        if (entityOpt.isPresent()) {
            SubmissionJpaEntity entity = entityOpt.get();
            if (entity.isDeleted()) {
                entity.restore();
                submissionJpaRepository.save(entity);
                log.debug("제출물 복원 완료: ID={}", id);
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Submission> findByIdIncludeDeleted(Long id) {
        log.debug("삭제된 제출물 포함 조회 시작: ID={}", id);
        
        return submissionJpaRepository.findById(id)
                .map(SubmissionJpaEntity::toDomain);
    }
}