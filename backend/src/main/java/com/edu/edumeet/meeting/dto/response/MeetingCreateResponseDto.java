package com.edu.edumeet.meeting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingCreateResponseDto {
    private String title;
    private String email;
    private Long meetingId;
}
