package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.meeting.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 형태별 정책. (#65)
 *
 * <p>정책이 서비스 코드에 {@code == SessionType.INTERACTIVE} 로 흩어져 있었다.
 * <b>타입이 늘 때 그 자리를 빠뜨리면 새 타입이 조용히 다르게 동작한다.</b>
 * 실제로 채팅 저장 정책이 그렇게 되어 있었다 — enum 으로 옮겼다.
 *
 * <p>여기서는 <b>정책이 타입마다 명시적으로 정해져 있는지</b>를 본다.
 * 새 타입을 추가하면 이 테스트가 그 타입에 대해서도 돌아간다.
 */
@DisplayName("세션 형태 정책")
class SessionTypePolicyTest {

    @Test
    @DisplayName("정원 제한은 화상강의에만 있다")
    void only_interactive_limits_participants() {
        assertThat(SessionType.INTERACTIVE.hasParticipantLimit()).isTrue();
        assertThat(SessionType.BROADCAST.hasParticipantLimit()).isFalse();
        assertThat(SessionType.AUDIO_BROADCAST.hasParticipantLimit())
                .as("방송은 세그먼트를 배포하는 구조라 인원이 늘어도 서버 연산이 늘지 않는다")
                .isFalse();
    }

    @Test
    @DisplayName("참가자 발행은 화상강의에만 허용된다")
    void only_interactive_allows_publish() {
        assertThat(SessionType.INTERACTIVE.allowsParticipantPublish()).isTrue();
        assertThat(SessionType.BROADCAST.allowsParticipantPublish()).isFalse();
        assertThat(SessionType.AUDIO_BROADCAST.allowsParticipantPublish())
                .as("청취자는 발행하지 않는다. 채팅만 한다")
                .isFalse();
    }

    @Test
    @DisplayName("★ 오디오 전용은 오디오 방송뿐이다")
    void only_audio_broadcast_is_audio_only() {
        assertThat(SessionType.AUDIO_BROADCAST.isAudioOnly()).isTrue();
        assertThat(SessionType.INTERACTIVE.isAudioOnly()).isFalse();
        assertThat(SessionType.BROADCAST.isAudioOnly()).isFalse();
    }

    @Test
    @DisplayName("★ 방송은 발행 경로에서 채팅을 저장하지 않는다 - 측정이 DB 쓰기에 묻힌다")
    void broadcasts_do_not_persist_chat_inline() {
        assertThat(SessionType.INTERACTIVE.persistsChatInline())
                .as("수업 대화는 기록이다. 정원 30이라 쓰기량도 작다")
                .isTrue();
        assertThat(SessionType.BROADCAST.persistsChatInline()).isFalse();
        assertThat(SessionType.AUDIO_BROADCAST.persistsChatInline())
                .as("저장 자체를 안 한다는 뜻이 아니라 발행 경로에서 안 한다는 뜻이다 (#61)")
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(SessionType.class)
    @DisplayName("모든 타입이 네 가지 정책에 답한다 - 새 타입을 추가해도 빠지지 않는다")
    void every_type_answers_all_policies(SessionType type) {
        // 값 자체를 단언하지 않는다. 예외 없이 답이 나오는지만 본다.
        // 새 타입을 추가했을 때 정책 메서드가 NPE 나 미정의로 죽지 않는지 확인하는 장치다.
        type.hasParticipantLimit();
        type.allowsParticipantPublish();
        type.isAudioOnly();
        type.persistsChatInline();
    }
}
