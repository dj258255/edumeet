package com.edu.edumeet.openvidu.repository;

import com.edu.edumeet.openvidu.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
}
