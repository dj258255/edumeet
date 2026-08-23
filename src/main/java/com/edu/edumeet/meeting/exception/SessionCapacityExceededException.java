package com.edu.edumeet.meeting.exception;

/** 화상강의 세션의 정원이 가득 찼을 때. */
public class SessionCapacityExceededException extends RuntimeException {
    public SessionCapacityExceededException(String message) {
        super(message);
    }
}
