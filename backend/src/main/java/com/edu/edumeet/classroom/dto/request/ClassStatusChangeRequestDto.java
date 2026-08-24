package com.edu.edumeet.classroom.dto.request;

import com.edu.edumeet.classroom.domain.InviteStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ClassStatusChangeRequestDto {
    private Long classId;
    private InviteStatus status;

    @Builder
    public ClassStatusChangeRequestDto(Long classId, InviteStatus status) {
        this.classId = classId;
        this.status = status;
    }
}
