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
        
        SubmissionJpaEntity submissionJpaEntity;
        boolean isUpdate = submission.getId() != null;
        
        if (isUpdate) {
            // 기존 제출물 업데이트 - 기존 엔티티가 있는지 확인
            Optional<SubmissionJpaEntity> existingEntity = submissionJpaRepository.findById(submission.getId());
            if (existingEntity.isPresent()) {
                // 기존 제출파일 정보 삭제 (orphanRemoval=true로 자동 삭제됨)
                existingEntity.get().getSubmissionFiles().clear();
                
                // 새로운 엔티티로 대체 (ID는 유지)
                submissionJpaEntity = SubmissionJpaEntity.fromDomain(submission);
            } else {
                // 업데이트할 제출물이 존재하지 않음
                log.warn("업데이트하려는 제출물을 찾을 수 없음: ID={}", submission.getId());
                return null;
            }
        } else {
            // 새로운 제출물 생성
            submissionJpaEntity = SubmissionJpaEntity.fromDomain(submission);
        }
        
        // 도메인 -> JPA 엔티티 변환 후 저장
        SubmissionJpaEntity savedEntity = submissionJpaRepository.save(submissionJpaEntity);
        
        // 제출파일 정보 처리
        if (submission.getSubmissionFiles() != null && !submission.getSubmissionFiles().isEmpty()) {
            log.info("제출물 {}의 제출파일 정보 처리 - 파일 수: {}", savedEntity.getId(), submission.getSubmissionFiles().size());
            
            // 제출파일 정보는 SubmissionJpaEntity의 submissionFiles에 직접 추가
            // SubmissionFileUploadJpaEntity는 cascade=ALL, orphanRemoval=true로 설정되어 있어
            // SubmissionJpaEntity가 저장될 때 함께 저장됨
            submission.getSubmissionFiles().forEach(fileUpload -> {
                SubmissionFileUploadJpaEntity fileEntity = SubmissionFileUploadJpaEntity.builder()
                        .submission(savedEntity)
                        .uuid(fileUpload.getUuid())
                        .fileName(fileUpload.getFileName())
                        .ord(fileUpload.getOrd())
                        .img(fileUpload.isImg())
                        .fileSize(fileUpload.getFileSize())
                        .contentType(fileUpload.getContentType())
                        .uploadedBy(fileUpload.getUploadedBy())
                        .referenceId(savedEntity.getId())
                        .build();
                
                savedEntity.getSubmissionFiles().add(fileEntity);
            });
            
            // 변경된 엔티티 저장
            submissionJpaRepository.save(savedEntity);
        }
        
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