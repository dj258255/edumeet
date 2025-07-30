package com.edu.edumeet.email.application;

public interface AuthCodeService {

    void saveAuthCode(String email, String code);
    boolean verifyCode(String email, String code);


}
