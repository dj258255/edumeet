package com.edu.edumeet.homework.application;

import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.application.AssignmentRepository;
import com.edu.edumeet.homework.application.SubmissionRepository;
import com.edu.edumeet.homework.presentation.AssignmentService;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final ClassMemberRepository classMemberRepository;
    private final AttachmentAdapter attachmentAdapter;

    @Override
    @Transactional
    public Long createAssignment(AssignmentCreateDTO assignmentCreateDTO, Long classId) {
        log.info("과제 생성 시작: {}", assignmentCreateDTO.getTitle());

        // 1. 클래스 멤버들 먼저 조회 (성능 최적화)
        List<ClassMember> classMembers = classMemberRepository.findAllByClassRoomId(classId);
        
        // 2. 도메인 객체 생성
        Assignment assignment = createDtoToDomain(assignmentCreateDTO, classId, attachmentAdapter);
        
        // 3. 제출 현황 초기화 후 한 번에 저장 (성능 최적화: 중복 save() 제거)
        Assignment assignmentWithStatuses = assignment.initializeStudentStatuses(classMembers);
        Assignment savedAssignment = assignmentRepository.save(assignmentWithStatuses);

        log.info("과제 생성 완료: ID={}", savedAssignment.getId());
        return savedAssignment.getId();
    }

    @Override
    public AssignmentDTO getAssignment(Long id) {
        log.debug("과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment, attachmentAdapter);
    }


    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        log.info("과제 삭제 시작: ID={}", id);

        // 성능 최적화: 불필요한 조회 제거 (deleteById는 존재하지 않는 ID에도 안전)
        assignmentRepository.deleteById(id);
        log.info("과제 삭제 완료: ID={}", id);
    }

    @Override
    public List<AssignmentDTO> getAssignmentsByClassId(Long classId) {
        log.debug("클래스별 과제 목록 조회: classId={}", classId);

        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(classId);

        return assignments.stream()
                .map(assignment -> domainToDto(assignment, attachmentAdapter))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addAttachmentFile(Long assignmentId, Attachment attachment) {
        log.info("과제 첨부파일 추가: assignmentId={}, fileName={}", assignmentId, attachment.getFileName());

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        Assignment assignmentWithFile = assignment.addAttachmentFile(attachment);
        assignmentRepository.save(assignmentWithFile);

        log.info("과제 첨부파일 추가 완료: assignmentId={}", assignmentId);
    }

    @Override
    public AssignmentDTO getAssignmentWithAttachmentFiles(Long id) {
        log.debug("첨부파일 포함 과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findByIdWithAttachmentFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment, attachmentAdapter);
    }

    @Override
    public AssignmentDTO getAssignmentWithSubmissionStatuses(Long id) {
        log.debug("제출 현황 포함 과제 조회: ID={}", id);

        Assignment assignment = assignmentRepository.findByIdWithSubmissionStatuses(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        return domainToDto(assignment, attachmentAdapter);
    }

    @Override
    public AssignmentDTO getAssignmentWithAllDetails(Long id) {
        log.debug("모든 세부사항 포함 과제 조회: ID={}", id);

        // N+1 문제 해결: 단일 쿼리로 모든 관계 데이터 로드
        Assignment assignment = assignmentRepository.findByIdWithAllDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + id));

        // 해당 과제의 모든 제출물 조회 (제출 파일 포함)
        List<Submission> submissions = submissionRepository.findByAssignmentIdWithSubmissionFiles(id);
        
        return domainToDtoWithSubmissionFiles(assignment, submissions, attachmentAdapter);
    }

    /**
     * Assignment 도메인 객체를 AssignmentDTO로 변환 (제출 파일 정보 포함)
     */
    private AssignmentDTO domainToDtoWithSubmissionFiles(Assignment assignment, List<Submission> submissions, AttachmentAdapter attachmentAdapter) {
        // 학생 ID별로 제출물 매핑 (성능 최적화를 위한 Map 사용)
        Map<Long, Submission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(Submission::getClassMemberId, submission -> submission));
        
        // StudentSubmissionStatus 리스트에 제출 파일 정보 포함
        List<StudentSubmissionStatus> enrichedStatuses = assignment.getStudentSubmissionStatuses().stream()
                .map(status -> {
                    Submission submission = submissionMap.get(status.getStudentId());
                    if (submission != null && submission.isSubmitted()) {
                        // 제출 완료 상태이면 제출 파일 정보 포함하여 새로운 Status 생성
                        return StudentSubmissionStatus.submitted(
                                status.getAssignmentId(),
                                status.getStudentId(),
                                status.getStudentName(),
                                submission.getSubmissionFiles(),
                                submission.getModDate() // 제출 시간은 수정일시 사용
                        );
                    } else {
                        // 미제출 상태이면 기존 상태 그대로 반환
                        return status;
                    }
                })
                .collect(Collectors.toList());
        
        // 제출 파일 정보가 포함된 AssignmentDTO 생성
        return AssignmentDTO.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classId(assignment.getClassId())
                .createdById(assignment.getCreatedById())
                .createdByName(assignment.getCreatedByName())
                .attachmentFiles(attachmentAdapter.toFileUploadDTOList(assignment.getAttachmentFiles()))
                .studentSubmissionStatuses(enrichedStatuses)
                .regDate(assignment.getRegDate())
                .modDate(assignment.getModDate())
                .build();
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