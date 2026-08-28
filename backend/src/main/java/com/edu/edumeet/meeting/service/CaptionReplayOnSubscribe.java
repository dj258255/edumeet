package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자막을 구독하는 순간, 최근 것을 그 사람에게만 밀어 준다. (#165)
 *
 * <h3>왜 구독 시점인가</h3>
 * <b>서버는 재접속을 알아볼 방법이 없다.</b> 끊겼다 붙은 사람과 처음 들어온 사람이
 * 똑같이 SUBSCRIBE 로 들어온다. 그래서 재접속을 감지하려 하지 않고
 * <b>구독하는 모두에게</b> 최근 자막을 준다.
 *
 * <p>처음 들어온 사람에게도 직전 자막이 몇 개 보이는데, 그건 손해가 아니라 이득이다 —
 * 강의 중간에 들어온 사람이 맥락 없이 시작하지 않는다.
 *
 * <h3>왜 목적지로 안 보내고 채널로 직접 보내나</h3>
 * {@code convertAndSend(destination, ...)} 는 <b>그 방의 모두에게</b> 간다.
 * 한 명이 재접속할 때마다 방 전체가 지난 자막을 다시 받으면 고치려던 것보다 나쁘다.
 *
 * <p>{@code /user/} 목적지를 쓰는 방법도 있지만 <b>클라이언트가 목적지를 하나 더
 * 구독해야 한다.</b> 대신 <b>세션 아이디와 구독 아이디를 헤더에 넣어
 * {@code clientOutboundChannel} 로 직접</b> 보낸다. 그러면
 * <b>방금 구독한 그 연결로만</b> 가고, 클라이언트는 이미 걸어 둔 핸들러로 받는다.
 *
 * <p>{@code subscriptionId} 가 없으면 안 된다. STOMP 클라이언트는 MESSAGE 프레임을
 * {@code subscription} 헤더로 자기 구독에 맞춘다. 없으면 프레임이 버려진다.
 *
 * <h3>이 경로는 발행 큐를 안 쓴다</h3>
 * {@link CaptionBroadcastQueue} 는 <b>밀리면 버리는</b> 큐다. 최신성을 지키려는 정책이라
 * 복구본과 맞지 않는다. 복구본은 버리면 구멍이 그대로 남는다.
 *
 * <p>대신 구독 스레드에서 직접 보낸다. 그래도 되는 이유는 <b>양이 정해져 있어서</b>다 —
 * 버퍼 상한(기본 60건)이 곧 이 경로의 상한이다. 무제한으로 커질 수 없다.
 */
@Component
@Slf4j
public class CaptionReplayOnSubscribe {

    /**
     * 놓친 구간을 받는 전용 목적지. 실시간 자막 목적지와 나눈 이유가 둘이다.
     *
     * <p><b>하나. 한 프레임으로 보내야 순서가 지켜진다.</b> 아웃바운드 채널은 스레드 풀이라
     * 프레임을 여러 개 보내면 <b>순서가 안 지켜진다</b>(실측 뒤집힘 80.71%).
     * 자막은 전사라 순서가 틀리면 내용이 틀린 것이다. 목록 하나로 보내면 그 문제가 없다.
     *
     * <p><b>둘. 실시간 자막 목적지에 배열을 흘리면 받는 쪽이 모양을 둘로 나눠 다뤄야 한다.</b>
     * 놓친 구간은 실시간 자막이 아니라 <b>지나간 구간</b>이므로 자리도 다르다.
     */
    private static final Pattern CAPTION_GAP_TOPIC =
            Pattern.compile("^/topic/rooms/(\\d+)/captions/gap$");

    private final CaptionReplayBuffer buffer;
    private final MessageChannel clientOutboundChannel;
    private final ObjectMapper objectMapper;
    private final Counter replayed;
    private final Counter subscriptions;

    public CaptionReplayOnSubscribe(
            CaptionReplayBuffer buffer,
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel,
            ObjectMapper objectMapper,
            MeterRegistry registry) {
        this.buffer = buffer;
        this.clientOutboundChannel = clientOutboundChannel;
        this.objectMapper = objectMapper;
        this.replayed = Counter.builder("caption.replay.sent")
                .description("구독 시점에 되돌려 보낸 자막 수").register(registry);
        this.subscriptions = Counter.builder("caption.replay.subscriptions")
                .description("복구를 받은 구독 수. 재접속이 잦으면 이 값이 는다").register(registry);
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor in = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = in.getDestination();
        String sessionId = in.getSessionId();
        String subscriptionId = in.getSubscriptionId();
        if (destination == null || sessionId == null || subscriptionId == null) return;

        Matcher m = CAPTION_GAP_TOPIC.matcher(destination);
        if (!m.matches()) return;

        List<CaptionBroadcast> recent = buffer.recent(Long.valueOf(m.group(1)));
        if (recent.isEmpty()) return;

        // 한 프레임에 담아 보낸다. 여러 개로 나누면 순서가 안 지켜진다.
        List<CaptionBroadcast> gap = recent.stream().map(CaptionBroadcast::asReplay).toList();
        if (sendToSession(sessionId, subscriptionId, destination, gap)) {
            replayed.increment(gap.size());
            subscriptions.increment();
            log.debug("자막 복구본 {}건을 한 프레임으로 보냈다. session={}", gap.size(), sessionId);
        }
    }

    private boolean sendToSession(String sessionId, String subscriptionId,
                                  String destination, List<CaptionBroadcast> payload) {
        try {
            SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            h.setSessionId(sessionId);
            h.setSubscriptionId(subscriptionId);
            h.setDestination(destination);
            h.setContentType(MimeTypeUtils.APPLICATION_JSON);
            h.setLeaveMutable(true);
            byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return clientOutboundChannel.send(
                    MessageBuilder.createMessage(body, h.getMessageHeaders()));
        } catch (Exception e) {
            // 한 건이 실패해도 나머지는 보낸다. 여기서 죽으면 구독 자체가 깨진다.
            log.warn("자막 복구본 전송 실패. session={} destination={}", sessionId, destination, e);
            return false;
        }
    }
}
