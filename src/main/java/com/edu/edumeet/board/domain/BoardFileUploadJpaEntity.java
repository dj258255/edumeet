package com.edu.edumeet.board.domain;

import com.edu.edumeet.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 게시판과 파일 업로드 간의 연관관계를 관리하는 엔티티
 * Board 도메인에서 FileUpload를 참조할 수 있도록 함
 */
@Entity
@Table(name = "board_file_upload")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "boardJpaEntity") // 순환 참조 방지
public class BoardFileUploadJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private BoardJpaEntity boardJpaEntity;

    @Column(nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String fileName;

    private int ord;

    @Column(nullable = false)
    private boolean img;

    private Long fileSize;

    private String contentType;

    private String uploadedBy;

    @Column(name = "reference_id")
    private Long referenceId;

    // 엔티티 비교를 위한 equals와 hashCode 메서드
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoardFileUploadJpaEntity that = (BoardFileUploadJpaEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}