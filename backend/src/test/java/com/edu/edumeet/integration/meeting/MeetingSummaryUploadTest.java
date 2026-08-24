package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.s3.service.S3Uploader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파이썬 AI 서버의 요약본 업로드 경로를 검증한다. (#27)
 *
 * <p>이 경로는 #23 이전에 {@code /api/v1/**} permitAll 에 묻혀 <b>인증 없이 열려 있었다.</b>
 * 누구나 임의 클래스에 요약본을 덮어쓸 수 있었다.
 *
 * <p>{@code @AutoConfigureMockMvc} 를 쓴다. {@code webAppContextSetup} 은
 * 시큐리티 필터 체인을 타지 않아서 인증이 뚫려 있어도 통과한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AI 요약본 업로드")
class MeetingSummaryUploadTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired MockMvc mockMvc;
    @PersistenceContext EntityManager em;

    @MockitoBean S3Uploader s3Uploader;

    private Long meetingId;

    @BeforeEach
    void setUp() {
        Member owner = Member.builder().email("owner@test").nickname("주인").password("x").build();
        em.persist(owner);
        ClassRoom classRoom = ClassRoom.builder()
                .member(owner).title("클래스").description("-")
                .participantLimit(30).isDeleted(false).build();
        em.persist(classRoom);
        Meeting meeting = Meeting.builder()
                .classRoom(classRoom).title("회의").description("-")
                .sessionType(SessionType.INTERACTIVE)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .build();
        em.persist(meeting);
        em.flush();
        meetingId = meeting.getId();

        given(s3Uploader.uploadMultipartFile(any(), anyString(), anyString()))
                .willAnswer(inv -> "https://s3.test/" + inv.getArgument(2));
    }

    private MockMultipartFile md(String content) {
        return new MockMultipartFile("summary_md", "summary.md", "text/markdown", content.getBytes());
    }

    private MockMultipartFile pdf(String content) {
        return new MockMultipartFile("summary_pdf", "summary.pdf", "application/pdf", content.getBytes());
    }

    // --- 인증 ---

    @Test
    @DisplayName("토큰 없이 부르면 401 이다")
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId).file(md("# 요약")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰이 틀리면 401 이다")
    void 토큰_틀리면_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(md("# 요약"))
                        .header(InternalApiTokenFilter.HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    // --- 정상 경로 ---

    @Test
    @DisplayName("MD 와 PDF 를 둘 다 저장한다 - 이전에는 PDF 가 MD 를 덮어썼다")
    void MD_와_PDF_를_둘다_저장한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(md("# 요약")).file(pdf("%PDF-1.4"))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyExisted").value(false))
                .andExpect(jsonPath("$.markdownUrl").exists())
                .andExpect(jsonPath("$.pdfUrl").exists());

        em.flush(); em.clear();
        Meeting saved = em.find(Meeting.class, meetingId);
        assertThat(saved.getSummaryMdUrl()).as("MD URL 이 DB 에 남아야 한다").isNotNull();
        assertThat(saved.getSummaryPdfUrl()).as("PDF URL 이 DB 에 남아야 한다").isNotNull();
        assertThat(saved.getS3url()).as("하위 호환 필드는 PDF 를 가리킨다").isEqualTo(saved.getSummaryPdfUrl());
    }

    // --- 멱등성 ---

    @Test
    @DisplayName("재시도하면 S3 에 다시 올리지 않고 200 을 준다")
    void 재시도는_중복_업로드를_만들지_않는다() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(md("# 요약"))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isCreated());
        em.flush();

        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(md("# 요약"))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExisted").value(true));

        verify(s3Uploader, org.mockito.Mockito.times(1))
                .uploadMultipartFile(any(), anyString(), anyString());
    }

    // --- 검증 (회귀 방지) ---

    @Test
    @DisplayName("없는 meetingId 는 500 이 아니라 400 이다")
    void 없는_미팅은_400() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", 999999L)
                        .file(md("# 요약"))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 파일은 거부한다 - 이전에는 0바이트가 S3 에 올라갔다")
    void 빈_파일은_거부한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(new MockMultipartFile("summary_md", "summary.md", "text/markdown", new byte[0]))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isBadRequest());

        verify(s3Uploader, never()).uploadMultipartFile(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("파일이 하나도 없으면 400 이다")
    void 파일이_없으면_400() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("확장자가 맞지 않으면 400 이다")
    void 잘못된_확장자는_400() throws Exception {
        mockMvc.perform(multipart("/api/v1/internal/meetings/{id}/summary", meetingId)
                        .file(new MockMultipartFile("summary_pdf", "evil.exe",
                                "application/octet-stream", "MZ".getBytes()))
                        .header(InternalApiTokenFilter.HEADER, TOKEN))
                .andExpect(status().isBadRequest());
    }
}
