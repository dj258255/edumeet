package com.edu.edumeet.chat.repository;

import com.edu.edumeet.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최근 메시지부터. 입장 시 지난 대화를 보여주는 용도다. */
    @Query("SELECT m FROM ChatMessage m WHERE m.meeting.id = :meetingId ORDER BY m.sentAt DESC")
    List<ChatMessage> findRecent(@Param("meetingId") Long meetingId, Pageable pageable);

    long countByMeetingId(Long meetingId);

    /**
     * 다시보기 구간 조회. (#108)
     *
     * <p><b>{@code offset_millis} 로 자른다. 절대 시각이 아니다.</b>
     * 다시보기는 "재생 위치 12분 34초에 무슨 대화가 있었나" 를 묻는다.
     * {@code sentAt} 으로 자르려면 회의 시작 시각을 매번 빼야 하고,
     * 시작 시각이 나중에 보정되면 이미 저장된 것이 전부 어긋난다.
     *
     * <p>{@code offsetMillis} 가 {@code null} 인 행은 제외한다 -
     * V7 이전에 저장된 것이라 재생 위치를 모른다. 섞어서 주면
     * 클라이언트가 그것들을 0초에 몰아 그린다.
     *
     * <p><b>이 조건은 지금 기술적으로 중복이다.</b> SQL 3값 논리에서
     * {@code NULL >= 0} 은 {@code UNKNOWN} 이라 아래 범위 비교가 이미 걸러낸다.
     * 되돌려 확인했더니 시험이 안 잡혔다.
     *
     * <p>그래도 남기는 이유 - <b>의도가 범위 비교의 부작용에 얹혀 있으면 안 된다.</b>
     * 나중에 누군가 "재생 위치 미상은 0초로 보여주자" 며 {@code COALESCE} 를 넣는 순간
     * 이 조건이 없으면 조용히 섞인다.
     *
     * <p>정렬은 {@code offsetMillis} 다. 배치 저장이라 {@code id} 순서가
     * 발화 순서와 다를 수 있다 - 큐에서 나온 순서지 말한 순서가 아니다.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.meeting.id = :meetingId
              AND m.offsetMillis IS NOT NULL
              AND m.offsetMillis >= :fromMillis
              AND m.offsetMillis < :toMillis
            ORDER BY m.offsetMillis ASC, m.id ASC
            """)
    List<ChatMessage> findReplayWindow(@Param("meetingId") Long meetingId,
                                       @Param("fromMillis") long fromMillis,
                                       @Param("toMillis") long toMillis,
                                       Pageable pageable);
}
