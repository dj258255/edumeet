package com.edu.edumeet.openvidu.service;

import io.livekit.server.CanSubscribe;
import io.livekit.server.CanPublish;
import com.edu.edumeet.openvidu.exception.SessionCapacityExceededException;
import com.edu.edumeet.openvidu.repository.MeetingParticipantRepository;
import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.repository.MemberRepository;
import com.edu.edumeet.openvidu.domain.Meeting;
import com.edu.edumeet.openvidu.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.openvidu.dto.response.ClassMeetingInfoResponseDto;
import com.edu.edumeet.openvidu.dto.response.MeetingCreateResponseDto;
import com.edu.edumeet.openvidu.repository.MeetingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenviduService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final ClassRepository classRepository;
    private final ClassMemberRepository classMemberRepository;
    private final MemberRepository memberRepository;

    // OpenVidu 인증 정보 (application.properties에 추가)
    @Value("${openvidu.livekit.api.key}")
    private String LIVEKIT_API_KEY;

    @Value("${openvidu.livekit.api.secret}")
    private String LIVEKIT_API_SECRET;

    @Value("${openvidu.url}")
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
     * 룸 정보 조회 (OpenVidu Admin API 사용)
     */
    public Map<String, Object> getRoomInfo(String roomName) {
        RestTemplate restTemplate = new RestTemplate();

        String url = "http://localhost:7880/api/v1/rooms/" + roomName;


        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            return response.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            log.info("Room not found: {}", roomName);
            return null;
        } catch (Exception e) {
            log.error("Failed to get room info", e);
            throw new RuntimeException("Failed to get room info: " + e.getMessage());
        }
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

    @Transactional
    public void endMeeting(String email, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("미팅을 찾을 수 없습니다."));

        meeting.endNow();
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
