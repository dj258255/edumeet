package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.config.HlsProperties;
import com.edu.edumeet.meeting.domain.HlsEgressPlan;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import io.livekit.server.EgressServiceClient;
import livekit.LivekitEgress.EgressInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 방송 세션을 HLS 로 내보낸다. (#75)
 *
 * <p>요청을 만드는 일은 {@link HlsEgressPlanner} 가 하고, 여기서는 <b>부르고 기록하는 일</b>만 한다.
 * 나눈 이유는 함정이 전부 요청 쪽에 있어서 그쪽만 순수하게 테스트하고 싶었기 때문이다.
 *
 * <p><b>실패를 삼키지 않는다.</b> egress 시작 실패는 방송이 안 나가는 것과 같으므로
 * 호출자에게 그대로 올린다. 특히 {@code ErrNotEnoughCPU} 는
 * <b>설정 실수가 아니라 인스턴스 용량 부족</b>이라 조용히 넘기면 원인을 찾을 수 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HlsEgressService {

    private final EgressServiceClient egressServiceClient;
    private final HlsEgressPlanner planner;
    private final MeetingRepository meetingRepository;
    private final HlsProperties properties;

    @Transactional
    public String start(String email, Long meetingId) {
        Meeting meeting = requireHost(email, meetingId);

        if (!properties.isEnabled()) {
            throw new IllegalStateException("HLS 가 꺼져 있습니다. egress 인스턴스가 붙어 있어야 켭니다.");
        }
        if (meeting.getHlsEgressId() != null) {
            return meeting.getHlsEgressId();  // 이미 내보내는 중이면 두 번 시작하지 않는다
        }

        HlsEgressPlan plan = planner.plan(meeting);
        log.info("HLS egress 시작 - meetingId={}, type={}, audioOnly={}, cpuCost={}",
                meetingId, meeting.getSessionType(), plan.audioOnly(), plan.estimatedCpuCost());

        EgressInfo info = execute(egressServiceClient.startRoomCompositeEgress(
                plan.roomName(),
                plan.output(),
                "",              // layout - 오디오 전용에서는 쓰이지 않는다
                null,            // preset - 기본값(H264_720P_30). 오디오면 무시된다
                null,            // advanced
                plan.audioOnly(),
                false,           // videoOnly - 채팅과 함께 소리가 나가야 한다
                ""               // customBaseUrl
        ));

        meeting.startHls(info.getEgressId(), planner.livePlaylistUrl(meetingId));
        return info.getEgressId();
    }

    @Transactional
    public void stop(String email, Long meetingId) {
        Meeting meeting = requireHost(email, meetingId);

        String egressId = meeting.getHlsEgressId();
        if (egressId == null) {
            return;
        }
        execute(egressServiceClient.stopEgress(egressId));

        // ★ 플레이리스트 주소는 지우지 않는다.
        //   방송이 끝나면 live.m3u8 은 의미가 없어지지만 index.m3u8 은 다시보기로 남는다.
        meeting.stopHls();
        log.info("HLS egress 중지 - meetingId={}, egressId={}", meetingId, egressId);
    }

    /**
     * egress 는 아무나 시작하면 안 된다.
     *
     * <p>권한 문제이기 이전에 <b>자원 문제다.</b> egress 하나가 코어를 1~4개 먹고,
     * 인스턴스 하나가 방 하나만 처리한다. 참가자가 시작할 수 있으면
     * <b>한 명이 인스턴스를 통째로 점유해 다른 방송을 전부 막을 수 있다.</b>
     */
    private Meeting requireHost(String email, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + meetingId));
        String ownerEmail = meeting.getClassRoom().getMember().getEmail();
        if (!ownerEmail.equals(email)) {
            throw new AccessDeniedException("방송 송출은 클래스 생성자만 제어할 수 있습니다.");
        }
        return meeting;
    }

    private <T> T execute(retrofit2.Call<T> call) {
        try {
            retrofit2.Response<T> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                String body = response.errorBody() == null ? "" : safeRead(response.errorBody());
                throw new IllegalStateException(
                        "LiveKit egress 호출 실패 (HTTP " + response.code() + "): " + body);
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("LiveKit egress 에 연결하지 못했습니다.", e);
        }
    }

    private String safeRead(okhttp3.ResponseBody body) {
        try {
            return body.string();
        } catch (IOException e) {
            return "(응답 본문을 읽지 못했다)";
        }
    }
}
