package com.edu.edumeet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 다시보기 채팅 배치 flush 에 필요하다. (#61)
// 이게 없으면 @Scheduled 가 등록되지 않고, 큐는 차기만 하다 버린다 -
// 에러도 안 나고 지표(chat.archive.dropped)만 조용히 오른다.
@EnableScheduling
@SpringBootApplication
@EnableJpaAuditing //BaseEntity
public class EduMeetApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduMeetApplication.class, args);
    }
}
