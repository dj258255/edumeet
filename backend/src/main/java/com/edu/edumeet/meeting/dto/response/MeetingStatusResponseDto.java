package com.edu.edumeet.meeting.dto.response;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 방송 시청 화면이 폴링하는 세션 상태. (#124)
 *
 * <p>프론트가 {@code /api/v1/meeting/{meetingId}} 에서 {@code hlsPlaylistUrl} 을 찾고 있었는데
 * 백엔드에는 그 엔드포인트가 없었다. 방송 송출은 살아도 시청 화면이 URL 을 찾을 수 없는 구조였다.
 */
@Getter
@Builder
public class MeetingStatusResponseDto {
    private Long meetingId;
    private Long classId;
    private String title;
    private String description;
    private SessionType sessionType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean broadcasting;
    private String hlsPlaylistUrl;

    public static MeetingStatusResponseDto from(Meeting meeting) {
        return MeetingStatusResponseDto.builder()
                .meetingId(meeting.getId())
                .classId(meeting.getClassRoom().getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .sessionType(meeting.getSessionType())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .broadcasting(meeting.isBroadcasting())
                .hlsPlaylistUrl(meeting.getHlsPlaylistUrl())
                .build();
    }
}
