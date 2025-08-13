package com.edu.edumeet.classroom.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.openvidu.domain.Meeting;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "class_room")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassRoom extends BaseEntity {
    @Id
    @GeneratedValue
    @Column(name = "class_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToMany(mappedBy = "classRoom", fetch = FetchType.LAZY)
    private List<ClassMember> classMember;

    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Tag> tags;

    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Meeting> meetings;

    // 썸네일 관련 필드들 (별도 엔티티 대신 직접 포함)
    @Column(name = "thumbnail_uuid")
    private String thumbnailUuid;
    
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    
    @Column(name = "original_url")
    private String originalUrl;

    private String title;

    private String description;

    @Column(name = "participant_limit")
    private int participantLimit;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean isDeleted = false;

    public void markAsDeleted() {
        this.isDeleted = true;
    }
    
    /**
     * 썸네일 정보 업데이트
     */
    public void updateThumbnail(String thumbnailUuid, String thumbnailUrl, String originalUrl) {
        this.thumbnailUuid = thumbnailUuid;
        this.thumbnailUrl = thumbnailUrl;
        this.originalUrl = originalUrl;
    }
    
    /**
     * 썸네일 존재 여부 확인
     */
    public boolean hasThumbnail() {
        return thumbnailUuid != null && !thumbnailUuid.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassRoom classRoom = (ClassRoom) o;
        return Objects.equals(id, classRoom.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
