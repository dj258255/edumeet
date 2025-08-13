package com.edu.edumeet.homework.application;

import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.edu.edumeet.homework.infrastructure.StudentSubmissionStatusJpaEntity;
import com.edu.edumeet.homework.infrastructure.StudentSubmissionStatusJpaRepository;
import com.edu.edumeet.homework.infrastructure.SubmissionFileUploadJpaEntity;
import com.edu.edumeet.homework.infrastructure.SubmissionFileUploadJpaRepository;
import com.edu.edumeet.homework.presentation.SubmissionService;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttachmentAdapter attachmentAdapter;
    private final StudentSubmissionStatusJpaRepository studentSubmissionStatusJpaRepository;
    private final SubmissionFileUploadJpaRepository submissionFileUploadJpaRepository;

    @Override
    @Transactional
    public Long submitAssignment(SubmissionCreateDTO submissionCreateDTO) {
        log.info("과제 제출 시작: assignmentId={}, classMemberEmail={}", 
                submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberEmail());

        // 1. 도메인 객체 생성
        Submission submission = createDtoToDomain(submissionCreateDTO);

        // 2. 제출물 저장
        Submission savedSubmission = submissionRepository.save(submission);

        // 3. StudentSubmissionStatus 업데이트 및 제출 파일 연결
        Optional<StudentSubmissionStatusJpaEntity> statusEntityOpt = studentSubmissionStatusJpaRepository
                .findByAssignmentIdAndStudentEmail(submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberEmail());
        
        if (statusEntityOpt.isPresent()) {
            StudentSubmissionStatusJpaEntity statusEntity = statusEntityOpt.get();
            
            // 제출 상태를 SUBMITTED로 변경하고 제출 시간 설정
            StudentSubmissionStatusJpaEntity updatedEntity = StudentSubmissionStatusJpaEntity.builder()
                    .id(statusEntity.getId())
                    .assignment(statusEntity.getAssignment())
                    .studentEmail(statusEntity.getStudentEmail())
                    .studentName(statusEntity.getStudentName())
                    .status(SubmissionStatus.SUBMITTED)
                    .submittedAt(java.time.LocalDateTime.now())
                    .submissionFiles(statusEntity.getSubmissionFiles()) // 먼저 기존 파일 유지
                    .build();
            
            StudentSubmissionStatusJpaEntity savedStatusEntity = studentSubmissionStatusJpaRepository.save(updatedEntity);
            
            // 4. 제출한 파일들을 StudentSubmissionStatus와도 연결
            updateStudentSubmissionStatusFiles(savedSubmission, savedStatusEntity);
            
            log.info("StudentSubmissionStatus 업데이트 완료: assignmentId={}, studentEmail={}", 
                    submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberEmail());
        } else {
            log.warn("StudentSubmissionStatus를 찾을 수 없음: assignmentId={}, studentEmail={}", 
                    submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberEmail());
        }

        log.info("과제 제출 완료: ID={}", savedSubmission.getId());
        return savedSubmission.getId();
    }

    @Override
    public SubmissionDTO getSubmission(Long id) {
        log.debug("제출물 조회: ID={}", id);

        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + id));

        return domainToDto(submission, attachmentAdapter);
    }


    @Override
    @Transactional
    public void deleteSubmission(Long id) {
        log.info("제출물 삭제 시작: ID={}", id);

        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + id));

        submissionRepository.deleteById(id);
        log.info("제출물 삭제 완료: ID={}", id);
    }

    @Override
    public List<SubmissionDTO> getSubmissionsByAssignmentId(Long assignmentId) {
        log.debug("과제별 제출물 목록 조회: assignmentId={}", assignmentId);

        List<Submission> submissions = submissionRepository.findByAssignmentId(assignmentId);

        return submissions.stream()
                .map(submission -> domainToDto(submission, attachmentAdapter))
                .collect(Collectors.toList());
    }

    @Override
    public List<SubmissionDTO> getSubmissionsByClassMemberEmail(String classMemberEmail) {
        log.debug("학생별 제출물 목록 조회 (과제 제목 포함): classMemberEmail={}", classMemberEmail);

        List<Submission> submissions = submissionRepository.findByClassMemberEmailOrderByRegDateDesc(classMemberEmail);

        return submissions.stream()
                .map(submission -> {
                    // 과제 제목을 가져오기 위해 Assignment 조회
                    Assignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                            .orElse(null);
                    String assignmentTitle = assignment != null ? assignment.getTitle() : "알 수 없는 과제";
                    return domainToDtoWithAssignmentTitle(submission, assignmentTitle, attachmentAdapter);
                })
                .collect(Collectors.toList());
    }

    @Override
    public SubmissionDTO getSubmissionByAssignmentAndClassMember(Long assignmentId, String classMemberEmail) {
        log.debug("특정 과제의 특정 학생 제출물 조회: assignmentId={}, classMemberEmail={}", assignmentId, classMemberEmail);

        Submission submission = submissionRepository.findByAssignmentIdAndClassMemberEmail(assignmentId, classMemberEmail)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다."));

        return domainToDto(submission, attachmentAdapter);
    }

    @Override
    @Transactional
    public void addSubmissionFile(Long submissionId, Attachment attachment) {
        log.info("제출물 파일 추가: submissionId={}, fileName={}", submissionId, attachment.getFileName());

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + submissionId));

        Submission submissionWithFile = submission.addSubmissionFile(attachment);
        submissionRepository.save(submissionWithFile);

        log.info("제출물 파일 추가 완료: submissionId={}", submissionId);
    }

    @Override
    public SubmissionDTO getSubmissionWithFiles(Long id) {
        log.debug("첨부파일 포함 제출물 조회: ID={}", id);

        Submission submission = submissionRepository.findByIdWithSubmissionFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + id));

        return domainToDto(submission, attachmentAdapter);
    }

    @Override
    @Transactional
    public void restoreSubmission(Long id) {
        log.info("제출물 복원 시작: ID={}", id);

        boolean restored = submissionRepository.restoreById(id);
        if (!restored) {
            throw new IllegalArgumentException("복원할 수 없는 제출물입니다: " + id);
        }

        log.info("제출물 복원 완료: ID={}", id);
    }

    /**
     * 제출한 파일들을 StudentSubmissionStatus와 연결
     * 중복 저장을 방지하기 위해 기존 파일에 StudentSubmissionStatus만 업데이트
     */
    @Transactional
    private void updateStudentSubmissionStatusFiles(Submission savedSubmission, StudentSubmissionStatusJpaEntity statusEntity) {
        log.debug("StudentSubmissionStatus 파일 연결 시작: submissionId={}, statusId={}", 
                savedSubmission.getId(), statusEntity.getId());
        
        // 해당 제출물의 파일들을 조회
        List<SubmissionFileUploadJpaEntity> submissionFiles = submissionFileUploadJpaRepository
                .findBySubmissionId(savedSubmission.getId());
        
        // 기존 파일 엔티티에 StudentSubmissionStatus만 업데이트 (중복 저장 방지)
        for (SubmissionFileUploadJpaEntity fileEntity : submissionFiles) {
            // 도메인이 제대로 설정되어 있는 파일들만 연결 (도메인 없는 파일은 연결하지 않음)
            if (fileEntity.getDomain() != null && !fileEntity.getDomain().isEmpty()) {
                // 기존 엔티티를 업데이트하되, 기존 ID 유지하여 중복 저장 방지
                SubmissionFileUploadJpaEntity updatedFileEntity = SubmissionFileUploadJpaEntity.builder()
                        .id(fileEntity.getId()) // 기존 ID 유지
                        .submission(fileEntity.getSubmission())
                        .studentSubmissionStatus(statusEntity) // StudentSubmissionStatus 연결
                        .uuid(fileEntity.getUuid())
                        .fileName(fileEntity.getFileName())
                        .ord(fileEntity.getOrd())
                        .img(fileEntity.isImg())
                        .fileSize(fileEntity.getFileSize())
                        .contentType(fileEntity.getContentType())
                        .uploadedBy(fileEntity.getUploadedBy())
                        .referenceId(fileEntity.getReferenceId())
                        .domain(fileEntity.getDomain())
                        .build();
                
                submissionFileUploadJpaRepository.save(updatedFileEntity);
                log.debug("파일 연결 완료: uuid={}, domain={}", fileEntity.getUuid(), fileEntity.getDomain());
            } else {
                log.warn("도메인이 없는 파일은 StudentSubmissionStatus와 연결하지 않음: uuid={}", fileEntity.getUuid());
            }
        }
        
        log.debug("StudentSubmissionStatus 파일 연결 완료: 총 파일 수={}, 연결된 파일 수={}", 
                submissionFiles.size(), 
                submissionFiles.stream().filter(f -> f.getDomain() != null && !f.getDomain().isEmpty()).count());
    }
}