package com.edu.edumeet.meeting.dto.request;


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
}
