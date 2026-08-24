package com.edu.edumeet.meeting.domain;

import com.edu.edumeet.classroom.domain.ClassRoom;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    /**
     * 세션 형태. 이 값에 따라 정원 제한과 참가 권한이 갈린다. (#2)
     * 기존 세션은 전부 화상강의였으므로 기본값은 INTERACTIVE 다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    @Builder.Default
    private SessionType sessionType = SessionType.INTERACTIVE;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 하위 호환용. PDF 가 있으면 PDF, 없으면 MD 를 담는다.
     * 새 코드는 summaryMdUrl / summaryPdfUrl 을 직접 본다. (#27)
     */
    private String s3url;

    @Column(name = "summary_md_url", length = 500)
    private String summaryMdUrl;

    @Column(name = "summary_pdf_url", length = 500)
    private String summaryPdfUrl;

    /** 진행 중인 HLS egress 의 id. null 이면 내보내는 중이 아니다. (#75) */
    @Column(name = "hls_egress_id", length = 100)
    private String hlsEgressId;

    /** 플레이어가 여는 라이브 플레이리스트 주소. 방송이 끝나도 지우지 않는다. (#75) */
    @Column(name = "hls_playlist_url", length = 500)
    private String hlsPlaylistUrl;

    public void assignTo(ClassRoom classRoom) {
        this.classRoom = classRoom;
        if (classRoom != null) {
            classRoom.getMeetings().add(this);
        }
    }

    public void changeEndTime(LocalDateTime newEndTime) {
        if (newEndTime != null && !newEndTime.isAfter(this.startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.endTime = newEndTime;
    }

    /** 정원 제한을 적용하는 세션인가. */
    public boolean hasParticipantLimit() {
        return this.sessionType.hasParticipantLimit();
    }

    /** 이 세션의 정원. 라이브방송은 제한이 없다. */
    public int participantLimit() {
        return hasParticipantLimit() ? this.classRoom.getParticipantLimit() : Integer.MAX_VALUE;
    }

    public void endNow() {
        changeEndTime(LocalDateTime.now());
    }

    public void changeS3Url(String newS3Url) {
        this.s3url = newS3Url;
    }

    /**
     * 요약본 URL 을 기록한다. 둘 다 null 이면 호출하는 쪽 잘못이다.
     * s3url 은 기존 조회 코드가 계속 읽으므로 함께 갱신한다. (#27)
     */
    public void recordSummary(String markdownUrl, String pdfUrl) {
        if (markdownUrl == null && pdfUrl == null) {
            throw new IllegalArgumentException("요약본 URL 이 하나도 없습니다.");
        }
        this.summaryMdUrl = markdownUrl;
        this.summaryPdfUrl = pdfUrl;
        this.s3url = (pdfUrl != null) ? pdfUrl : markdownUrl;
    }

    /** 이미 요약본이 기록되어 있는가. 멱등성 판단에 쓴다. */
    public boolean hasSummary() {
        return this.summaryMdUrl != null || this.summaryPdfUrl != null;
    }

    /** HLS 내보내기를 시작했다고 기록한다. (#75) */
    public void startHls(String egressId, String playlistUrl) {
        this.hlsEgressId = egressId;
        this.hlsPlaylistUrl = playlistUrl;
    }

    /**
     * HLS 내보내기를 멈췄다고 기록한다.
     *
     * <p><b>플레이리스트 주소는 지우지 않는다.</b> 방송이 끝나면 {@code live.m3u8} 은
     * 의미를 잃지만 같은 디렉터리의 {@code index.m3u8} 은 다시보기로 남는다.
     * 주소를 지우면 다시보기로 가는 길이 끊긴다.
     */
    public void stopHls() {
        this.hlsEgressId = null;
    }

    /** 지금 HLS 로 내보내는 중인가. */
    public boolean isStreamingHls() {
        return hlsEgressId != null;
    }

    @PrePersist @PreUpdate
    private void validateTimes() {
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }
}
