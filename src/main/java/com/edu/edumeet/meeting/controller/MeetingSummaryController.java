package com.edu.edumeet.meeting.controller;

import com.edu.edumeet.meeting.dto.SummaryUploadResult;
import com.edu.edumeet.meeting.service.MeetingSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자용 요약본 조회. 업로드는 {@link InternalMeetingSummaryController} 로 옮겼다. (#27)
 */
@RestController
@RequestMapping("/api/v1/meeting")
@RequiredArgsConstructor
@Slf4j
public class MeetingSummaryController {

    private final MeetingSummaryService meetingSummaryService;

    /** 요약본이 없으면 204. 빈 본문에 success=true 를 담는 것보다 상태 코드로 말하는 게 낫다. */
    @GetMapping("/summary/{classId}")
    public ResponseEntity<SummaryUploadResult> getSummary(@PathVariable("classId") Long classId) {
        return meetingSummaryService.findLatestSummary(classId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
