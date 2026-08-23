package com.edu.edumeet.meeting.hls;

import com.edu.edumeet.member.domain.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 방송 송출 제어. (#75)
 *
 * <p>세션 생성과 분리한 이유 — <b>방송 시작은 세션 시작과 다른 사건이다.</b>
 * 방을 열어 두고 준비하다가 송출만 나중에 켜는 흐름이 정상이고,
 * egress 는 켜는 순간부터 코어를 점유하므로 <b>필요한 만큼만 켜야 한다.</b>
 */
@RestController
@RequestMapping("/api/v1/meeting/{meetingId}/hls")
@RequiredArgsConstructor
public class HlsEgressController {

    private final HlsEgressService hlsEgressService;

    @PostMapping
    public ResponseEntity<Map<String, String>> start(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        String egressId = hlsEgressService.start(member.getEmail(), meetingId);
        return ResponseEntity.ok(Map.of("egressId", egressId));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> stop(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        hlsEgressService.stop(member.getEmail(), meetingId);
        return ResponseEntity.ok(Map.of("message", "송출을 중지했습니다."));
    }
}
