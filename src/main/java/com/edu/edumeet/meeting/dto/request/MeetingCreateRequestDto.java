package com.edu.edumeet.meeting.dto.request;


import com.edu.edumeet.meeting.domain.SessionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingCreateRequestDto {
    private String title;
    private String description;
    private Long classId;

    /**
     * 방송 형태. 비워 두면 화상강의다. (#76)
     *
     * <p>이 필드가 없어서 <b>API 로 만든 모든 세션이 화상강의였다.</b>
     * 세션 타입별 정책도, 오디오 방송도, HLS 도 만들어 두고 도달할 수 없었다.
     *
     * <p>기본값을 두는 이유는 기존 클라이언트가 이 필드를 보내지 않기 때문이다.
     * 필수로 만들면 <b>지금 돌고 있는 화상강의가 전부 깨진다.</b>
     */
    private SessionType sessionType;

    /** 요청한 형태. 없으면 화상강의로 본다. */
    public SessionType sessionTypeOrDefault() {
        return sessionType == null ? SessionType.INTERACTIVE : sessionType;
    }
}
