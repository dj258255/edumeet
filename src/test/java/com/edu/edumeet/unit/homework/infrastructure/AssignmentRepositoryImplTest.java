package com.edu.edumeet.homework.infrastructure;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.homework.domain.Assignment;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentRepositoryImpl 테스트")
class AssignmentRepositoryImplTest {

    @InjectMocks
    private AssignmentRepositoryImpl assignmentRepository;

    @Mock
    private AssignmentJpaRepository assignmentJpaRepository;

    private Assignment assignmentWithFiles;
    private Assignment assignmentWithoutFiles;
    private AssignmentJpaEntity assignmentEntityWithFiles;
    private AssignmentJpaEntity assignmentEntityWithoutFiles;

    @BeforeEach
    void setUp() {
        System.out.println("[DEBUG_LOG] 테스트 데이터 초기화 시작");
        
        // 파일이 있는 과제
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

        assignmentWithFiles = Assignment.builder()
                .id(1L)
                .title("파일 포함 과제")
                .description("참고자료가 첨부된 과제입니다")
                .classId(1L)
                .createdById(10L)
                .createdByName("김선생")
                .attachmentFiles(Arrays.asList(testFile))
                .regDate(LocalDateTime.now())
                .modDate(LocalDateTime.now())
                .build();

        // 파일이 없는 과제
        assignmentWithoutFiles = Assignment.builder()
                .id(2L)
                .title("파일 없는 과제")
                .description("별도의 첨부파일 없이 진행하는 과제입니다")
                .classId(1L)
                .createdById(10L)
                .createdByName("김선생")
                .attachmentFiles(Arrays.asList()) // 빈 리스트
                .regDate(LocalDateTime.now())
                .modDate(LocalDateTime.now())
                .build();

        // JPA Entity
        assignmentEntityWithFiles = AssignmentJpaEntity.fromDomain(assignmentWithFiles);
        assignmentEntityWithoutFiles = AssignmentJpaEntity.fromDomain(assignmentWithoutFiles);
        
        System.out.println("[DEBUG_LOG] 테스트 데이터 초기화 완료");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 파일이 포함된 과제를 저장할 수 있다")
    void saveAssignmentWithFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 파일 포함 과제 저장");
        System.out.println("[DEBUG_LOG] 저장할 과제 제목: " + assignmentWithFiles.getTitle());
        System.out.println("[DEBUG_LOG] 첨부파일 개수: " + assignmentWithFiles.getAttachmentFiles().size());

        when(assignmentJpaRepository.save(any(AssignmentJpaEntity.class)))
                .thenReturn(assignmentEntityWithFiles);

        // When
        Assignment savedAssignment = assignmentRepository.save(assignmentWithFiles);

        // Then
        assertThat(savedAssignment).isNotNull();
        assertThat(savedAssignment.getId()).isEqualTo(1L);
        assertThat(savedAssignment.getTitle()).isEqualTo("파일 포함 과제");
        assertThat(savedAssignment.getAttachmentFiles()).hasSize(1);
        assertThat(savedAssignment.getAttachmentFiles().get(0).getFileName()).isEqualTo("guide.pdf");

        verify(assignmentJpaRepository).save(any(AssignmentJpaEntity.class));
        
        System.out.println("[DEBUG_LOG] 파일 포함 과제 저장 성공, ID: " + savedAssignment.getId());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 파일이 없는 과제를 저장할 수 있다")
    void saveAssignmentWithoutFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 파일 없는 과제 저장");
        System.out.println("[DEBUG_LOG] 저장할 과제 제목: " + assignmentWithoutFiles.getTitle());
        System.out.println("[DEBUG_LOG] 첨부파일 개수: " + assignmentWithoutFiles.getAttachmentFiles().size());

        when(assignmentJpaRepository.save(any(AssignmentJpaEntity.class)))
                .thenReturn(assignmentEntityWithoutFiles);

        // When
        Assignment savedAssignment = assignmentRepository.save(assignmentWithoutFiles);

        // Then
        assertThat(savedAssignment).isNotNull();
        assertThat(savedAssignment.getId()).isEqualTo(2L);
        assertThat(savedAssignment.getTitle()).isEqualTo("파일 없는 과제");
        assertThat(savedAssignment.getAttachmentFiles()).isEmpty();

        verify(assignmentJpaRepository).save(any(AssignmentJpaEntity.class));
        
        System.out.println("[DEBUG_LOG] 파일 없는 과제 저장 성공, ID: " + savedAssignment.getId());
    }

    @Test
    @DisplayName("[DEBUG_LOG] ID로 과제를 조회할 수 있다")
    void findById() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: ID로 과제 조회");
        
        when(assignmentJpaRepository.findByIdAndDeletedAtIsNull(eq(1L)))
                .thenReturn(Optional.of(assignmentEntityWithFiles));

        // When
        Optional<Assignment> foundAssignment = assignmentRepository.findById(1L);

        // Then
        assertThat(foundAssignment).isPresent();
        assertThat(foundAssignment.get().getId()).isEqualTo(1L);
        assertThat(foundAssignment.get().getTitle()).isEqualTo("파일 포함 과제");
        assertThat(foundAssignment.get().getAttachmentFiles()).hasSize(1);

        verify(assignmentJpaRepository).findByIdAndDeletedAtIsNull(eq(1L));
        
        System.out.println("[DEBUG_LOG] 과제 조회 성공, 제목: " + foundAssignment.get().getTitle());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 클래스별 과제 목록을 조회할 수 있다")
    void findByClassIdOrderByRegDateDesc() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 클래스별 과제 목록 조회");
        
        List<AssignmentJpaEntity> entities = Arrays.asList(
                assignmentEntityWithFiles,
                assignmentEntityWithoutFiles
        );

        when(assignmentJpaRepository.findByClassIdOrderByRegDateDesc(eq(1L)))
                .thenReturn(entities);

        System.out.println("[DEBUG_LOG] Mock 설정 완료, 조회할 클래스 ID: 1");

        // When
        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(1L);

        // Then
        assertThat(assignments).hasSize(2);
        assertThat(assignments.get(0).getId()).isEqualTo(1L);
        assertThat(assignments.get(0).getTitle()).isEqualTo("파일 포함 과제");
        assertThat(assignments.get(0).getAttachmentFiles()).hasSize(1);
        assertThat(assignments.get(1).getId()).isEqualTo(2L);
        assertThat(assignments.get(1).getTitle()).isEqualTo("파일 없는 과제");
        assertThat(assignments.get(1).getAttachmentFiles()).isEmpty();

        verify(assignmentJpaRepository).findByClassIdOrderByRegDateDesc(eq(1L));
        
        System.out.println("[DEBUG_LOG] 클래스별 과제 목록 조회 성공, 조회된 과제 개수: " + assignments.size());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 첨부파일 포함 과제를 조회할 수 있다")
    void findByIdWithAttachmentFiles() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 첨부파일 포함 과제 조회");
        
        when(assignmentJpaRepository.findByIdWithAttachmentFiles(eq(1L)))
                .thenReturn(Optional.of(assignmentEntityWithFiles));

        // When
        Optional<Assignment> foundAssignment = assignmentRepository.findByIdWithAttachmentFiles(1L);

        // Then
        assertThat(foundAssignment).isPresent();
        assertThat(foundAssignment.get().getId()).isEqualTo(1L);
        assertThat(foundAssignment.get().getAttachmentFiles()).hasSize(1);
        assertThat(foundAssignment.get().getAttachmentFiles().get(0).getFileName()).isEqualTo("guide.pdf");
        assertThat(foundAssignment.get().getAttachmentFiles().get(0).getUuid()).isEqualTo("test-uuid-123");

        verify(assignmentJpaRepository).findByIdWithAttachmentFiles(eq(1L));
        
        System.out.println("[DEBUG_LOG] 첨부파일 포함 과제 조회 성공, 첨부파일 개수: " + 
                foundAssignment.get().getAttachmentFiles().size());
    }

    @Test
    @DisplayName("[DEBUG_LOG] 논리적 삭제를 수행할 수 있다")
    void deleteById() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 논리적 삭제");
        
        AssignmentJpaEntity entityToDelete = assignmentEntityWithFiles;
        when(assignmentJpaRepository.findById(eq(1L)))
                .thenReturn(Optional.of(entityToDelete));
        when(assignmentJpaRepository.save(any(AssignmentJpaEntity.class)))
                .thenReturn(entityToDelete);

        System.out.println("[DEBUG_LOG] 삭제할 과제 ID: 1");

        // When
        assignmentRepository.deleteById(1L);

        // Then
        verify(assignmentJpaRepository).findById(eq(1L));
        verify(assignmentJpaRepository).save(any(AssignmentJpaEntity.class));
        
        System.out.println("[DEBUG_LOG] 논리적 삭제 완료");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 논리적 삭제된 과제를 복원할 수 있다")
    void restoreById() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 과제 복원");
        
        AssignmentJpaEntity deletedEntity = assignmentEntityWithFiles;
        // 삭제된 상태로 설정
        deletedEntity.delete();
        
        when(assignmentJpaRepository.findById(eq(1L)))
                .thenReturn(Optional.of(deletedEntity));
        when(assignmentJpaRepository.save(any(AssignmentJpaEntity.class)))
                .thenReturn(deletedEntity);

        System.out.println("[DEBUG_LOG] 복원할 과제 ID: 1");

        // When
        boolean restored = assignmentRepository.restoreById(1L);

        // Then
        assertThat(restored).isTrue();
        
        verify(assignmentJpaRepository).findById(eq(1L));
        verify(assignmentJpaRepository).save(any(AssignmentJpaEntity.class));
        
        System.out.println("[DEBUG_LOG] 과제 복원 성공");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 삭제된 과제 포함하여 조회할 수 있다")
    void findByIdIncludeDeleted() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 삭제된 과제 포함 조회");
        
        when(assignmentJpaRepository.findById(eq(1L)))
                .thenReturn(Optional.of(assignmentEntityWithFiles));

        // When
        Optional<Assignment> foundAssignment = assignmentRepository.findByIdIncludeDeleted(1L);

        // Then
        assertThat(foundAssignment).isPresent();
        assertThat(foundAssignment.get().getId()).isEqualTo(1L);
        assertThat(foundAssignment.get().getTitle()).isEqualTo("파일 포함 과제");

        verify(assignmentJpaRepository).findById(eq(1L));
        
        System.out.println("[DEBUG_LOG] 삭제된 과제 포함 조회 성공");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 도메인과 엔티티 간 변환이 올바르게 동작한다")
    void domainEntityConversionTest() {
        // Given
        System.out.println("[DEBUG_LOG] 테스트 시작: 도메인-엔티티 변환 테스트");
        
        // When - Domain to Entity 변환
        AssignmentJpaEntity entityFromDomain = AssignmentJpaEntity.fromDomain(assignmentWithFiles);
        System.out.println("[DEBUG_LOG] Domain -> Entity 변환 완료");
        
        // Entity to Domain 변환
        Assignment domainFromEntity = assignmentEntityWithFiles.toDomain();
        System.out.println("[DEBUG_LOG] Entity -> Domain 변환 완료");

        // Then - Domain to Entity 검증
        assertThat(entityFromDomain.getTitle()).isEqualTo(assignmentWithFiles.getTitle());
        assertThat(entityFromDomain.getDescription()).isEqualTo(assignmentWithFiles.getDescription());
        assertThat(entityFromDomain.getClassId()).isEqualTo(assignmentWithFiles.getClassId());
        assertThat(entityFromDomain.getCreatedById()).isEqualTo(assignmentWithFiles.getCreatedById());
        assertThat(entityFromDomain.getCreatedByName()).isEqualTo(assignmentWithFiles.getCreatedByName());
        
        // Entity to Domain 검증
        assertThat(domainFromEntity.getId()).isEqualTo(assignmentEntityWithFiles.getId());
        assertThat(domainFromEntity.getTitle()).isEqualTo(assignmentEntityWithFiles.getTitle());
        
        System.out.println("[DEBUG_LOG] 도메인-엔티티 변환 테스트 완료");
    }
}