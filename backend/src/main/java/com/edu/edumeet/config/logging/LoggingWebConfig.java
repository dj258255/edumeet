package com.edu.edumeet.config.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 핸들러 매핑 결과를 로그 컨텍스트에 반영한다. */
@Configuration
public class LoggingWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MeetingIdLogInterceptor()).order(0);
    }
}
