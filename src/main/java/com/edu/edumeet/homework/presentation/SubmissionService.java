package com.edu.edumeet.homework.presentation;

import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;

import java.util.List;

/**
 * 제출물 애플리케이션 서비스 인터페이스
 * 도메인과 프레젠테이션 계층 사이의 조정자 역할
 */
public interface SubmissionService {

    /**
     * Submission 도메인 객체를 SubmissionDTO로 변환
     */
    default SubmissionDTO domainToDto(Submission submission, AttachmentAdapter attachmentAdapter) {
        return SubmissionDTO.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignmentId())
                .classMemberEmail(submission.getClassMemberEmail())
                .classMemberName(submission.getClassMemberName())
                .content(submission.getContent())
                .status(submission.getStatus())
                .submissionFiles(attachmentAdapter.toFileUploadDTOList(submission.getSubmissionFiles()))
                .regDate(submission.getRegDate())
                .modDate(submission.getModDate())
                .build();
    }

    /**
     * Submission 도메인 객체를 SubmissionDTO로 변환 (과제 제목 포함)
     */
    default SubmissionDTO domainToDtoWithAssignmentTitle(Submission submission, String assignmentTitle, AttachmentAdapter attachmentAdapter) {
        return SubmissionDTO.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignmentId())
                .assignmentTitle(assignmentTitle)
                .classMemberEmail(submission.getClassMemberEmail())
                .classMemberName(submission.getClassMemberName())
                .content(submission.getContent())
                .status(submission.getStatus())
                .submissionFiles(attachmentAdapter.toFileUploadDTOList(submission.getSubmissionFiles()))
                .regDate(submission.getRegDate())
                .modDate(submission.getModDate())
                .build();
    }

    /**
     * SubmissionCreateDTO를 Submission 도메인 객체로 변환
     */
    default Submission createDtoToDomain(SubmissionCreateDTO dto) {
        return Submission.builder()
                .assignmentId(dto.getAssignmentId())
                .classMemberEmail(dto.getClassMemberEmail())
                .classMemberName(dto.getClassMemberName())
                .content(dto.getContent())
                .submissionFiles(dto.getAttachmentFiles() != null ? dto.getAttachmentFiles() : new java.util.ArrayList<>())
                .build();
    }

    /**
     * 제출물 제출
     * @param submissionCreateDTO 제출할 제출물 정보
     * @return 제출된 제출물의 ID
     */
    Long submitAssignment(SubmissionCreateDTO submissionCreateDTO);

    /**
     * 제출물 조회
     * @param id 조회할 제출물 ID
     * @return 제출물 정보
     */
    SubmissionDTO getSubmission(Long id);


    /**
     * 제출물 삭제
     * @param id 삭제할 제출물 ID
     */
    void deleteSubmission(Long id);

    /**
     * 과제별 제출물 목록 조회
     * @param assignmentId 과제 ID
     * @return 제출물 목록
     */
    List<SubmissionDTO> getSubmissionsByAssignmentId(Long assignmentId);

    /**
     * 학생별 제출물 목록 조회
     * @param classMemberEmail 학생 이메일
     * @return 제출물 목록
     */
    List<SubmissionDTO> getSubmissionsByClassMemberEmail(String classMemberEmail);

    /**
     * 특정 과제의 특정 학생 제출물 조회
     * @param assignmentId 과제 ID
     * @param classMemberEmail 학생 이메일
     * @return 제출물 정보
     */
    SubmissionDTO getSubmissionByAssignmentAndClassMember(Long assignmentId, String classMemberEmail);

    /**
     * 제출물에 파일 추가
     * @param submissionId 제출물 ID
     * @param attachment 추가할 파일
     */
    void addSubmissionFile(Long submissionId, Attachment attachment);

    /**
     * 첨부파일 포함 제출물 조회
     * @param id 조회할 제출물 ID
     * @return 첨부파일 포함 제출물 정보
     */
    SubmissionDTO getSubmissionWithFiles(Long id);

    /**
     * 제출물 복원
     * @param id 복원할 제출물 ID
     */
    void restoreSubmission(Long id);
}