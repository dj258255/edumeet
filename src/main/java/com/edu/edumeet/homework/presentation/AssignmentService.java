package com.edu.edumeet.homework.presentation;

import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.homework.presentation.dto.StudentSubmissionStatusDTO;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 과제 애플리케이션 서비스 인터페이스
 * 도메인과 프레젠테이션 계층 사이의 조정자 역할
 */
public interface AssignmentService {

    /**
     * StudentSubmissionStatus를 StudentSubmissionStatusDTO로 변환
     */
    default StudentSubmissionStatusDTO statusToDto(StudentSubmissionStatus status, AttachmentAdapter attachmentAdapter) {
        return StudentSubmissionStatusDTO.builder()
                .assignmentId(status.getAssignmentId())
                .studentEmail(status.getStudentEmail())
                .studentName(status.getStudentName())
                .status(status.getStatus())
                .submittedAt(status.getSubmittedAt())
                .submissionFiles(status.getSubmissionFiles() != null ? 
                    attachmentAdapter.toFileUploadDTOList(status.getSubmissionFiles()) : 
                    new ArrayList<>())
                .build();
    }

    /**
     * Assignment 도메인 객체를 AssignmentDTO로 변환
     */
    default AssignmentDTO domainToDto(Assignment assignment, AttachmentAdapter attachmentAdapter) {
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
                .studentSubmissionStatuses(assignment.getStudentSubmissionStatuses().stream()
                    .map(status -> statusToDto(status, attachmentAdapter))
                    .collect(Collectors.toList()))
                .regDate(assignment.getRegDate())
                .modDate(assignment.getModDate())
                .build();
    }

    /**
     * AssignmentCreateDTO를 Assignment 도메인 객체로 변환
     */
    default Assignment createDtoToDomain(AssignmentCreateDTO dto, Long classId, AttachmentAdapter attachmentAdapter) {
        return Assignment.builder()
            .title(dto.getTitle())
            .description(dto.getDescription())
            .classId(classId)
            .createdByEmail(dto.getCreatedByEmail())
            .createdByName(dto.getCreatedByName())
            .attachmentFiles(dto.getAttachmentFiles() != null ? 
                attachmentAdapter.fromFileUploadDTOList(dto.getAttachmentFiles()) : // ✅ 어댑터 사용
                new ArrayList<>())
            .build();
}

    /**
     * 과제 생성
     * @param assignmentCreateDTO 생성할 과제 정보
     * @param classId 클래스 ID
     * @return 생성된 과제의 ID
     */
    Long createAssignment(AssignmentCreateDTO assignmentCreateDTO, Long classId);

    /**
     * 과제 조회
     * @param id 조회할 과제 ID
     * @return 과제 정보
     */
    AssignmentDTO getAssignment(Long id);


    /**
     * 과제 삭제
     * @param id 삭제할 과제 ID
     */
    void deleteAssignment(Long id);

    /**
     * 클래스별 과제 목록 조회
     * @param classId 클래스 ID
     * @return 과제 목록
     */
    List<AssignmentDTO> getAssignmentsByClassId(Long classId);

    /**
     * 클래스별 과제 목록 조회 (특정 사용자의 제출 상태 포함)
     * @param classId 클래스 ID
     * @param userEmail 사용자 이메일
     * @return 과제 목록 (사용자 제출 상태 포함)
     */
    List<AssignmentDTO> getAssignmentsByClassIdWithUserStatus(Long classId, String userEmail);

    /**
     * 과제에 첨부파일 추가
     * @param assignmentId 과제 ID
     * @param attachment 첨부할 파일
     */
    void addAttachmentFile(Long assignmentId, Attachment attachment);

    /**
     * 첨부파일 포함 과제 조회
     * @param id 조회할 과제 ID
     * @return 첨부파일 포함 과제 정보
     */
    AssignmentDTO getAssignmentWithAttachmentFiles(Long id);

    /**
     * 제출 현황 포함 과제 조회
     * @param id 조회할 과제 ID
     * @return 제출 현황 포함 과제 정보
     */
    AssignmentDTO getAssignmentWithSubmissionStatuses(Long id);

    /**
     * 첨부파일과 제출 현황을 모두 포함한 과제 조회
     * @param id 조회할 과제 ID
     * @return 모든 세부사항 포함 과제 정보
     */
    AssignmentDTO getAssignmentWithAllDetails(Long id);

    /**
     * 과제 복원
     * @param id 복원할 과제 ID
     */
    void restoreAssignment(Long id);
}