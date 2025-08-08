package com.edu.edumeet.homework.submission.domain;

public enum SubmissionStatus {
    SUBMITTED("제출완료"),
    NOT_SUBMITTED("미제출");

    private final String description;


    SubmissionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
