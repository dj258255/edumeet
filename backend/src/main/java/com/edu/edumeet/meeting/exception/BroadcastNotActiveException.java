package com.edu.edumeet.meeting.exception;

/** 방송 프로세스가 더 이상 살아 있지 않아 조각을 받을 수 없을 때. */
public class BroadcastNotActiveException extends RuntimeException {
    public BroadcastNotActiveException(String message) {
        super(message);
    }
}
