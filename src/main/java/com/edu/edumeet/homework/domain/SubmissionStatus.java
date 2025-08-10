package com.edu.edumeet.homework.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionStatus {
    NOT_SUBMITTED("미제출"),
    SUBMITTED("제출완료");

    private final String displayName;
}