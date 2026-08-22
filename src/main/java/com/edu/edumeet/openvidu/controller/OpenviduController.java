package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.openvidu.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.openvidu.dto.response.ClassMeetingInfoResponseDto;
import com.edu.edumeet.openvidu.dto.response.MeetingCreateResponseDto;
import com.edu.edumeet.openvidu.service.OpenviduService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetingroom")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Log4j2
public class OpenviduController {

    private final OpenviduService openviduService;

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> createToken(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody MeetingCreateRequestDto meetingCreateRequestDto) {
        
        Map<String, Object> response = new HashMap<>();
        
        // 인증 확인
        if (member == null) {
            log.error("❌ 인증 실패 - SecurityMember가 null입니다");
            response.put("success", false);
            response.put("error", "인증이 필요합니다. 로그인 후 다시 시도해주세요.");
            return ResponseEntity.status(401).body(response);
        }
        
        // SecurityMember 객체 상세 정보 로깅
        log.info("🔍 SecurityMember 상세 정보:");
        log.info("  - memberId: {}", member.getMemberId());
        log.info("  - email: {}", member.getEmail());
        log.info("  - member 객체: {}", member.getMember());
        log.info("  - member.getId(): {}", member.getMember().getId());
        
        log.info("🔑 토큰 생성 요청 - member: {}, title: {}, classId: {}", 
                member.getEmail(), meetingCreateRequestDto.getTitle(), meetingCreateRequestDto.getClassId());
        
        try {
            // 미팅 생성
            openviduService.create(member.getMemberId(), meetingCreateRequestDto);
            log.info("✅ 미팅 생성 완료");
            
            // 토큰 생성
            Map<String, Object> tokenResult = openviduService.createToken(meetingCreateRequestDto.getTitle(), member.getEmail());
            log.info("✅ 토큰 생성 완료 - roomName: {}", meetingCreateRequestDto.getTitle());
            
            return ResponseEntity.ok(tokenResult);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ 토큰 생성 실패 - 잘못된 요청: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            log.error("❌ 토큰 생성 실패 - 서버 오류: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "토큰 생성 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
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

    /**
     * 세션에서 나간다. 정원을 반환한다.
     *
     * <p>이 엔드포인트가 없어서 {@code leaveSession} 이 호출되는 곳이 아예 없었다. (#23)
     * 클라이언트가 호출하지 않고 브라우저를 닫는 경우는
     * {@link LiveKitWebhookController} 가 처리한다.
     */
    @PostMapping("/{meetingId}/leave")
    public ResponseEntity<Map<String, String>> leave(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        openviduService.leaveSession(meetingId, member.getEmail());
        return ResponseEntity.ok(Map.of("message", "세션에서 나갔습니다."));
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