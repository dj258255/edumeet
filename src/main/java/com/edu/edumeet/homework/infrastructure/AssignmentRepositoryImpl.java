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
        
        AssignmentJpaEntity assignmentJpaEntity;
        boolean isUpdate = assignment.getId() != null;
        
        if (isUpdate) {
            // 기존 과제 업데이트 - 기존 엔티티가 있는지 확인
            Optional<AssignmentJpaEntity> existingEntity = assignmentJpaRepository.findById(assignment.getId());
            if (existingEntity.isPresent()) {
                // 기존 첨부파일 정보 삭제 (orphanRemoval=true로 자동 삭제됨)
                existingEntity.get().getAttachmentFiles().clear();
                
                // 새로운 엔티티로 대체 (ID는 유지)
                assignmentJpaEntity = AssignmentJpaEntity.fromDomain(assignment);
            } else {
                // 업데이트할 과제가 존재하지 않음
                log.warn("업데이트하려는 과제를 찾을 수 없음: ID={}", assignment.getId());
                return null;
            }
        } else {
            // 새로운 과제 생성 - 파일 없이 먼저 생성
            Assignment assignmentWithoutFiles = Assignment.builder()
                    .title(assignment.getTitle())
                    .description(assignment.getDescription())
                    .classId(assignment.getClassId())
                    .createdByEmail(assignment.getCreatedByEmail())
                    .createdByName(assignment.getCreatedByName())
                    .attachmentFiles(new java.util.ArrayList<>()) // 빈 리스트
                    .regDate(assignment.getRegDate())
                    .modDate(assignment.getModDate())
                    .deletedAt(assignment.getDeletedAt())
                    .build();
            assignmentJpaEntity = AssignmentJpaEntity.fromDomain(assignmentWithoutFiles);
        }
        
        // 도메인 -> JPA 엔티티 변환 후 저장 (파일 없이)
        AssignmentJpaEntity savedEntity = assignmentJpaRepository.save(assignmentJpaEntity);
        
        // 첨부파일 정보 처리 (referenceId 설정 후)
        if (assignment.getAttachmentFiles() != null && !assignment.getAttachmentFiles().isEmpty()) {
            log.info("과제 {}의 첨부파일 정보 처리 - 파일 수: {}", savedEntity.getId(), assignment.getAttachmentFiles().size());
            
            // 기존 파일 정보 모두 삭제 (중복 방지)
            savedEntity.getAttachmentFiles().clear();
            
            // 첨부파일 정보는 AssignmentJpaEntity의 attachmentFiles에 직접 추가
            // AssignmentFileUploadJpaEntity는 cascade=ALL, orphanRemoval=true로 설정되어 있어
            // AssignmentJpaEntity가 저장될 때 함께 저장됨
            assignment.getAttachmentFiles().forEach(fileUpload -> {
                AssignmentFileUploadJpaEntity fileEntity = AssignmentFileUploadJpaEntity.builder()
                        .assignment(savedEntity)
                        .uuid(fileUpload.getUuid())
                        .fileName(fileUpload.getFileName())
                        .ord(fileUpload.getOrd())
                        .img(fileUpload.isImg())
                        .fileSize(fileUpload.getFileSize())
                        .contentType(fileUpload.getContentType())
                        .uploadedBy(fileUpload.getUploadedBy())
                        .referenceId(savedEntity.getId())
                        .domain("assignment")
                        .build();
                
                savedEntity.getAttachmentFiles().add(fileEntity);
            });
        }
        
        // 학생 제출 현황 처리 (assignment_id 설정 후)
        if (assignment.getStudentSubmissionStatuses() != null && !assignment.getStudentSubmissionStatuses().isEmpty()) {
            log.info("과제 {}의 학생 제출 현황 처리 - 학생 수: {}", savedEntity.getId(), assignment.getStudentSubmissionStatuses().size());
            
            // 기존 제출 현황 모두 삭제 (중복 방지)
            savedEntity.getStudentSubmissionStatuses().clear();
            
            // 학생 제출 현황은 AssignmentJpaEntity의 studentSubmissionStatuses에 직접 추가
            // StudentSubmissionStatusJpaEntity는 cascade=ALL, orphanRemoval=true로 설정되어 있어
            // AssignmentJpaEntity가 저장될 때 함께 저장됨
            assignment.getStudentSubmissionStatuses().forEach(status -> {
                StudentSubmissionStatusJpaEntity statusEntity = StudentSubmissionStatusJpaEntity.builder()
                        .assignment(savedEntity)
                        .studentId(status.getStudentId())
                        .studentName(status.getStudentName())
                        .status(status.getStatus())
                        .submittedAt(status.getSubmittedAt())
                        .build();
                
                savedEntity.getStudentSubmissionStatuses().add(statusEntity);
            });
        }
        
        // 첨부파일 또는 제출현황이 있는 경우 변경된 엔티티 저장
        if ((assignment.getAttachmentFiles() != null && !assignment.getAttachmentFiles().isEmpty()) ||
            (assignment.getStudentSubmissionStatuses() != null && !assignment.getStudentSubmissionStatuses().isEmpty())) {
            assignmentJpaRepository.save(savedEntity);
        }
        
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