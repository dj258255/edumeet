package com.edu.edumeet.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners( value = { AuditingEntityListener.class } )
@Getter
public class BaseEntity {

    @CreatedDate
    @Column(name = "regDate" , updatable = false)
    private LocalDateTime regDate;

    @LastModifiedDate
    @Column(name = "modDate")
    private LocalDateTime modDate;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    /**
     * 엔티티를 논리적으로 삭제 처리
     * deletedAt 필드에 현재 시간을 설정
     */
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 삭제된 엔티티를 복원
     * deletedAt 필드를 null로 설정
     */
    public void restoreDeleted() {
        this.deletedAt = null;
    }

    /**
     * 엔티티가 삭제되었는지 확인
     * @return 삭제 여부
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}