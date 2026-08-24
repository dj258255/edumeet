package com.edu.edumeet.email.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailVarificationRequest {

    private String email;
    private String code;

}
