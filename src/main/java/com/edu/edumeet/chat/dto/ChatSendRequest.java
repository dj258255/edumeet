package com.edu.edumeet.chat.dto;

/** 클라이언트가 보내는 것. 보낸 사람은 헤더의 Principal 에서 읽는다 - 페이로드를 믿지 않는다. */
public record ChatSendRequest(String content) {}
