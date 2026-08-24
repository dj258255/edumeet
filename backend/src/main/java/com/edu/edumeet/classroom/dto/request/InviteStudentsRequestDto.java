package com.edu.edumeet.classroom.dto.request;

import lombok.Getter;

import java.util.List;

@Getter
public class InviteStudentsRequestDto {
    private List<String> emails;
}
