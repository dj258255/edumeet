package com.edu.edumeet.meeting.controller;

import com.edu.edumeet.meeting.dto.response.MeetingStatusResponseDto;
import com.edu.edumeet.meeting.service.MeetingService;
import com.edu.edumeet.member.domain.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 단건 상태. (#124)
 *
 * <p>방송 시청 화면이 {@code hlsPlaylistUrl} 을 폴링하는 계약이다.
 * 이전에는 프론트만 이 주소를 부르고 백엔드에는 엔드포인트가 없었다.
 */
@RestController
@RequestMapping("/api/v1/meeting")
@RequiredArgsConstructor
public class MeetingStatusController {

    private final MeetingService meetingService;

    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingStatusResponseDto> get(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingStatus(member.getEmail(), meetingId));
    }
}
