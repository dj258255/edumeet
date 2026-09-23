package com.edu.edumeet.integration.classroom;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.domain.Tag;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 수업 API 가 200 · 빈 본문을 주는 문제의 재현 시험. (#209)
 *
 * <p>운영에서 이 세 경로가 <b>200 인데 0바이트</b>였다. 에러 로그도 없었다.
 * <pre>
 *   GET /api/v1/classroom              내 수업 목록 (1건)
 *   GET /api/v1/classroom/{classId}    수업 상세
 *   GET /api/v1/meetingroom/{classId}  수업의 회의 목록 (1건)
 * </pre>
 *
 * <p>반면 {@code /api/v1/classroom/joined}(<b>빈</b> 목록)와 {@code /api/v1/meeting/{meetingId}} 는
 * 정상 JSON 이었다. 그래서 시험도 <b>항목이 있는</b> 수업을 만들고, 실제 JWT 로 보안 필터를 통과시킨다
 * ({@code ClassResourceAuthorizationTest} 방식).
 *
 * <p>응답 모양(필드 이름·구조)은 바뀌지 않아야 하므로 배열 길이와 대표 필드까지 단언한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("수업 API 응답 본문")
class ClassroomResponseBodyTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long classId;
    private Long meetingId;
    private String ownerEmail;
    private String token;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        String owner = "classroom-body-" + n + "@test";

        transactionTemplate.executeWithoutResult(status -> {
            Member member = Member.builder().email(owner).nickname("주인").password("x").build();
            em.persist(member);

            ClassRoom classRoom = ClassRoom.builder()
                    .member(member)
                    .title("수업 " + n)
                    .description("본문 재현")
                    .participantLimit(30)
                    .isDeleted(false)
                    .build();
            em.persist(classRoom);
            // 목록에 항목이 있는 상태를 만든다 - 운영에서 이때만 깨졌다.
            em.persist(Tag.builder().classRoom(classRoom).name("태그").build());

            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom)
                    .title("회의")
                    .description("-")
                    .sessionType(SessionType.INTERACTIVE)
                    .startTime(LocalDateTime.now())
                    .build();
            em.persist(meeting);
            em.flush();

            classId = classRoom.getId();
            meetingId = meeting.getId();
        });

        token = jwtService.generateAccessToken(1L, owner);
        ownerEmail = owner;
    }

    /**
     * 이 시험이 만든 행만 지운다. (#172)
     *
     * <p>다른 시험의 데이터를 건드리지 않도록 id·email 로 좁혀서 지운다.
     * 참조 순서(회의 → 태그 → 수업 → 회원)를 지킨다.
     */
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM Meeting m WHERE m.classRoom.id = :classId")
                    .setParameter("classId", classId)
                    .executeUpdate();
            em.createQuery("DELETE FROM Tag t WHERE t.classRoom.id = :classId")
                    .setParameter("classId", classId)
                    .executeUpdate();
            em.createQuery("DELETE FROM ClassRoom c WHERE c.id = :classId")
                    .setParameter("classId", classId)
                    .executeUpdate();
            em.createQuery("DELETE FROM Member m WHERE m.email = :email")
                    .setParameter("email", ownerEmail)
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("★ 내 수업 목록은 200 이고 비어 있지 않은 JSON 이다")
    void my_classes_body_is_not_empty() throws Exception {
        MockHttpServletResponse response = call("/api/v1/classroom");

        assertNotAnEmptyBody(response, "내 수업 목록");
        assertThat(bodyText(response))
                .as(describe(response))
                .contains("\"classId\":" + classId)
                .contains("\"title\":\"수업 ");
    }

    @Test
    @DisplayName("★ 수업 상세는 200 이고 비어 있지 않은 JSON 이다")
    void class_detail_body_is_not_empty() throws Exception {
        MockHttpServletResponse response = call("/api/v1/classroom/" + classId);

        assertNotAnEmptyBody(response, "수업 상세");
        assertThat(bodyText(response))
                .as(describe(response))
                .contains("\"classId\":" + classId);
    }

    @Test
    @DisplayName("★ 수업의 회의 목록은 200 이고 비어 있지 않은 JSON 이다")
    void meeting_list_body_is_not_empty() throws Exception {
        MockHttpServletResponse response = call("/api/v1/meetingroom/" + classId);

        assertNotAnEmptyBody(response, "회의 목록");
        assertThat(bodyText(response))
                .as(describe(response))
                .contains("\"meetingId\":" + meetingId)
                .contains("\"host\":true");
    }

    /**
     * 정상이던 두 경로. 깨진 경로와의 차이가 "수업을 거치는가 · 항목이 있는가" 인지 확인한다.
     * 참여한 수업이 없으므로 빈 배열이 정상이다.
     */
    @Test
    @DisplayName("참여한 수업이 없으면 200 · [] (운영에서도 정상이던 경로)")
    void joined_classes_is_empty_array() throws Exception {
        MockHttpServletResponse response = call("/api/v1/classroom/joined");

        assertThat(response.getStatus()).as(describe(response)).isEqualTo(200);
        assertThat(bodyText(response))
                .as(describe(response))
                .isEqualTo("[]");
    }

    private MockHttpServletResponse call(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).headers(bearer(token))).andReturn();
        return result.getResponse();
    }

    private void assertNotAnEmptyBody(MockHttpServletResponse response, String what) {
        assertThat(response.getStatus()).as(describe(response)).isEqualTo(200);
        assertThat(response.getContentAsByteArray())
                .as("%s 의 본문이 비었다 - 200 이어도 쓸 수 없다. %s", what, describe(response))
                .isNotEmpty();
        assertThat(response.getContentType())
                .as("%s 에 Content-Type 이 없다. 본문을 쓰지 않았다는 뜻이다. %s", what, describe(response))
                .contains("application/json");
    }

    private String describe(MockHttpServletResponse response) {
        return "HTTP %d · Content-Type=%s · %d바이트 · 본문=%s".formatted(
                response.getStatus(),
                response.getContentType(),
                response.getContentAsByteArray().length,
                bodyText(response));
    }

    private String bodyText(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
