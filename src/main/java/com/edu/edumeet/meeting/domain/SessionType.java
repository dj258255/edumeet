package com.edu.edumeet.meeting.domain;

import java.util.List;

/**
 * 세션 형태.
 *
 * 프로토콜이 다르고, 그에 따라 백엔드가 적용하는 규칙이 전부 갈린다.
 *
 * <pre>
 *                  INTERACTIVE      BROADCAST        AUDIO_BROADCAST
 *                  (화상강의)        (라이브방송)       (오디오 방송)
 *   미디어          비디오+오디오      비디오+오디오      오디오만
 *   지연            1초 미만          수 초             수 초
 *   정원            제한한다          제한하지 않는다     제한하지 않는다
 *   참여 권한       발언·화면공유      시청 + 채팅        청취 + 채팅
 *   비용 축         서버 CPU          대역폭            대역폭 (비디오의 1/11~1/22)
 * </pre>
 *
 * 정원을 화상에만 두는 이유:
 * WebRTC 는 SFU 가 참가자 수만큼 스트림을 중계하므로 CPU 한계가 곧 인원 한계다.
 * 반면 방송은 세그먼트를 배포하는 구조라 인원이 늘어도 서버 연산이 늘지 않는다.
 * 늘어나는 것은 대역폭 비용이다.
 */
public enum SessionType {

    /** 양방향 화상강의. 정원 제한이 있고 참가자가 발행할 수 있다. */
    INTERACTIVE,

    /** 단방향 라이브방송. 정원 제한이 없고 시청자는 발행할 수 없다. */
    BROADCAST,

    /**
     * 오디오 전용 라이브방송. (#65)
     *
     * <p><b>라디오는 청각장애인에게 접근이 원천 차단된 매체다.</b>
     * 자막이 붙는 TV·영화와 달리 오디오 전용 방송에는 붙을 자리가 없다.
     * 이 서비스는 이미 STT 자막 파이프라인을 갖고 있으므로,
     * <b>입구만 만들면 라디오를 "볼 수 있게" 된다.</b>
     *
     * <p>대역폭은 비디오의 1/11~1/22 다. 다만 <b>싸서 하는 게 아니라 접근성 때문에 한다.</b>
     * 싸다는 사실은 "개인 서버에서 실측 가능하다" 를 만들 뿐이다.
     */
    AUDIO_BROADCAST;

    /** 정원 제한을 적용하는 형태인가. */
    public boolean hasParticipantLimit() {
        return this == INTERACTIVE;
    }

    /** 일반 참가자가 미디어를 발행할 수 있는 형태인가. */
    public boolean allowsParticipantPublish() {
        return this == INTERACTIVE;
    }

    /** 비디오 트랙 없이 오디오만 다루는가. */
    public boolean isAudioOnly() {
        return this == AUDIO_BROADCAST;
    }

    /**
     * 발행자에게 허용할 미디어 소스. 비어 있으면 <b>제한하지 않는다</b>는 뜻이다. (#72)
     *
     * <p>LiveKit 은 토큰에 {@code canPublishSources} 가 없으면 모든 소스를 허용한다.
     * 그래서 비디오 세션은 의도적으로 비워 둔다 -
     * <b>여기에 카메라·마이크·화면공유를 나열해 봐야 표현되는 정책이 없다.</b>
     * 오늘의 소스 목록을 얼려서, LiveKit 이 소스를 추가하면 화상 세션이 조용히 막히게 만들 뿐이다.
     *
     * <p>반대로 오디오 방송에는 <b>표현할 정책이 있다.</b> 마이크만이다.
     * 이 목록은 서명된 JWT 안에 들어가므로 SFU 가 강제한다 -
     * 클라이언트를 고쳐도, 토큰을 다른 SDK 에 넣어도 비디오는 올라가지 않는다.
     *
     * <p><b>왜 서버가 막아야 하는가.</b> 실측한 egress 는 시청자당 1.42 Mbps 였고 비디오 기준이다.
     * 오디오 전용의 비용 모델은 그 전제 위에 서 있는데,
     * 전제를 서버가 지키지 않으면 <b>비용 계산이 근거를 잃는다.</b>
     */
    public List<String> publishableSources() {
        // screen_share_audio 는 뺐다. 라디오에서 시스템 오디오(음원 재생)를 허용할지는
        // 기술 문제가 아니라 저작권·편성 문제라, 필요해지면 그때 정한다.
        return this == AUDIO_BROADCAST ? List.of("microphone") : List.of();
    }

    /**
     * 채팅을 발행 경로에서 동기로 저장하는가.
     *
     * <p>방송(비디오·오디오)은 저장하지 않는다. 저장 자체를 안 한다는 뜻이 아니라
     * <b>발행 경로에서 하지 않는다</b>는 뜻이다 - 다시보기용 비동기 저장은 #61 에서 다룬다.
     * 그리고 #43 에서 확인했듯 <b>발행 경로의 DB 쓰기는 브로드캐스트 측정을 가린다.</b>
     */
    public boolean persistsChatInline() {
        return this == INTERACTIVE;
    }
}
