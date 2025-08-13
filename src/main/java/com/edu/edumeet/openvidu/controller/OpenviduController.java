package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.openvidu.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.openvidu.dto.response.MeetingCreateResponseDto;
import com.edu.edumeet.openvidu.service.OpenviduService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/room/{roomName}")
    public ResponseEntity<Map<String, Object>> getRoomInfo(@PathVariable String roomName) {
        Map<String, Object> roomInfo = openviduService.getRoomInfo(roomName);

        if (roomInfo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(roomInfo);
    }
}