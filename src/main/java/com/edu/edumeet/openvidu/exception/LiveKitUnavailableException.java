package com.edu.edumeet.openvidu.exception;

/**
 * LiveKit 서버에 닿지 못했거나 제한 시간 안에 답하지 않았다.
 *
 * <p>"방이 없다"와 구분해야 한다. 방이 없는 것은 정상적인 조회 결과(404)지만,
 * 서버에 닿지 못한 것은 우리 쪽 의존 시스템의 장애(503)다. 이를 404 로 뭉뚱그리면
 * 클라이언트가 "방이 삭제되었구나"로 오해하고, 운영에서는 장애가 보이지 않는다.
 */
public class LiveKitUnavailableException extends RuntimeException {
    public LiveKitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
