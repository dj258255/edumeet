package com.edu.edumeet.meeting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingFileResponseDto {

    private Long meetingId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String downloadUrl;
    private boolean available;
    private String message;

    public static MeetingFileResponseDto of(Long meetingId, String fileName, Long fileSize,
                                            String contentType, String downloadUrl, boolean available) {
        return MeetingFileResponseDto.builder()
                .meetingId(meetingId)
                .fileName(fileName)
                .fileSize(fileSize)
                .contentType(contentType)
                .downloadUrl(downloadUrl)
                .available(available)
                .build();
    }

    public static MeetingFileResponseDto unavailable(Long meetingId, String message) {
        return MeetingFileResponseDto.builder()
                .meetingId(meetingId)
                .fileName(null)
                .fileSize(0L)
                .contentType(null)
                .downloadUrl(null)
                .available(false)
                .message(message)
                .build();
    }
}