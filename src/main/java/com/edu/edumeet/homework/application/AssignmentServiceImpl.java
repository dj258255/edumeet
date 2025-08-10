package com.edu.edumeet.homework.application;

import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.presentation.AssignmentService;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentUpdateDTO;
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
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ClassMemberRepository classMemberRepository;

    @Override
    @Transactional
    public Long createAssignment(AssignmentCreateDTO assignmentCreateDTO) {
        log.info("과제 생성 시작: {}", assignmentCreateDTO.getTitle());

        // 1. 도메인 객체 생성
        Assignment assignment = createDtoToDomain(assignmentCreateDTO);

        // 2. 과제 저장
        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 3. 클래스 멤버들의 제출 현황 초기화
        List<ClassMember> classMembers = classMemberRepository.findAllByClassRoomId(assignmentCreateDTO.getClassId());
        Assignment assignmentWithStatuses = savedAssignment.initializeStudentStatuses(classMembers);
        assignmentRepository.save(assignmentWithStatuses);

        log.info("과제 생성 완료: ID={}", savedAssignment.getId());
        return savedAssignment.getId();
    }

    @Override
    public AssignmentDTO getAssignment(Long id) {
        log.debug("과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment);
    }

    @Override
    @Transactional
    public void updateAssignment(Long id, AssignmentUpdateDTO assignmentUpdateDTO) {
        log.info("과제 수정 시작: ID={}", id);

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        // 권한 체크 (생성자만 수정 가능)
        if (!assignment.isCreatedBy(assignmentUpdateDTO.getUpdaterId())) {
            throw new IllegalArgumentException("과제를 수정할 권한이 없습니다.");
        }

        Assignment updatedAssignment = assignment.update(
                assignmentUpdateDTO.getTitle(),
                assignmentUpdateDTO.getDescription()
        );

        assignmentRepository.save(updatedAssignment);
        log.info("과제 수정 완료: ID={}", id);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        log.info("과제 삭제 시작: ID={}", id);

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        assignmentRepository.deleteById(id);
        log.info("과제 삭제 완료: ID={}", id);
    }

    @Override
    public List<AssignmentDTO> getAssignmentsByClassId(Long classId) {
        log.debug("클래스별 과제 목록 조회: classId={}", classId);

        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(classId);

        return assignments.stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addAttachmentFile(Long assignmentId, FileUpload fileUpload) {
        log.info("과제 첨부파일 추가: assignmentId={}, fileName={}", assignmentId, fileUpload.getFileName());

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        Assignment assignmentWithFile = assignment.addAttachmentFile(fileUpload);
        assignmentRepository.save(assignmentWithFile);

        log.info("과제 첨부파일 추가 완료: assignmentId={}", assignmentId);
    }

    @Override
    public AssignmentDTO getAssignmentWithAttachmentFiles(Long id) {
        log.debug("첨부파일 포함 과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findByIdWithAttachmentFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment);
    }

    @Override
    public AssignmentDTO getAssignmentWithSubmissionStatuses(Long id) {
        log.debug("제출 현황 포함 과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findByIdWithSubmissionStatuses(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment);
    }

    @Override
    @Transactional
    public void restoreAssignment(Long id) {
        log.info("과제 복원 시작: ID={}", id);

        boolean restored = assignmentRepository.restoreById(id);
        if (!restored) {
            throw new IllegalArgumentException("복원할 수 없는 과제입니다: " + id);
        }

        log.info("과제 복원 완료: ID={}", id);
    }
}