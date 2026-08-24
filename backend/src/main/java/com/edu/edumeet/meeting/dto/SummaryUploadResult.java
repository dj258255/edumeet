package com.edu.edumeet.meeting.dto;

/**
 * 요약본 업로드 결과. (#27)
 *
 * @param alreadyExisted 이미 요약본이 있어서 이번 호출이 아무것도 바꾸지 않은 경우 true.
 *                       파이썬이 재시도했을 때 중복 업로드를 만들지 않았다는 뜻이다.
 */
public record SummaryUploadResult(
        Long meetingId,
        Long classId,
        String markdownUrl,
        String pdfUrl,
        boolean alreadyExisted
) {}
