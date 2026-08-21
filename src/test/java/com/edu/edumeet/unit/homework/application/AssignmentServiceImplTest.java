package com.edu.edumeet.unit.homework.application;

import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.repository.SubmissionRepository;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.service.AssignmentService;
import com.edu.edumeet.homework.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.dto.AssignmentDTO;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
import com.edu.edumeet.attachment.presentation.dto.AttachmentDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentService 테스트")
class AssignmentServiceTest {

    @InjectMocks
    private AssignmentService assignmentService;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ClassMemberRepository classMemberRepository;

    @Mock
    private AttachmentAdapter attachmentAdapter;
    
    @Mock
    private com.edu.edumeet.homework.repository.SubmissionRepository submissionRepository;

    private AssignmentCreateDTO createDTOWithFiles;
    private AssignmentCreateDTO createDTOWithoutFiles;
    private Assignment savedAssignment;
    private List<ClassMember> classMembers;

    @BeforeEach
    void setUp() {
        System.out.println("[DEBUG_LOG] 테스트 데이터 초기화 시작");
        
        // 파일이 포함된 과제 생성 DTO
        Attachment testFile = Attachment.builder()
                .uuid("test-uuid-123")
                .fileName("guide.pdf")
                .ord(1)
                .img(false)
                .fileSize(1024000L)
                .contentType("application/pdf")
                .uploadedBy("김선생")
                .referenceId(1L)
                .uploadedAt(LocalDateTime.now())
                .build();

        // 테스트용 AttachmentDTO 생성
        AttachmentDTO testFileDTO = AttachmentDTO.builder()
                .uuid("test-uuid-123")
                .fileName("guide.pdf")
                .ord(1)
                .img(false)
                .domain("assignment")
                .referenceId(1L)
                .build();

        createDTOWithFiles = AssignmentCreateDTO.builder()
                .title("파일 포함 과제")
                .description("참고자료가 첨부된 과제입니다")
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .attachmentFiles(Arrays.asList(testFileDTO)) // 실제 AttachmentDTO 리스트 제공
                .build();

        // 파일이 없는 과제 생성 DTO
        createDTOWithoutFiles = AssignmentCreateDTO.builder()
                .title("파일 없는 과제")
                .description("별도의 첨부파일 없이 진행하는 과제입니다")
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .attachmentFiles(null)
                .build();

        // 저장된 과제
        savedAssignment = Assignment.builder()
                .id(1234L)
                .title("파일 포함 과제")
                .description("참고자료가 첨부된 과제입니다")
                .classId(1L)
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .build();

        // 클래스 멤버들
        Member teacher = Member.builder()
                .id(10L)
                .nickname("김선생")
                .email("teacher@example.com")
                .build();

        Member student1 = Member.builder()
                .id(20L)
                .nickname("김학생1")
                .email("student1@example.com")
                .build();

        Member student2 = Member.builder()
                .id(21L)
                .nickname("김학생2")
                .email("student2@example.com")
                .build();

        classMembers = Arrays.asList(
                ClassMember.builder().member(teacher).build(),
                ClassMember.builder().member(student1).build(),
                ClassMember.builder().member(student2).build()
        );

        System.out.println("[DEBUG_LOG] 테스트 데이터 초기화 완료");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 선생님이 파일과 함께 과제를 생성할 수 있다")
    void createAssignmentWithFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 파일 포함 과제 생성");
        System.out.println("[DEBUG_LOG] 첨부파일 개수: " + createDTOWithFiles.getAttachmentFiles().size());
        
        when(assignmentRepository.save(any(Assignment.class)))
                .thenReturn(savedAssignment);
        when(classMemberRepository.findAllByClassRoomId(eq(1L)))
                .thenReturn(classMembers);

        // When
        Long assignmentId = assignmentService.createAssignment(createDTOWithFiles, 1L);

        // Then
        assertThat(assignmentId).isEqualTo(1234L);
        
        verify(assignmentRepository, atLeastOnce()).save(any(Assignment.class));
        verify(classMemberRepository, atLeastOnce()).findAllByClassRoomId(eq(1L));
        
        System.out.println("[DEBUG_LOG] 파일 포함 과제 생성 성공, ID: " + assignmentId);
    }

    @Test
    @DisplayName("[DEBUG_LOG] 선생님이 파일 없이 과제를 생성할 수 있다")
    void createAssignmentWithoutFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 파일 없는 과제 생성");
        System.out.println("[DEBUG_LOG] 첨부파일: " + createDTOWithoutFiles.getAttachmentFiles());
        
        Assignment assignmentWithoutFiles = Assignment.builder()
                .id(5678L)
                .title("파일 없는 과제")
                .description("별도의 첨부파일 없이 진행하는 과제입니다")
                .classId(1L)
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .build();

        when(assignmentRepository.save(any(Assignment.class)))
                .thenReturn(assignmentWithoutFiles);
        when(classMemberRepository.findAllByClassRoomId(eq(1L)))
                .thenReturn(classMembers);

        // When
        Long assignmentId = assignmentService.createAssignment(createDTOWithoutFiles, 1L);

        // Then
        assertThat(assignmentId).isEqualTo(5678L);
        
        verify(assignmentRepository, atLeastOnce()).save(any(Assignment.class));
        verify(classMemberRepository, atLeastOnce()).findAllByClassRoomId(eq(1L));
        
        System.out.println("[DEBUG_LOG] 파일 없는 과제 생성 성공, ID: " + assignmentId);
    }

    @Test
    @DisplayName("[DEBUG_LOG] 과제 조회 시 첨부파일이 함께 조회된다")
    void getAssignmentWithAttachmentFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 첨부파일 포함 과제 조회");
        
        // AttachmentAdapter mock 설정
        AttachmentDTO testFileDTO = AttachmentDTO.builder()
                .uuid("test-uuid-123")
                .fileName("guide.pdf")
                .ord(1)
                .img(false)
                .domain("assignment")
                .referenceId(1234L)
                .s3Url("https://s3.amazonaws.com/test-bucket/assignment/test-uuid-123_guide.pdf")
                .build();
        
        when(attachmentAdapter.toFileUploadDTOList(any()))
                .thenReturn(Arrays.asList(testFileDTO));
        
        when(assignmentRepository.findByIdAndDeletedAtIsNull(eq(1234L)))
                .thenReturn(Optional.of(savedAssignment));

        // When
        AssignmentDTO result = assignmentService.getAssignment(1234L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1234L);
        assertThat(result.getTitle()).isEqualTo("파일 포함 과제");
        assertThat(result.getAttachmentFiles()).hasSize(1);
        assertThat(result.getAttachmentFiles().get(0).getFileName()).isEqualTo("guide.pdf");
        assertThat(result.getAttachmentFiles().get(0).getUuid()).isEqualTo("test-uuid-123");
        
        verify(assignmentRepository).findByIdAndDeletedAtIsNull(eq(1234L));
        
        System.out.println("[DEBUG_LOG] 첨부파일 포함 과제 조회 성공, 첨부파일 개수: " + result.getAttachmentFiles().size());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 과제에 첨부파일을 추가할 수 있다")
    void addAttachmentFile() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 과제 첨부파일 추가");
        
        Attachment newFile = Attachment.builder()
                .uuid("new-uuid-456")
                .fileName("additional.pdf")
                .ord(2)
                .img(false)
                .fileSize(2048000L)
                .contentType("application/pdf")
                .uploadedBy("김선생")
                .referenceId(1234L)
                .uploadedAt(LocalDateTime.now())
                .build();

        savedAssignment.addAttachmentFile(newFile);

        when(assignmentRepository.findByIdAndDeletedAtIsNull(eq(1234L)))
                .thenReturn(Optional.of(savedAssignment));
        when(assignmentRepository.save(any(Assignment.class)))
                .thenReturn(savedAssignment);

        System.out.println("[DEBUG_LOG] 추가할 파일명: " + newFile.getFileName());

        // When
        assignmentService.addAttachmentFile(1234L, newFile);

        // Then
        verify(assignmentRepository).findByIdAndDeletedAtIsNull(eq(1234L));
        verify(assignmentRepository).save(any(Assignment.class));
        
        System.out.println("[DEBUG_LOG] 첨부파일 추가 성공");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 클래스별 과제 목록을 조회할 수 있다")
    void getAssignmentsByClassId() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 클래스별 과제 목록 조회");
        
        // AttachmentAdapter mock 설정 (빈 리스트 처리)
        when(attachmentAdapter.toFileUploadDTOList(any()))
                .thenReturn(Arrays.asList());
        
        // 목록 조회는 과제별 반복 호출이 아니라 IN 절 배치 조회를 사용한다 (#4)
        when(submissionRepository.findByAssignmentIdsWithSubmissionFiles(any()))
                .thenReturn(Arrays.asList());
        
        Assignment assignment1 = Assignment.builder()
                .id(1L)
                .title("첫 번째 과제")
                .classId(1L)
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .build();

        Assignment assignment2 = Assignment.builder()
                .id(2L)
                .title("두 번째 과제")
                .classId(1L)
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .build();

        List<Assignment> assignments = Arrays.asList(assignment1, assignment2);

        when(assignmentRepository.findByClassIdOrderByRegDateDesc(eq(1L)))
                .thenReturn(assignments);

        System.out.println("[DEBUG_LOG] Mock 설정 완료, 과제 개수: " + assignments.size());

        // When
        List<AssignmentDTO> result = assignmentService.getAssignmentsByClassId(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("첫 번째 과제");
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getTitle()).isEqualTo("두 번째 과제");
        
        verify(assignmentRepository).findByClassIdOrderByRegDateDesc(eq(1L));
        
        System.out.println("[DEBUG_LOG] 클래스별 과제 목록 조회 성공, 조회된 과제 개수: " + result.size());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 도메인 변환이 올바르게 동작한다")
    void domainConversionTest() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 도메인 변환 테스트");
        
        // AttachmentDTO를 Attachment로 변환하는 mock 설정
        Attachment testAttachment = Attachment.builder()
                .uuid("test-uuid-123")
                .fileName("guide.pdf")
                .ord(1)
                .img(false)
                .build();
        
        when(attachmentAdapter.fromFileUploadDTOList(any()))
                .thenReturn(Arrays.asList(testAttachment));
        
        // AttachmentAdapter mock 설정 (Domain -> DTO 변환용)
        AttachmentDTO testFileDTO = AttachmentDTO.builder()
                .uuid("test-uuid-123")
                .fileName("guide.pdf")
                .ord(1)
                .img(false)
                .domain("assignment")
                .referenceId(1234L)
                .s3Url("https://s3.amazonaws.com/test-bucket/assignment/test-uuid-123_guide.pdf")
                .build();
        
        when(attachmentAdapter.toFileUploadDTOList(any()))
                .thenReturn(Arrays.asList(testFileDTO));
        
        // When - DTO to Domain 변환
        Assignment domainFromDTO = assignmentService.createDtoToDomain(createDTOWithFiles, 1L, attachmentAdapter);
        System.out.println("[DEBUG_LOG] DTO -> Domain 변환 완료");
        
        // Domain to DTO 변환
        AssignmentDTO dtoFromDomain = assignmentService.domainToDto(savedAssignment, attachmentAdapter);
        System.out.println("[DEBUG_LOG] Domain -> DTO 변환 완료");

        // Then - DTO to Domain 검증
        assertThat(domainFromDTO.getTitle()).isEqualTo(createDTOWithFiles.getTitle());
        assertThat(domainFromDTO.getDescription()).isEqualTo(createDTOWithFiles.getDescription());
        assertThat(domainFromDTO.getClassId()).isEqualTo(1L);
        assertThat(domainFromDTO.getCreatedByEmail()).isEqualTo(createDTOWithFiles.getCreatedByEmail());
        assertThat(domainFromDTO.getCreatedByName()).isEqualTo(createDTOWithFiles.getCreatedByName());
        assertThat(domainFromDTO.getAttachmentFiles()).hasSize(1);
        
        // Domain to DTO 검증
        assertThat(dtoFromDomain.getId()).isEqualTo(savedAssignment.getId());
        assertThat(dtoFromDomain.getTitle()).isEqualTo(savedAssignment.getTitle());
        assertThat(dtoFromDomain.getAttachmentFiles()).hasSize(1);
        
        System.out.println("[DEBUG_LOG] 도메인 변환 테스트 완료 - 파일 포함 여부 확인됨");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 파일이 null인 경우에도 빈 리스트로 처리된다")
    void handleNullAttachmentFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: null 파일 처리 테스트");
        
        // When - null 첨부파일로 도메인 변환
        Assignment domainFromNullFiles = assignmentService.createDtoToDomain(createDTOWithoutFiles, 1L, attachmentAdapter);
        
        // Then
        assertThat(domainFromNullFiles.getAttachmentFiles()).isNotNull();
        assertThat(domainFromNullFiles.getAttachmentFiles()).isEmpty();
        
        System.out.println("[DEBUG_LOG] null 파일 처리 완료 - 빈 리스트로 변환됨");
    }
}