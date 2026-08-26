package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.dto.CaptionMeetingSummary;
import com.edu.edumeet.meeting.dto.CaptionMeetingsResponse;
import com.edu.edumeet.meeting.service.CaptionArchiveQueue;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자막이 있는 회의 목록. (#133)
 *
 * <p><b>이 엔드포인트는 사람이 아니라 MCP 도구가 부른다.</b>
 * 도구를 쓰는 쪽은 meetingId 를 모른다 — 모른 채로 시작할 수 있어야 도구가 쓸모 있다.
 *
 * <p>여기서 확인하는 것은 세 가지다.
 * <ul>
 *   <li><b>자막이 없는 회의는 안 나온다</b> — 목록에 있는데 열면 비어 있는 것이 제일 나쁘다
 *   <li><b>정렬은 저장 시각이 아니라 발화 시각</b> — 비동기 저장이라 둘이 다르다
 *   <li><b>상한이 강제된다</b> — 응답이 그대로 모델 컨텍스트에 들어간다
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "edumeet.caption.archive.flush-interval-ms=600000")
@ActiveProfiles("test")
@DisplayName("자막 있는 회의 목록")
class CaptionMeetingsListTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String TOKEN = "test-internal-token";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired CaptionArchiveQueue captionArchiveQueue;
    @PersistenceContext EntityManager em;

    private Long withCaptions;
    private Long withoutCaptions;

    @BeforeEach
    void setUp() {
        withCaptions = createMeeting("자막 있는 회의");
        withoutCaptions = createMeeting("자막 없는 회의");
    }

    private Long createMeeting(String title) {
        String email = "mcp-" + SEQ.incrementAndGet() + "@test";
        final Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("강사").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title(title).description("-")
                    .sessionType(SessionType.AUDIO_BROADCAST)
                    .startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            id[0] = meeting.getId();
        });
        return id[0];
    }

    private void postCaption(Long meetingId, String text, long sequence, long spokenAt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalApiTokenFilter.HEADER, TOKEN);
        rest.postForEntity(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + meetingId + "/captions",
                new HttpEntity<>(Map.of(
                        "text", text, "sequence", sequence,
                        "spokenAt", spokenAt, "finalSegment", true), headers),
                String.class);
    }

    private ResponseEntity<CaptionMeetingsResponse> list(String token, String query) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.add(InternalApiTokenFilter.HEADER, token);
        return rest.exchange(
                "http://localhost:" + port + "/api/v1/internal/meetings/captions" + query,
                HttpMethod.GET, new HttpEntity<>(headers), CaptionMeetingsResponse.class);
    }

    @Test
    @DisplayName("토큰 없이 부르면 401 이다")
    void requires_internal_token() {
        assertThat(list(null, "").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("★ 자막이 없는 회의는 목록에 없다 - 열면 비어 있는 항목을 도구에 주지 않는다")
    void meetings_without_captions_are_absent() {
        postCaption(withCaptions, "저장될 자막", 1, System.currentTimeMillis());
        captionArchiveQueue.flush();

        CaptionMeetingsResponse body = list(TOKEN, "?limit=200").getBody();

        assertThat(body).isNotNull();
        assertThat(body.meetings()).extracting(CaptionMeetingSummary::meetingId)
                .contains(withCaptions)
                .doesNotContain(withoutCaptions);
    }

    @Test
    @DisplayName("★ 조각 수와 마지막 발화 시각이 함께 온다 - 도구가 이것만 보고 고를 수 있어야 한다")
    void carries_segment_count_and_last_spoken_at() {
        long base = System.currentTimeMillis();
        postCaption(withCaptions, "첫 문장", 1, base);
        postCaption(withCaptions, "둘째 문장", 2, base + 5_000);
        captionArchiveQueue.flush();

        CaptionMeetingsResponse body = list(TOKEN, "?limit=200").getBody();

        assertThat(body).isNotNull();
        CaptionMeetingSummary mine = body.meetings().stream()
                .filter(m -> m.meetingId().equals(withCaptions))
                .findFirst().orElseThrow();

        assertThat(mine.title()).isEqualTo("자막 있는 회의");
        assertThat(mine.segmentCount()).isEqualTo(2L);
        assertThat(mine.lastSpokenAt())
                .as("저장 시각이 아니라 발화 시각이다. 비동기 저장이라 둘은 다르다")
                .isEqualTo(base + 5_000);
    }

    @Test
    @DisplayName("★ partial 자막만 있는 회의는 목록에 없다 - 저장 자체가 안 되므로")
    void partial_only_meeting_is_absent() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalApiTokenFilter.HEADER, TOKEN);
        rest.postForEntity(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + withoutCaptions + "/captions",
                new HttpEntity<>(Map.of(
                        "text", "중간 결과", "sequence", 1,
                        "spokenAt", System.currentTimeMillis(), "finalSegment", false), headers),
                String.class);
        captionArchiveQueue.flush();

        CaptionMeetingsResponse body = list(TOKEN, "?limit=200").getBody();

        assertThat(body).isNotNull();
        assertThat(body.meetings()).extracting(CaptionMeetingSummary::meetingId)
                .doesNotContain(withoutCaptions);
    }

    @Test
    @DisplayName("★ limit 이 강제된다 - 응답이 그대로 모델 컨텍스트에 들어간다")
    void limit_is_enforced() {
        postCaption(withCaptions, "자막", 1, System.currentTimeMillis());
        captionArchiveQueue.flush();

        CaptionMeetingsResponse body = list(TOKEN, "?limit=1").getBody();

        assertThat(body).isNotNull();
        assertThat(body.meetings()).hasSize(1);
        assertThat(body.returned())
                .as("잘렸는지 부르는 쪽이 알아야 한다. 배열 길이를 세게 하면 감싼 의미가 없다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("limit 을 터무니없이 크게 줘도 상한에서 잘린다")
    void limit_above_max_is_clamped() {
        postCaption(withCaptions, "자막", 1, System.currentTimeMillis());
        captionArchiveQueue.flush();

        assertThat(list(TOKEN, "?limit=100000").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list(TOKEN, "?limit=100000").getBody())
                .isNotNull()
                .extracting(CaptionMeetingsResponse::returned)
                .satisfies(n -> assertThat((int) n).isLessThanOrEqualTo(200));
    }
}
