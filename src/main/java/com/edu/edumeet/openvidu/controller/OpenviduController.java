package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.openvidu.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.openvidu.dto.response.ClassMeetingInfoResponseDto;
import com.edu.edumeet.openvidu.dto.response.MeetingCreateResponseDto;
import com.edu.edumeet.openvidu.service.OpenviduService;
import lombok.RequiredArgsConstructor;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetingroom")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OpenviduController {

    private final OpenviduService openviduService;

    @PostMapping("/token")
    public ResponseEntity<MeetingCreateResponseDto> createToken(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody MeetingCreateRequestDto meetingCreateRequestDto) {
        return ResponseEntity.ok(openviduService.create(member.getMemberId(), meetingCreateRequestDto));
    }

    @PatchMapping("/{meetingId}")
    public ResponseEntity<Map<String, String>> endMeeting(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        openviduService.endMeeting(member.getEmail(), meetingId);
        return ResponseEntity.ok(Map.of(
                "message", "화상강의를 완료하였습니다."
        ));
    }

    @GetMapping("/{classId}")
    public ResponseEntity<List<ClassMeetingInfoResponseDto>> getLiveList(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long classId) {
        return ResponseEntity.ok(openviduService.getMeetingList(member.getEmail(), classId));
    }

    @GetMapping("/room/{roomName}")
    public ResponseEntity<Map<String, Object>> getRoomInfo(@PathVariable String roomName) {
        Map<String, Object> roomInfo = openviduService.getRoomInfo(roomName);

        if (roomInfo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(roomInfo);
    }
}