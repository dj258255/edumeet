package com.edu.edumeet.meeting.domain;

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
