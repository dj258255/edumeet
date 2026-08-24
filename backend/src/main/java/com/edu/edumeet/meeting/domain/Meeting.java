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
    @Column(name = "broadcast_session_id", length = 100)
    private String broadcastSessionId;

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

    /** 방송 송출을 시작했다고 기록한다. (#75, #123 에서 자체 구현으로 교체) */
    public void startBroadcast(String sessionId, String playlistUrl) {
        this.broadcastSessionId = sessionId;
        this.hlsPlaylistUrl = playlistUrl;
    }

    /**
     * 방송 송출을 멈췄다고 기록한다.
     *
     * <p><b>플레이리스트 주소는 지우지 않는다.</b> "이 세션은 방송이었다" 는 사실 자체가
     * 기록이고, 다시보기 채팅이 재생 위치를 기준으로 동작하려면 방송이었다는 것을 알아야 한다.
     *
     * <p><b>다만 이 주소로는 다시 볼 수 없다.</b> 송출은 {@code delete_segments} 로 돌아
     * 오래된 세그먼트를 지운다. 지우지 않으면 방송 내내 디스크가 찬다.
     * <b>다시보기 영상은 이 경로가 만들지 않는다</b> — 별도 녹화가 필요하고, 아직 없다.
     * ({@code docs/plan/05-own-hls.md} 의 "안 만든 것")
     */
    public void stopBroadcast() {
        this.broadcastSessionId = null;
    }

    /** 지금 방송을 송출 중인가. */
    public boolean isBroadcasting() {
        return broadcastSessionId != null;
    }

    @PrePersist @PreUpdate
    private void validateTimes() {
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }
}
