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

import java.util.ArrayList;
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
        log.debug("클래스별 과제 목록 조회 (제출 파일 포함): classId={}", classId);

        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(classId);

        if (assignments.isEmpty()) {
            return List.of();
        }

        // 과제마다 제출물을 조회하면 쿼리가 과제 수에 비례해 늘어난다(N+1).
        // 전체 과제의 제출물을 IN 절로 한 번에 가져온 뒤 메모리에서 과제별로 묶는다.
        List<Long> assignmentIds = assignments.stream()
                .map(Assignment::getId)
                .collect(Collectors.toList());

        Map<Long, List<Submission>> submissionsByAssignmentId =
                submissionRepository.findByAssignmentIdsWithSubmissionFiles(assignmentIds)
                        .stream()
                        .collect(Collectors.groupingBy(Submission::getAssignmentId));

        return assignments.stream()
                .map(assignment -> domainToDtoWithSubmissionFiles(
                        assignment,
                        submissionsByAssignmentId.getOrDefault(assignment.getId(), List.of()),
                        attachmentAdapter))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssignmentDTO> getAssignmentsByClassIdWithUserStatus(Long classId, String userEmail) {
        log.debug("클래스별 과제 목록 조회 (사용자 제출 상태 포함): classId={}, userEmail={}", classId, userEmail);

        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(classId);

        return assignments.stream()
                .map(assignment -> {
                    // 기본 AssignmentDTO 생성
                    AssignmentDTO assignmentDTO = domainToDto(assignment, attachmentAdapter);
                    
                    // 현재 사용자의 제출 상태 확인
                    boolean isSubmitted = assignment.getStudentSubmissionStatuses().stream()
                            .anyMatch(status -> status.getStudentEmail().equals(userEmail) && 
                                     status.getStatus() == com.edu.edumeet.homework.domain.SubmissionStatus.SUBMITTED);
                    
                    // done 필드 추가 (프론트엔드에서 사용)
                    return AssignmentDTO.builder()
                            .id(assignmentDTO.getId())
                            .title(assignmentDTO.getTitle())
                            .description(assignmentDTO.getDescription())
                            .classId(assignmentDTO.getClassId())
                            .createdByEmail(assignmentDTO.getCreatedByEmail())
                            .createdByName(assignmentDTO.getCreatedByName())
                            .attachmentFiles(assignmentDTO.getAttachmentFiles())
                            .studentSubmissionStatuses(assignmentDTO.getStudentSubmissionStatuses())
                            .regDate(assignmentDTO.getRegDate())
                            .modDate(assignmentDTO.getModDate())
                            .done(isSubmitted) // 제출 여부 설정
                            .build();
                })
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
        // 학생 Email별로 제출물 매핑 (성능 최적화를 위한 Map 사용)
        Map<String, Submission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(Submission::getClassMemberEmail, submission -> submission));
        
        // StudentSubmissionStatus 리스트에 제출 파일 정보 포함하고 DTO로 변환
        List<com.edu.edumeet.homework.presentation.dto.StudentSubmissionStatusDTO> enrichedStatuses = assignment.getStudentSubmissionStatuses().stream()
                .map(status -> {
                    Submission submission = submissionMap.get(status.getStudentEmail());
                    StudentSubmissionStatus enrichedStatus;
                    if (submission != null && submission.isSubmitted()) {
                        // 제출 완료 상태이면 제출 파일 정보 포함하여 새로운 Status 생성
                        enrichedStatus = StudentSubmissionStatus.submitted(
                                status.getAssignmentId(),
                                status.getStudentEmail(),
                                status.getStudentName(),
                                submission.getSubmissionFiles(),
                                submission.getModDate() // 제출 시간은 수정일시 사용
                        );
                    } else {
                        // 미제출 상태이면 기존 상태 그대로 반환
                        enrichedStatus = status;
                    }
                    // StudentSubmissionStatus를 DTO로 변환
                    return statusToDto(enrichedStatus, attachmentAdapter);
                })
                .collect(Collectors.toList());
        
        // 제출 파일 정보가 포함된 AssignmentDTO 생성
        return AssignmentDTO.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classId(assignment.getClassId())
                .createdByEmail(assignment.getCreatedByEmail())
                .createdByName(assignment.getCreatedByName())
                .attachmentFiles(assignment.getAttachmentFiles() != null ? 
                    attachmentAdapter.toFileUploadDTOList(assignment.getAttachmentFiles()) : 
                    new ArrayList<>())
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