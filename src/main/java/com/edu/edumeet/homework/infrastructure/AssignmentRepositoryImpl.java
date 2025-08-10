package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.homework.application.AssignmentRepository;
import com.edu.edumeet.homework.domain.Assignment;
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
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private final AssignmentJpaRepository assignmentJpaRepository;

    @Override
    @Transactional
    public Assignment save(Assignment assignment) {
        log.debug("과제 저장 시작: {}", assignment.getTitle());
        
        AssignmentJpaEntity entity = AssignmentJpaEntity.fromDomain(assignment);
        AssignmentJpaEntity savedEntity = assignmentJpaRepository.save(entity);
        
        log.debug("과제 저장 완료: ID={}", savedEntity.getId());
        return savedEntity.toDomain();
    }

    @Override
    public Optional<Assignment> findById(Long id) {
        log.debug("과제 조회 시작: ID={}", id);
        
        return assignmentJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(AssignmentJpaEntity::toDomain);
    }

    @Override
    public List<Assignment> findByClassIdOrderByRegDateDesc(Long classId) {
        log.debug("클래스별 과제 조회 시작: classId={}", classId);
        
        return assignmentJpaRepository.findByClassIdOrderByRegDateDesc(classId)
                .stream()
                .filter(entity -> entity.getDeletedAt() == null)
                .map(AssignmentJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Assignment> findByIdWithAttachmentFiles(Long id) {
        log.debug("첨부파일 포함 과제 조회 시작: ID={}", id);
        
        return assignmentJpaRepository.findByIdWithAttachmentFiles(id)
                .map(AssignmentJpaEntity::toDomain);
    }

    @Override
    public Optional<Assignment> findByIdWithSubmissionStatuses(Long id) {
        log.debug("제출 현황 포함 과제 조회 시작: ID={}", id);
        
        return assignmentJpaRepository.findByIdWithSubmissionStatuses(id)
                .map(AssignmentJpaEntity::toDomain);
    }

    @Override
    public Optional<Assignment> findByIdWithAllDetails(Long id) {
        log.debug("모든 세부사항 포함 과제 조회 시작: ID={} (N+1 최적화)", id);
        
        return assignmentJpaRepository.findByIdWithAllDetails(id)
                .map(AssignmentJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("과제 삭제 시작: ID={}", id);
        
        assignmentJpaRepository.findById(id)
                .ifPresent(entity -> {
                    entity.delete();
                    assignmentJpaRepository.save(entity);
                    log.debug("과제 삭제 완료: ID={}", id);
                });
    }

    @Override
    @Transactional
    public boolean restoreById(Long id) {
        log.debug("과제 복원 시작: ID={}", id);
        
        Optional<AssignmentJpaEntity> entityOpt = assignmentJpaRepository.findById(id);
        if (entityOpt.isPresent()) {
            AssignmentJpaEntity entity = entityOpt.get();
            if (entity.isDeleted()) {
                entity.restore();
                assignmentJpaRepository.save(entity);
                log.debug("과제 복원 완료: ID={}", id);
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Assignment> findByIdIncludeDeleted(Long id) {
        log.debug("삭제된 과제 포함 조회 시작: ID={}", id);
        
        return assignmentJpaRepository.findById(id)
                .map(AssignmentJpaEntity::toDomain);
    }
}