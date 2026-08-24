package com.edu.edumeet.meeting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.edu.edumeet.meeting.domain.SessionType;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClassMeetingInfoResponseDto {
    private Long meetingId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String s3url;
    private SessionType sessionType;
    private String hlsPlaylistUrl;
    private boolean broadcasting;
}
