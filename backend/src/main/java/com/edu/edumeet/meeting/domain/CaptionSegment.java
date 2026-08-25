package com.edu.edumeet.meeting.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회의 후 요약·검색에 쓸 final 자막 조각. (#131)
 *
 * <p>partial 자막은 화면에서 계속 바뀌므로 저장하지 않는다.
 * 저장하면 같은 발화가 여러 번 들어가 요약 토큰을 낭비하고,
 * transcript 도 "사용자가 본 중간 결과"와 "최종 발화"가 뒤섞인다.
 */
@Entity
@Table(name = "caption_segment",
        indexes = {
                @Index(name = "idx_caption_segment_transcript",
                        columnList = "meeting_id, final_segment, sequence, spoken_at, id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_caption_segment_meeting_sequence",
                        columnNames = {"meeting_id", "sequence"})
        })
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptionSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(name = "sequence")
    private Long sequence;

    @Column(name = "spoken_at")
    private Long spokenAt;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;

    @Column(name = "published_at", nullable = false)
    private Long publishedAt;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "final_segment", nullable = false)
    @Builder.Default
    private boolean finalSegment = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public static CaptionSegment finalOf(Meeting meeting, Long sequence, Long spokenAt,
                                         Long receivedAt, Long publishedAt, String text) {
        return CaptionSegment.builder()
                .meeting(meeting)
                .sequence(sequence)
                .spokenAt(spokenAt)
                .receivedAt(receivedAt)
                .publishedAt(publishedAt)
                .text(text)
                .finalSegment(true)
                .build();
    }
}
