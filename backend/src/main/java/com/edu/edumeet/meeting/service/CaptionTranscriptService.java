package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.domain.CaptionSegment;
import com.edu.edumeet.meeting.dto.CaptionMeetingSummary;
import com.edu.edumeet.meeting.dto.CaptionMeetingsResponse;
import com.edu.edumeet.meeting.dto.CaptionTranscriptResponse;
import com.edu.edumeet.meeting.repository.CaptionSegmentRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 저장된 final 자막을 회의 후 transcript 로 만든다. (#131)
 *
 * <p>요약 입력은 partial 자막을 쓰지 않는다. partial 은 화면에서는 빠르지만
 * 계속 바뀌므로 요약에 넣으면 같은 말이 반복되어 토큰을 낭비한다.
 */
@Service
@RequiredArgsConstructor
public class CaptionTranscriptService {

    static final int DEFAULT_MEETING_LIMIT = 50;
    static final int MAX_MEETING_LIMIT = 200;

    private final MeetingRepository meetingRepository;
    private final CaptionSegmentRepository captionSegmentRepository;

    @Transactional(readOnly = true)
    public CaptionTranscriptResponse buildTranscript(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new IllegalArgumentException("존재하지 않는 회의입니다: " + meetingId);
        }
        List<CaptionSegment> segments = captionSegmentRepository.findTranscriptSegments(meetingId);
        String text = segments.stream()
                .map(CaptionSegment::getText)
                .collect(Collectors.joining("\n"));
        return new CaptionTranscriptResponse(
                meetingId, segments.size(), text, System.currentTimeMillis());
    }

    /**
     * final 자막이 있는 회의 목록. (#133)
     *
     * <p>상한을 강제하는 이유 - 부르는 쪽이 MCP 도구다. 도구 응답은 그대로
     * 모델 컨텍스트에 들어가므로 "전부 주기" 가 곧 컨텍스트 낭비다.
     * 그리고 {@code returned} 를 같이 돌려주므로 잘렸는지 부르는 쪽이 안다.
     *
     * @param limit 요청한 개수. 1 미만이면 기본값, {@value #MAX_MEETING_LIMIT} 초과면 잘린다
     */
    @Transactional(readOnly = true)
    public CaptionMeetingsResponse listMeetingsWithCaptions(int limit) {
        int effective = limit <= 0 ? DEFAULT_MEETING_LIMIT : Math.min(limit, MAX_MEETING_LIMIT);
        List<CaptionMeetingSummary> meetings =
                captionSegmentRepository.findMeetingsWithCaptions(PageRequest.of(0, effective));
        return CaptionMeetingsResponse.of(meetings);
    }
}
