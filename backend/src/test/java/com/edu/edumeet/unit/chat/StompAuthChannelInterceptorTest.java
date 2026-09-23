package com.edu.edumeet.unit.chat;

import com.edu.edumeet.chat.config.ChatAccessDeniedException;
import com.edu.edumeet.chat.config.StompAuthChannelInterceptor;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("STOMP 방 구독 인가")
class StompAuthChannelInterceptorTest {

    private static final Long MEETING_ID = 7L;
    private static final Long CLASS_ID = 3L;
    private static final String HOST = "host@test";
    private static final String MEMBER = "member@test";
    private static final String OUTSIDER = "outsider@test";

    private MeetingParticipantRepository participantRepository;
    private MeetingRepository meetingRepository;
    private ClassMemberRepository classMemberRepository;
    private StompAuthChannelInterceptor interceptor;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        participantRepository = mock(MeetingParticipantRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        classMemberRepository = mock(ClassMemberRepository.class);
        interceptor = new StompAuthChannelInterceptor(
                mock(JwtService.class), participantRepository, meetingRepository, classMemberRepository);

        meeting = mock(Meeting.class);
        ClassRoom classRoom = mock(ClassRoom.class);
        Member owner = mock(Member.class);
        when(meeting.getClassRoom()).thenReturn(classRoom);
        when(classRoom.getId()).thenReturn(CLASS_ID);
        when(classRoom.getMember()).thenReturn(owner);
        when(owner.getEmail()).thenReturn(HOST);
        when(meetingRepository.findByIdWithClassRoomAndOwner(MEETING_ID)).thenReturn(Optional.of(meeting));
    }

    @Test
    @DisplayName("★ 방송 수업 멤버는 참가 기록이 없어도 구독한다")
    void 방송_수업_멤버는_참가_기록_없이_구독한다() {
        // 이 시험은 기존 코드에서 findActive(...)=empty 로 거절되어 실패한다.
        when(meeting.getSessionType()).thenReturn(SessionType.BROADCAST);
        when(classMemberRepository.findByClassRoomIdAndMemberEmail(CLASS_ID, MEMBER))
                .thenReturn(Optional.of(mock(ClassMember.class)));

        assertThatCode(() -> subscribe(MEMBER, "/topic/rooms/" + MEETING_ID)).doesNotThrowAnyException();

        verifyNoInteractions(participantRepository);
    }

    @Test
    @DisplayName("방송 비멤버는 구독할 수 없다")
    void 방송_비멤버는_구독_불가() {
        when(meeting.getSessionType()).thenReturn(SessionType.BROADCAST);
        when(classMemberRepository.findByClassRoomIdAndMemberEmail(CLASS_ID, OUTSIDER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscribe(OUTSIDER, "/topic/rooms/" + MEETING_ID))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    @DisplayName("방송 호스트는 참가 기록 없이 구독한다")
    void 방송_호스트는_구독한다() {
        when(meeting.getSessionType()).thenReturn(SessionType.BROADCAST);

        assertThatCode(() -> subscribe(HOST, "/topic/rooms/" + MEETING_ID)).doesNotThrowAnyException();

        verifyNoInteractions(participantRepository, classMemberRepository);
    }

    @Test
    @DisplayName("화상 회의 수업 멤버도 활성 참가 기록이 없으면 거절한다")
    void 화상_회의는_참가_기록을_요구한다() {
        when(meeting.getSessionType()).thenReturn(SessionType.INTERACTIVE);
        when(participantRepository.findActive(MEETING_ID, MEMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscribe(MEMBER, "/topic/rooms/" + MEETING_ID))
                .isInstanceOf(ChatAccessDeniedException.class);

        verifyNoInteractions(classMemberRepository);
    }

    @Test
    @DisplayName("없는 회의는 세션 형태와 관계없이 거절한다")
    void 없는_회의는_구독_불가() {
        Long missingMeetingId = 999L;
        when(meetingRepository.findByIdWithClassRoomAndOwner(missingMeetingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscribe(MEMBER, "/topic/rooms/" + missingMeetingId))
                .isInstanceOf(ChatAccessDeniedException.class);

        verifyNoInteractions(participantRepository, classMemberRepository);
    }

    @Test
    @DisplayName("열거하지 않은 방 목적지는 거절한다")
    void 알_수_없는_목적지는_거절한다() {
        assertThatThrownBy(() -> subscribe(MEMBER, "/topic/rooms/" + MEETING_ID + "/other"))
                .isInstanceOf(ChatAccessDeniedException.class);

        verifyNoInteractions(meetingRepository, participantRepository, classMemberRepository);
    }

    private void subscribe(String email, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(email, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, mock(MessageChannel.class));
    }
}
