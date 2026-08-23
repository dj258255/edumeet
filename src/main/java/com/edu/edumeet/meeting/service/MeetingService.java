package com.edu.edumeet.meeting.service;

import io.livekit.server.CanSubscribe;
import io.livekit.server.CanPublish;
import com.edu.edumeet.meeting.exception.LiveKitUnavailableException;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import retrofit2.Response;

import java.io.IOException;
import com.edu.edumeet.meeting.exception.SessionCapacityExceededException;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.repository.MemberRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.meeting.dto.response.ClassMeetingInfoResponseDto;
import com.edu.edumeet.meeting.dto.response.MeetingCreateResponseDto;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Optional;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.classroom.service.ClassAccessChecker;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final ClassRepository classRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassAccessChecker classAccessChecker;
    private final MemberRepository memberRepository;
    private final RoomServiceClient roomServiceClient;

    // OpenVidu 인증 정보 (application.properties에 추가)
    @Value("${livekit.api.key}")
    private String LIVEKIT_API_KEY;

    @Value("${livekit.api.secret}")
    private String LIVEKIT_API_SECRET;

    @Value("${livekit.url}")
    private String OPENVIDU_URL;

    /**
     * LiveKit 토큰 생성 (직접 생성)
     */
    public Map<String, Object> createToken(String roomName, String participantName) {
        try {
            // AccessToken 생성
            AccessToken token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
            token.setName(participantName);
            token.setIdentity(participantName);

            // 권한 부여
            token.addGrants(
                    new RoomJoin(true),
                    new RoomName(roomName)
            );

            // 토큰 유효시간 설정 (옵션)
            token.setTtl(Duration.ofHours(6).toMillis());

            String jwt = token.toJwt();

            log.info("Token created for participant: {} in room: {}", participantName, roomName);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("url", OPENVIDU_URL);
            response.put("roomName", roomName);
            response.put("participantName", participantName);

            return response;

        } catch (Exception e) {
            log.error("Failed to create token", e);
            throw new RuntimeException("Failed to create token: " + e.getMessage());
        }
    }

    /**
     * 룸 정보 조회.
     *
     * <p>이전 구현은 {@code GET /api/v1/rooms/{name}} 을 Basic Auth 로 불렀는데,
     * <b>그 주소도 그 인증 방식도 LiveKit 에는 없다.</b> OpenVidu 시절 코드가 남아 있었고
     * 실제 LiveKit 서버는 401 로 답한다. 이 메서드는 한 번도 동작한 적이 없다.
     * 공식 SDK 의 {@code RoomServiceClient} 로 바꿨다. ({@code LiveKitConfig})
     *
     * <p>타임아웃도 없었다. {@code new RestTemplate()} 의 기본값이 무한이라
     * LiveKit 이 응답하지 않으면 요청 스레드가 영원히 잡힌다.
     *
     * @return 룸 정보. 룸이 없으면 {@code null}
     * @throws LiveKitUnavailableException LiveKit 에 닿지 못했거나 제한 시간을 넘겼을 때
     */
    public Map<String, Object> getRoomInfo(String roomName, String email) {
        // 방 이름에서 회의를 찾아 그 클래스의 접근 권한을 본다. (#62)
        // 이 검사가 없어서 로그인만 하면 남의 방 정보를 읽을 수 있었다.
        Long meetingId = parseMeetingId(roomName);
        if (meetingId == null) {
            throw new AccessDeniedException("접근할 수 없는 방입니다.");
        }
        Long classId = meetingRepository.findById(meetingId)
                .map(m -> m.getClassRoom().getId())
                .orElseThrow(() -> new AccessDeniedException("접근할 수 없는 방입니다."));
        classAccessChecker.requireMember(classId, email);

        try {
            Response<List<LivekitModels.Room>> response =
                    roomServiceClient.listRooms(List.of(roomName)).execute();

            if (!response.isSuccessful()) {
                // 인증 실패·서버 오류. 룸이 없는 것과 다르다.
                throw new LiveKitUnavailableException(
                        "LiveKit 이 오류로 응답했습니다: HTTP " + response.code(), null);
            }

            List<LivekitModels.Room> rooms = response.body();
            if (rooms == null || rooms.isEmpty()) {
                // 룸이 없는 것은 정상적인 조회 결과다. 장애가 아니다.
                log.info("룸 없음: {}", roomName);
                return null;
            }
            return toMap(rooms.get(0));

        } catch (IOException e) {
            // 연결 실패·타임아웃. 404 로 뭉뚱그리면 클라이언트는 "삭제됐구나"로
            // 오해하고 운영에서는 LiveKit 장애가 보이지 않는다.
            log.error("LiveKit 응답 없음 - room={}", roomName, e);
            throw new LiveKitUnavailableException("LiveKit 에 연결할 수 없습니다.", e);
        }
    }

    private static Map<String, Object> toMap(LivekitModels.Room room) {
        Map<String, Object> result = new HashMap<>();
        result.put("sid", room.getSid());
        result.put("name", room.getName());
        result.put("numParticipants", room.getNumParticipants());
        result.put("numPublishers", room.getNumPublishers());
        result.put("creationTime", room.getCreationTime());
        result.put("emptyTimeout", room.getEmptyTimeout());
        result.put("maxParticipants", room.getMaxParticipants());
        return result;
    }

    public MeetingCreateResponseDto create(Long memberId, MeetingCreateRequestDto meetingCreateRequestDto) {
        log.info("📝 미팅 생성 시작 - memberId: {}, classId: {}, title: {}", 
                memberId, meetingCreateRequestDto.getClassId(), meetingCreateRequestDto.getTitle());
        
        ClassRoom classRoom = classRepository.findById(meetingCreateRequestDto.getClassId())
                .orElseThrow(() -> {
                    log.error("❌ 클래스를 찾을 수 없습니다 - classId: {}", meetingCreateRequestDto.getClassId());
                    return new IllegalArgumentException("클래스를 찾을 수 없습니다.");
                });

        log.info("✅ 클래스 조회 완료 - classId: {}, classOwnerId: {}", 
                classRoom.getId(), classRoom.getMember().getId());

        // 수업 생성자 또는 참여자인지 확인
        boolean isCreator = classRoom.getMember().getId().equals(memberId);
        boolean isParticipant = classMemberRepository.existsByClassRoomIdAndMemberId(
                meetingCreateRequestDto.getClassId(), memberId);
        
        log.info("🔍 권한 확인 - isCreator: {}, isParticipant: {}", isCreator, isParticipant);

        if (!isCreator && !isParticipant) {
            log.error("❌ 권한 없음 - 요청자: {}, 클래스 소유자: {}, 참여자 여부: {}", 
                    memberId, classRoom.getMember().getId(), isParticipant);
            throw new IllegalArgumentException("해당 클래스의 생성자 또는 참여자가 아닙니다.");
        }

        Meeting meeting = Meeting.builder()
                .title(meetingCreateRequestDto.getTitle())
                .description(meetingCreateRequestDto.getDescription())
                .startTime(LocalDateTime.now())
                .classRoom(classRoom)
                .build();

        meetingRepository.save(meeting);
        log.info("✅ 미팅 생성 완료 - meetingId: {}, title: {}", meeting.getId(), meeting.getTitle());
        
        return MeetingCreateResponseDto.builder()
                .title(meeting.getTitle())
                .email(classRoom.getMember().getEmail())
                .meetingId(meeting.getId())
                .build();
    }

    /**
     * 화상강의를 종료한다.
     *
     * <p>이전에는 {@code email} 을 받으면서 쓰지 않아 <b>누구나 남의 회의를 종료할 수 있었다.</b>
     * 그리고 DB 의 {@code endTime} 만 찍고 <b>LiveKit 룸은 그대로 두어</b> 참가자들이 계속
     * 연결된 채로 남았다. (#23)
     *
     * <p>같은 서비스의 {@code getMeetingList} 는 생성자/참여자 권한을 검사한다.
     * 종료는 더 강한 권한이 필요하므로 <b>클래스 생성자만</b> 허용한다.
     */
    @Transactional
    public void endMeeting(String email, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("미팅을 찾을 수 없습니다."));

        String ownerEmail = meeting.getClassRoom().getMember().getEmail();
        if (!ownerEmail.equals(email)) {
            throw new AccessDeniedException("화상강의는 클래스 생성자만 종료할 수 있습니다.");
        }

        meeting.endNow();

        // 참가 기록을 닫는다. 이게 없으면 정원이 반환되지 않는다.
        int closed = meetingParticipantRepository.closeAllActive(meetingId, LocalDateTime.now());
        log.info("화상강의 종료 - meetingId={}, 정리한 참가 기록={}건", meetingId, closed);

        // LiveKit 룸을 실제로 닫는다. DB 만 바꾸면 참가자는 계속 연결돼 있다.
        deleteLiveKitRoom(meeting);
    }

    /**
     * LiveKit 룸을 삭제한다.
     *
     * <p>실패해도 회의 종료 자체는 되돌리지 않는다. 룸은 비어 있으면 LiveKit 이
     * {@code emptyTimeout} 후 자동으로 정리하므로, 여기서 실패해도 최종적으로는 회복된다.
     * 반대로 예외를 던지면 DB 종료 처리까지 롤백되어 상태가 더 나빠진다.
     */
    private void deleteLiveKitRoom(Meeting meeting) {
        String roomName = "meeting-" + meeting.getId();
        try {
            Response<Void> response = roomServiceClient.deleteRoom(roomName).execute();
            if (!response.isSuccessful()) {
                log.warn("LiveKit 룸 삭제 실패 - room={}, HTTP {}", roomName, response.code());
            }
        } catch (IOException e) {
            log.warn("LiveKit 룸 삭제 실패 - room={}", roomName, e);
        }
    }

    /**
     * 참가자를 세션에서 내보낸다. LiveKit Webhook 이 {@code participant_left} 를 보낼 때 호출된다.
     *
     * <p>정원 반환의 실제 경로다. 클라이언트가 명시적으로 나가지 않고 브라우저를 닫아도
     * LiveKit 이 이 이벤트를 보내주므로 정원이 회수된다. (#23)
     */
    @Transactional
    public void releaseParticipant(String roomName, String participantEmail) {
        Long meetingId = parseMeetingId(roomName);
        if (meetingId == null) {
            log.debug("우리가 만든 룸이 아니다 - room={}", roomName);
            return;
        }
        meetingParticipantRepository.findActive(meetingId, participantEmail)
                .ifPresent(MeetingParticipant::leave);
    }

    /** 룸 전체가 끝났을 때 남아 있는 참가 기록을 모두 닫는다. */
    @Transactional
    public void releaseRoom(String roomName) {
        Long meetingId = parseMeetingId(roomName);
        if (meetingId == null) {
            return;
        }
        int closed = meetingParticipantRepository.closeAllActive(meetingId, LocalDateTime.now());
        log.info("룸 종료로 참가 기록 정리 - meetingId={}, {}건", meetingId, closed);
    }

    /** {@code meeting-{id}} 형식에서 id 를 뽑는다. 형식이 다르면 null. */
    private Long parseMeetingId(String roomName) {
        if (roomName == null || !roomName.startsWith("meeting-")) {
            return null;
        }
        try {
            return Long.parseLong(roomName.substring("meeting-".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<ClassMeetingInfoResponseDto> getMeetingList(String email, Long classId) {
        log.info("📋 미팅 목록 조회 시작 - email: {}, classId: {}", email, classId);
        
        ClassRoom classRoom = classRepository.findById(classId)
                .orElseThrow(() -> {
                    log.error("❌ 클래스를 찾을 수 없습니다 - classId: {}", classId);
                    return new IllegalArgumentException("클래스를 찾을 수 없습니다.");
                });

        // 사용자 ID 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("❌ 사용자를 찾을 수 없습니다 - email: {}", email);
                    return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                });

        // 수업 생성자 또는 참여자인지 확인
        boolean isCreator = classRoom.getMember().getEmail().equals(email);
        boolean isParticipant = classMemberRepository.existsByClassRoomIdAndMemberId(classId, member.getId());
        
        log.info("🔍 권한 확인 - isCreator: {}, isParticipant: {}", isCreator, isParticipant);

        if (!isCreator && !isParticipant) {
            log.error("❌ 권한 없음 - 요청자: {}, 클래스 소유자: {}, 참여자 여부: {}", 
                    email, classRoom.getMember().getEmail(), isParticipant);
            throw new IllegalArgumentException("해당 클래스의 생성자 또는 참여자가 아닙니다.");
        }

        List<ClassMeetingInfoResponseDto> meetings = meetingRepository.findAllSortedByNullFirst(classId)
                .stream()
                .map(m -> ClassMeetingInfoResponseDto.builder()
                        .meetingId(m.getId())
                        .title(m.getTitle())
                        .description(m.getDescription())
                        .startTime(m.getStartTime())
                        .endTime(m.getEndTime())
                        .s3url(m.getS3url())
                        .build())
                .toList();
        
        log.info("✅ 미팅 목록 조회 완료 - 미팅 수: {}", meetings.size());
        return meetings;
    }

    /**
     * 세션에 참가하고 입장 토큰을 발급한다.
     *
     * 세션 형태에 따라 두 가지가 갈린다. (#2)
     *
     * <pre>
     *                   INTERACTIVE            BROADCAST
     *   정원            classRoom 의 정원 적용   제한 없음
     *   발행 권한       허용                    진행자만 허용
     * </pre>
     *
     * 정원 검증은 "현재 인원을 세고 -> 정원과 비교하고 -> 참가를 기록한다"의 세 단계다.
     * 원자적이지 않으면 동시 요청이 모두 검사를 통과해 초과 입장이 생긴다.
     * 세션 행에 쓰기 잠금을 걸어 이 구간을 직렬화한다.
     */
    @Transactional
    public Map<String, Object> joinSession(Long meetingId, String participantEmail, boolean isHost) {
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + meetingId));

        // 이미 참가 중이면 정원을 다시 소비하지 않는다 (새로고침·재접속)
        Optional<MeetingParticipant> already =
                meetingParticipantRepository.findActive(meetingId, participantEmail);

        if (already.isEmpty()) {
            if (meeting.hasParticipantLimit()) {
                long current = meetingParticipantRepository.countActiveByMeetingId(meetingId);
                if (current >= meeting.participantLimit()) {
                    throw new SessionCapacityExceededException(
                            "정원이 가득 찼습니다. (정원 " + meeting.participantLimit() + "명)");
                }
            }
            meetingParticipantRepository.save(MeetingParticipant.join(meeting, participantEmail));
        }

        return createToken(meeting, participantEmail, isHost);
    }

    /** 세션에서 나간다. 정원을 다시 확보하기 위해 필요하다. */
    @Transactional
    public void leaveSession(Long meetingId, String participantEmail) {
        meetingParticipantRepository.findActive(meetingId, participantEmail)
                .ifPresent(MeetingParticipant::leave);
    }

    /**
     * 세션 형태에 맞는 LiveKit 토큰을 만든다.
     *
     * 라이브방송 시청자에게 발행 권한을 주지 않는 것이 핵심이다.
     * 이전에는 모든 참가자에게 RoomJoin(true) 만 부여해 누구나 발행할 수 있었다.
     */
    private Map<String, Object> createToken(Meeting meeting, String participantName, boolean isHost) {
        boolean canPublish = isHost || meeting.getSessionType().allowsParticipantPublish();
        String roomName = "meeting-" + meeting.getId();

        AccessToken token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
        token.setName(participantName);
        token.setIdentity(participantName);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(canPublish),
                new CanSubscribe(true)
        );
        token.setTtl(Duration.ofHours(6).toMillis());

        log.info("입장 토큰 발급 - meetingId={}, type={}, participant={}, canPublish={}",
                meeting.getId(), meeting.getSessionType(), participantName, canPublish);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token.toJwt());
        response.put("url", OPENVIDU_URL);
        response.put("roomName", roomName);
        response.put("participantName", participantName);
        response.put("sessionType", meeting.getSessionType());
        response.put("canPublish", canPublish);
        return response;
    }
}
