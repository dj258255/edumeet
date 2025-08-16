package com.edu.edumeet.openvidu.service;

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
}
