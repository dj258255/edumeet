package com.edu.edumeet.homework.application;

import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.presentation.SubmissionService;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionUpdateDTO;
import com.edu.edumeet.upload.domain.FileUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;

    @Override
    @Transactional
    public Long submitAssignment(SubmissionCreateDTO submissionCreateDTO) {
        log.info("과제 제출 시작: assignmentId={}, classMemberId={}", 
                submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberId());

        // 1. 도메인 객체 생성
        Submission submission = createDtoToDomain(submissionCreateDTO);

        // 2. 제출물 저장
        Submission savedSubmission = submissionRepository.save(submission);

        // 3. 과제의 제출 상태 업데이트
        Assignment assignment = assignmentRepository.findById(submissionCreateDTO.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + submissionCreateDTO.getAssignmentId()));

        Assignment updatedAssignment = assignment.updateSubmissionStatus(submissionCreateDTO.getClassMemberId());
        assignmentRepository.save(updatedAssignment);

        log.info("과제 제출 완료: ID={}", savedSubmission.getId());
        return savedSubmission.getId();
    }

    @Override
    public SubmissionDTO getSubmission(Long id) {
        log.debug("제출물 조회: ID={}", id);

        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + id));

        return domainToDto(submission);
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
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<SubmissionDTO> getSubmissionsByClassMemberId(Long classMemberId) {
        log.debug("학생별 제출물 목록 조회: classMemberId={}", classMemberId);

        List<Submission> submissions = submissionRepository.findByClassMemberIdOrderByRegDateDesc(classMemberId);

        return submissions.stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    @Override
    public SubmissionDTO getSubmissionByAssignmentAndClassMember(Long assignmentId, Long classMemberId) {
        log.debug("특정 과제의 특정 학생 제출물 조회: assignmentId={}, classMemberId={}", assignmentId, classMemberId);

        Submission submission = submissionRepository.findByAssignmentIdAndClassMemberId(assignmentId, classMemberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다."));

        return domainToDto(submission);
    }

    @Override
    @Transactional
    public void addSubmissionFile(Long submissionId, FileUpload fileUpload) {
        log.info("제출물 파일 추가: submissionId={}, fileName={}", submissionId, fileUpload.getFileName());

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + submissionId));

        Submission submissionWithFile = submission.addSubmissionFile(fileUpload);
        submissionRepository.save(submissionWithFile);

        log.info("제출물 파일 추가 완료: submissionId={}", submissionId);
    }

    @Override
    public SubmissionDTO getSubmissionWithFiles(Long id) {
        log.debug("첨부파일 포함 제출물 조회: ID={}", id);

        Submission submission = submissionRepository.findByIdWithSubmissionFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + id));

        return domainToDto(submission);
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
}