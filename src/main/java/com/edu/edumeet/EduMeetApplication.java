package com.edu.edumeet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing //BaseEntity
public class EduMeetApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduMeetApplication.class, args);
    }
}
