package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.domain.CaptionSegment;
import com.edu.edumeet.meeting.dto.CaptionTranscriptResponse;
import com.edu.edumeet.meeting.repository.CaptionSegmentRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
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
}
