package com.edu.edumeet.homework.presentation;

import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.attachment.domain.Attachment;

import java.util.ArrayList;
import java.util.List;

/**
 * 과제 애플리케이션 서비스 인터페이스
 * 도메인과 프레젠테이션 계층 사이의 조정자 역할
 */
public interface AssignmentService {

    /**
     * Assignment 도메인 객체를 AssignmentDTO로 변환
     */
    default AssignmentDTO domainToDto(Assignment assignment) {
        return AssignmentDTO.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classId(assignment.getClassId())
                .createdById(assignment.getCreatedById())
                .createdByName(assignment.getCreatedByName())
                .attachmentFiles(assignment.getAttachmentFiles())
                .studentSubmissionStatuses(assignment.getStudentSubmissionStatuses())
                .regDate(assignment.getRegDate())
                .modDate(assignment.getModDate())
                .build();
    }

    /**
     * AssignmentCreateDTO를 Assignment 도메인 객체로 변환
     */
    default Assignment createDtoToDomain(AssignmentCreateDTO dto) {
        return Assignment.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .classId(dto.getClassId())
                .createdById(dto.getCreatedById())
                .createdByName(dto.getCreatedByName())
                .attachmentFiles(dto.getAttachmentFiles() != null ? dto.getAttachmentFiles() : new ArrayList<>())
                .build();
    }

    /**
     * 과제 생성
     * @param assignmentCreateDTO 생성할 과제 정보
     * @return 생성된 과제의 ID
     */
    Long createAssignment(AssignmentCreateDTO assignmentCreateDTO);

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