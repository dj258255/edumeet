package com.edu.edumeet.openvidu.domain;

/**
 * 세션 형태.
 *
 * 프로토콜이 다르고, 그에 따라 백엔드가 적용하는 규칙이 전부 갈린다.
 *
 * <pre>
 *                  INTERACTIVE(화상강의)   BROADCAST(라이브방송)
 *   프로토콜        WebRTC                 LL-HLS
 *   지연            1초 미만                수 초
 *   정원            제한한다                제한하지 않는다
 *   참여 권한       발언·화면공유           시청 + 채팅
 *   비용 축         서버 CPU               대역폭
 * </pre>
 *
 * 정원을 화상에만 두는 이유:
 * WebRTC 는 SFU 가 참가자 수만큼 스트림을 중계하므로 CPU 한계가 곧 인원 한계다.
 * 반면 LL-HLS 는 세그먼트를 배포하는 구조라 인원이 늘어도 서버 연산이 늘지 않는다.
 * 늘어나는 것은 대역폭 비용이다.
 */
public enum SessionType {

    /** 양방향 화상강의. 정원 제한이 있고 참가자가 발행할 수 있다. */
    INTERACTIVE,

    /** 단방향 라이브방송. 정원 제한이 없고 시청자는 발행할 수 없다. */
    BROADCAST;

    /** 정원 제한을 적용하는 형태인가. */
    public boolean hasParticipantLimit() {
        return this == INTERACTIVE;
    }

    /** 일반 참가자가 미디어를 발행할 수 있는 형태인가. */
    public boolean allowsParticipantPublish() {
        return this == INTERACTIVE;
    }
}
